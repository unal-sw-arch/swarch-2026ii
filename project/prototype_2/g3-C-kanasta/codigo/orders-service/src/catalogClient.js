// REST client for the Catalog Service (direct call inside the backend network)
const CATALOG_URL = process.env.CATALOG_URL || "http://localhost:8001/api/catalog";

async function getProduct(productId) {
  const response = await fetch(`${CATALOG_URL}/products/${productId}`);
  if (response.status === 404) throw new Error("El producto no existe");
  if (!response.ok) throw new Error("No fue posible consultar el catálogo");
  return response.json();
}

async function changeStock(productId, quantity, action) {
  const response = await fetch(`${CATALOG_URL}/products/${productId}/${action}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ quantity }),
  });
  if (!response.ok) throw new Error("No hay cantidad suficiente de un producto");
  return response.json();
}

const reserveStock = (productId, quantity) => changeStock(productId, quantity, "reserve");
const releaseStock = (productId, quantity) => changeStock(productId, quantity, "release");

module.exports = { getProduct, reserveStock, releaseStock };
