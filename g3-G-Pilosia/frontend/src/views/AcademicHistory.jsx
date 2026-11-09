import { useEffect, useState } from "react";
import { getAcademicHistory } from "../api.js";

function formatGrade(value) {
  return value === null || value === undefined ? "-" : value.toFixed(1);
}

export default function AcademicHistory({ student }) {
  const [history, setHistory] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    getAcademicHistory(student.code)
      .then(setHistory)
      .catch((e) => setError(e.message));
  }, [student.code]);

  if (error) return <div className="alert alert-error">{error}</div>;
  if (!history) return <p className="empty">Cargando historia académica...</p>;

  return (
    <section>
      <div className="page-header">
        <h1>Historia académica</h1>
      </div>
      <p className="intro">
        {student.name}, {student.program}
      </p>

      {history.periods.length === 0 ? (
        <p className="empty">Todavía no tienes notas registradas. Aparecerán aquí cuando tus docentes las registren.</p>
      ) : (
        <div className="summary">
          <div>
            <span className="average">{history.average.toFixed(2)}</span>
            <span className="average-label">Promedio acumulado</span>
          </div>
          <div>
            <p>
              <strong>{history.approvedCredits}</strong> de {history.totalCredits} créditos cursados están aprobados.
            </p>
            <p className="muted">El promedio se calcula con las notas de cada asignatura según sus créditos.</p>
          </div>
        </div>
      )}

      {history.periods.map((p) => (
        <div className="section" key={p.period}>
          <div className="section-title">
            <h2>Periodo {p.period}</h2>
            <span className="meta">
              Promedio del periodo <strong>{p.average.toFixed(2)}</strong>
            </span>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th style={{ width: "90px" }}>Código</th>
                  <th>Asignatura</th>
                  <th style={{ width: "90px" }}>Créditos</th>
                  <th style={{ width: "80px" }}>Nota</th>
                  <th style={{ width: "110px" }}>Estado</th>
                </tr>
              </thead>
              <tbody>
                {p.grades.map((g) => (
                  <tr key={g.courseCode}>
                    <td className="num">{g.courseCode}</td>
                    <td>{g.courseName}</td>
                    <td>{g.credits}</td>
                    <td className="num">{formatGrade(g.grade)}</td>
                    <td className={g.passed ? "passed" : "failed"}>{g.passed ? "Aprobada" : "Reprobada"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ))}
    </section>
  );
}
