import { qdrantClient } from "../config/qdrant.js";
import { env } from "../config/env.js";
import { dimension, templateVersion, validateCollection } from "../config/embedding-space.js";

function validateVector(vector) {
  if (
    !Array.isArray(vector) ||
    vector.length !== dimension ||
    !vector.every(
      (value) => typeof value === "number" && Number.isFinite(value)
    ) ||
    Math.abs(Math.hypot(...vector) - 1) >= 1e-4
  ) {
    throw new Error("User embedding contains an invalid vector");
  }
}

class UserEmbeddingRepository {
  async findByUserId(userId) {
    await validateCollection(env.userCollection, "users");
    const points = await qdrantClient.retrieve(
      env.userCollection,
      {
        ids: [userId],
        with_payload: true,
        with_vector: true
      }
    );

    if (!points || points.length === 0) {
      return null;
    }

    const point = points[0];
    const payload = point.payload ?? {};

    if (payload.status === "NO_PROFILE") {
      return null;
    }

    if (
      payload.user_id !== userId ||
      String(point.id) !== userId ||
      !["READY", "PARTIAL"].includes(payload.status) ||
      payload.embedding_model_version !== env.embeddingModelVersion ||
      payload.text_template_version !== templateVersion
    ) {
      throw new Error("Incompatible user profile");
    }
    const vector = point.vector;

    validateVector(vector);

    return {
      userId,
      vector,
      payload
    };
  }
}

export const userEmbeddingRepository =
  new UserEmbeddingRepository();
