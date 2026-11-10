import { useEffect, useState } from "react";
import { fetchRecommendations } from "../api/recommendations";

export default function useRecommendations(token, enabled = true, limit = 20) {
  const [attempt, setAttempt] = useState(0);
  const key = `${token ?? ""}|${enabled}|${limit}|${attempt}`;
  const [result, setResult] = useState({ key: null, mode: null, videos: [], error: "" });

  useEffect(() => {
    if (!enabled || !token) return undefined;

    let ignore = false;
    fetchRecommendations({ token, limit })
      .then(({ mode, videos }) => {
        if (!ignore) setResult({ key, mode, videos, error: "" });
      })
      .catch((error) => {
        if (!ignore) setResult({ key, mode: null, videos: [], error: error.message });
      });

    return () => {
      ignore = true;
    };
  }, [enabled, key, limit, token]);

  const current = result.key === key;
  return {
    mode: current ? result.mode : null,
    videos: current ? result.videos : [],
    loading: Boolean(enabled && token && !current),
    error: current ? result.error : "",
    reload: () => setAttempt((value) => value + 1),
  };
}
