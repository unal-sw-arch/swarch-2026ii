import { useId, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import useComments from "../hooks/useComments";
import { Spinner } from "./Feedback";
import { AlertIcon, MessageIcon, SendIcon } from "./Icons";
import { gradientFor } from "../utils/gradients";
import { formatRelativeTime } from "../utils/format";

const MAX_LENGTH = 1000;

// Comments carry the author's display name. Comments posted before names were
// stored fall back to a short handle derived from the author's id.
const displayName = (item) =>
  item.authorName || `User ${String(item.userId ?? "").slice(0, 6)}`;

const initials = (name) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join("") || "?";

function Avatar({ authorId, name }) {
  return (
    <span
      aria-hidden="true"
      className={`flex size-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br text-xs font-semibold ${gradientFor(authorId ?? name)}`}
    >
      {initials(name)}
    </span>
  );
}

function StatusLine({ item, onRetry, onDiscard }) {
  if (item.status === "sending") {
    return (
      <span className="inline-flex items-center gap-1.5 text-zinc-500">
        <Spinner className="size-3" /> Sending…
      </span>
    );
  }
  if (item.status === "pending") {
    return <span className="text-zinc-500 animate-pulse">Publishing…</span>;
  }
  if (item.status === "error") {
    return (
      <span className="inline-flex flex-wrap items-center gap-x-3 gap-y-1 text-red-400">
        <span>Not posted{item.error ? `: ${item.error}` : ""}</span>
        <button type="button" onClick={onRetry} className="font-medium text-zinc-200 hover:underline">
          Retry
        </button>
        <button type="button" onClick={onDiscard} className="text-zinc-400 hover:underline">
          Discard
        </button>
      </span>
    );
  }
  return null;
}

function CommentForm({ onSubmit }) {
  const [text, setText] = useState("");
  const [error, setError] = useState("");
  const inputId = useId();
  const trimmed = text.trim();
  const tooLong = trimmed.length > MAX_LENGTH;

  const submit = (event) => {
    event.preventDefault();
    if (!trimmed) {
      setError("Write something before posting.");
      return;
    }
    if (tooLong) return;
    setError("");
    setText("");
    onSubmit(trimmed);
  };

  return (
    <form onSubmit={submit} className="flex flex-col gap-2">
      <label htmlFor={inputId} className="sr-only">
        Write a comment
      </label>
      <textarea
        id={inputId}
        value={text}
        onChange={(e) => {
          setText(e.target.value);
          if (error) setError("");
        }}
        onKeyDown={(e) => {
          if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) submit(e);
        }}
        rows={3}
        placeholder="Write a comment…"
        aria-invalid={Boolean(error) || tooLong}
        className="w-full resize-y rounded-lg border border-zinc-800 bg-zinc-900 px-4 py-3 text-sm placeholder:text-zinc-500 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
      />
      <div className="flex items-center justify-between gap-3">
        <p className={`text-xs ${tooLong || error ? "text-red-400" : "text-zinc-500"}`} role={error ? "alert" : undefined}>
          {error || `${trimmed.length}/${MAX_LENGTH}`}
        </p>
        <button
          type="submit"
          disabled={!trimmed || tooLong}
          className="inline-flex items-center gap-2 rounded-md bg-brand-600 px-4 py-2 text-sm font-semibold transition hover:bg-brand-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <SendIcon className="size-4" /> Comment
        </button>
      </div>
    </form>
  );
}

export default function Comments({ videoId }) {
  const { token, userId, userName } = useAuth();
  const sessionToken = userId ? token : null;
  const { items, loading, error, hasMore, loadingMore, loadMore, reload, add, retry, discard } =
    useComments(videoId, sessionToken, { userId, userName });
  const headingId = useId();

  return (
    <section aria-labelledby={headingId} className="mt-10 border-t border-zinc-800 pt-6">
      <h2 id={headingId} className="flex items-center gap-2 text-lg font-semibold">
        <MessageIcon className="size-5 text-zinc-400" /> Comments
      </h2>

      <div className="mt-4">
        {sessionToken ? (
          <CommentForm onSubmit={add} />
        ) : (
          <p className="rounded-lg border border-zinc-800 bg-zinc-900/60 px-4 py-3 text-sm text-zinc-400">
            <Link to="/login" className="font-medium text-brand-400 hover:underline">
              Log in
            </Link>{" "}
            to join the conversation.
          </p>
        )}
      </div>

      {loading ? (
        <ul className="mt-6 space-y-5" aria-hidden="true">
          {[0, 1, 2].map((i) => (
            <li key={i} className="flex animate-pulse gap-3">
              <div className="size-9 rounded-full bg-zinc-800" />
              <div className="flex-1">
                <div className="h-3 w-32 rounded bg-zinc-800" />
                <div className="mt-2 h-3 w-3/4 rounded bg-zinc-800/70" />
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <>
          {error && (
            <div role="alert" className="mt-6 flex items-center gap-3 rounded-lg border border-red-900/60 bg-red-950/20 px-4 py-3 text-sm">
              <AlertIcon className="size-5 shrink-0 text-red-400" />
              <span className="flex-1 text-zinc-300">Comments couldn't be loaded. {error}</span>
              <button type="button" onClick={reload} className="rounded-md bg-zinc-800 px-3 py-1.5 font-medium hover:bg-zinc-700">
                Try again
              </button>
            </div>
          )}
          {items.length === 0 && !error ? (
            <p className="mt-6 text-sm text-zinc-500">No comments yet. Be the first to share your thoughts.</p>
          ) : (
            <ul className="mt-6 space-y-5">
              {items.map((item) => {
                const isYou = Boolean(item.localId) || item.userId === userId;
                const name = displayName(item);
                return (
                  <li key={item.key} className={`flex gap-3 ${item.status === "error" ? "opacity-80" : ""}`}>
                    <Avatar authorId={item.userId ?? userId} name={name} />
                    <div className="min-w-0 flex-1">
                      <p className="flex flex-wrap items-baseline gap-x-2 text-sm">
                        <span className="font-medium text-zinc-200">{name}</span>
                        {isYou && (
                          <span className="rounded bg-zinc-800 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-zinc-400">
                            You
                          </span>
                        )}
                        <time dateTime={item.createdAt} className="text-xs text-zinc-500">
                          {formatRelativeTime(item.createdAt)}
                        </time>
                      </p>
                      <p className="mt-1 whitespace-pre-line break-words text-sm text-zinc-300">{item.text}</p>
                      {item.localId && (
                        <p className="mt-1 text-xs" aria-live="polite">
                          <StatusLine item={item} onRetry={() => retry(item.localId)} onDiscard={() => discard(item.localId)} />
                        </p>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
          {hasMore && (
            <button
              type="button"
              onClick={loadMore}
              disabled={loadingMore}
              className="mt-6 inline-flex items-center gap-2 rounded-md bg-zinc-800 px-4 py-2 text-sm font-medium transition hover:bg-zinc-700 disabled:cursor-wait disabled:opacity-60"
            >
              {loadingMore && <Spinner className="size-4" />} Show more comments
            </button>
          )}
        </>
      )}
    </section>
  );
}
