import { useEffect, useState } from "react";
import { getProfessorGroups } from "../api.js";
import { Schedule } from "../Schedule.jsx";

export default function MyGroups({ professor, onSelectGroup }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    getProfessorGroups(professor.id)
      .then(setData)
      .catch((e) => setError(e.message));
  }, [professor.id]);

  if (error) return <div className="alert alert-error">{error}</div>;
  if (!data) return <p className="empty">Cargando grupos...</p>;

  return (
    <section>
      <div className="page-header">
        <h1>Mis grupos</h1>
        <span className="period">Periodo {data.period}</span>
      </div>
      <p className="intro">Grupos que dictas este periodo. Entra a uno para registrar las notas finales.</p>

      {data.groups.length === 0 ? (
        <p className="empty">No tienes grupos asignados en este periodo.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Asignatura</th>
                <th>Grupo</th>
                <th>Horario</th>
                <th>Estudiantes</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {data.groups.map((g) => (
                <tr key={g.id}>
                  <td>
                    <strong>{g.course_name}</strong>
                    <div className="muted">
                      {g.course_code}, {g.credits} créditos
                    </div>
                  </td>
                  <td className="num">{g.number}</td>
                  <td>
                    <Schedule schedule={g.schedule} />
                  </td>
                  <td>
                    {g.enrolled} de {g.capacity}
                  </td>
                  <td className="right">
                    <button className="btn btn-primary btn-small" onClick={() => onSelectGroup(g.id)}>
                      Calificar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
