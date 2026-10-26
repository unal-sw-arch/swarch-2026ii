import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { Spinner } from "../components/Feedback";

export default function OAuthCallback() {
  const { setSession } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    const token = new URLSearchParams(window.location.hash.slice(1)).get(
      "token",
    );
    if (token) {
      setSession(token);
      navigate("/catalog", { replace: true });
    } else {
      navigate("/login?error=google", { replace: true });
    }
  }, []);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 text-zinc-400">
      <Spinner className="size-8 text-brand-500" />
      <p>Signing in…</p>
    </div>
  );
}
