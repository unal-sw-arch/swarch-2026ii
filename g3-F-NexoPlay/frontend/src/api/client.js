const API = import.meta.env.VITE_API_URL;
export const STREAMING = import.meta.env.VITE_STREAMING_URL;

async function send(url, { method = "GET", body, token } = {}) {
  const isFormData =
    typeof FormData !== "undefined" && body instanceof FormData;
  const res = await fetch(url, {
    method,
    headers: {
      ...(body && !isFormData && { "Content-Type": "application/json" }),
      ...(token && { Authorization: `Bearer ${token}` }),
    },
    body: isFormData ? body : body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    const detail = Array.isArray(data?.detail)
      ? data.detail.map((issue) => issue.msg).join(", ")
      : data?.detail;
    throw new Error(detail || data?.error || "Network error");
  }
  return data;
}

// API Gateway: auth and video management (uploads need a token)
export const request = (path, options) => send(`${API}${path}`, options);

// Streaming balancer: public catalog and playback, no token
export const streamingRequest = (path) => send(`${STREAMING}${path}`);
