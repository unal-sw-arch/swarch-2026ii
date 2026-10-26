import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import useCatalog from "./useCatalog";

// The streaming API pages with cursors, which only point forward. To go back,
// we remember the cursor that starts each visited page (cursors[0] is page 1,
// always null). They live in sessionStorage so refresh and the browser's back
// button keep working within the tab.
const STORAGE_KEY = "catalogCursors";

function readCursors() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(STORAGE_KEY));
    if (Array.isArray(saved)) return saved;
  } catch {
    // corrupted storage: start over
  }
  return [null];
}

// Page number lives in the URL (?page=2); unknown pages fall back to page 1
export default function useCatalogPages(pageSize = 20) {
  const [params, setParams] = useSearchParams();
  const [cursors, setCursors] = useState(readCursors);

  const requested = Number.parseInt(params.get("page") ?? "1", 10);
  const page =
    requested > 1 && typeof cursors[requested - 1] === "string" ? requested : 1;
  const cursor = page === 1 ? null : cursors[page - 1];

  const catalog = useCatalog(pageSize, cursor);

  const goTo = (target) =>
    setParams(target === 1 ? {} : { page: String(target) });

  const goNext = () => {
    if (!catalog.nextCursor) return;
    const next = cursors.slice(0, page);
    next[page] = catalog.nextCursor;
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    setCursors(next);
    goTo(page + 1);
  };

  const goPrevious = () => page > 1 && goTo(page - 1);

  const refreshAfterDelete = () => {
    // Deletion can empty the current page and invalidate later cursors.
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify([null]));
    setCursors([null]);
    goTo(1);
    catalog.reload();
  };

  return {
    ...catalog,
    page,
    hasPrevious: page > 1,
    hasNext: Boolean(catalog.nextCursor),
    goNext,
    goPrevious,
    refreshAfterDelete,
  };
}
