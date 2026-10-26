import json
import math
import logging
import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory, TemporaryFile

from shared.services.processing_jobs import ClaimedJob, ProcessingJobService

logger = logging.getLogger(__name__)


class TranscodingError(RuntimeError):
    pass


class TranscodingService:
    """Generate HLS and a poster; intermediate MP4 files stay on local temporary storage."""

    def __init__(self, blob_storage, jobs: ProcessingJobService):
        self.blob_storage = blob_storage
        self.jobs = jobs
        self.ffmpeg = os.getenv("FFMPEG_BINARY", "ffmpeg")
        self.ffprobe = os.getenv("FFPROBE_BINARY", "ffprobe")
        self.timeout = int(os.getenv("TRANSCODE_TIMEOUT_SECONDS", "3600"))
        self.threads = int(os.getenv("FFMPEG_THREADS", "2"))
        self.segment_seconds = int(os.getenv("HLS_SEGMENT_SECONDS", "4"))
        if self.timeout < 1 or self.threads < 1 or self.segment_seconds < 1:
            raise ValueError("FFmpeg timeout and thread count must be positive")

    def check_tools(self) -> None:
        for binary in (self.ffmpeg, self.ffprobe):
            subprocess.run([binary, "-version"], check=True, capture_output=True, timeout=10)

    def probe(self, path: Path) -> dict:
        result = subprocess.run(
            [self.ffprobe, "-v", "error", "-protocol_whitelist", "file,pipe",
             "-show_streams", "-show_format", "-of", "json", str(path)],
            capture_output=True, text=True, timeout=30,
        )
        if result.returncode:
            raise TranscodingError("ffprobe could not decode the video: " + result.stderr[-2000:])
        data = json.loads(result.stdout)
        streams = data.get("streams", [])
        video = next((s for s in streams if s.get("codec_type") == "video"
                      and not s.get("disposition", {}).get("attached_pic")), None)
        if video is None:
            raise TranscodingError("File has no video stream")
        audio = next((s for s in streams if s.get("codec_type") == "audio"), None)
        duration = float(data.get("format", {}).get("duration") or video.get("duration") or 0)
        width, height = int(video.get("width", 0)), int(video.get("height", 0))
        if width < 2 or height < 2 or not math.isfinite(duration) or duration <= 0:
            raise TranscodingError("Invalid video dimensions or duration")
        sar = video.get("sample_aspect_ratio", "1:1")
        if sar not in ("N/A", "0:1"):
            numerator, denominator = map(int, sar.split(":"))
            if numerator > 0 and denominator > 0:
                width = max(2, round(width * numerator / denominator))
        rotation = next((s.get("rotation", 0) for s in video.get("side_data_list", [])
                         if "rotation" in s), video.get("tags", {}).get("rotate", 0))
        if abs(round(float(rotation))) % 180 == 90:
            width, height = height, width
        return {
            "width": width, "height": height, "duration": duration,
            "source_width": int(video["width"]), "source_height": int(video["height"]),
            "video_codec": video["codec_name"],
            "audio_codec": audio.get("codec_name") if audio else None,
            "video_stream_index": video["index"],
            "audio_stream_index": audio["index"] if audio else None,
        }

    @staticmethod
    def profiles(source: dict) -> list[dict]:
        heights = [h for h in (1080, 720, 480) if h <= source["height"]]
        if not heights:
            heights = [source["height"]]
        profiles = []
        for height in heights:
            width = max(2, int(source["width"] * height / source["height"]) // 2 * 2)
            even_height = height // 2 * 2
            label = f"{height}p" if height in (1080, 720, 480) else "source"
            profiles.append({"profile": f"mp4_{label}", "format": "mp4",
                             "width": width, "height": even_height})
        return profiles

    def command(self, source_path: Path, output_path: Path, profile: dict, source: dict) -> list[str]:
        args = [self.ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                "-protocol_whitelist", "file,pipe", "-i", str(source_path),
                "-map", f"0:{source['video_stream_index']}"]
        if source["audio_stream_index"] is not None:
            args += ["-map", f"0:{source['audio_stream_index']}"]
        args += ["-map_metadata", "-1", "-vf",
                 f"scale={profile['width']}:{profile['height']},setsar=1",
                 "-pix_fmt", "yuv420p", "-threads", str(self.threads)]
        args += ["-c:v", "libx264", "-preset", "medium", "-crf", "23",
                 "-force_key_frames", f"expr:gte(t,n_forced*{self.segment_seconds})",
                 "-sc_threshold", "0", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart"]
        return args + [str(output_path)]

    def encode(self, source_path: Path, output_path: Path, profile: dict, source: dict) -> None:
        self._run(self.command(source_path, output_path, profile, source))

    def _run(self, command: list[str]) -> None:
        # Keep stderr on disk so malformed/long files cannot exhaust memory through logs.
        with TemporaryFile() as error_log:
            try:
                result = subprocess.run(
                    command,
                    stdout=subprocess.DEVNULL, stderr=error_log, timeout=self.timeout,
                )
            except subprocess.TimeoutExpired as error:
                raise TranscodingError("FFmpeg exceeded the configured timeout") from error
            if result.returncode:
                error_log.seek(0, os.SEEK_END)
                error_log.seek(max(0, error_log.tell() - 2000))
                raise TranscodingError("FFmpeg failed: " + error_log.read().decode(errors="replace"))

    def process(self, claim: ClaimedJob) -> None:
        with TemporaryDirectory(prefix=f"video-{claim.video_id}-") as temporary:
            directory = Path(temporary)
            original = directory / "original"
            self.blob_storage.download(claim.bucket, claim.object_key, str(original))
            source = self.probe(original)
            self.jobs.record_source(claim, source)
            mp4_outputs = []
            for profile in ([] if claim.hls_completed else self.profiles(source)):
                self.jobs.heartbeat(claim)
                output = directory / f"{profile['profile']}.{profile['format']}"
                self.encode(original, output, profile, source)
                actual = self.probe(output)
                expected_codecs = ("h264", "aac")
                if (actual["width"], actual["height"]) != (profile["width"], profile["height"]):
                    raise TranscodingError("Encoded output dimensions do not match the profile")
                if actual["video_codec"] != expected_codecs[0] or (
                    source["audio_codec"] is not None and actual["audio_codec"] != expected_codecs[1]
                ):
                    raise TranscodingError("Encoded output codecs do not match the profile")
                mp4_outputs.append((profile, output))
            if not claim.hls_completed:
                self.generate_hls(directory, claim, source, mp4_outputs)
            if not claim.poster_completed:
                self.generate_poster(directory, original, claim, source)
            self.jobs.complete(claim)

    def generate_poster(self, directory: Path, original: Path, claim: ClaimedJob, source: dict) -> None:
        poster = directory / "poster.jpg"
        width = min(1280, source["width"]) // 2 * 2
        height = max(2, round(source["height"] * width / source["width"]) // 2 * 2)
        self.jobs.heartbeat(claim)
        self._run([
            self.ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
            "-protocol_whitelist", "file,pipe", "-i", str(original),
            "-ss", str(min(1.0, source["duration"] / 2)),
            "-map", f"0:{source['video_stream_index']}", "-frames:v", "1",
            "-vf", f"scale={width}:{height},setsar=1", "-q:v", "2",
            "-threads", str(self.threads), str(poster),
        ])
        if not poster.is_file() or poster.stat().st_size == 0:
            raise TranscodingError("FFmpeg did not generate the poster")
        with poster.open("rb") as stream:
            if stream.read(2) != b"\xff\xd8":
                raise TranscodingError("Invalid JPEG poster")
            with self.jobs.artifact_lock(claim):
                key = f"videos/{claim.video_id}/thumbnails/poster.jpg"
                self.blob_storage.upload(stream, key, "image/jpeg")
        self.jobs.record_poster(claim, {
            "bucket": self.blob_storage.bucket_name, "object_key": key,
            "width": width, "height": height, "size_bytes": poster.stat().st_size,
            "content_type": "image/jpeg",
        })

    def generate_hls(self, directory: Path, claim: ClaimedJob, source: dict, mp4_outputs: list) -> None:
        root = directory / "hls"
        root.mkdir()
        prefix = f"videos/{claim.video_id}/hls"
        renditions = []
        master = ["#EXTM3U", "#EXT-X-VERSION:3", "#EXT-X-INDEPENDENT-SEGMENTS"]
        for profile, mp4 in mp4_outputs:
            self.jobs.heartbeat(claim)
            label = profile["profile"].removeprefix("mp4_")
            output = root / label
            output.mkdir()
            playlist = output / "index.m3u8"
            self._run([
                self.ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                "-protocol_whitelist", "file,pipe", "-i", str(mp4),
                "-map", "0:v:0", "-map", "0:a:0?", "-c", "copy",
                "-hls_time", str(self.segment_seconds), "-hls_playlist_type", "vod",
                "-hls_flags", "independent_segments", "-hls_segment_filename",
                str(output / "segment_%03d.ts"), str(playlist),
            ])
            actual = self.probe(playlist)
            lines = playlist.read_text().splitlines()
            # FFmpeg rounds a sub-second segment to target duration zero. HLS
            # requires a positive target duration even for very short uploads.
            lines = ["#EXT-X-TARGETDURATION:1" if line == "#EXT-X-TARGETDURATION:0" else line for line in lines]
            playlist.write_text("\n".join(lines) + "\n", encoding="utf-8")
            segments = [line for line in lines if line and not line.startswith("#")]
            durations = [float(line.split(":", 1)[1].rstrip(",")) for line in lines if line.startswith("#EXTINF:")]
            if (not segments or len(segments) != len(durations) or "#EXT-X-ENDLIST" not in lines
                or any(Path(name).name != name or not name.endswith(".ts") for name in segments)):
                raise TranscodingError("Invalid HLS media playlist")
            if (actual["width"], actual["height"]) != (profile["width"], profile["height"]):
                raise TranscodingError("Invalid HLS rendition dimensions")
            sizes = [(output / name).stat().st_size for name in segments]
            if any(size <= 0 for size in sizes) or any(d <= 0 for d in durations):
                raise TranscodingError("Empty HLS segment")
            bandwidth = math.ceil(max(size * 8 / duration for size, duration in zip(sizes, durations)) * 1.1)
            master += [f"#EXT-X-STREAM-INF:BANDWIDTH={bandwidth},RESOLUTION={profile['width']}x{profile['height']}",
                       f"{label}/index.m3u8"]
            renditions.append({
                "profile": f"hls_{label}", "width": profile["width"], "height": profile["height"],
                "video_codec": actual["video_codec"], "audio_codec": actual["audio_codec"],
                "duration": sum(durations), "size_bytes": sum(sizes) + playlist.stat().st_size,
                "playlist_object_key": f"{prefix}/{label}/index.m3u8",
                "segments": [f"{prefix}/{label}/{name}" for name in segments],
            })
        if not renditions:
            raise TranscodingError("HLS needs at least one rendition")
        (root / "master.m3u8").write_text("\n".join(master) + "\n", encoding="utf-8")
        assets = []
        # Upload segments first, playlists next, master last. Register only a complete package.
        paths = sorted(root.rglob("*"), key=lambda p: (p.suffix == ".m3u8", p.name == "master.m3u8", str(p)))
        for path in paths:
            if not path.is_file():
                continue
            key = f"{prefix}/{path.relative_to(root).as_posix()}"
            mime = "application/vnd.apple.mpegurl" if path.suffix == ".m3u8" else "video/mp2t"
            with path.open("rb") as stream:
                with self.jobs.artifact_lock(claim):
                    self.blob_storage.upload(stream, key, mime)
            assets.append({"object_key": key, "content_type": mime, "size_bytes": path.stat().st_size})
        self.jobs.record_hls(claim, {
            "bucket": self.blob_storage.bucket_name, "master_object_key": f"{prefix}/master.m3u8",
            "duration": source["duration"], "segment_duration_seconds": self.segment_seconds,
            "renditions": renditions, "assets": assets,
        })
