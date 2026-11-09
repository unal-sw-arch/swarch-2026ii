import { cookies } from "next/headers";
import App from "../src/App.jsx";
import { loadInitialData } from "../src/server-api.js";

// La página se genera en cada petición (SSR), no una sola vez al compilar
export const dynamic = "force-dynamic";

export default async function Page() {
  const cookieStore = await cookies();
  const initialData = await loadInitialData(cookieStore.get("pilosia-session")?.value);
  return <App {...initialData} />;
}
