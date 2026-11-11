// Estas funciones se ejecutan en el servidor de Next.js (SSR), no en el navegador.
// El servidor le pide los datos al API Gateway y la página llega al navegador ya armada.

const GATEWAY_URL = process.env.API_GATEWAY_URL || "http://localhost:8000";

async function get(path) {
  const response = await fetch(`${GATEWAY_URL}${path}`, { cache: "no-store" });
  if (!response.ok) throw new Error(`El gateway respondió ${response.status} en ${path}`);
  return response.json();
}

// La cookie de sesión tiene la forma "student:20231001" o "professor:2"
export async function loadInitialData(sessionCookie) {
  const data = {
    initialSession: null,
    initialUsers: null,
    initialCatalog: null,
    initialEnrollments: null,
    initialGroups: null,
  };

  try {
    const [role, id] = (sessionCookie || "").split(":");

    if (role === "student" && id) {
      const [user, catalog, enrollments] = await Promise.all([
        get(`/api/students/${id}`),
        get("/api/courses"),
        get(`/api/students/${id}/enrollments`),
      ]);
      data.initialSession = { role, user };
      data.initialCatalog = catalog;
      data.initialEnrollments = enrollments.enrollments;
    }

    if (role === "professor" && id) {
      const professors = await get("/api/professors");
      const user = professors.find((p) => String(p.id) === id);
      if (user) {
        data.initialSession = { role, user };
        data.initialGroups = await get(`/api/professors/${id}/groups`);
      }
    }

    if (!data.initialSession) {
      const [students, professors] = await Promise.all([get("/api/students"), get("/api/professors")]);
      data.initialUsers = { student: students, professor: professors };
    }
  } catch (error) {
    // si el gateway no responde, la página se arma en el navegador como respaldo
    console.error("No se pudieron cargar los datos en el servidor:", error.message);
  }

  return data;
}
