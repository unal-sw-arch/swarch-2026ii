import { request, STREAMING, streamingRequest } from "./client";
import { formatDuration } from "../utils/format";

// Streaming service video -> the shape the UI components use
const toVideo = (v) => ({
  id: v.id,
  ownerId: v.ownerId,
  title: v.title,
  description: v.description,
  duration: formatDuration(v.durationSeconds),
  thumbnailUrl: v.hasThumbnail
    ? `${STREAMING}/videos/${v.id}/thumbnail`
    : undefined,
});

// One page of the public catalog (no token): completed videos, newest first.
// Pass the previous page's nextCursor to continue; null means no more pages.
export async function fetchCatalog({ limit = 20, cursor } = {}) {
  const params = new URLSearchParams({ limit: String(limit) });
  if (cursor) params.set("cursor", cursor);

  const page = await streamingRequest(`/videos?${params}`);
  return { videos: page.items.map(toVideo), nextCursor: page.nextCursor };
}

// One completed video by id, independent of the catalog page size
export async function fetchVideo(id) {
  return toVideo(await streamingRequest(`/videos/${encodeURIComponent(id)}`));
}

export function uploadVideo({ file, title, description, category, token }) {
  const form = new FormData();

  form.append(
    "metadata",
    JSON.stringify({ title, description, category }),
  );
  form.append("video", file);

  return request("/api/videos", {
    method: "POST",
    body: form,
    token,
  });
}

export function fetchUploadStatus(videoId, token) {
  return request(`/api/videos/${encodeURIComponent(videoId)}`, { token });
}

export function deleteVideo(videoId, token) {
  return request(`/api/videos/${encodeURIComponent(videoId)}`, {
    method: "DELETE",
    token,
  });
}
