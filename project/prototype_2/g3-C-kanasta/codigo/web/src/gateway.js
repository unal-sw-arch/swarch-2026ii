// Client for the API Gateway. The web server uses it while it renders each page.
const GATEWAY_URL = process.env.GATEWAY_URL || "http://localhost:8000";

function errorMessage(data) {
  if (typeof data.detail === "string") return data.detail;
  if (Array.isArray(data.detail)) return "Revisa los datos del formulario";
  return data.message || "No fue posible completar la operación";
}

// ---------- REST ----------

async function rest(path, { method = "GET", body, token } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(GATEWAY_URL + path, { method, headers, body: body && JSON.stringify(body) });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(errorMessage(data));
    error.status = response.status;
    throw error;
  }
  return data;
}

// ---------- GraphQL ----------

async function graphql(query, variables = {}, token) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(`${GATEWAY_URL}/graphql`, {
    method: "POST",
    headers,
    body: JSON.stringify({ query, variables }),
  });
  const result = await response.json();
  if (result.errors) throw new Error(result.errors[0].message);
  return result.data;
}

const CART_FIELDS = "id total estimated items { productId name price unit image quantity subtotal }";
const ORDER_FIELDS =
  "id customerName email phone address notes total estimated status createdAt items { productId name price unit quantity subtotal }";

module.exports = {
  // Catalog Service (REST)
  getStore: () => rest("/api/catalog/store"),
  getCategories: () => rest("/api/catalog/categories"),
  getProducts: (search, category) =>
    rest(`/api/catalog/products?${new URLSearchParams({ search, category })}`),
  getProduct: (id) => rest(`/api/catalog/products/${id}`),
  getReviews: (id) => rest(`/api/catalog/products/${id}/reviews`),
  saveReview: (id, review, token) => rest(`/api/catalog/products/${id}/reviews`, { method: "POST", body: review, token }),

  // Accounts Service (REST)
  register: (data) => rest("/api/accounts/register", { method: "POST", body: data }),
  login: (email, password) => rest("/api/accounts/login", { method: "POST", body: { email, password } }),
  logout: (token) => rest("/api/accounts/logout", { method: "POST", token }),
  getMe: (token) => rest("/api/accounts/me", { token }),
  updateMe: (data, token) => rest("/api/accounts/me", { method: "PUT", body: data, token }),
  changePassword: (data, token) => rest("/api/accounts/me/password", { method: "PUT", body: data, token }),

  // Orders Service (GraphQL)
  getCart: async (cartId) =>
    (await graphql(`query ($cartId: ID!) { cart(cartId: $cartId) { ${CART_FIELDS} } }`, { cartId })).cart,

  addToCart: (cartId, productId, quantity) =>
    graphql(
      `mutation ($cartId: ID!, $productId: Int!, $quantity: Float!) {
        addToCart(cartId: $cartId, productId: $productId, quantity: $quantity) { id }
      }`,
      { cartId, productId, quantity }
    ),

  updateCartItem: (cartId, productId, quantity) =>
    graphql(
      `mutation ($cartId: ID!, $productId: Int!, $quantity: Float!) {
        updateCartItem(cartId: $cartId, productId: $productId, quantity: $quantity) { id }
      }`,
      { cartId, productId, quantity }
    ),

  removeFromCart: (cartId, productId) =>
    graphql(
      `mutation ($cartId: ID!, $productId: Int!) { removeFromCart(cartId: $cartId, productId: $productId) { id } }`,
      { cartId, productId }
    ),

  placeOrder: async (cartId, phone, address, notes, token) =>
    (
      await graphql(
        `mutation ($cartId: ID!, $phone: String!, $address: String!, $notes: String) {
          placeOrder(cartId: $cartId, phone: $phone, address: $address, notes: $notes) { id }
        }`,
        { cartId, phone, address, notes },
        token
      )
    ).placeOrder,

  myOrders: async (token) => (await graphql(`query { myOrders { ${ORDER_FIELDS} } }`, {}, token)).myOrders,

  getOrder: async (id, token) =>
    (await graphql(`query ($id: ID!) { order(id: $id) { ${ORDER_FIELDS} } }`, { id }, token)).order,

  // Image Provider (HTTP)
  getImage: (name) => fetch(`${GATEWAY_URL}/images/${encodeURIComponent(name)}`),
};
