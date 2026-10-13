import { useEffect, useState } from "react";
import { UploadIcon } from "../components/Icons";
import { useAuth } from "../auth/useAuth";
import { fetchUploadStatus, uploadVideo } from "../api/videos";
import { statusColors } from "../utils/format";

export default function Upload() {
  const { token } = useAuth();
  const [file, setFile] = useState(null);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState("");
  const [upload, setUpload] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState("");

  // --- 1. DEFINIMOS LAS CONSTANTES DE ESTADO AQUÍ ---
  const currentStatus = upload?.processing?.status ?? "Unknown";
  const colorClass = statusColors[currentStatus] || "text-zinc-400";
  // --------------------------------------------------

  useEffect(() => {
    const videoId = upload?.video_id;
    const status = upload?.processing?.status;

    if (!videoId || !token || !["PENDING", "PROCESSING"].includes(status)) {
      return undefined;
    }

    let cancelled = false;
    let timeout;

    const pollStatus = async () => {
      try {
        const latest = await fetchUploadStatus(videoId, token);
        if (cancelled) return;

        setUpload((current) => ({ ...current, ...latest }));
        if (["PENDING", "PROCESSING"].includes(latest.processing?.status)) {
          timeout = setTimeout(pollStatus, 3000);
        }
      } catch (pollError) {
        if (!cancelled) {
          setError(`Could not check processing status: ${pollError.message}`);
        }
      }
    };

    timeout = setTimeout(pollStatus, 3000);
    return () => {
      cancelled = true;
      clearTimeout(timeout);
    };
  }, [upload?.video_id, upload?.processing?.status, token]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setUpload(null);
    setUploading(true);

    try {
      const result = await uploadVideo({
        file,
        title,
        description,
        category,
        token,
      });
      setUpload(result);
    } catch (uploadError) {
      setError(uploadError.message);
    } finally {
      setUploading(false);
    }
  };

  return (
    <main className="mx-auto max-w-3xl px-4 pt-28 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">Upload video</h1>
      <p className="mt-2 text-zinc-400">
        Share a video with everyone browsing the catalog.
      </p>
      <form
        onSubmit={handleSubmit}
        className="mt-8 space-y-5 rounded-xl border border-zinc-800 bg-zinc-900/40 p-6"
      >
        <label className="block space-y-2">
          <span className="text-sm font-medium">Title</span>
          <input
            required
            maxLength={255}
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            className="w-full rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2"
          />
        </label>
        <label className="block space-y-2">
          <span className="text-sm font-medium">Description</span>
          <textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            rows={3}
            className="w-full rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2"
          />
        </label>
        <label className="block space-y-2">
          <span className="text-sm font-medium">Category</span>
          <input
            maxLength={100}
            value={category}
            onChange={(event) => setCategory(event.target.value)}
            className="w-full rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2"
          />
        </label>
        <label className="block space-y-2">
          <span className="text-sm font-medium">Video file</span>
          <span className="flex items-center gap-3 rounded-lg border border-dashed border-zinc-700 p-4">
            <UploadIcon className="size-5 shrink-0 text-zinc-300" />
            <input
              required
              type="file"
              accept=".mp4,.avi,.mov,video/mp4,video/quicktime,video/x-msvideo"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              className="min-w-0 text-sm"
            />
          </span>
          <span className="block text-xs text-zinc-500">
            Accepted formats: MP4, AVI, MOV.
          </span>
        </label>
        {error && (
          <p role="alert" className="text-sm text-red-400">
            {error}
          </p>
        )}
        <button
          type="submit"
          disabled={uploading || !file}
          className="rounded-lg bg-white px-5 py-2.5 font-semibold text-zinc-950 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {uploading ? "Uploading..." : "Upload video"}
        </button>
      </form>
      {upload && (
        <section
          aria-live="polite"
          className="mt-6 rounded-xl border border-zinc-800 p-5"
        >
          <p className="font-medium">{upload.message}</p>
          <p className="mt-2 text-sm text-zinc-400">
            Video ID: <code>{upload.video_id}</code>
          </p>
          <p className="mt-1 text-sm">
            <span className="text-zinc-400">Processing status: </span>
            <span className={`font-semibold ${colorClass}`}>
              {currentStatus}
            </span>
          </p>
        </section>
      )}
    </main>
  );
}
