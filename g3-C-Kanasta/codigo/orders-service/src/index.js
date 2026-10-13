const express = require("express");
const { createHandler } = require("graphql-http/lib/use/express");
const schema = require("./schema");
const resolvers = require("./resolvers");
const db = require("./db");

const app = express();
const PORT = process.env.PORT || 8002;

app.get("/health", (req, res) => res.json({ status: "ok" }));
app.all("/graphql", createHandler({ schema, rootValue: resolvers }));

db.connect()
  .then(() => app.listen(PORT, () => console.log(`Orders service listening on port ${PORT}`)))
  .catch((error) => {
    console.error("Could not connect to MongoDB", error);
    process.exit(1);
  });
