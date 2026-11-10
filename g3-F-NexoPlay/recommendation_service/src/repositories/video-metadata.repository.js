import { postgresPool } from "../config/postgres.js";

class VideoMetadataRepository {
  async findCompletedByIds(videoIds) {
    if (!Array.isArray(videoIds) || videoIds.length === 0) {
      return [];
    }

    const uniqueIds = [...new Set(videoIds)];

    const query = `
      SELECT
        id,
        owner_id,
        title,
        description,
        category,
        status,
        thumbnail_object_key,
        duration_seconds,
        width,
        height,
        created_at,
        processed_at
      FROM video_metadata
      WHERE
        id = ANY($1::uuid[])
        AND status = 'COMPLETED'
      ORDER BY array_position($1::uuid[], id);
    `;

    const result = await postgresPool.query(query, [uniqueIds]);

    return result.rows.map((row) => this.mapVideo(row));
  }

  
    async findGeneralCatalog({ limit, offset }) {
    const query = `
        SELECT
        id,
        owner_id,
        title,
        description,
        category,
        status,
        thumbnail_object_key,
        duration_seconds,
        width,
        height,
        created_at,
        processed_at
        FROM video_metadata
        WHERE status = 'COMPLETED'
        ORDER BY created_at DESC, id DESC
        LIMIT $1
        OFFSET $2;
    `;

    const result = await postgresPool.query(query, [limit, offset]);

    return result.rows.map((row) => this.mapVideo(row));
    }

  mapVideo(row) {
    return {
      videoId: row.id,
      ownerId: row.owner_id,
      title: row.title,
      description: row.description,
      category: row.category,
      thumbnailUrl: `/videos/${row.id}/thumbnail`,
      durationSeconds:
        row.duration_seconds === null
          ? null
          : Number(row.duration_seconds),
      width: row.width,
      height: row.height,
      createdAt: row.created_at,
      processedAt: row.processed_at
    };
  }
}

export const videoMetadataRepository =
  new VideoMetadataRepository();