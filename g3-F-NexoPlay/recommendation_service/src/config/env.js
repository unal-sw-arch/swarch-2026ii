import "dotenv/config";



function required(name) {
  const value = process.env[name];

  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }

  return value;
}

function number(name, defaultValue) {
  const value = process.env[name];

  if (value === undefined || value === "") {
    return defaultValue;
  }

  return Number(value);
}

export const env = {
  nodeEnv: process.env.NODE_ENV ?? "development",
  port: number("RECOMMENDATION_PORT", 8088),
  qdrantUrl: required("QDRANT_URL"),

  videoCollection: process.env.VIDEO_COLLECTION ?? "video_embeddings",
  userCollection: process.env.USER_COLLECTION ?? "user_embeddings",
  embeddingModelVersion: required("EMBEDDING_MODEL_VERSION"),
  textTemplateVersion: process.env.TEXT_TEMPLATE_VERSION ?? "video-text-v1",

  defaultRecommendationLimit: number("DEFAULT_RECOMMENDATION_LIMIT", 10),
  maxRecommendationLimit: number("MAX_RECOMMENDATION_LIMIT", 50),
  candidateMultiplier: number("CANDIDATE_MULTIPLIER", 3),

  postgresHost: process.env.POSTGRES_HOST ?? "localhost",
  postgresPort: number("POSTGRES_PORT", 5432),
  postgresDatabase: required("POSTGRES_DB"),
  postgresUser: required("POSTGRES_USER"),
  postgresPassword: required("POSTGRES_PASSWORD"),
};