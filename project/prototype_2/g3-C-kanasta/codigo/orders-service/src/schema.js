const { buildSchema } = require("graphql");

// unit: "unidad", "kg" or "litro". quantity can have decimals for kg and litro.
module.exports = buildSchema(`
  type CartItem {
    productId: Int!
    name: String!
    price: Int!
    unit: String!
    image: String!
    quantity: Float!
    subtotal: Int!
  }

  type Cart {
    id: ID!
    items: [CartItem!]!
    total: Int!
    estimated: Boolean!
  }

  type OrderItem {
    productId: Int!
    name: String!
    price: Int!
    unit: String!
    quantity: Float!
    subtotal: Int!
  }

  type Order {
    id: ID!
    userId: Int!
    customerName: String!
    email: String!
    phone: String!
    address: String!
    notes: String!
    items: [OrderItem!]!
    total: Int!
    estimated: Boolean!
    status: String!
    createdAt: String!
  }

  type Query {
    cart(cartId: ID!): Cart!
    myOrders: [Order!]!
    order(id: ID!): Order
    allOrders(status: String): [Order!]!
  }

  type Mutation {
    addToCart(cartId: ID!, productId: Int!, quantity: Float!): Cart!
    updateCartItem(cartId: ID!, productId: Int!, quantity: Float!): Cart!
    removeFromCart(cartId: ID!, productId: Int!): Cart!
    placeOrder(cartId: ID!, phone: String!, address: String!, notes: String): Order!
    updateOrderStatus(orderId: ID!, status: String!): Order!
  }
`);
