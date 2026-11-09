import { useEffect, useState } from "react";
import { getGroupGrades, getGroupStudents, registerGrade } from "../api.js";

// Vista del docente: estudiantes de un grupo y registro/modificación de la nota final
export default function GroupGrades({ groupId, onBack }) {
  const [group, setGroup] = useState(null);
  const [grades, setGrades] = useState({}); // nota guardada por código de estudiante
  const [inputs, setInputs] = useState({}); // lo que el docente está escribiendo
  const [message, setMessage] = useState(null);

  async function load() {
    try {
      // REST (courses-service) para el grupo y GraphQL (grades-service) para las notas
      const [groupData, gradeList] = await Promise.all([
        getGroupStudents(groupId),
        getGroupGrades(groupId),
      ]);
      const saved = {};
      gradeList.forEach((g) => (saved[g.studentCode] = g.grade));
      setGroup(groupData);
      setGrades(saved);
      setInputs(Object.fromEntries(Object.entries(saved).map(([code, g]) => [code, g.toFixed(1)])));
    } catch (error) {
      setMessage({ type: "error", text: error.message });
    }
  }

  useEffect(() => {
    load();
  }, [groupId]);

  async function handleSave(student) {
    setMessage(null);
    const value = Number((inputs[student.code] || "").replace(",", "."));
    if (inputs[student.code] === undefined || inputs[student.code] === "" || Number.isNaN(value)) {
      setMessage({ type: "error", text: "Escribe una nota válida (por ejemplo 4.2)" });
      return;
    }
    try {
      const result = await registerGrade(groupId, student.code, value);
      setMessage({ type: "success", text: `Nota guardada: ${student.name} quedó con ${result.grade.toFixed(1)}` });
      load();
    } catch (error) {
      setMessage({ type: "error", text: error.message });
    }
  }

  if (!group) {
    return message ? <div className="alert alert-error">{message.text}</div> : <p className="empty">Cargando grupo...</p>;
  }

  return (
    <section>
      <button className="link-back" onClick={onBack}>
        Volver a mis grupos
      </button>
      <div className="page-header">
        <h1>
          {group.course.name}, grupo {group.number}
        </h1>
        <span className="period">Periodo {group.period}</span>
      </div>
      <p className="intro">Escribe la nota final de cada estudiante, de 0.0 a 5.0, y guárdala. La asignatura se aprueba con 3.0.</p>

      {message && <div className={`alert alert-${message.type}`}>{message.text}</div>}

      {group.students.length === 0 ? (
        <p className="empty">Este grupo no tiene estudiantes inscritos.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Código</th>
                <th>Estudiante</th>
                <th>Programa</th>
                <th>Nota registrada</th>
                <th>Nueva nota</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {group.students.map((s) => {
                const saved = grades[s.code];
                return (
                  <tr key={s.code}>
                    <td className="num">{s.code}</td>
                    <td>{s.name}</td>
                    <td>{s.program}</td>
                    <td>
                      {saved === undefined ? (
                        <span className="muted">Sin nota</span>
                      ) : (
                        <span className={saved >= 3 ? "passed" : "failed"}>{saved.toFixed(1)}</span>
                      )}
                    </td>
                    <td>
                      <input
                        className="grade-input"
                        type="number"
                        min="0"
                        max="5"
                        step="0.1"
                        aria-label={`Nota de ${s.name}`}
                        value={inputs[s.code] ?? ""}
                        onChange={(e) => setInputs({ ...inputs, [s.code]: e.target.value })}
                      />
                    </td>
                    <td className="right">
                      <button className="btn btn-primary btn-small" onClick={() => handleSave(s)}>
                        {saved === undefined ? "Guardar nota" : "Modificar nota"}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
