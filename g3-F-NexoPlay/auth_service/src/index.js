import cookieParser from "cookie-parser";
import crypto from "node:crypto";
import express from "express";
import bcrypt from "bcrypt";
import jwt from "jsonwebtoken";
import pg from "pg";

const app = express();
app.use(express.json());
app.use(cookieParser());

const {
  GOOGLE_CLIENT_ID,
  GOOGLE_CLIENT_SECRET,
  GOOGLE_REDIRECT_URI,
  FRONTEND_URL,
} = process.env;
const pool = new pg.Pool({ connectionString: process.env.DATABASE_URL });

const signToken = (user) =>
  jwt.sign({ sub: user.id, role: user.role }, process.env.JWT_SECRET, {
    expiresIn: "1h",
  });

// Steps 1-2: send the user to Google
app.get("/auth/google", (req, res) => {
  const state = crypto.randomBytes(16).toString("hex");
  res.cookie("oauth_state", state, {
    httpOnly: true,
    sameSite: "lax",
    maxAge: 10 * 60 * 1000,
  });
  const params = new URLSearchParams({
    client_id: GOOGLE_CLIENT_ID,
    redirect_uri: GOOGLE_REDIRECT_URI,
    response_type: "code",
    scope: "openid email profile",
    state,
  });
  res.redirect(`https://accounts.google.com/o/oauth2/v2/auth?${params}`);
});

// Steps 3-6: Google sends the user back with a "code"
app.get("/auth/google/callback", async (req, res) => {
  const fail = () => res.redirect(`${FRONTEND_URL}/login?error=google`);
  const { code, state, error } = req.query;

  if (error || !code || !state || state !== req.cookies.oauth_state)
    return fail();
  res.clearCookie("oauth_state");

  try {
    // Exchange the code for an access token
    const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        code,
        client_id: GOOGLE_CLIENT_ID,
        client_secret: GOOGLE_CLIENT_SECRET,
        redirect_uri: GOOGLE_REDIRECT_URI,
        grant_type: "authorization_code",
      }),
    });
    if (!tokenRes.ok) return fail();
    const { access_token } = await tokenRes.json();

    // Fetch the user's profile
    const infoRes = await fetch(
      "https://openidconnect.googleapis.com/v1/userinfo",
      {
        headers: { Authorization: `Bearer ${access_token}` },
      },
    );
    if (!infoRes.ok) return fail();
    const profile = await infoRes.json();
    if (!profile.email_verified) return fail();

    // Find or create the user
    let { rows } = await pool.query(
      "SELECT * FROM users WHERE google_sub = $1 OR email = $2",
      [profile.sub, profile.email],
    );
    let user = rows[0];
    if (!user) {
      ({ rows } = await pool.query(
        "INSERT INTO users (email, name, provider, google_sub) VALUES ($1, $2, 'google', $3) RETURNING *",
        [profile.email, profile.name, profile.sub],
      ));
      user = rows[0];
    } else if (!user.google_sub) {
      await pool.query("UPDATE users SET google_sub = $1 WHERE id = $2", [
        profile.sub,
        user.id,
      ]);
    }

    // Hand our JWT to the frontend
    res.redirect(`${FRONTEND_URL}/oauth/callback#token=${signToken(user)}`);
  } catch (e) {
    console.error(e);
    fail();
  }
});

app.post("/auth/register", async (req, res) => {
  const { email, name, password } = req.body;
  if (!email || !name || !password)
    return res.status(400).json({ error: "Missing fields" });
  const hash = await bcrypt.hash(password, 10);
  try {
    const { rows } = await pool.query(
      "INSERT INTO users (email, name, password_hash) VALUES ($1,$2,$3) RETURNING id, email, name, role",
      [email, name, hash],
    );
    res.status(201).json({ user: rows[0], token: signToken(rows[0]) });
  } catch (e) {
    if (e.code === "23505")
      return res.status(409).json({ error: "Email already registered" });
    res.status(500).json({ error: "Internal error" });
  }
});

app.post("/auth/login", async (req, res) => {
  const { email, password } = req.body;
  const { rows } = await pool.query("SELECT * FROM users WHERE email=$1", [
    email,
  ]);
  const user = rows[0];
  if (
    !user ||
    !user.password_hash ||
    !(await bcrypt.compare(password, user.password_hash))
  )
    return res.status(401).json({ error: "Invalid credentials" });
  res.json({ token: signToken(user) });
});

app.listen(8081, () => console.log("auth-service :8081"));
