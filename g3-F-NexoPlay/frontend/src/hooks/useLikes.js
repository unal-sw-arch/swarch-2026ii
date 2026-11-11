import { useEffect, useState } from "react";
import { fetchLikes, setLike } from "../api/interactions";

// Like count and the viewer's own like, with optimistic toggling.
// The server applies likes asynchronously, so after a toggle the local state
// is the source of truth instead of an immediate (possibly stale) refetch.
export default function useLikes(videoId, token) {
  const key = `${videoId}|${token ?? ""}`;
  const [state, setState] = useState({ key: null, count: 0, liked: false, error: "" });
  const [saving, setSaving] = useState(false);
  const [toggleError, setToggleError] = useState("");

  useEffect(() => {
    let ignore = false;
    fetchLikes(videoId, token)
      .then(({ count, liked }) => !ignore && setState({ key, count, liked, error: "" }))
      .catch((err) => !ignore && setState({ key, count: 0, liked: false, error: err.message }));
    return () => {
      ignore = true;
    };
  }, [videoId, token, key]);

  const toggle = async () => {
    if (!token || saving || state.key !== key) return;
    const previous = state;
    const liked = !previous.liked;
    setSaving(true);
    setToggleError("");
    setState({ ...previous, liked, count: Math.max(0, previous.count + (liked ? 1 : -1)) });
    try {
      await setLike(videoId, liked, token);
    } catch (err) {
      setState(previous);
      setToggleError(err.message);
    } finally {
      setSaving(false);
    }
  };

  const current = state.key === key;
  return {
    count: current ? state.count : 0,
    liked: current && state.liked,
    loading: !current,
    error: current ? state.error : "",
    toggleError,
    saving,
    toggle,
  };
}
