import { useEffect, useState } from "react";
import { enroll, getCourses, getEnrollments } from "../api.js";
import { Schedule } from "../Schedule.jsx";

export default function Catalog({ student }) {
  const [search, setSearch] = useState("");
  const [catalog, setCatalog] = useState(null);
  const [enrollments, setEnrollments] = useState([]);
  const [message, setMessage] = useState(null);

  async function load(text = search) {
    try {
      const [courses, enrolled] = await Promise.all([
        getCourses(text),
        getEnrollments(student.code),
      ]);
      setCatalog(courses);
      setEnrollments(enrolled.enrollments);
    } catch (error) {
      setMessage({ type: "error", text: error.message });
    }
  }

  useEffect(() => {
    load("");
  }, []);

  async function handleEnroll(groupId) {
    setMessage(null);
    try {
      const result = await enroll(student.code, groupId);
      setMessage({ type: "success", text: result.message });
    } catch (error) {
      setMessage({ type: "error", text: error.message });
    }
    load();
  }

  function handleSearch(event) {
    event.preventDefault();
    load();
  }

  return (
    <section>
      <div className="page-header">
        <h1>Catálogo de asignaturas</h1>
        {catalog && <span className="period">Periodo {catalog.period}</span>}
      </div>
      <p className="intro">
        Escoge un grupo para inscribir la asignatura. El sistema revisa los cupos, los créditos y que no se cruce con tu horario.
      </p>

      <form className="search-bar" onSubmit={handleSearch}>
        <input
          aria-label="Buscar asignatura"
          placeholder="Nombre o código de la asignatura"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <button className="btn btn-outline" type="submit">Buscar</button>
      </form>

      {message && <div className={`alert alert-${message.type}`}>{message.text}</div>}

      {!catalog && <p className="empty">Cargando asignaturas...</p>}
      {catalog && catalog.courses.length === 0 && (
        <p className="empty">No hay asignaturas que coincidan con la búsqueda.</p>
      )}

      {catalog?.courses.map((course) => {
        // si ya tiene la materia, se muestra en qué grupo quedó y no se puede inscribir otro
        const enrolledGroup = enrollments.find((e) => e.course_code === course.code)?.group_id;
        return (
          <div className="section" key={course.code}>
            <div className="section-title">
              <h2>{course.name}</h2>
              <span className="meta">
                <strong>{course.code}</strong>&nbsp;&nbsp;{course.credits} créditos
              </span>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th style={{ width: "70px" }}>Grupo</th>
                    <th>Docente</th>
                    <th>Horario</th>
                    <th style={{ width: "110px" }}>Cupos libres</th>
                    <th style={{ width: "110px" }}></th>
                  </tr>
                </thead>
                <tbody>
                  {course.groups.map((group) => (
                    <tr key={group.id}>
                      <td className="num">{group.number}</td>
                      <td>{group.professor}</td>
                      <td>
                        <Schedule schedule={group.schedule} />
                      </td>
                      <td>
                        {group.available === 0 ? (
                          <span className="full">Sin cupos</span>
                        ) : (
                          `${group.available} de ${group.capacity}`
                        )}
                      </td>
                      <td className="right">
                        {enrolledGroup === group.id && <span className="tag-enrolled">Inscrita</span>}
                        {!enrolledGroup && (
                          <button className="btn btn-primary btn-small" onClick={() => handleEnroll(group.id)}>
                            Inscribir
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        );
      })}
    </section>
  );
}
