import { APP_NAME } from "../config";

export default function Footer() {
  return (
    <footer className="mt-6 border-t border-zinc-900 py-8 text-center text-xs text-zinc-500">
      © {new Date().getFullYear()} {APP_NAME} · Distributed video streaming
      platform
    </footer>
  );
}
