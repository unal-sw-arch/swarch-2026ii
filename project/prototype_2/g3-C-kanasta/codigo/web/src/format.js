// Small helpers used by the page templates

const STATUS_LABELS = {
  PENDING: "Pendiente",
  SHIPPED: "En camino",
  DELIVERED: "Entregado",
  CANCELLED: "Cancelado",
};

const PRICE_UNITS = { unidad: "", kg: " / kg", litro: " / litro" };

function formatPrice(value) {
  return "$ " + Math.round(value).toLocaleString("es-CO");
}

// 1.5 kg -> "1,5 kg"
function formatQuantity(quantity, unit) {
  const text = Number(quantity).toLocaleString("es-CO", { maximumFractionDigits: 2 });
  if (unit === "kg") return `${text} kg`;
  if (unit === "litro") return `${text} L`;
  return text;
}

function priceWithUnit(product) {
  return formatPrice(product.price) + PRICE_UNITS[product.unit];
}

function formatDate(isoDate) {
  return new Date(isoDate).toLocaleDateString("es-CO", { year: "numeric", month: "long", day: "numeric" });
}

function stars(rating) {
  const full = Math.round(rating);
  return "★".repeat(full) + "☆".repeat(5 - full);
}

// Products sold by weight or volume use steps of 0.25
function quantityStep(unit) {
  return unit === "unidad" ? 1 : 0.25;
}

function shortId(id) {
  return id.slice(-6).toUpperCase();
}

module.exports = { STATUS_LABELS, formatPrice, formatQuantity, priceWithUnit, formatDate, stars, quantityStep, shortId };
