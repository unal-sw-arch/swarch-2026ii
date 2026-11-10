import { env } from "./env.js";
import { qdrantClient } from "./qdrant.js";

export const dimension = 384;
export const templateVersion = env.textTemplateVersion;

export async function validateCollection(name, role) {
  const info = await qdrantClient.getCollection(name);
  const vectors = info.config?.params?.vectors;
  const identity = info.config?.metadata?.nexoplay;
  if (vectors?.size !== dimension || vectors?.distance !== "Cosine" || vectors.multivector_config
      || identity?.role !== role || identity?.schema_version !== "1.0"
      || identity?.embedding_model_version !== env.embeddingModelVersion
      || identity?.text_template_version !== templateVersion || identity?.normalization !== "L2") {
    throw new Error("Incompatible embedding collection");
  }
}
