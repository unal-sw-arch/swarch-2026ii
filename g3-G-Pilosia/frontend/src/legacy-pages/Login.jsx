import { useEffect, useState } from "react";
import { getProfessors, getStudents } from "../api.js";

// Pantalla de ingreso: se escoge el rol y un usuario de prueba (no hay contraseñas en el prototipo)
export default function Login({ onLogin }) {
  const [role, setRole] = useState("student");
  const [users, setUsers] = useState([]);
  const [selected, setSelected] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    setUsers([]);
    setSelected("");
    setError("");
    const request = role === "student" ? getStudents() : getProfessors();
    request.then(setUsers).catch(() => setError("No se pudo cargar la lista de usuarios"));
  }, [role]);

  function getKey(user) {
    return role === "student" ? user.code : String(user.id);
  }

  function handleSubmit(event) {
    event.preventDefault();
    const user = users.find((u) => getKey(u) === selected);
    if (user) onLogin({ role, user });
  }

  return (
    <div className="login-page">
      <aside className="login-side">
        <img src="/simbolo.svg" alt="" />
        <h1>Pilosia</h1>
        <p>Inscripción de asignaturas, horario y notas en un solo lugar.</p>
      </aside>

      <main className="login-main">
        <form className="login-form" onSubmit={handleSubmit}>
          <h2>Ingresar</h2>
          <p>Escoge cómo quieres entrar y el usuario de prueba.</p>

          <div className="role-switch">
            <button
              type="button"
              className={role === "student" ? "active" : ""}
              onClick={() => setRole("student")}
            >
              Estudiante
            </button>
            <button
              type="button"
              className={role === "professor" ? "active" : ""}
              onClick={() => setRole("professor")}
            >
              Docente
            </button>
          </div>

          <label htmlFor="user">Usuario</label>
          <select id="user" value={selected} onChange={(e) => setSelected(e.target.value)}>
            <option value="">Selecciona un usuario</option>
            {users.map((u) => (
              <option key={getKey(u)} value={getKey(u)}>
                {role === "student" ? `${u.code} - ${u.name}` : u.name}
              </option>
            ))}
          </select>

          {error && <div className="alert alert-error">{error}</div>}

          <button type="submit" className="btn btn-primary" disabled={!selected}>
            Ingresar
          </button>
          <p className="hint">Es un prototipo: los usuarios son datos de prueba y no se pide contraseña.</p>
        </form>
      </main>
    </div>
  );
}
