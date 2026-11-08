const SHORT_DAYS = { 1: "Lun", 2: "Mar", 3: "Mié", 4: "Jue", 5: "Vie", 6: "Sáb" };

// Muestra cada bloque del horario en una línea, ej: "Lun 07:00 a 09:00"
export function Schedule({ schedule }) {
  if (!schedule || schedule.length === 0) return <span className="muted">Sin horario</span>;
  return (
    <span className="schedule-lines">
      {schedule.map((b) => (
        <span key={`${b.day}-${b.start}`}>
          <span className="day">{SHORT_DAYS[b.day]}</span> {b.start} a {b.end}
        </span>
      ))}
    </span>
  );
}
