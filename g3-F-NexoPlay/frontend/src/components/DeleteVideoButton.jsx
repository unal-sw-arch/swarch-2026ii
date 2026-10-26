import { useId, useRef, useState } from "react";
import { deleteVideo } from "../api/videos";
import { useAuth } from "../auth/useAuth";

export default function DeleteVideoButton({ video, onDeleted }) {
  const { token, userId } = useAuth();
  const dialog = useRef(null);
  const titleId = useId();
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState("");

  if (!userId || !video?.ownerId || userId !== video.ownerId) return null;

  const open = () => {
    setError("");
    dialog.current.showModal();
  };

  const confirm = async () => {
    if (deleting) return;
    setDeleting(true);
    setError("");
    try {
      await deleteVideo(video.id, token);
    } catch (err) {
      setError(err.message);
      setDeleting(false);
      return;
    }
    dialog.current.close();
    setDeleting(false);
    onDeleted?.(video.id);
  };

  return (
    <>
      <button
        type="button"
        onClick={open}
        className="rounded-md border border-red-900/70 px-3 py-2 text-sm font-medium text-red-400 transition hover:bg-red-950/40 focus-visible:outline-2 focus-visible:outline-red-400"
        aria-label={`Delete video: ${video.title}`}
      >
        Delete video
      </button>
      <dialog
        ref={dialog}
        aria-labelledby={titleId}
        aria-busy={deleting}
        onCancel={(event) => { if (deleting) event.preventDefault(); }}
        className="fixed inset-0 m-auto w-[calc(100%-2rem)] max-w-md rounded-xl border border-zinc-700 bg-zinc-900 p-6 text-white shadow-xl backdrop:bg-black/70"
      >
        <h2 id={titleId} className="text-xl font-semibold">Delete video?</h2>
        <p className="mt-3 break-words text-sm text-zinc-300">
          “{video.title}” and all its video files will be permanently deleted. This cannot be undone.
        </p>
        {error && <p role="alert" className="mt-4 text-sm text-red-400">{error}</p>}
        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            onClick={() => dialog.current.close()}
            disabled={deleting}
            autoFocus
            className="rounded-md bg-zinc-800 px-4 py-2 text-sm hover:bg-zinc-700 disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={confirm}
            disabled={deleting}
            className="rounded-md bg-red-600 px-4 py-2 text-sm font-semibold hover:bg-red-500 disabled:cursor-wait disabled:opacity-50"
          >
            {deleting ? "Deleting…" : "Delete permanently"}
          </button>
        </div>
      </dialog>
    </>
  );
}
