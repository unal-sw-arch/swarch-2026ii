import express from "express";
import cors from "cors";
import jwt from "jsonwebtoken";
import { createProxyMiddleware } from "http-proxy-middleware";

const app = express();

app.use(cors({ origin: process.env.FRONTEND_ORIGIN }));

// Security: strip any x-user-* headers sent by the client,
// so nobody can impersonate another user.
app.use((req, res, next) => {
  delete req.headers["x-user-id"];
  delete req.headers["x-user-role"];
  next();
});

// Middleware that requires a valid token
function requireAuth(req, res, next) {
  const header = req.headers.authorization || "";
  const [type, token] = header.split(" ");
  if (type !== "Bearer" || !token) {
    return res.status(401).json({ error: "Token required" });
  }
  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET);
    if (
      !payload ||
      typeof payload !== "object" ||
      typeof payload.sub !== "string" ||
      !payload.sub
    ) {
      return res.status(401).json({ error: "Invalid token" });
    }
    req.user = payload;
    // Internal services learn who the user is from these headers
    req.headers["x-user-id"] = payload.sub;
    req.headers["x-user-role"] = payload.role;
    next();
  } catch {
    res.status(401).json({ error: "Invalid or expired token" });
  }
}

app.get("/health", (req, res) => res.json({ status: "ok" }));

// PUBLIC ROUTE: /auth/** goes to the Auth Service
app.use(
  createProxyMiddleware({
    target: process.env.AUTH_URL,
    pathFilter: "/auth",
    changeOrigin: true,
  }),
);

// PROTECTED ROUTE: validate the token first, then forward
app.use("/api/videos", requireAuth);
app.use(
  createProxyMiddleware({
    target: process.env.PROCESSING_URL,
    pathFilter: "/api/videos",
    changeOrigin: true,
    pathRewrite: (path, req) => {
      const pathname = path.split("?")[0];

      if (req.method === "POST" && pathname === "/api/videos") {
        return path.replace(/^\/api\/videos/, "/upload_video");
      }

      return path.replace(/^\/api\/videos/, "/videos");
    },
  }),
);

app.listen(6767, () => console.log("api-gateway :6767"));
