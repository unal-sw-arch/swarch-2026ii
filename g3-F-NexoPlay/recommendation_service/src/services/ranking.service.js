export const rankingService = {
  rankCandidates({ candidates, videos, limit, offset = 0 }) {
    const byId = new Map(videos.map(video => [video.videoId, video]));
    const bestScoreById = new Map();

    for (const candidate of candidates) {
      if (
        typeof candidate.videoId !== "string" ||
        !byId.has(candidate.videoId) ||
        !Number.isFinite(candidate.score) ||
        candidate.score < -1 ||
        candidate.score > 1
      ) {
        continue;
      }

      const currentScore = bestScoreById.get(candidate.videoId);
      if (currentScore === undefined || candidate.score > currentScore) {
        bestScoreById.set(candidate.videoId, candidate.score);
      }
    }

    return [...bestScoreById]
      .sort(([videoIdA, scoreA], [videoIdB, scoreB]) =>
        scoreB - scoreA || videoIdA.localeCompare(videoIdB)
      )
      .slice(offset, offset + limit)
      .map(([videoId, score]) => {
        const video = byId.get(videoId);
        return {
          video_id: video.videoId,
          title: video.title,
          category: video.category,
          thumbnail_url: video.thumbnailUrl,
          score
        };
      });
  }
};
