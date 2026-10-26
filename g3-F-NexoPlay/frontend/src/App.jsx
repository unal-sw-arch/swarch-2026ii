import { lazy, Suspense } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import ProtectedRoute from "./auth/ProtectedRoute";
import Navbar from "./components/Navbar";
import Footer from "./components/Footer";
import AuthPage from "./pages/AuthPage";
import Catalog from "./pages/Catalog";
import Upload from "./pages/Upload";
import OAuthCallback from "./pages/OAuthCallback";

// Lazy so hls.js only downloads when someone opens a video
const Watch = lazy(() => import("./pages/Watch"));

export default function App() {
  const onAuthPage = ["/login", "/register", "/oauth/callback"].includes(
    useLocation().pathname,
  );

  return (
    <div className="flex min-h-screen flex-col">
      {!onAuthPage && <Navbar />}
      {/* Pages fill the space between navbar and footer; *:w-full keeps
          their mx-auto containers full width inside the flex column */}
      <div className="flex flex-1 flex-col *:w-full">
        <Suspense>
          <Routes>
            {/* Public: anyone can browse and watch */}
            <Route path="/catalog" element={<Catalog />} />
            <Route path="/watch/:id" element={<Watch />} />
            <Route path="/login" element={<AuthPage mode="login" />} />
            <Route path="/register" element={<AuthPage mode="register" />} />
            <Route path="/oauth/callback" element={<OAuthCallback />} />
            {/* Protected: requires login */}
            <Route
              path="/upload"
              element={
                <ProtectedRoute>
                  <Upload />
                </ProtectedRoute>
              }
            />
            <Route path="*" element={<Navigate to="/catalog" replace />} />
          </Routes>
        </Suspense>
      </div>
      {!onAuthPage && <Footer />}
    </div>
  );
}
