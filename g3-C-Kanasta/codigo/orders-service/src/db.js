const { MongoClient } = require("mongodb");

const MONGO_URL = process.env.MONGO_URL || "mongodb://localhost:27017";
const client = new MongoClient(MONGO_URL);
let db;

async function connect() {
  await client.connect();
  db = client.db(process.env.MONGO_DB || "orders");
  console.log("Connected to MongoDB");
}

const carts = () => db.collection("carts");
const orders = () => db.collection("orders");

module.exports = { connect, carts, orders };
