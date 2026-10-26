const { buildSchema } = require("graphql");

module.exports = buildSchema(`
  type CartItem {
    productId: Int!
    name: String!
    price: Int!
    image: String!
    quantity: Int!
  }

  type Cart {
    id: ID!
    items: [CartItem!]!
    total: Int!
  }

  type OrderItem {
    productId: Int!
    name: String!
    price: Int!
    quantity: Int!
  }

  type Order {
    id: ID!
    customerName: String!
    email: String!
    address: String!
    items: [OrderItem!]!
    total: Int!
    status: String!
    createdAt: String!
  }

  type Query {
    cart(cartId: ID!): Cart!
    ordersByEmail(email: String!): [Order!]!
    allOrders: [Order!]!
  }

  type Mutation {
    addToCart(cartId: ID!, productId: Int!, quantity: Int!): Cart!
    updateCartItem(cartId: ID!, productId: Int!, quantity: Int!): Cart!
    removeFromCart(cartId: ID!, productId: Int!): Cart!
    placeOrder(cartId: ID!, customerName: String!, email: String!, address: String!): Order!
    updateOrderStatus(orderId: ID!, status: String!): Order!
  }
`);
