import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { Logo } from "../components/Navbar";
import { Spinner } from "../components/Feedback";
import { EyeIcon, EyeOffIcon, GoogleIcon } from "../components/Icons";

const input =
  "w-full rounded-md bg-zinc-900 border border-zinc-700 px-4 py-2.5 text-sm placeholder-zinc-500 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500";
const label = "block text-sm font-medium text-zinc-300 mb-1.5";

export default function AuthPage({ mode }) {
  const isRegister = mode === "register";
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [params] = useSearchParams();
  const [error, setError] = useState(
    params.get("error") ? "Could not sign in with Google" : "",
  );

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      if (isRegister) await register(name, email, password);
      else await login(email, password);
      navigate("/catalog");
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <aside className="relative hidden overflow-hidden bg-gradient-to-br from-brand-700 via-red-950 to-zinc-950 lg:flex lg:flex-col lg:justify-between lg:p-12">
        <div className="absolute -right-32 -top-32 size-96 rounded-full bg-brand-500/20 blur-3xl" />
        <div className="absolute -bottom-40 -left-20 size-96 rounded-full bg-indigo-600/20 blur-3xl" />
        <div className="relative">
          <Logo />
        </div>
        <div className="relative">
          <h2 className="text-4xl font-bold leading-tight tracking-tight">
            Watch anything.
            <br />
            Share everything.
          </h2>
          <p className="mt-4 max-w-md text-zinc-300">
            Browse the catalog for free. Create an account to upload and manage
            your own videos.
          </p>
        </div>
      </aside>

      <div className="flex items-center justify-center px-4 py-12">
        <div className="w-full max-w-sm">
          <div className="mb-8 lg:hidden">
            <Logo />
          </div>
          <h1 className="text-2xl font-semibold">
            {isRegister ? "Create account" : "Welcome back"}
          </h1>
          <p className="mt-1 mb-8 text-sm text-zinc-400">
            {isRegister ? "Sign up to get started" : "Log in to continue"}
          </p>

          <form onSubmit={handleSubmit} className="space-y-4">
            {isRegister && (
              <div>
                <label htmlFor="name" className={label}>
                  Name
                </label>
                <input
                  id="name"
                  className={input}
                  autoComplete="name"
                  placeholder="Your name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </div>
            )}
            <div>
              <label htmlFor="email" className={label}>
                Email
              </label>
              <input
                id="email"
                className={input}
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div>
              <label htmlFor="password" className={label}>
                Password
              </label>
              <div className="relative">
                <input
                  id="password"
                  className={`${input} pr-11`}
                  type={showPassword ? "text" : "password"}
                  autoComplete={
                    isRegister ? "new-password" : "current-password"
                  }
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((s) => !s)}
                  className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-zinc-500 hover:text-zinc-300"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? (
                    <EyeOffIcon className="size-4" />
                  ) : (
                    <EyeIcon className="size-4" />
                  )}
                </button>
              </div>
            </div>

            {error && (
              <p
                role="alert"
                className="rounded-md border border-red-900 bg-red-950/50 px-3 py-2 text-sm text-red-400"
              >
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={submitting}
              className="flex w-full items-center justify-center gap-2 rounded-md bg-brand-600 py-2.5 text-sm font-semibold hover:bg-brand-500 disabled:cursor-not-allowed disabled:opacity-60 transition"
            >
              {submitting && <Spinner className="size-4" />}
              {isRegister ? "Sign up" : "Log in"}
            </button>
          </form>

          <div className="my-6 flex items-center gap-3 text-xs text-zinc-500">
            <div className="h-px flex-1 bg-zinc-800" /> or{" "}
            <div className="h-px flex-1 bg-zinc-800" />
          </div>
          <a
            href={`${import.meta.env.VITE_API_URL}/auth/google`}
            className="flex w-full items-center justify-center gap-3 rounded-md border border-zinc-700 py-2.5 text-sm font-medium hover:bg-zinc-900 transition"
          >
            <GoogleIcon className="size-5" />
            Continue with Google
          </a>

          <p className="mt-8 text-center text-sm text-zinc-400">
            {isRegister ? (
              <>
                Already have an account?{" "}
                <Link
                  to="/login"
                  className="font-medium text-brand-400 hover:underline"
                >
                  Log in
                </Link>
              </>
            ) : (
              <>
                Don't have an account?{" "}
                <Link
                  to="/register"
                  className="font-medium text-brand-400 hover:underline"
                >
                  Sign up
                </Link>
              </>
            )}
          </p>
          <p className="mt-3 text-center text-sm">
            <Link to="/catalog" className="text-zinc-500 hover:text-white">
              Browse without an account →
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
