import { useEffect, useState } from "react";
import { getSchedule } from "../api.js";

const DAYS = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"];
const FIRST_HOUR = 7;
const LAST_HOUR = 20; // la última franja es 19:00 - 20:00

// Colores para distinguir las asignaturas: [color del borde, fondo suave]
const COLORS = [
  ["#8c2b32", "rgba(140, 43, 50, 0.08)"],
  ["#5d5c31", "rgba(93, 92, 49, 0.12)"],
  ["#000000", "rgba(0, 0, 0, 0.06)"],
  ["#8c2b32", "rgba(140, 43, 50, 0.16)"],
  ["#5d5c31", "rgba(93, 92, 49, 0.22)"],
  ["#000000", "rgba(0, 0, 0, 0.12)"],
];

function hourOf(time) {
  return Number(time.split(":")[0]);
}

export default function MySchedule({ student }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    getSchedule(student.code)
      .then(setData)
      .catch((e) => setError(e.message));
  }, [student.code]);

  if (error) return <div className="alert alert-error">{error}</div>;
  if (!data) return <p className="empty">Cargando horario...</p>;

  // Asignar un color a cada asignatura
  const courseCodes = [...new Set(data.blocks.map((b) => b.course_code))];
  const colorOf = (code) => COLORS[courseCodes.indexOf(code) % COLORS.length];

  // Para cada día, qué bloque empieza en cada hora. Las horas del prototipo son en punto.
  const hours = [];
  for (let h = FIRST_HOUR; h < LAST_HOUR; h++) hours.push(h);

  const occupied = {}; // celdas que ya cubre un bloque de varias horas
  function renderCell(dayIndex, hour) {
    const day = dayIndex + 1;
    const key = `${day}-${hour}`;
    if (occupied[key]) return null;

    const block = data.blocks.find((b) => b.day === day && hourOf(b.start) === hour);
    if (!block) return <td key={key}></td>;

    const duration = hourOf(block.end) - hourOf(block.start);
    for (let i = 1; i < duration; i++) occupied[`${day}-${hour + i}`] = true;
    const [border, background] = colorOf(block.course_code);
    return (
      <td key={key} rowSpan={duration} className="class-block" style={{ borderLeftColor: border, background }}>
        <strong>{block.course_name}</strong>
        <span>
          Grupo {block.group_number}, {block.start} a {block.end}
        </span>
      </td>
    );
  }

  return (
    <section>
      <div className="page-header">
        <h1>Mi horario</h1>
        <span className="period">Periodo {data.period}</span>
      </div>
      <p className="intro">Horario semanal con las asignaturas que tienes inscritas.</p>

      {data.blocks.length === 0 && (
        <p className="empty">No tienes asignaturas inscritas en este periodo. Inscríbelas desde el catálogo.</p>
      )}

      <div className="table-wrap">
        <table className="timetable">
          <thead>
            <tr>
              <th></th>
              {DAYS.map((d) => (
                <th key={d}>{d}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {hours.map((hour) => (
              <tr key={hour}>
                <td className="hour">{`${String(hour).padStart(2, "0")}:00`}</td>
                {DAYS.map((_, dayIndex) => renderCell(dayIndex, hour))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
