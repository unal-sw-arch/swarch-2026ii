// Base de datos de grades-service (notas de los estudiantes).
// Este script lo ejecuta el contenedor de MongoDB la primera vez que arranca.

db = db.getSiblingDB("grades_db");

db.createCollection("grades");

// Un estudiante solo puede tener una nota por grupo
db.grades.createIndex({ groupId: 1, studentCode: 1 }, { unique: true });
db.grades.createIndex({ studentCode: 1 });

const now = new Date();

// Notas de periodos anteriores (los groupId coinciden con los grupos de PostgreSQL)
db.grades.insertMany([
  // Laura Gómez Ruiz
  { groupId: 1, studentCode: "20231001", courseCode: "INF101", courseName: "Programación de Computadores", credits: 3, period: "2024-2", grade: 4.2, updatedAt: now },
  { groupId: 2, studentCode: "20231001", courseCode: "MAT101", courseName: "Cálculo Diferencial", credits: 4, period: "2024-2", grade: 3.5, updatedAt: now },
  { groupId: 3, studentCode: "20231001", courseCode: "HUM101", courseName: "Cátedra de Ciudadanía", credits: 2, period: "2024-2", grade: 4.6, updatedAt: now },
  { groupId: 4, studentCode: "20231001", courseCode: "INF201", courseName: "Estructuras de Datos", credits: 3, period: "2025-1", grade: 4.0, updatedAt: now },
  { groupId: 6, studentCode: "20231001", courseCode: "MAT102", courseName: "Cálculo Integral", credits: 4, period: "2025-1", grade: 2.8, updatedAt: now },
  { groupId: 7, studentCode: "20231001", courseCode: "FIS101", courseName: "Física Mecánica", credits: 4, period: "2025-1", grade: 3.6, updatedAt: now },

  // Andrés Martínez López
  { groupId: 4, studentCode: "20231002", courseCode: "INF201", courseName: "Estructuras de Datos", credits: 3, period: "2025-1", grade: 3.9, updatedAt: now },
  { groupId: 5, studentCode: "20231002", courseCode: "MAT201", courseName: "Álgebra Lineal", credits: 3, period: "2025-1", grade: 4.1, updatedAt: now },
  { groupId: 6, studentCode: "20231002", courseCode: "MAT102", courseName: "Cálculo Integral", credits: 4, period: "2025-1", grade: 3.2, updatedAt: now },

  // Camila Torres Díaz
  { groupId: 1, studentCode: "20222003", courseCode: "INF101", courseName: "Programación de Computadores", credits: 3, period: "2024-2", grade: 4.8, updatedAt: now },
  { groupId: 2, studentCode: "20222003", courseCode: "MAT101", courseName: "Cálculo Diferencial", credits: 4, period: "2024-2", grade: 4.0, updatedAt: now },
]);
