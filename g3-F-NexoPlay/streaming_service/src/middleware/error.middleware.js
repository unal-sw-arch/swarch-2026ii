export function errorMiddleware(error, req, res, next) {
  if (res.headersSent) {
    return next(error);
  }

  if (error.code === "VIDEO_NOT_FOUND") {
    return res.status(404).json({
      error: "VIDEO_NOT_FOUND",
      message: "The video does not exist"
    });
  }

  if (error.code === "INVALID_VIDEO_ID") {
    return res.status(400).json({
      error: "INVALID_VIDEO_ID",
      message: "The video ID is not valid"
    });
  }

  if (error.code === "VIDEO_NOT_READY") {
    return res.status(409).json({
      error: "VIDEO_NOT_READY",
      message: "The video is not available yet",
      status: error.status
    });
  }

  if (
    error.code === "MANIFEST_NOT_FOUND" ||
    error.code === "MANIFEST_NOT_CONFIGURED" ||
    error.code === "THUMBNAIL_NOT_CONFIGURED" ||
    error.code === "OBJECT_NOT_FOUND"
  ) {
    return res.status(404).json({
      error: error.code,
      message: error.message
    });
  }

  if (
    error.code === "INVALID_PAGINATION" ||
    error.code === "INVALID_CURSOR"
  ) {
    return res.status(400).json({
      error: error.code,
      message: error.message
    });
  }

  if (
    error.code === "INVALID_HLS_PATH" ||
    error.code === "INVALID_OBJECT_KEY"
  ) {
    return res.status(400).json({
      error: error.code,
      message: "The requested path is not valid"
    });
  }

  console.error("Unhandled error:", error);

  return res.status(503).json({
    error: "STREAMING_UNAVAILABLE",
    message: "Could not retrieve the streaming resource"
  });
}
