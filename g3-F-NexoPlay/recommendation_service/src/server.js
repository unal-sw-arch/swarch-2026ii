import app from "./app.js";
import { env } from "./config/env.js";
import { postgresPool } from "./config/postgres.js";

const server = app.listen(env.port, "0.0.0.0", () => {
  console.log(
    `Recommendation Service listening on port ${env.port}`
  );
});

async function shutdown(signal) {
  console.log(`${signal} received. Shutting down...`);

  server.close(async () => {
    await postgresPool.end();
    console.log("PostgreSQL pool closed.");
    process.exit(0);
  });

//   setTimeout(() => {
//     console.error("Forced shutdown.");

//     process.exit(1);
//   }, 10000).unref();
}

process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));