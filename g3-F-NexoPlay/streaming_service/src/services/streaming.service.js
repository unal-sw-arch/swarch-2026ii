
import { validate as isUuid } from "uuid";
import { Readable } from "node:stream";

import { findVideoById, findCompletedVideos } from "../repositories/metadata.repository.js";
import { getObject, objectExists } from "../repositories/storage.repository.js";
import { getCached, setCached } from "../repositories/cache.repository.js";
import { env } from "../../env.js"

const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 100;

function fail(code, message) {
  const error = new Error(message);
  error.code = code;
  return error;
}

function normalizeKey(key) {
  if (typeof key !== "string" || key.includes("\0")) {
    throw fail("INVALID_OBJECT_KEY", "Invalid object path");
  }

  const parts = key.replaceAll("\\", "/").split("/");

  if (
    key.startsWith("/") ||
    parts.some((part) => part === ".." || part === ".")
  ) {
    throw fail("INVALID_OBJECT_KEY", "Invalid object path");
  }

  return parts.filter(Boolean).join("/");
}

function registeredKey(key, expectedPrefix, missingCode) {
  if (!key) {
    throw fail(missingCode, "The resource is not registered");
  }

  const normalized = normalizeKey(key);

  if (!normalized.startsWith(expectedPrefix)) {
    throw fail("INVALID_OBJECT_KEY", "Invalid object path");
  }

  return normalized;
}

function masterKey(videoId, metadata) {
  return registeredKey(
    metadata.manifest_object_key,
    `videos/${videoId}/hls/`,
    "MANIFEST_NOT_CONFIGURED"
  );
}

async function getMetadata(videoId) {
  if (!isUuid(videoId)) {
    const error = new Error("El ID debe ser un UUID válido");
    error.code = "INVALID_VIDEO_ID";
    throw error;
  }

  // Check the database on every request so deleted videos cannot still be
  // watched through stale metadata or cached playlists on any replica.
  return findVideoById(videoId);
}

async function requireCompletedVideo(videoId) {
  const video = await getMetadata(videoId);

  if (!video) {
    const error = new Error("Video no encontrado");
    error.code = "VIDEO_NOT_FOUND";
    throw error;
  }

  if (video.status !== "COMPLETED") {
    const error = new Error("El video todavía no está disponible");
    error.code = "VIDEO_NOT_READY";
    error.status = video.status;
    throw error;
  }

  return video;
}

function toVideoDto(video) {
  return {
    id: video.id,
    ownerId: video.owner_id,
    title: video.title,
    description: video.description,
    category: video.category,
    durationSeconds:
      video.duration_seconds == null ? null : Number(video.duration_seconds),
    width: video.width,
    height: video.height,
    hasThumbnail: Boolean(video.thumbnail_object_key),
    createdAt: video.created_at
  };
}

function parseLimit(value) {
  if (value === undefined) return DEFAULT_PAGE_SIZE;

  const limit = Number(value);

  if (!Number.isInteger(limit) || limit < 1 || limit > MAX_PAGE_SIZE) {
    throw fail(
      "INVALID_PAGINATION",
      `limit must be an integer between 1 and ${MAX_PAGE_SIZE}`
    );
  }

  return limit;
}

// The cursor is opaque to clients: base64url of {"c": created_at, "i": id}
function encodeCursor(video) {
  return Buffer.from(
    JSON.stringify({ c: video.created_at_exact, i: video.id })
  ).toString("base64url");
}

function decodeCursor(value) {
  if (value === undefined) return null;

  try {
    const { c, i } = JSON.parse(
      Buffer.from(String(value), "base64url").toString("utf8")
    );

    if (typeof c === "string" && !Number.isNaN(Date.parse(c)) && isUuid(i)) {
      return { createdAt: c, id: i };
    }
  } catch {
    // falls through to the error below
  }

  throw fail("INVALID_CURSOR", "The pagination cursor is not valid");
}

export async function getCatalog({ limit, cursor } = {}) {
  const pageSize = parseLimit(limit);
  const after = decodeCursor(cursor);

  // One extra row tells us whether another page exists
  const rows = await findCompletedVideos(pageSize + 1, after);
  const page = rows.slice(0, pageSize);
  const hasMore = rows.length > pageSize;

  return {
    items: page.map(toVideoDto),
    nextCursor: hasMore ? encodeCursor(page.at(-1)) : null
  };
}

export async function getVideo(videoId) {
  return toVideoDto(await requireCompletedVideo(videoId));
}

export async function getVideoAvailability(videoId) {
  const video = await requireCompletedVideo(videoId);
  const key = masterKey(videoId, video);

  try {
    await objectExists(key);
  } catch (error) {
    if (
      error.name === "NotFound" ||
      error.name === "NoSuchKey" ||
      error.$metadata?.httpStatusCode === 404
    ) {
      const missing = new Error("No se encontró el master playlist");
      missing.code = "MANIFEST_NOT_FOUND";
      throw missing;
    }

    throw error;
  }

  return {
    videoId: video.id,
    available: true,
    status: video.status
  };
}

export async function getThumbnail(videoId) {
  const video = await requireCompletedVideo(videoId);
  const key = registeredKey(
    video.thumbnail_object_key,
    `videos/${videoId}/thumbnails/`,
    "THUMBNAIL_NOT_CONFIGURED"
  );

  return getObject(key);
}


async function readBody(body) {
  if (Buffer.isBuffer(body)) {
    return body;
  }

  if (typeof body === "string") {
    return Buffer.from(body);
  }

  const chunks = [];
  for await (const chunk of body) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }

  return Buffer.concat(chunks);
}

async function getPlaylist(key, cacheKey) {
  const cached = await getCached(cacheKey);

  if (cached) {
    const body = Buffer.from(cached.content);

    return {
      body: Readable.from([body]),
      contentType: cached.contentType,
      contentLength: body.length
    };
  }

  const object = await getObject(key);
  const content = await readBody(object.body);
  const contentType = object.contentType;

  await setCached(cacheKey, {content: content.toString("utf8"), contentType}, env.playlistTTL);

  return {
    ...object,
    body: Readable.from([content]),
    contentLength: content.length
  };
}

// export async function getMasterPlaylist(videoId) {
//   const video = await requireCompletedVideo(videoId);
//   const key = masterKey(videoId, video)

//   return getPlaylist(key, `hls:${videoId}:master.m3u8`);
// }

export async function getHlsResource(videoId, resourcePath) {
  const video = await requireCompletedVideo(videoId);
  const root = `videos/${videoId}/hls/`;

  const master = masterKey(videoId, video);
  const relativePath = normalizeKey(resourcePath);

  if (!relativePath) {
    throw fail("INVALID_HLS_PATH", "Invalid HLS path");
  }

  const key = normalizeKey(`${root}${relativePath}`);
  console.log(key, root, master)

  if (!key.startsWith(root)) {
    throw fail("INVALID_HLS_PATH", "Invalid HLS path");
  }

  if (key.endsWith(".m3u8")) {
    return getPlaylist(key, `hls:${videoId}:${relativePath}`);
  } else if (key.endsWith(".ts")) {
    return getObject(key);
  }

  const error = new Error("Tipo de recurso HLS no permitido");
  error.code = "INVALID_HLS_PATH";
  throw error;
}
