const { ObjectId } = require("mongodb");
const db = require("./db");
const catalog = require("./catalogClient");

const STATUSES = ["PENDING", "SHIPPED", "DELIVERED", "CANCELLED"];

// ---------- helpers ----------

function cartTotal(items) {
  return items.reduce((sum, item) => sum + item.price * item.quantity, 0);
}

async function loadCart(cartId) {
  const cart = await db.carts().findOne({ _id: cartId });
  const items = cart ? cart.items : [];
  return { id: cartId, items, total: cartTotal(items) };
}

async function saveItems(cartId, items) {
  await db.carts().updateOne({ _id: cartId }, { $set: { items, updatedAt: new Date() } }, { upsert: true });
  return loadCart(cartId);
}

function toOrder(doc) {
  return { ...doc, id: doc._id.toString(), createdAt: doc.createdAt.toISOString() };
}

// ---------- queries ----------

async function cart({ cartId }) {
  return loadCart(cartId);
}

async function ordersByEmail({ email }) {
  const docs = await db.orders().find({ email: email.trim().toLowerCase() }).sort({ createdAt: -1 }).toArray();
  return docs.map(toOrder);
}

async function allOrders() {
  const docs = await db.orders().find().sort({ createdAt: -1 }).toArray();
  return docs.map(toOrder);
}

// ---------- mutations ----------

async function addToCart({ cartId, productId, quantity }) {
  if (quantity <= 0) throw new Error("La cantidad debe ser mayor que cero");

  const product = await catalog.getProduct(productId);
  const { items } = await loadCart(cartId);
  const existing = items.find((item) => item.productId === productId);
  const newQuantity = (existing ? existing.quantity : 0) + quantity;

  if (newQuantity > product.stock) throw new Error(`Solo hay ${product.stock} unidades disponibles`);

  if (existing) {
    existing.quantity = newQuantity;
  } else {
    items.push({ productId, name: product.name, price: product.price, image: product.image, quantity });
  }
  return saveItems(cartId, items);
}

async function updateCartItem({ cartId, productId, quantity }) {
  if (quantity <= 0) return removeFromCart({ cartId, productId });

  const product = await catalog.getProduct(productId);
  if (quantity > product.stock) throw new Error(`Solo hay ${product.stock} unidades disponibles`);

  const { items } = await loadCart(cartId);
  const item = items.find((i) => i.productId === productId);
  if (item) item.quantity = quantity;
  return saveItems(cartId, items);
}

async function removeFromCart({ cartId, productId }) {
  const { items } = await loadCart(cartId);
  return saveItems(cartId, items.filter((item) => item.productId !== productId));
}

async function placeOrder({ cartId, customerName, email, address }) {
  if (!customerName.trim() || !email.includes("@") || !address.trim()) {
    throw new Error("Por favor completa nombre, correo y dirección");
  }

  const { items } = await loadCart(cartId);
  if (items.length === 0) throw new Error("El carrito está vacío");

  // 1. Check current price and stock of every product
  const orderItems = [];
  for (const item of items) {
    const product = await catalog.getProduct(item.productId);
    if (product.stock < item.quantity) throw new Error(`No hay unidades suficientes de "${product.name}"`);
    orderItems.push({ productId: product.id, name: product.name, price: product.price, quantity: item.quantity });
  }

  // 2. Reserve the units in the catalog
  for (const item of orderItems) {
    await catalog.reserveStock(item.productId, item.quantity);
  }

  // 3. Save the order and empty the cart
  const order = {
    customerName: customerName.trim(),
    email: email.trim().toLowerCase(),
    address: address.trim(),
    items: orderItems,
    total: cartTotal(orderItems),
    status: "PENDING",
    createdAt: new Date(),
  };
  const result = await db.orders().insertOne(order);
  await saveItems(cartId, []);

  return toOrder({ ...order, _id: result.insertedId });
}

async function updateOrderStatus({ orderId, status }) {
  if (!STATUSES.includes(status)) throw new Error("Estado no válido");

  const doc = await db.orders().findOneAndUpdate(
    { _id: new ObjectId(orderId) },
    { $set: { status } },
    { returnDocument: "after" }
  );
  if (!doc) throw new Error("Pedido no encontrado");
  return toOrder(doc);
}

module.exports = {
  cart,
  ordersByEmail,
  allOrders,
  addToCart,
  updateCartItem,
  removeFromCart,
  placeOrder,
  updateOrderStatus,
};
