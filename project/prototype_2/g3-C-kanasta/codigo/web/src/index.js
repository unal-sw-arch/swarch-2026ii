// Kanasta Web: customer store. Every page is rendered on the server (SSR) with data
// asked to the API Gateway, and sent to the browser as finished HTML.
const crypto = require("crypto");
const path = require("path");
const express = require("express");
const cookieParser = require("cookie-parser");
const gateway = require("./gateway");
const format = require("./format");

const app = express();
const PORT = process.env.PORT || 8080;

app.set("view engine", "ejs");
app.set("views", path.join(__dirname, "..", "views"));
app.use(express.static(path.join(__dirname, "..", "public")));
app.use(express.urlencoded({ extended: false }));
app.use(cookieParser());

// ---------- helpers ----------

const COOKIE = { httpOnly: true, sameSite: "lax", maxAge: 30 * 24 * 3600 * 1000 };

function flash(res, text, type = "ok") {
  res.cookie("flash", JSON.stringify({ text, type }), { httpOnly: true, sameSite: "lax" });
}

function back(req, fallback = "/") {
  const referer = req.get("Referer") || "";
  return referer.startsWith(`${req.protocol}://${req.get("host")}`) ? referer : fallback;
}

// Wraps a route so that any error becomes a message for the customer
function page(handler) {
  return async (req, res) => {
    try {
      await handler(req, res);
    } catch (error) {
      if (req.method === "POST") {
        flash(res, error.message, "error");
        return res.redirect(back(req));
      }
      res.status(error.status || 500).render("error", { title: "Algo salió mal", message: error.message });
    }
  };
}

function requireLogin(req, res, next) {
  if (res.locals.user) return next();
  flash(res, "Inicia sesión para continuar", "error");
  res.redirect(`/ingresar?volver=${encodeURIComponent(req.originalUrl)}`);
}

// The store data changes very little, so it is kept for 30 seconds
let storeCache = { data: null, time: 0 };
async function getStore() {
  if (!storeCache.data || Date.now() - storeCache.time > 30000) {
    storeCache = { data: await gateway.getStore(), time: Date.now() };
  }
  return storeCache.data;
}

// ---------- data used by every page (store, user, cart badge, message) ----------

app.use(async (req, res, next) => {
  Object.assign(res.locals, format, { user: null, cartCount: 0, flash: null, title: "" });

  if (req.cookies.flash) {
    try {
      res.locals.flash = JSON.parse(req.cookies.flash);
    } catch {}
    res.clearCookie("flash");
  }

  req.token = req.cookies.kanasta_token;
  req.cartId = req.cookies.kanasta_cart;
  if (!req.cartId) {
    req.cartId = "cart-" + crypto.randomUUID();
    res.cookie("kanasta_cart", req.cartId, COOKIE);
  }

  try {
    res.locals.store = await getStore();
  } catch {
    return res.status(503).send("<h1>La tienda no está disponible en este momento. Intenta de nuevo en unos minutos.</h1>");
  }

  if (req.token) {
    try {
      res.locals.user = await gateway.getMe(req.token);
    } catch {
      res.clearCookie("kanasta_token");
      req.token = null;
    }
  }

  try {
    const cart = await gateway.getCart(req.cartId);
    res.locals.cartCount = cart.items.length;
  } catch {}
  next();
});

// ---------- images (passed through from the Image Provider) ----------

app.get("/images/:name", async (req, res) => {
  try {
    const image = await gateway.getImage(req.params.name);
    res.status(image.status);
    res.set("Content-Type", image.headers.get("content-type") || "application/octet-stream");
    res.set("Cache-Control", image.headers.get("cache-control") || "no-cache");
    res.send(Buffer.from(await image.arrayBuffer()));
  } catch {
    res.status(404).end();
  }
});

// ---------- catalog ----------

app.get("/", page(async (req, res) => {
  const search = (req.query.buscar || "").trim();
  const category = req.query.categoria || "";
  const [categories, products] = await Promise.all([gateway.getCategories(), gateway.getProducts(search, category)]);
  res.render("catalogo", { title: "Catálogo", categories, products, search, category });
}));

app.get("/producto/:id", page(async (req, res) => {
  const [product, reviews] = await Promise.all([gateway.getProduct(req.params.id), gateway.getReviews(req.params.id)]);
  const myReview = res.locals.user && reviews.find((r) => r.user_id === res.locals.user.id);
  res.render("producto", { title: product.name, product, reviews, myReview });
}));

app.post("/producto/:id/opinion", requireLogin, page(async (req, res) => {
  await gateway.saveReview(req.params.id, { rating: Number(req.body.rating), comment: req.body.comment || "" }, req.token);
  flash(res, "¡Gracias por tu opinión!");
  res.redirect(`/producto/${req.params.id}#opiniones`);
}));

