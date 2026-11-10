import { request } from "./client";

// Interactions Service through the API Gateway. Writes need a token and are
// asynchronous: they return { event_id, status: "PENDING" } and a worker
// persists them shortly after. Reads are public; a token adds liked_by_me.
const base = (videoId) => `/api/interactions/videos/${encodeURIComponent(videoId)}`;

const toComment = (c) => ({
  id: c.comment_id,
  userId: c.user_id,
  authorName: c.author_name ?? null,
  text: c.text,
  createdAt: c.created_at,
});

export function recordView(videoId, token) {
  return request(`${base(videoId)}/views`, { method: "POST", token });
}

export function setLike(videoId, liked, token) {
  return request(`${base(videoId)}/like`, {
    method: liked ? "PUT" : "DELETE",
    token,
  });
}

export async function fetchLikes(videoId, token) {
  const data = await request(`${base(videoId)}/likes`, { token });
  return { count: data.like_count, liked: Boolean(data.liked_by_me) };
}

// One page of comments, newest first. Pass the previous nextCursor to continue.
export async function fetchComments(videoId, { limit = 10, cursor } = {}) {
  const params = new URLSearchParams({ limit: String(limit) });
  if (cursor) params.set("cursor", cursor);
  const page = await request(`${base(videoId)}/comments?${params}`);
  return { comments: page.items.map(toComment), nextCursor: page.next_cursor };
}

export async function postComment(videoId, text, token) {
  const data = await request(`${base(videoId)}/comments`, {
    method: "POST",
    body: { text },
    token,
  });
  return { eventId: data.event_id, comment: toComment(data.comment) };
}

export async function fetchEventStatus(eventId, token) {
  const data = await request(
    `/api/interactions/events/${encodeURIComponent(eventId)}`,
    { token },
  );
  return data.status; // PENDING | CONFIRMED
}

// Polls until the worker has stored the event. Resolves true when confirmed,
// false if it is still pending after the last attempt.
export async function waitForEvent(eventId, token, { attempts = 8, delayMs = 750 } = {}) {
  for (let i = 0; i < attempts; i++) {
    await new Promise((resolve) => setTimeout(resolve, delayMs));
    if ((await fetchEventStatus(eventId, token)) === "CONFIRMED") return true;
  }
  return false;
}
