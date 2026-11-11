import pg from "pg";
import { env } from "./env.js";

const { Pool } = pg;

export const postgresPool = new Pool({
  host: env.postgresHost,
  port: env.postgresPort,
  database: env.postgresDatabase,
  user: env.postgresUser,
  password: env.postgresPassword,
  max: 10,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 5000
});

postgresPool.on("error", (error) => {
  console.error("Unexpected PostgreSQL pool error:", error);
});

export async function checkPostgresConnection() {
  const result = await postgresPool.query("SELECT 1");
  return result.rowCount === 1;
}