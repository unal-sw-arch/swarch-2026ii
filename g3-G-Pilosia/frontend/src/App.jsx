import { useState } from "react";
import Login from "./pages/Login.jsx";
import Catalog from "./pages/Catalog.jsx";
import MyEnrollments from "./pages/MyEnrollments.jsx";
import MySchedule from "./pages/MySchedule.jsx";
import AcademicHistory from "./pages/AcademicHistory.jsx";
import MyGroups from "./pages/MyGroups.jsx";
import GroupGrades from "./pages/GroupGrades.jsx";

// Opciones del menú según el rol
const MENUS = {
  student: [
    { id: "catalog", label: "Catálogo de asignaturas" },
    { id: "enrollments", label: "Mis inscripciones" },
    { id: "schedule", label: "Mi horario" },
    { id: "history", label: "Historia académica" },
  ],
  professor: [{ id: "groups", label: "Mis grupos" }],
};

// La sesión se guarda en el navegador para no perderla al recargar.
// No hay autenticación real: es solo para el prototipo.
function loadSession() {
  try {
    return JSON.parse(localStorage.getItem("pilosia-session"));
  } catch {
    return null;
  }
}

function saveSession(session) {
  try {
    if (session) localStorage.setItem("pilosia-session", JSON.stringify(session));
    else localStorage.removeItem("pilosia-session");
  } catch {
    // si el navegador no deja guardar, la sesión solo dura mientras la página esté abierta
  }
}

export default function App() {
  const [session, setSession] = useState(loadSession);
  const [page, setPage] = useState(session ? MENUS[session.role][0].id : null);
  const [selectedGroup, setSelectedGroup] = useState(null);

  function handleLogin(newSession) {
    saveSession(newSession);
    setSession(newSession);
    setPage(MENUS[newSession.role][0].id);
  }

  function handleLogout() {
    saveSession(null);
    setSession(null);
    setPage(null);
    setSelectedGroup(null);
  }

  if (!session) {
    return <Login onLogin={handleLogin} />;
  }

  const { role, user } = session;

  function renderPage() {
    switch (page) {
      case "catalog":
        return <Catalog student={user} />;
      case "enrollments":
        return <MyEnrollments student={user} />;
      case "schedule":
        return <MySchedule student={user} />;
      case "history":
        return <AcademicHistory student={user} />;
      case "groups":
        return (
          <MyGroups
            professor={user}
            onSelectGroup={(groupId) => {
              setSelectedGroup(groupId);
              setPage("grades");
            }}
          />
        );
      case "grades":
        return <GroupGrades groupId={selectedGroup} onBack={() => setPage("groups")} />;
      default:
        return null;
    }
  }

  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <div className="topbar-row">
            <div className="brand">
              <img src="/logo.svg" alt="Pilosia, sistema de información académica" />
            </div>
            <div className="user-box">
              <div>
                <strong>{user.name}</strong>
                <span>{role === "student" ? `Estudiante, código ${user.code}` : `Docente, ${user.department}`}</span>
              </div>
              <button className="btn btn-outline btn-small" onClick={handleLogout}>
                Salir
              </button>
            </div>
          </div>
          <nav className="tabs">
            {MENUS[role].map((item) => (
              <button
                key={item.id}
                className={page === item.id || (page === "grades" && item.id === "groups") ? "active" : ""}
                onClick={() => setPage(item.id)}
              >
                {item.label}
              </button>
            ))}
          </nav>
        </div>
      </header>

      <main className="content">{renderPage()}</main>
    </>
  );
}
