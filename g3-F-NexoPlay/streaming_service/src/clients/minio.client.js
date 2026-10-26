
import { S3Client } from "@aws-sdk/client-s3";
import { env } from "../../env.js";

export const s3 = new S3Client({
  endpoint: env.minioEndpoint,
  region: "us-east-1",
  forcePathStyle: true,
  credentials: {
    accessKeyId: env.minioAccessKey,
    secretAccessKey: env.minioSecretKey
  }
});

export const bucket = env.minioBucket;
