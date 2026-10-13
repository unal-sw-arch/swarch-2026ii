// REST client for the Catalog Service
const CATALOG_URL = process.env.CATALOG_URL || "http://localhost:8001";

async function getProduct(productId) {
  const response = await fetch(`${CATALOG_URL}/api/products/${productId}`);
  if (response.status === 404) throw new Error("El producto no existe");
  if (!response.ok) throw new Error("No fue posible consultar el catálogo");
  return response.json();
}

async function reserveStock(productId, quantity) {
  const response = await fetch(`${CATALOG_URL}/api/products/${productId}/reserve`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ quantity }),
  });
  if (!response.ok) throw new Error("No hay unidades suficientes de un producto");
  return response.json();
}

module.exports = { getProduct, reserveStock };
