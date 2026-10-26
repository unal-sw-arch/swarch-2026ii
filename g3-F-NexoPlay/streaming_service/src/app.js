
import express from "express";
import { env } from "../env.js";
import streamingRoutes from "./routes/streaming.routes.js";
import { errorMiddleware } from "./middleware/error.middleware.js";

const app = express();

app.disable("x-powered-by");

// The frontend and hls.js load the catalog, playlists and segments cross-origin
app.use((req, res, next) => {
  res.setHeader("Access-Control-Allow-Origin", env.corsOrigin);
  res.setHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Range");

  if (req.method === "OPTIONS") {
    return res.sendStatus(204);
  }

  next();
});

app.use(express.json());

app.get("/health", (req, res) => {
  res.status(200).json({
    status: "ok",
    service: "streaming-service"
  });
});

app.use("/videos", streamingRoutes);

app.use((req, res) => {
  res.status(404).json({
    error: "ROUTE_NOT_FOUND",
    message: "The requested route does not exist"
  });
});

app.use(errorMiddleware);

export default app;
