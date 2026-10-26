import { useEffect, useState } from "react";
import { fetchCatalog } from "../api/videos";

// Loads one page of the public catalog: the first page when cursor is null,
// otherwise the page that starts at that cursor. Exposes loading/error state,
// the cursor of the following page and a retry.
export default function useCatalog(pageSize = 20, cursor = null) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState({
    key: null,
    videos: [],
    nextCursor: null,
    error: "",
  });
  // Results are tagged with the request they answer, so switching pages
  // never shows the previous page's videos while the new one loads
  const key = `${attempt}|${pageSize}|${cursor}`;

  useEffect(() => {
    let ignore = false;
    fetchCatalog({ limit: pageSize, cursor })
      .then(
        (page) =>
          !ignore &&
          setResult({ key, videos: page.videos, nextCursor: page.nextCursor, error: "" }),
      )
      .catch(
        (err) =>
          !ignore &&
          setResult({ key, videos: [], nextCursor: null, error: err.message }),
      );
    return () => {
      ignore = true;
    };
  }, [key, pageSize, cursor]);

  const current = result.key === key;
  return {
    videos: current ? result.videos : [],
    nextCursor: current ? result.nextCursor : null,
    loading: !current,
    error: current ? result.error : "",
    reload: () => setAttempt((n) => n + 1),
  };
}
