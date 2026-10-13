
import { redis } from "../clients/redis.client.js";

export async function getCached(key) {
    try {
        const cached = await redis.get(key);
        return JSON.parse(cached)
    } catch (error) {
        console.error("Redis GET falló:", error.message);
        return null;
    }
}

export async function setCached(key, value, ttlSeconds) {
  try {
    await redis.set(key, JSON.stringify(value), {
      EX: ttlSeconds
    });
  } catch (error) {
    console.error("Redis SET falló:", error.message);
  }
}

export async function deleteCached(key) {
  try {
    await redis.del(key);
  } catch (error) {
    console.error("Redis DEL falló:", error.message);
  }
}
