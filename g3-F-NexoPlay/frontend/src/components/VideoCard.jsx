import { Link } from "react-router-dom";
import { PlayIcon } from "./Icons";
import { gradientFor } from "../utils/gradients";
import DeleteVideoButton from "./DeleteVideoButton";

export default function VideoCard({ video, onDeleted }) {
  return (
    <article>
      <Link
        to={`/watch/${video.id}`}
        className="group block rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-4 focus-visible:ring-offset-zinc-950"
      >
        <div className="relative aspect-video overflow-hidden rounded-lg bg-zinc-800">
          {video.thumbnailUrl ? (
            <img
              src={video.thumbnailUrl}
              alt=""
              loading="lazy"
              className="size-full object-cover transition duration-300 group-hover:scale-105"
            />
          ) : (
            <div
              className={`size-full bg-gradient-to-br ${gradientFor(video.id)} transition duration-300 group-hover:scale-105`}
            />
          )}
          <div className="absolute inset-0 flex items-center justify-center bg-black/0 transition group-hover:bg-black/40">
            <span className="flex size-12 scale-75 items-center justify-center rounded-full bg-white/90 text-zinc-900 opacity-0 shadow-lg transition group-hover:scale-100 group-hover:opacity-100">
              <PlayIcon className="ml-0.5 size-5" />
            </span>
          </div>
          {video.duration && (
            <span className="absolute bottom-2 right-2 rounded bg-black/80 px-1.5 py-0.5 text-xs font-medium tabular-nums">
              {video.duration}
            </span>
          )}
        </div>
        <h3 className="mt-3 line-clamp-2 font-medium leading-snug group-hover:text-white">
          {video.title}
        </h3>
        {video.description && (
          <p className="mt-1 line-clamp-2 text-sm text-zinc-400">
            {video.description}
          </p>
        )}
      </Link>
      {onDeleted && (
        <div className="mt-3">
          <DeleteVideoButton video={video} onDeleted={onDeleted} />
        </div>
      )}
    </article>
  );
}
