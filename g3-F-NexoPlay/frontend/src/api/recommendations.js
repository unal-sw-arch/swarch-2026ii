import { request, STREAMING } from "./client";

function toVideo(item) {
  if (!item || typeof item.video_id !== "string" || !item.title) {
    throw new Error("Recommendation service returned an invalid video");
  }

  const thumbnailUrl = item.thumbnail_url
    ? new URL(item.thumbnail_url, STREAMING).href
    : undefined;

  return {
    id: item.video_id,
    title: item.title,
    category: item.category ?? null,
    thumbnailUrl,
    score: item.score,
  };
}

export async function fetchRecommendations({ token, limit = 20 } = {}) {
  const params = new URLSearchParams({ limit: String(limit) });
  const result = await request(`/api/recommendations?${params}`, { token });

  return {
    mode: result.mode,
    videos: result.items.map(toVideo),
  };
}
