import { useEffect, useState } from "react";
import { cancelEnrollment, getEnrollments } from "../api.js";
import { Schedule } from "../Schedule.jsx";

export default function MyEnrollments({ student }) {
  const [data, setData] = useState(null);
  const [message, setMessage] = useState(null);

  function load() {
    getEnrollments(student.code)
      .then(setData)
      .catch((error) => setMessage({ type: "error", text: error.message }));
  }

  useEffect(load, [student.code]);

  async function handleCancel(enrollment) {
    if (!window.confirm(`¿Cancelar la inscripción de ${enrollment.course_name}?`)) return;
    try {
      const result = await cancelEnrollment(student.code, enrollment.id);
      setMessage({ type: "success", text: result.message });
    } catch (error) {
      setMessage({ type: "error", text: error.message });
    }
    load();
  }

  return (
    <section>
      <div className="page-header">
        <h1>Mis inscripciones</h1>
        {data && <span className="period">Periodo {data.period}</span>}
      </div>
      <p className="intro">Asignaturas que tienes inscritas este periodo. Puedes cancelar cualquiera de ellas.</p>

      {message && <div className={`alert alert-${message.type}`}>{message.text}</div>}
      {!data && <p className="empty">Cargando inscripciones...</p>}

      {data && (
        <>
          <div className="credits">
            <p>
              <strong>{data.total_credits}</strong> de {data.max_credits} créditos inscritos
            </p>
            <div className="credit-bar">
              <div style={{ width: `${Math.min(100, (data.total_credits / data.max_credits) * 100)}%` }}></div>
            </div>
          </div>

          {data.enrollments.length === 0 ? (
            <p className="empty">Aún no tienes asignaturas inscritas. Puedes inscribirlas desde el catálogo.</p>
          ) : (
            <div className="section table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Código</th>
                    <th>Asignatura</th>
                    <th>Grupo</th>
                    <th>Docente</th>
                    <th>Horario</th>
                    <th>Créditos</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {data.enrollments.map((e) => (
                    <tr key={e.id}>
                      <td className="num">{e.course_code}</td>
                      <td>{e.course_name}</td>
                      <td>{e.group_number}</td>
                      <td>{e.professor}</td>
                      <td>
                        <Schedule schedule={e.schedule} />
                      </td>
                      <td>{e.credits}</td>
                      <td className="right">
                        <button className="btn btn-outline-wine btn-small" onClick={() => handleCancel(e)}>
                          Cancelar
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  );
}
