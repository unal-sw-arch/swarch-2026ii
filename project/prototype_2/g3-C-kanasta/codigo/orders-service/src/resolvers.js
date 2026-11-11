const { ObjectId } = require("mongodb");
const db = require("./db");
const catalog = require("./catalogClient");
const publisher = require("./publisher");

const STATUSES = ["PENDING", "SHIPPED", "DELIVERED", "CANCELLED"];
const UNIT_NAMES = { unidad: "unidades", kg: "kg", litro: "litros" };

// ---------- helpers ----------

function subtotal(item) {
  return Math.round(item.price * item.quantity);
}

function withSubtotals(items) {
  return items.map((item) => ({ ...item, subtotal: subtotal(item) }));
}

function total(items) {
  return items.reduce((sum, item) => sum + subtotal(item), 0);
}

// Products sold by weight or volume have an estimated price
function isEstimated(items) {
  return items.some((item) => item.unit !== "unidad");
}

function checkQuantity(product, quantity) {
  if (!(quantity > 0)) throw new Error("La cantidad debe ser mayor que cero");
  if (product.unit === "unidad" && !Number.isInteger(quantity)) {
    throw new Error(`"${product.name}" se vende por unidades enteras`);
  }
  if (quantity > product.stock) {
    throw new Error(`Solo hay ${product.stock} ${UNIT_NAMES[product.unit]} disponibles de "${product.name}"`);
  }
}

async function loadCart(cartId) {
  const cart = await db.carts().findOne({ _id: cartId });
  const items = cart ? cart.items : [];
  return { id: cartId, items: withSubtotals(items), total: total(items), estimated: isEstimated(items) };
}

async function saveItems(cartId, items) {
  const clean = items.map(({ subtotal, ...item }) => item);
  await db.carts().updateOne({ _id: cartId }, { $set: { items: clean, updatedAt: new Date() } }, { upsert: true });
  return loadCart(cartId);
}

function toOrder(doc) {
  return { notes: "", ...doc, id: doc._id.toString(), createdAt: doc.createdAt.toISOString() };
}

// The API Gateway sends who is calling in the X-User-* headers
function requireUser(context) {
  if (!context.user) throw new Error("Debes iniciar sesión");
  return context.user;
}

function requireAdmin(context) {
  const user = requireUser(context);
  if (user.role !== "ADMIN") throw new Error("Solo el administrador puede hacer esto");
  return user;
}

function round(quantity) {
  return Math.round(quantity * 100) / 100;
}

// ---------- queries ----------

async function cart({ cartId }) {
  return loadCart(cartId);
}

async function myOrders(args, context) {
  const user = requireUser(context);
  const docs = await db.orders().find({ userId: user.id }).sort({ createdAt: -1 }).toArray();
  return docs.map(toOrder);
}

async function order({ id }, context) {
  const user = requireUser(context);
  if (!ObjectId.isValid(id)) return null;
  const doc = await db.orders().findOne({ _id: new ObjectId(id) });
  if (!doc || (doc.userId !== user.id && user.role !== "ADMIN")) return null;
  return toOrder(doc);
}

async function allOrders({ status }, context) {
  requireAdmin(context);
  const filter = status ? { status } : {};
  const docs = await db.orders().find(filter).sort({ createdAt: -1 }).toArray();
  return docs.map(toOrder);
}

// ---------- mutations ----------

async function addToCart({ cartId, productId, quantity }) {
  const product = await catalog.getProduct(productId);
  const { items } = await loadCart(cartId);
  const existing = items.find((item) => item.productId === productId);
  const newQuantity = round((existing ? existing.quantity : 0) + quantity);

  checkQuantity(product, quantity);
  checkQuantity(product, newQuantity);

  if (existing) {
    existing.quantity = newQuantity;
  } else {
    items.push({ productId, name: product.name, price: product.price, unit: product.unit, image: product.image, quantity: round(quantity) });
  }
  return saveItems(cartId, items);
}

async function updateCartItem({ cartId, productId, quantity }) {
  if (quantity <= 0) return removeFromCart({ cartId, productId });

  const product = await catalog.getProduct(productId);
  checkQuantity(product, quantity);

  const { items } = await loadCart(cartId);
  const item = items.find((i) => i.productId === productId);
  if (item) item.quantity = round(quantity);
  return saveItems(cartId, items);
}

async function removeFromCart({ cartId, productId }) {
  const { items } = await loadCart(cartId);
  return saveItems(cartId, items.filter((item) => item.productId !== productId));
}

async function placeOrder({ cartId, phone, address, notes }, context) {
  const user = requireUser(context);
  if (!phone.trim() || !address.trim()) throw new Error("Por favor escribe el teléfono y la dirección de entrega");

  const { items } = await loadCart(cartId);
  if (items.length === 0) throw new Error("El carrito está vacío");

  // 1. Check current price and stock of every product
  const orderItems = [];
  for (const item of items) {
    const product = await catalog.getProduct(item.productId);
    checkQuantity(product, item.quantity);
    orderItems.push({ productId: product.id, name: product.name, price: product.price, unit: product.unit, quantity: item.quantity });
  }

  // 2. Reserve the stock in the catalog (if one fails, give back what was reserved)
  const reserved = [];
  try {
    for (const item of orderItems) {
      await catalog.reserveStock(item.productId, item.quantity);
      reserved.push(item);
    }
  } catch (error) {
    for (const item of reserved) await catalog.releaseStock(item.productId, item.quantity);
    throw error;
  }

  // 3. Save the order and empty the cart
  const newOrder = {
    userId: user.id,
    customerName: user.name,
    email: user.email,
    phone: phone.trim(),
    address: address.trim(),
    notes: (notes || "").trim(),
    items: withSubtotals(orderItems),
    total: total(orderItems),
    estimated: isEstimated(orderItems),
    status: "PENDING",
    createdAt: new Date(),
  };
  const result = await db.orders().insertOne(newOrder);
  await saveItems(cartId, []);
  const saved = toOrder({ ...newOrder, _id: result.insertedId });

  // 4. Ask for the receipt email (asynchronous)
  publisher.publish({
    type: "ORDER_PLACED",
    to: saved.email,
    name: saved.customerName,
    data: {
      orderId: saved.id.slice(-6).toUpperCase(),
      items: saved.items,
      total: saved.total,
      address: saved.address,
      phone: saved.phone,
      notes: saved.notes,
      estimated: saved.estimated,
    },
  });
  return saved;
}

async function updateOrderStatus({ orderId, status }, context) {
  requireAdmin(context);
  if (!STATUSES.includes(status)) throw new Error("Estado no válido");
  if (!ObjectId.isValid(orderId)) throw new Error("Pedido no encontrado");

  const current = await db.orders().findOne({ _id: new ObjectId(orderId) });
  if (!current) throw new Error("Pedido no encontrado");
  if (current.status === status) return toOrder(current);
  if (current.status === "CANCELLED" || current.status === "DELIVERED") {
    throw new Error("Este pedido ya está cerrado y no se puede cambiar");
  }

  // A cancelled order gives its products back to the inventory
  if (status === "CANCELLED") {
    for (const item of current.items) await catalog.releaseStock(item.productId, item.quantity);
  }

  const doc = await db.orders().findOneAndUpdate(
    { _id: current._id },
    { $set: { status } },
    { returnDocument: "after" }
  );
  return toOrder(doc);
}

module.exports = {
  cart,
  myOrders,
  order,
  allOrders,
  addToCart,
  updateCartItem,
  removeFromCart,
  placeOrder,
  updateOrderStatus,
};
