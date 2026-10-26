
import { createClient } from "redis";
import { env } from "../../env.js";

export const redis = createClient({
  url: env.redisUrl
});

redis.on("error", (error) => {
  console.error("Error de Redis:", error);
});

export async function connectRedis() {
  if (!redis.isOpen) {
    await redis.connect();
  }
}

export async function quitRedis() {
  if (redis.isOpen) {
    await redis.quit();
  }
}