import { QdrantClient } from "@qdrant/js-client-rest";
import { env } from "./env.js";

export const qdrantClient = new QdrantClient({
  url: env.qdrantUrl,
  timeout: 5000
});

export async function checkQdrantConnection() {
  await qdrantClient.getCollections();
  return true;
}