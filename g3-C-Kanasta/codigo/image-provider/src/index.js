const express = require("express");
const fs = require("fs");
const path = require("path");

const app = express();
const PORT = process.env.PORT || 8003;
const IMAGES_DIR = path.join(__dirname, "..", "images");
const ALLOWED = [".jpg", ".jpeg", ".png", ".webp"];

// Simple SVG used when an image does not exist
function placeholder(text) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300">
    <rect width="100%" height="100%" fill="#eef2f0"/>
    <text x="50%" y="50%" font-family="sans-serif" font-size="22" fill="#7a8a83"
          text-anchor="middle" dominant-baseline="middle">${text}</text>
  </svg>`;
}

app.get("/health", (req, res) => res.json({ status: "ok" }));

// List of available images
app.get("/images", (req, res) => {
  const files = fs.readdirSync(IMAGES_DIR).filter((f) => ALLOWED.includes(path.extname(f).toLowerCase()));
  res.json(files);
});

// Returns one image, or a placeholder if it does not exist
app.get("/images/:name", (req, res) => {
  const name = path.basename(req.params.name);
  const file = path.join(IMAGES_DIR, name);

  if (ALLOWED.includes(path.extname(name).toLowerCase()) && fs.existsSync(file)) {
    res.set("Cache-Control", "public, max-age=86400");
    return res.sendFile(file);
  }
  res.type("image/svg+xml").send(placeholder("Imagen no disponible"));
});

app.listen(PORT, () => console.log(`Image provider listening on port ${PORT}`));
