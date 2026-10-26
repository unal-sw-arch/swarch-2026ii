
import { existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import dotenv from "dotenv";

const projectEnvPath = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "../.env"
);

if (existsSync(projectEnvPath)) {
  dotenv.config({ path: projectEnvPath });
}

function required(name) {
  const value = process.env[name];

  if (!value) {
    throw new Error(`Missing environment variable ${name}`);
  }

  return value;
}

export const env = {
  port: Number(process.env.STREAMING_PORT || 8082),
  // Same variables and local defaults as the Python services; Compose points
  // them to metadataba-db inside the internal network
  postgres: {
    host: required("POSTGRES_HOST"),
    port: Number(process.env.POSTGRES_PORT || 5433),
    database: process.env.POSTGRES_DB || "metadataba_db",
    user: required("POSTGRES_USER"),
    password: required("POSTGRES_PASSWORD"),
  },
  minioEndpoint: required("MINIO_ENDPOINT_URL"),
  minioAccessKey: required("MINIO_ROOT_USER"),
  minioSecretKey: required("MINIO_ROOT_PASSWORD"),
  minioBucket: process.env.MINIO_BUCKET_NAME,
  redisUrl: required("REDIS_URL"),
  metadataTTL: process.env.METADATA_TTL || 60,
  playlistTTL: process.env.PLAYLIST_TTL || 600,
  // Catalog and playback are public, so any origin may read them by default
  corsOrigin: process.env.CORS_ORIGIN || "*",
};