"use client";

import { useEffect, useState } from "react";
import { getNotifications } from "./api.js";
import Login from "./views/Login.jsx";
import Catalog from "./views/Catalog.jsx";
import MyEnrollments from "./views/MyEnrollments.jsx";
import MySchedule from "./views/MySchedule.jsx";
import AcademicHistory from "./views/AcademicHistory.jsx";
import MyGroups from "./views/MyGroups.jsx";
import GroupGrades from "./views/GroupGrades.jsx";

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

// La sesión se guarda en una cookie para que el servidor (SSR) sepa qué página armar.
// No hay autenticación real: es solo para el prototipo.
function saveSession(session) {
  if (session) {
    const id = session.role === "student" ? session.user.code : session.user.id;
    document.cookie = `pilosia-session=${session.role}:${id}; path=/; max-age=86400; SameSite=Lax`;
  } else {
    document.cookie = "pilosia-session=; path=/; max-age=0";
  }
}

export default function App({ initialSession, initialUsers, initialCatalog, initialEnrollments, initialGroups }) {
  const [session, setSession] = useState(initialSession);
  const [page, setPage] = useState(initialSession ? MENUS[initialSession.role][0].id : null);
  const [selectedGroup, setSelectedGroup] = useState(null);
  // Datos que ya trajo el servidor. Solo sirven para la primera vista; después cada vista consulta sola.
  const [ssrData, setSsrData] = useState({
    catalog: initialCatalog,
    enrollments: initialEnrollments,
    groups: initialGroups,
  });
  const [notifications, setNotifications] = useState([]);

  useEffect(() => {
    if (!session || session.role !== "professor") {
      setNotifications([]);
      return undefined;
    }

    let active = true;
    const loadNotifications = () => {
      getNotifications(session.user.id)
        .then((items) => {
          if (active) setNotifications(items);
        })
        .catch(() => {
          if (active) setNotifications([]);
        });
    };
    loadNotifications();
    const timer = window.setInterval(loadNotifications, 3000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [session]);

  function goTo(newPage) {
    setSsrData({});
    setPage(newPage);
  }

  function handleLogin(newSession) {
    saveSession(newSession);
    setSsrData({});
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
    return <Login onLogin={handleLogin} initialUsers={initialUsers} />;
  }

  const { role, user } = session;

  function renderPage() {
    switch (page) {
      case "catalog":
        return <Catalog student={user} initialCatalog={ssrData.catalog} initialEnrollments={ssrData.enrollments} />;
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
            initialData={ssrData.groups}
            onSelectGroup={(groupId) => {
              setSelectedGroup(groupId);
              goTo("grades");
            }}
          />
        );
      case "grades":
        return <GroupGrades groupId={selectedGroup} onBack={() => goTo("groups")} />;
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
                onClick={() => goTo(item.id)}
              >
                {item.label}
              </button>
            ))}
          </nav>
        </div>
      </header>

      {role === "professor" && notifications.length > 0 && (
        <aside className="notification-banner" role="status">
          <strong>Nueva actividad</strong>
          <span>
            {notifications[notifications.length - 1].studentName} se inscribió en{" "}
            {notifications[notifications.length - 1].courseName}.
          </span>
        </aside>
      )}
      <main className="content">{renderPage()}</main>
    </>
  );
}
