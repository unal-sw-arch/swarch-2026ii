import { Link, useNavigate, useParams } from "react-router-dom";
import { STREAMING } from "../api/client";
import useCatalog from "../hooks/useCatalog";
import useVideo from "../hooks/useVideo";
import HlsPlayer from "../components/HlsPlayer";
import VideoCard from "../components/VideoCard";
import DeleteVideoButton from "../components/DeleteVideoButton";
import { ArrowLeftIcon } from "../components/Icons";

// HLS master playlist on the streaming service. Public, no token.
// It must stay under /hls/ so its relative variant URIs resolve.
const playlistUrl = (id) => `${STREAMING}/videos/${id}/hls/master.m3u8`;

export default function Watch() {
  const { id } = useParams();
  const navigate = useNavigate();
  // Details come from the detail endpoint, so any video works, not only
  // those on the first catalog page; the catalog only feeds "More videos"
  const { video, loading } = useVideo(id);
  // The sidebar shows 6 others, so 7 covers the case where this video is listed
  const { videos } = useCatalog(7);
  const more = videos.filter((v) => v.id !== id).slice(0, 6);

  return (
    <main className="mx-auto max-w-7xl px-4 pt-20 sm:px-6">
      <Link
        to="/catalog"
        className="mb-4 inline-flex items-center gap-2 text-sm text-zinc-400 hover:text-white"
      >
        <ArrowLeftIcon className="size-4" /> Back to catalog
      </Link>
      <div className="grid gap-8 lg:grid-cols-[1fr_340px]">
        <div>
          <HlsPlayer
            key={id}
            src={playlistUrl(id)}
            poster={video?.thumbnailUrl}
          />
          <h1 className="mt-5 text-2xl font-semibold">
            {video?.title ?? (loading ? " " : `Video ${id}`)}
          </h1>
          <div className="mt-4">
            <DeleteVideoButton key={id} video={video} onDeleted={() => navigate("/catalog", { replace: true })} />
          </div>
          {video?.description && (
            <p className="mt-3 whitespace-pre-line text-zinc-400">
              {video.description}
            </p>
          )}
        </div>
        {more.length > 0 && (
          <aside>
            <h2 className="mb-4 font-semibold">More videos</h2>
            <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-1">
              {more.map((v) => (
                <VideoCard key={v.id} video={v} />
              ))}
            </div>
          </aside>
        )}
      </div>
    </main>
  );
}
