
import { env } from "../config/env.js";
import { userEmbeddingRepository } from "../repositories/user-embedding.repository.js";
import { videoEmbeddingRepository } from "../repositories/video-embedding.repository.js";
import { videoMetadataRepository } from "../repositories/video-metadata.repository.js";
import { rankingService } from "./ranking.service.js";
import { catalogService } from "./catalog.service.js";

class RecommendationService {
  async getRecommendations({ userId, limit, offset = 0 }) {
    const parsedLimit = this.validateLimit(limit);
    const parsedOffset = this.validateOffset(offset);

    // Sin usuario autenticado: catálogo general.
    if (!userId) {
      return this.getGeneralCatalog(parsedLimit, parsedOffset);
    }

    return await this.getPersonalizedRecommendations({
      userId,
      limit: parsedLimit,
      offset: parsedOffset
    });
  }

  async getPersonalizedRecommendations({ userId, limit, offset }) {
    let candidates;
    try {
      const profile = await userEmbeddingRepository.findByUserId(userId);
      if (!profile) return this.getGeneralCatalog(limit, offset);
      const candidateLimit = (offset + limit) * env.candidateMultiplier;
      candidates = await videoEmbeddingRepository.findSimilarVideos(profile.vector, candidateLimit);
    } catch (error) {
      console.warn("Vector recommendations unavailable; using general catalog:", error.message);
      return this.getGeneralCatalog(limit, offset);
    }

    if (!candidates.length) {
      return this.getGeneralCatalog(limit, offset);
    }

    const videoIds = candidates.map(candidate => candidate.videoId);

    const videos = await videoMetadataRepository.findCompletedByIds(videoIds);

    if (!videos.length) {
      return this.getGeneralCatalog(limit, offset);
    }

    const items = rankingService.rankCandidates({
      candidates,
      videos,
      limit,
      offset
    });

    if (!items.length) {
      return this.getGeneralCatalog(limit, offset);
    }

    return {
      mode: "PERSONALIZED",
      items
    };
  }

  async getGeneralCatalog(limit, offset) {
    return catalogService.getGeneralCatalog({ limit, offset });
  }

  validateLimit(limit) {
    const parsed = limit === undefined || limit === "" ? env.defaultRecommendationLimit : Number(limit);
    if (!Number.isInteger(parsed) || parsed < 1) {
      const error = new Error("limit inválido.");
      error.statusCode = 400;
      throw error;
    }
    return Math.min(parsed, env.maxRecommendationLimit);
  }

  validateOffset(offset) {
    const parsed = Number(offset);

    if (!Number.isInteger(parsed) || parsed < 0) {
      const error = new Error("offset inválido.");
      error.statusCode = 400;
      throw error;
    }

    return parsed;
  }
}

export const recommendationService = new RecommendationService();
