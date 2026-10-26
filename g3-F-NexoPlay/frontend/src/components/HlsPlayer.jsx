import { useEffect, useRef, useState } from "react";
import Hls from "hls.js";
import { AlertIcon } from "./Icons";
import { Spinner } from "./Feedback";

const nativeHls = () =>
  document
    .createElement("video")
    .canPlayType("application/vnd.apple.mpegurl") !== "";
const supported = Hls.isSupported() || nativeHls();

// HLS player: hls.js where supported (MSE), native playback on Safari/iOS.
// Render with key={src} so a new source starts from a fresh loading state.
export default function HlsPlayer({ src, poster }) {
  const videoRef = useRef(null);
  const [status, setStatus] = useState(supported ? "loading" : "error"); // loading | ready | error

  useEffect(() => {
    const video = videoRef.current;

    if (Hls.isSupported()) {
      const hls = new Hls();
      hls.on(Hls.Events.MANIFEST_PARSED, () => setStatus("ready"));
      hls.on(Hls.Events.ERROR, (_, data) => {
        if (data.fatal) setStatus("error");
      });
      hls.loadSource(src);
      hls.attachMedia(video);
      return () => hls.destroy();
    }

    if (supported) {
      const onReady = () => setStatus("ready");
      const onError = () => setStatus("error");
      video.addEventListener("loadedmetadata", onReady);
      video.addEventListener("error", onError);
      video.src = src;
      return () => {
        video.removeEventListener("loadedmetadata", onReady);
        video.removeEventListener("error", onError);
        video.removeAttribute("src");
        video.load();
      };
    }
  }, [src]);

  return (
    <div className="relative aspect-video w-full overflow-hidden rounded-xl bg-black shadow-2xl shadow-black/50">
      <video
        ref={videoRef}
        controls={status === "ready"}
        playsInline
        poster={poster}
        className="size-full"
      />
      {status === "loading" && (
        <div className="absolute inset-0 flex items-center justify-center text-zinc-400">
          <Spinner className="size-10" />
        </div>
      )}
      {status === "error" && (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-zinc-950/90 px-6 text-center">
          <AlertIcon className="size-10 text-red-400" />
          <p className="font-medium">This video can't be played right now</p>
          <p className="text-sm text-zinc-400">
            The stream is unavailable or your browser doesn't support HLS.
          </p>
        </div>
      )}
    </div>
  );
}
