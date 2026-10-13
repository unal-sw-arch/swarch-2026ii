// Connects the web app with the services (Nginx forwards each path to its service)
const CATALOG_API = "/api";       // Catalog Service (REST)
const ORDERS_API = "/graphql";    // Orders Service (GraphQL)
const IMAGES_API = "/images";     // Image Provider (HTTP)

const STATUS_LABELS = {
  PENDING: "Pendiente",
  SHIPPED: "Enviado",
  DELIVERED: "Entregado",
  CANCELLED: "Cancelado",
};

// ---------- Catalog Service (REST) ----------

async function getJson(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error("No fue posible cargar la información");
  return response.json();
}

function getCategories() {
  return getJson(`${CATALOG_API}/categories`);
}

function getProducts(search = "", category = "") {
  const params = new URLSearchParams({ search, category });
  return getJson(`${CATALOG_API}/products?${params}`);
}

function getProduct(id) {
  return getJson(`${CATALOG_API}/products/${id}`);
}

// ---------- Image Provider (HTTP) ----------

function imageUrl(name) {
  return `${IMAGES_API}/${name}`;
}

// ---------- Orders Service (GraphQL) ----------

async function graphql(query, variables = {}) {
  const response = await fetch(ORDERS_API, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ query, variables }),
  });
  const result = await response.json();
  if (result.errors) throw new Error(result.errors[0].message);
  return result.data;
}

const CART_FIELDS = "id total items { productId name price image quantity }";
const ORDER_FIELDS = "id customerName email address total status createdAt items { productId name price quantity }";

async function getCart() {
  const data = await graphql(`query ($cartId: ID!) { cart(cartId: $cartId) { ${CART_FIELDS} } }`, { cartId: getCartId() });
  return data.cart;
}

async function addToCart(productId, quantity) {
  const data = await graphql(
    `mutation ($cartId: ID!, $productId: Int!, $quantity: Int!) {
      addToCart(cartId: $cartId, productId: $productId, quantity: $quantity) { ${CART_FIELDS} }
    }`,
    { cartId: getCartId(), productId, quantity }
  );
  return data.addToCart;
}

async function updateCartItem(productId, quantity) {
  const data = await graphql(
    `mutation ($cartId: ID!, $productId: Int!, $quantity: Int!) {
      updateCartItem(cartId: $cartId, productId: $productId, quantity: $quantity) { ${CART_FIELDS} }
    }`,
    { cartId: getCartId(), productId, quantity }
  );
  return data.updateCartItem;
}

async function removeFromCart(productId) {
  const data = await graphql(
    `mutation ($cartId: ID!, $productId: Int!) {
      removeFromCart(cartId: $cartId, productId: $productId) { ${CART_FIELDS} }
    }`,
    { cartId: getCartId(), productId }
  );
  return data.removeFromCart;
}

async function placeOrder(customerName, email, address) {
  const data = await graphql(
    `mutation ($cartId: ID!, $customerName: String!, $email: String!, $address: String!) {
      placeOrder(cartId: $cartId, customerName: $customerName, email: $email, address: $address) { ${ORDER_FIELDS} }
    }`,
    { cartId: getCartId(), customerName, email, address }
  );
  return data.placeOrder;
}

async function getOrdersByEmail(email) {
  const data = await graphql(`query ($email: String!) { ordersByEmail(email: $email) { ${ORDER_FIELDS} } }`, { email });
  return data.ordersByEmail;
}

async function getAllOrders() {
  const data = await graphql(`query { allOrders { ${ORDER_FIELDS} } }`);
  return data.allOrders;
}

async function updateOrderStatus(orderId, status) {
  const data = await graphql(
    `mutation ($orderId: ID!, $status: String!) { updateOrderStatus(orderId: $orderId, status: $status) { id status } }`,
    { orderId, status }
  );
  return data.updateOrderStatus;
}

// ---------- helpers ----------

// Each browser gets its own cart id (there are no user accounts yet)
function getCartId() {
  let id = localStorage.getItem("kanastaCartId");
  if (!id) {
    id = "cart-" + Date.now() + "-" + Math.random().toString(36).slice(2, 8);
    localStorage.setItem("kanastaCartId", id);
  }
  return id;
}

function formatPrice(value) {
  return "$ " + value.toLocaleString("es-CO");
}

function formatDate(isoDate) {
  return new Date(isoDate).toLocaleDateString("es-CO", { year: "numeric", month: "long", day: "numeric" });
}

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

async function refreshCartBadge(cart) {
  const badge = document.getElementById("cart-count");
  if (!badge) return;
  try {
    const current = cart || (await getCart());
    badge.textContent = current.items.reduce((sum, item) => sum + item.quantity, 0);
  } catch {
    badge.textContent = "0";
  }
}

function showMessage(text, type = "ok") {
  const box = document.getElementById("message");
  box.textContent = text;
  box.className = `message ${type}`;
  clearTimeout(box.timer);
  box.timer = setTimeout(() => (box.className = "message hidden"), 3500);
}
