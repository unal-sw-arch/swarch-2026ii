
import pg from "pg";
import { env } from "../../env.js";

const { Pool } = pg;

export const pool = new Pool({
  ...env.postgres,
  max: 10,
  connectionTimeoutMillis: 5000,
  idleTimeoutMillis: 30000
});

pool.on("error", (error) => {
  console.error("Error on idle PostgreSQL connection:", error.message);
});

export async function closePostgres() {
  await pool.end();
}
