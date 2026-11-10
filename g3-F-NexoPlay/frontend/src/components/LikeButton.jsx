import { Link } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import useLikes from "../hooks/useLikes";
import { HeartIcon } from "./Icons";
import { formatCount } from "../utils/format";

// Like toggle with count. Visitors see the count and a prompt to log in.
export default function LikeButton({ videoId }) {
  const { token, userId } = useAuth();
  const sessionToken = userId ? token : null; // ignore expired tokens
  const { count, liked, loading, saving, toggle, toggleError } = useLikes(videoId, sessionToken);

  const label = loading ? "–" : formatCount(count);
  const pill =
    "inline-flex items-center gap-2 rounded-full border px-4 py-2 text-sm font-medium transition focus-visible:outline-2 focus-visible:outline-brand-400";

  if (!sessionToken) {
    return (
      <Link
        to="/login"
        title="Log in to like this video"
        className={`${pill} border-zinc-700 text-zinc-300 hover:bg-zinc-800`}
      >
        <HeartIcon className="size-4" />
        <span>{label}</span>
        <span className="sr-only">likes. Log in to like this video</span>
      </Link>
    );
  }

  return (
    <div className="flex items-center gap-3">
      <button
        type="button"
        onClick={toggle}
        disabled={loading || saving}
        aria-pressed={liked}
        aria-label={`${liked ? "Remove like" : "Like"}. ${label} likes`}
        className={`${pill} disabled:cursor-wait ${
          liked
            ? "border-brand-600 bg-brand-600/15 text-brand-400 hover:bg-brand-600/25"
            : "border-zinc-700 text-zinc-200 hover:bg-zinc-800"
        }`}
      >
        <HeartIcon filled={liked} className={`size-4 transition ${liked ? "scale-110" : ""}`} />
        <span aria-hidden="true">{label}</span>
      </button>
      {toggleError && (
        <p role="alert" className="text-sm text-red-400">
          {toggleError}
        </p>
      )}
    </div>
  );
}
