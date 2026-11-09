const express = require("express");
const fs = require("fs");
const path = require("path");

const app = express();
const PORT = process.env.PORT || 8003;
const IMAGES_DIR = process.env.IMAGES_DIR || path.join(__dirname, "..", "images");
const ALLOWED = [".jpg", ".jpeg", ".png", ".webp"];

// Simple SVG used when an image does not exist
function placeholder(text) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300">
    <rect width="100%" height="100%" fill="#eef2f0"/>
    <text x="50%" y="50%" font-family="sans-serif" font-size="22" fill="#7a8a83"
          text-anchor="middle" dominant-baseline="middle">${text}</text>
  </svg>`;
}

// Keeps only safe characters, for example "Mi Foto.JPG" -> "mi-foto.jpg"
function safeName(name) {
  return path.basename(name || "").toLowerCase().replace(/[^a-z0-9.\-]+/g, "-");
}

app.get("/health", (req, res) => res.json({ status: "ok" }));

// List of available images
app.get("/images", (req, res) => {
  const files = fs.readdirSync(IMAGES_DIR).filter((f) => ALLOWED.includes(path.extname(f).toLowerCase()));
  res.json(files.sort());
});

// Returns one image, or a placeholder if it does not exist
app.get("/images/:name", (req, res) => {
  const name = path.basename(req.params.name);
  const file = path.join(IMAGES_DIR, name);

  if (ALLOWED.includes(path.extname(name).toLowerCase()) && fs.existsSync(file)) {
    res.set("Cache-Control", "public, max-age=3600");
    return res.sendFile(file);
  }
  res.type("image/svg+xml").send(placeholder("Imagen no disponible"));
});

// Saves a new image sent by the admin app: POST /images?name=photo.jpg (body = image bytes)
app.post("/images", express.raw({ type: "image/*", limit: "5mb" }), (req, res) => {
  const name = safeName(req.query.name);
  if (!ALLOWED.includes(path.extname(name))) {
    return res.status(400).json({ detail: "Solo se aceptan imágenes JPG, PNG o WEBP" });
  }
  if (!Buffer.isBuffer(req.body) || req.body.length === 0) {
    return res.status(400).json({ detail: "La imagen está vacía" });
  }
  fs.writeFileSync(path.join(IMAGES_DIR, name), req.body);
  res.status(201).json({ name });
});

app.listen(PORT, () => console.log(`Image provider listening on port ${PORT}`));
