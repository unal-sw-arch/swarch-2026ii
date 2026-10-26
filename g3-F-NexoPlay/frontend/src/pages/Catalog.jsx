import { useRef, useState } from "react";
import { Link } from "react-router-dom";
import useCatalogPages from "../hooks/useCatalogPages";
import { gradientFor } from "../utils/gradients";
import VideoCard from "../components/VideoCard";
import { EmptyState, ErrorState, SkeletonCard } from "../components/Feedback";
import { PlayIcon, SearchIcon } from "../components/Icons";

function Hero({ video }) {
  return (
    <section className="relative h-[70vh] min-h-[420px] max-h-[640px] overflow-hidden">
      {video.thumbnailUrl ? (
        <img
          src={video.thumbnailUrl}
          alt=""
          className="absolute inset-0 size-full object-cover"
        />
      ) : (
        <div
          className={`absolute inset-0 bg-gradient-to-br ${gradientFor(video.id)}`}
        />
      )}
      <div className="absolute inset-0 bg-gradient-to-r from-zinc-950 via-zinc-950/60 to-transparent" />
      <div className="absolute inset-0 bg-gradient-to-t from-zinc-950 via-transparent to-transparent" />
      <div className="relative mx-auto flex h-full max-w-7xl flex-col justify-end px-4 pb-16 sm:px-6">
        <span className="mb-3 w-fit rounded bg-brand-600/90 px-2 py-0.5 text-xs font-semibold uppercase tracking-wider">
          Featured
        </span>
        <h1 className="max-w-2xl text-4xl font-bold tracking-tight sm:text-5xl">
          {video.title}
        </h1>
        {video.description && (
          <p className="mt-4 max-w-xl text-zinc-300 line-clamp-3">
            {video.description}
          </p>
        )}
        <Link
          to={`/watch/${video.id}`}
          className="mt-6 inline-flex w-fit items-center gap-2 rounded-md bg-white px-6 py-2.5 font-semibold text-zinc-900 hover:bg-zinc-200 transition"
        >
          <PlayIcon className="size-5" /> Play
        </Link>
      </div>
    </section>
  );
}

function Pagination({ page, hasPrevious, hasNext, onPrevious, onNext }) {
  const button =
    "rounded-md bg-zinc-800 px-4 py-2 text-sm font-medium transition hover:bg-zinc-700 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-zinc-800";
  return (
    <nav
      aria-label="Catalog pages"
      className="mt-auto flex items-center justify-center gap-4 pt-10"
    >
      <button onClick={onPrevious} disabled={!hasPrevious} className={button}>
        ‹ Previous
      </button>
      <span className="text-sm tabular-nums text-zinc-400">Page {page}</span>
      <button onClick={onNext} disabled={!hasNext} className={button}>
        Next ›
      </button>
    </nav>
  );
}

export default function Catalog() {
  const {
    videos,
    loading,
    error,
    reload,
    page,
    hasPrevious,
    hasNext,
    goNext,
    goPrevious,
    refreshAfterDelete,
  } = useCatalogPages();
  const [query, setQuery] = useState("");
  const browseRef = useRef(null);

  const filtered = videos.filter((v) =>
    v.title.toLowerCase().includes(query.trim().toLowerCase()),
  );

  // New page: bring the top of the grid back into view
  const changePage = (go) => {
    go();
    browseRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  let content;
  if (loading) {
    content = (
      <div className="grid gap-x-5 gap-y-8 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {Array.from({ length: 8 }, (_, i) => (
          <SkeletonCard key={i} />
        ))}
      </div>
    );
  } else if (error) {
    content = <ErrorState message={error} onRetry={reload} />;
  } else if (filtered.length === 0) {
    content = query ? (
      <EmptyState
        title="No results"
        message={`Nothing on this page matches "${query}".`}
      />
    ) : (
      <EmptyState
        title="No videos yet"
        message="Uploaded videos will show up here."
      />
    );
  } else {
    content = (
      <div className="grid gap-x-5 gap-y-8 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {filtered.map((v) => (
          <VideoCard key={v.id} video={v} onDeleted={refreshAfterDelete} />
        ))}
      </div>
    );
  }

  const paged = hasPrevious || hasNext;

  return (
    <main className="flex flex-1 flex-col">
      {/* The featured hero is the newest video, so it belongs to page 1 only */}
      {page === 1 && !loading && !error && videos.length > 0 ? (
        <Hero video={videos[0]} />
      ) : (
        <div className="h-24" />
      )}
      <section
        ref={browseRef}
        className="mx-auto flex w-full max-w-7xl flex-1 scroll-mt-20 flex-col px-4 sm:px-6"
      >
        <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <h2 className="text-xl font-semibold">Browse videos</h2>
          <label className="relative block sm:w-72">
            <span className="sr-only">Search videos</span>
            <SearchIcon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-zinc-500" />
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search by title"
              className="w-full rounded-md border border-zinc-800 bg-zinc-900 py-2 pl-9 pr-3 text-sm placeholder-zinc-500 focus:border-brand-500 focus:outline-none"
            />
          </label>
        </div>
        {/* Search filters the current page only; say so when there are others */}
        {query && paged && !loading && !error && (
          <p className="-mt-3 mb-5 text-sm text-zinc-500">
            Searching page {page} only.
          </p>
        )}
        {content}
        {paged && !error && (
          <Pagination
            page={page}
            hasPrevious={hasPrevious}
            hasNext={hasNext}
            onPrevious={() => changePage(goPrevious)}
            onNext={() => changePage(goNext)}
          />
        )}
      </section>
    </main>
  );
}
