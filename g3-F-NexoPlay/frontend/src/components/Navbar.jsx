import { useEffect, useState } from "react";
import { Link, NavLink, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { APP_NAME } from "../config";
import { CloseIcon, MenuIcon, PlayIcon } from "./Icons";

const linkClass = ({ isActive }) =>
  `text-sm transition ${isActive ? "text-white font-medium" : "text-zinc-400 hover:text-zinc-200"}`;

export function Logo() {
  return (
    <Link
      to="/catalog"
      className="flex items-center gap-2 font-bold tracking-tight"
    >
      <span className="flex size-8 items-center justify-center rounded-md bg-brand-600">
        <PlayIcon className="ml-0.5 size-4 text-white" />
      </span>
      <span className="text-lg">{APP_NAME}</span>
    </Link>
  );
}

export default function Navbar() {
  const { token, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [scrolled, setScrolled] = useState(false);
  // Menu is tied to the path it was opened on, so navigating closes it
  const [openOn, setOpenOn] = useState(null);
  const open = openOn === pathname;

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 16);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const handleLogout = () => {
    logout();
    setOpenOn(null);
    navigate("/catalog");
  };

  const authButton = token ? (
    <button
      onClick={handleLogout}
      className="rounded-md border border-zinc-700 px-4 py-1.5 text-sm font-medium hover:bg-zinc-800 transition"
    >
      Log out
    </button>
  ) : (
    <Link
      to="/login"
      className="rounded-md bg-brand-600 px-4 py-1.5 text-sm font-medium hover:bg-brand-500 transition"
    >
      Log in
    </Link>
  );

  const links = (
    <>
      <NavLink to="/catalog" className={linkClass}>
        Catalog
      </NavLink>
      {token && (
        <NavLink to="/upload" className={linkClass}>
          Upload
        </NavLink>
      )}
    </>
  );

  return (
    <header
      className={`fixed inset-x-0 top-0 z-30 transition-colors duration-300 ${
        scrolled || open
          ? "bg-zinc-950/90 backdrop-blur border-b border-zinc-800/80"
          : "bg-gradient-to-b from-black/80 to-transparent border-b border-transparent"
      }`}
    >
      <nav className="mx-auto flex h-16 max-w-7xl items-center gap-8 px-4 sm:px-6">
        <Logo />
        <div className="hidden items-center gap-6 md:flex">{links}</div>
        <div className="ml-auto hidden md:block">{authButton}</div>
        <button
          onClick={() => setOpenOn(open ? null : pathname)}
          className="ml-auto rounded-md p-2 text-zinc-300 hover:bg-zinc-800 md:hidden"
          aria-label={open ? "Close menu" : "Open menu"}
          aria-expanded={open}
        >
          {open ? (
            <CloseIcon className="size-5" />
          ) : (
            <MenuIcon className="size-5" />
          )}
        </button>
      </nav>
      {open && (
        <div className="flex flex-col gap-4 border-t border-zinc-800 px-4 py-4 md:hidden">
          {links}
          <div>{authButton}</div>
        </div>
      )}
    </header>
  );
}
