import express from "express";
import healthRoutes from "./routes/health.routes.js";
import recommendationsRoutes from "./routes/recommendation.routes.js"

const app = express();

app.use(express.json());
app.use("/health", healthRoutes);
app.use("/api/recommendations", recommendationsRoutes);

app.get("/", (req, res) => {
  res.json({
    service: "recommendation-service",
    status: "running"
  });
});

app.use((req, res) => {
  res.status(404).json({
    error: "ROUTE_NOT_FOUND",
    message: "The requested route does not exist"
  });
});

app.use((error, req, res, next) => {
  const status = Number.isInteger(error.statusCode) ? error.statusCode : 500;
  if (status >= 500) console.error("Recommendation request failed:", error.message);
  res.status(status).json({ error: status === 400 ? "INVALID_REQUEST" : "INTERNAL_SERVER_ERROR" });
});

export default app;
