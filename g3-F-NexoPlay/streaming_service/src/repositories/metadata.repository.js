
import { pool } from "../clients/postgres.client.js";

export async function findVideoById(videoId) {
  const query = `
    SELECT
      id,
      owner_id,
      title,
      description,
      category,
      status,
      manifest_object_key,
      thumbnail_object_key,
      duration_seconds,
      width,
      height,
      created_at,
      updated_at,
      processed_at
    FROM video_metadata
    WHERE id = $1
    LIMIT 1
  `;

  const result = await pool.query(query, [videoId]);

  return result.rows[0] ?? null;
}

// Keyset pagination, newest first. "after" is the (created_at, id) of the last
// row of the previous page; created_at travels as text to keep microseconds.
export async function findCompletedVideos(limit, after = null) {
  const query = `
    SELECT
      id,
      owner_id,
      title,
      description,
      category,
      duration_seconds,
      width,
      height,
      thumbnail_object_key,
      created_at,
      created_at::text AS created_at_exact
    FROM video_metadata
    WHERE status = 'COMPLETED'
      AND (
        $2::timestamptz IS NULL
        OR (created_at, id) < ($2::timestamptz, $3::uuid)
      )
    ORDER BY created_at DESC, id DESC
    LIMIT $1
  `;

  const result = await pool.query(query, [
    limit,
    after?.createdAt ?? null,
    after?.id ?? null
  ]);

  return result.rows;
}