// ---------- cart ----------

app.get("/carrito", page(async (req, res) => {
  const cart = await gateway.getCart(req.cartId);
  res.render("carrito", { title: "Carrito", cart });
}));

app.post("/carrito/agregar", page(async (req, res) => {
  await gateway.addToCart(req.cartId, Number(req.body.productId), parseFloat(req.body.quantity));
  flash(res, "Producto agregado al carrito");
  res.redirect(back(req));
}));

app.post("/carrito/actualizar", page(async (req, res) => {
  await gateway.updateCartItem(req.cartId, Number(req.body.productId), parseFloat(req.body.quantity));
  res.redirect("/carrito");
}));

app.post("/carrito/quitar", page(async (req, res) => {
  await gateway.removeFromCart(req.cartId, Number(req.body.productId));
  flash(res, "Producto quitado del carrito");
  res.redirect("/carrito");
}));

// ---------- checkout and orders ----------

app.get("/comprar", requireLogin, page(async (req, res) => {
  const cart = await gateway.getCart(req.cartId);
  if (cart.items.length === 0) return res.redirect("/carrito");
  res.render("comprar", { title: "Confirmar pedido", cart });
}));

app.post("/comprar", requireLogin, page(async (req, res) => {
  const { phone = "", address = "", notes = "" } = req.body;
  const order = await gateway.placeOrder(req.cartId, phone, address, notes, req.token);
  res.redirect(`/pedidos/${order.id}?nuevo=1`);
}));

app.get("/pedidos", requireLogin, page(async (req, res) => {
  const orders = await gateway.myOrders(req.token);
  res.render("pedidos", { title: "Mis pedidos", orders });
}));

app.get("/pedidos/:id", requireLogin, page(async (req, res) => {
  const order = await gateway.getOrder(req.params.id, req.token);
  if (!order) throw Object.assign(new Error("No encontramos este pedido"), { status: 404 });
  res.render("pedido", { title: "Pedido", order, isNew: req.query.nuevo === "1" });
}));

// ---------- accounts ----------

app.get("/registro", (req, res) => res.render("registro", { title: "Crear cuenta", form: {} }));

app.post("/registro", async (req, res) => {
  const form = req.body;
  try {
    if (form.password !== form.password2) throw new Error("Las contraseñas no coinciden");
    const session = await gateway.register(form);
    res.cookie("kanasta_token", session.token, COOKIE);
    flash(res, `¡Bienvenido, ${session.user.name}! Te enviamos un correo de bienvenida.`);
    res.redirect("/");
  } catch (error) {
    res.locals.flash = { text: error.message, type: "error" };
    res.render("registro", { title: "Crear cuenta", form });
  }
});

app.get("/ingresar", (req, res) => res.render("ingresar", { title: "Ingresar", email: "", volver: req.query.volver || "/" }));

app.post("/ingresar", async (req, res) => {
  const { email = "", password = "" } = req.body;
  const volver = (req.body.volver || "/").startsWith("/") ? req.body.volver : "/";
  try {
    const session = await gateway.login(email, password);
    if (session.user.role === "ADMIN") {
      await gateway.logout(session.token);
      throw new Error("Las cuentas de administrador se usan en la aplicación de escritorio Kanasta Admin");
    }
    res.cookie("kanasta_token", session.token, COOKIE);
    flash(res, `Hola de nuevo, ${session.user.name}`);
    res.redirect(volver);
  } catch (error) {
    res.locals.flash = { text: error.message, type: "error" };
    res.render("ingresar", { title: "Ingresar", email, volver });
  }
});

app.post("/salir", async (req, res) => {
  if (req.token) await gateway.logout(req.token).catch(() => {});
  res.clearCookie("kanasta_token");
  flash(res, "Cerraste tu sesión");
  res.redirect("/");
});

app.get("/cuenta", requireLogin, (req, res) => res.render("cuenta", { title: "Mi cuenta" }));

app.post("/cuenta", requireLogin, page(async (req, res) => {
  const { name = "", phone = "", address = "" } = req.body;
  await gateway.updateMe({ name, phone, address }, req.token);
  flash(res, "Tus datos quedaron guardados");
  res.redirect("/cuenta");
}));

app.post("/cuenta/clave", requireLogin, page(async (req, res) => {
  const { currentPassword, newPassword, newPassword2 } = req.body;
  if (newPassword !== newPassword2) throw new Error("Las contraseñas nuevas no coinciden");
  await gateway.changePassword({ currentPassword, newPassword }, req.token);
  flash(res, "Cambiaste tu contraseña. Te enviamos un correo de aviso.");
  res.redirect("/cuenta");
}));

app.use((req, res) => res.status(404).render("error", { title: "No encontrado", message: "Esta página no existe." }));

app.listen(PORT, () => console.log(`Kanasta Web listening on port ${PORT}`));
