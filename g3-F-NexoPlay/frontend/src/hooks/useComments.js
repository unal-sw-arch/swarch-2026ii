import { useCallback, useEffect, useRef, useState } from "react";
import { fetchComments, postComment, waitForEvent } from "../api/interactions";

const PAGE_SIZE = 10;

// Paginated comments for one video plus the viewer's own new comments.
// New comments are shown immediately with a status:
//   sending   -> the request is in flight
//   pending   -> accepted; the worker has not stored it yet
//   confirmed -> stored and visible to everyone
//   error     -> not accepted; can be retried or discarded
// Mount with key={videoId} so a new video starts from a clean state.
export default function useComments(videoId, token, { userId = null, userName = null } = {}) {
  const [page, setPage] = useState({ comments: [], nextCursor: null, error: "", loading: true });
  const [loadingMore, setLoadingMore] = useState(false);
  const [mine, setMine] = useState([]); // [{ localId, comment, status, error }]
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const loadFirst = useCallback(() => {
    setPage((p) => ({ ...p, loading: true, error: "" }));
    return fetchComments(videoId, { limit: PAGE_SIZE })
      .then(({ comments, nextCursor }) =>
        mounted.current && setPage({ comments, nextCursor, error: "", loading: false }))
      .catch((err) =>
        mounted.current && setPage({ comments: [], nextCursor: null, error: err.message, loading: false }));
  }, [videoId]);

  useEffect(() => {
    loadFirst();
  }, [loadFirst]);

  const loadMore = async () => {
    if (!page.nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const { comments, nextCursor } = await fetchComments(videoId, {
        limit: PAGE_SIZE,
        cursor: page.nextCursor,
      });
      if (!mounted.current) return;
      setPage((p) => ({ ...p, comments: [...p.comments, ...comments], nextCursor, error: "" }));
    } catch (err) {
      if (mounted.current) setPage((p) => ({ ...p, error: err.message }));
    } finally {
      if (mounted.current) setLoadingMore(false);
    }
  };

  const update = (localId, changes) =>
    mounted.current &&
    setMine((list) => list.map((m) => (m.localId === localId ? { ...m, ...changes } : m)));

  const send = async (localId, text) => {
    update(localId, { status: "sending", error: "" });
    let accepted;
    try {
      accepted = await postComment(videoId, text, token);
    } catch (err) {
      update(localId, { status: "error", error: err.message });
      return false;
    }
    update(localId, { status: "pending", comment: accepted.comment });
    try {
      const confirmed = await waitForEvent(accepted.eventId, token);
      update(localId, { status: confirmed ? "confirmed" : "pending" });
    } catch {
      // The comment was accepted by the broker; only the status check failed.
    }
    return true;
  };

  const add = (text) => {
    const localId = `local-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    setMine((list) => [
      { localId, status: "sending", error: "", comment: { id: localId, userId, authorName: userName, text, createdAt: new Date().toISOString() } },
      ...list,
    ]);
    return send(localId, text);
  };

  const retry = (localId) => {
    const entry = mine.find((m) => m.localId === localId);
    if (entry) send(localId, entry.comment.text);
  };

  const discard = (localId) => setMine((list) => list.filter((m) => m.localId !== localId));

  // Own comments first; drop any that the server list already contains.
  const serverIds = new Set(page.comments.map((c) => c.id));
  const items = [
    ...mine
      .filter((m) => !serverIds.has(m.comment.id))
      .map((m) => ({ ...m.comment, key: m.localId, localId: m.localId, status: m.status, error: m.error })),
    ...page.comments.map((c) => ({ ...c, key: c.id, status: "confirmed" })),
  ];

  return {
    items,
    loading: page.loading,
    error: page.error,
    hasMore: Boolean(page.nextCursor),
    loadingMore,
    loadMore,
    reload: loadFirst,
    add,
    retry,
    discard,
  };
}
