import { Router } from "express";
import { checkPostgresConnection } from "../config/postgres.js";
import { checkQdrantConnection } from "../config/qdrant.js";

const router = Router();

router.get("/", (req, res) => {
  res.status(200).json({
    status: "ok",
    service: "recommendation-service"
  });
});

// router.get("/ready", async (req, res) => {
//   const [postgresResult, qdrantResult] = await Promise.allSettled([
//     checkPostgresConnection(),
//     checkQdrantConnection()
//   ]);

//   const dependencies = {
//     postgres: postgresResult.status === "fulfilled"
//       ? "connected"
//       : "unavailable",

//     qdrant: qdrantResult.status === "fulfilled"
//       ? "connected"
//       : "unavailable"
//   };

//   const ready = Object.values(dependencies).every(
//     (status) => status === "connected"
//   );

//   res.status(ready ? 200 : 503).json({
//     status: ready ? "ready" : "not_ready",
//     dependencies
//   });
// });

export default router;