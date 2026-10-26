
import {
  getCatalog,
  getVideo,
  getVideoAvailability,
  getThumbnail,
  getHlsResource
} from "../services/streaming.service.js";

function contentTypeFor(path) {
  if (path.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
  if (path.endsWith(".ts")) return "video/mp2t";
  return "application/octet-stream";
}

function pipeObject(res, result, contentType) {
  res.setHeader("Content-Type", contentType);

  if (result.contentLength != null) {
    res.setHeader("Content-Length", result.contentLength);
  }

  // Without this handler a dropped MinIO connection crashes the process
  result.body.on("error", (error) => {
    console.error("Error streaming object:", error.message);
    res.destroy(error);
  });

  result.body.pipe(res);
}

export async function catalog(req, res, next) {
  try {
    const { limit, cursor } = req.query;
    res.status(200).json(await getCatalog({ limit, cursor }));
  } catch (error) {
    next(error);
  }
}

export async function videoDetail(req, res, next) {
  try {
    res.status(200).json(await getVideo(req.params.videoId));
  } catch (error) {
    next(error);
  }
}

export async function availability(req, res, next) {
  try {
    const result = await getVideoAvailability(req.params.videoId);
    res.status(200).json(result);
  } catch (error) {
    next(error);
  }
}

// Kept for older clients: the master must be fetched under hls/ so its
// relative variant URIs resolve to the HLS route
export function masterPlaylist(req, res) {
  res.redirect(301, `${req.baseUrl}/${req.params.videoId}/hls/master.m3u8`);
}

export async function thumbnail(req, res, next) {
  try {
    const result = await getThumbnail(req.params.videoId);
    pipeObject(res, result, "image/jpeg");
  } catch (error) {
    next(error);
  }
}

export async function hlsResource(req, res, next) {
  try {
    const path = Array.isArray(req.params.resourcePath)
      ? req.params.resourcePath.join("/")
      : req.params.resourcePath;

    const result = await getHlsResource(req.params.videoId, path);

    pipeObject(res, result, contentTypeFor(path));
  } catch (error) {
    next(error);
  }
}
