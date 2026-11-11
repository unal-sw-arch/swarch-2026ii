import { qdrantClient } from "../config/qdrant.js";
import { env } from "../config/env.js";
import { dimension, templateVersion, validateCollection } from "../config/embedding-space.js";

function validateVector(vector) {
  if (
    !Array.isArray(vector) ||
    vector.length !== dimension ||
    !vector.every(
      (value) => typeof value === "number" && Number.isFinite(value)
    )
  ) {
    throw new Error("Invalid query embedding");
  }
}

class VideoEmbeddingRepository {
  async findSimilarVideos(vector, limit) {
    validateVector(vector);
    await validateCollection(env.videoCollection, "videos");

    const results = await qdrantClient.query(
      env.videoCollection,
      {
        query: vector,
        limit,
        with_payload: true,
        filter: { must: [
          { key: "status", match: { value: "READY" } },
          { key: "embedding_model_version", match: { value: env.embeddingModelVersion } },
          { key: "text_template_version", match: { value: templateVersion } },
        ] },
      }
    );

    return results.points.map((point) => ({
      videoId: String(point.payload?.video_id ?? point.id),
      score: point.score,
      payload: point.payload ?? {}
    }));
  }
}

export const videoEmbeddingRepository = new VideoEmbeddingRepository();
