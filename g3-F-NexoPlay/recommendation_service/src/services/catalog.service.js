import { env } from "../config/env.js";
import { videoMetadataRepository } from "../repositories/video-metadata.repository.js";

class CatalogService {
  async getGeneralCatalog({ limit, offset }) {
    const parsedLimit = this.validateLimit(limit);
    const parsedOffset = this.validateOffset(offset);

    const videos = await videoMetadataRepository.findGeneralCatalog({
      limit: parsedLimit,
      offset: parsedOffset
    });

    return {
      mode: "GENERAL",
      items: videos.map((video) => ({
        video_id: video.videoId,
        title: video.title,
        category: video.category,
        thumbnail_url: video.thumbnailUrl,
        score: null
      }))
    };
  }

  validateLimit(limit) {
    if (limit === undefined || limit === null || limit === "") {
      return env.defaultRecommendationLimit;
    }

    return Math.min(Number(limit), env.maxRecommendationLimit);
  }

  validateOffset(offset) {
    if (offset === undefined || offset === "") {
      return 0;
    }

    return Number(offset);
  }
}

export const catalogService = new CatalogService();