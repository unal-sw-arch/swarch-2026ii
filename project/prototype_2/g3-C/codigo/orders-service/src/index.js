const express = require("express");
const { createHandler } = require("graphql-http/lib/use/express");
const schema = require("./schema");
const resolvers = require("./resolvers");
const db = require("./db");
const publisher = require("./publisher");

const app = express();
const PORT = process.env.PORT || 8002;

// The API Gateway adds these headers after checking the session (the name comes URL-encoded)
function userFromHeaders(headers) {
  if (!headers["x-user-id"]) return null;
  return {
    id: Number(headers["x-user-id"]),
    name: decodeURIComponent((headers["x-user-name"] || "").replace(/\+/g, " ")),
    email: headers["x-user-email"],
    role: headers["x-user-role"],
  };
}

app.get("/health", (req, res) => res.json({ status: "ok" }));
app.all(
  "/graphql",
  createHandler({
    schema,
    rootValue: resolvers,
    context: (req) => ({ user: userFromHeaders(req.raw.headers) }),
  })
);

db.connect()
  .then(() => {
    publisher.connect();
    app.listen(PORT, () => console.log(`Orders service listening on port ${PORT}`));
  })
  .catch((error) => {
    console.error("Could not connect to MongoDB", error);
    process.exit(1);
  });
