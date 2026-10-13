import { useEffect, useState } from "react";
import { fetchVideo } from "../api/videos";

// Loads one video's details. The result is tagged with its id, so switching
// videos never shows the previous one's data while the new one loads.
export default function useVideo(id) {
  const [result, setResult] = useState({ id: null, video: null, error: "" });

  useEffect(() => {
    let ignore = false;
    fetchVideo(id)
      .then((video) => !ignore && setResult({ id, video, error: "" }))
      .catch((err) => !ignore && setResult({ id, video: null, error: err.message }));
    return () => {
      ignore = true;
    };
  }, [id]);

  const current = result.id === id;
  return {
    video: current ? result.video : null,
    loading: !current,
    error: current ? result.error : "",
  };
}
