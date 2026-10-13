
import app from "./app.js";
import { env } from "../env.js";
import { pool, closePostgres } from "./clients/postgres.client.js";
import { connectRedis, quitRedis } from "./clients/redis.client.js";

let server;

async function start() {
  await connectRedis();
  server = app.listen(env.port, "0.0.0.0", () => {
    console.log(`Streaming Service listening on ${env.port}`);
  });
}

async function shutdown() {
  console.log("Shutting down Streaming Service...");

  if (server) {
    await new Promise((resolve) => server.close(resolve));
  }

  await closePostgres();
  await quitRedis();

  process.exit(0);
}

process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);

start().catch((error) => {
  console.error("Could not start Streaming Service:", error);
  process.exit(1);
});
