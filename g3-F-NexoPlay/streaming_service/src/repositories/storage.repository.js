
import {
  GetObjectCommand,
  HeadObjectCommand
} from "@aws-sdk/client-s3";

import { s3, bucket } from "../clients/minio.client.js";

function isNotFound(error) {
  return (
    error.name === "NotFound" ||
    error.name === "NoSuchKey" ||
    error.$metadata?.httpStatusCode === 404
  );
}

async function send(command) {
  try {
    return await s3.send(command);
  } catch (error) {
    if (isNotFound(error)) {
      const missing = new Error("The resource does not exist");
      missing.code = "OBJECT_NOT_FOUND";
      throw missing;
    }

    throw error;
  }
}

export async function getObject(key) {
  const result = await send(
    new GetObjectCommand({Bucket: bucket, Key: key}));

  return {
    body: result.Body,
    contentType: result.ContentType,
    contentLength: result.ContentLength
  };
}

export async function objectExists(key) {
  await send(new HeadObjectCommand({Bucket: bucket, Key: key}));

  return true;
}
