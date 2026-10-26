// grades-service: API GraphQL de Pilosia.
// Guarda las notas en MongoDB. Los profesores registran notas y los estudiantes consultan su historia académica.

import { ApolloServer } from "@apollo/server";
import { startStandaloneServer } from "@apollo/server/standalone";
import { GraphQLError } from "graphql";
import { MongoClient } from "mongodb";

const MONGO_URL = process.env.MONGO_URL || "mongodb://localhost:27017";
const COURSES_SERVICE_URL = process.env.COURSES_SERVICE_URL || "http://localhost:8000";
const PORT = Number(process.env.PORT || 4000);
const PASSING_GRADE = 3.0;

const typeDefs = `#graphql
  type Grade {
    groupId: Int!
    studentCode: String!
    courseCode: String!
    courseName: String!
    credits: Int!
    period: String!
    grade: Float!
    passed: Boolean!
    updatedAt: String
  }

  type PeriodSummary {
    period: String!
    grades: [Grade!]!
    average: Float
    credits: Int!
  }

  type AcademicHistory {
    studentCode: String!
    periods: [PeriodSummary!]!
    average: Float
    approvedCredits: Int!
    totalCredits: Int!
  }

  type Query {
    "Historia académica de un estudiante: notas agrupadas por periodo y promedios"
    academicHistory(studentCode: String!): AcademicHistory!
    "Notas registradas en un grupo (para la vista del profesor)"
    groupGrades(groupId: Int!): [Grade!]!
  }

  type Mutation {
    "Registra o modifica la nota final de un estudiante en un grupo"
    registerGrade(groupId: Int!, studentCode: String!, grade: Float!): Grade!
  }
`;

// Promedio ponderado por créditos, redondeado a 2 decimales
function weightedAverage(grades) {
  const credits = grades.reduce((sum, g) => sum + g.credits, 0);
  if (credits === 0) return null;
  const points = grades.reduce((sum, g) => sum + g.grade * g.credits, 0);
  return Math.round((points / credits) * 100) / 100;
}

function toGrade(doc) {
  return {
    ...doc,
    passed: doc.grade >= PASSING_GRADE,
    updatedAt: doc.updatedAt ? doc.updatedAt.toISOString() : null,
  };
}

function userError(message) {
  return new GraphQLError(message, { extensions: { code: "BAD_USER_INPUT" } });
}

// Consulta a courses-service (REST) los datos del grupo y sus estudiantes inscritos
async function fetchGroup(groupId) {
  let response;
  try {
    response = await fetch(`${COURSES_SERVICE_URL}/api/groups/${groupId}/students`);
  } catch (error) {
    throw new GraphQLError("No se pudo comunicar con courses-service");
  }
  if (response.status === 404) throw userError("El grupo no existe");
  if (!response.ok) throw new GraphQLError("Error consultando courses-service");
  return response.json();
}

async function main() {
  const client = new MongoClient(MONGO_URL);
  await client.connect();
  const grades = client.db("grades_db").collection("grades");

  const resolvers = {
    Query: {
      academicHistory: async (_, { studentCode }) => {
        const docs = await grades
          .find({ studentCode })
          .sort({ period: 1, courseName: 1 })
          .toArray();
        const all = docs.map(toGrade);

        const byPeriod = new Map();
        for (const g of all) {
          if (!byPeriod.has(g.period)) byPeriod.set(g.period, []);
          byPeriod.get(g.period).push(g);
        }
        const periods = [...byPeriod.entries()].map(([period, list]) => ({
          period,
          grades: list,
          average: weightedAverage(list),
          credits: list.reduce((sum, g) => sum + g.credits, 0),
        }));

        return {
          studentCode,
          periods,
          average: weightedAverage(all),
          approvedCredits: all.filter((g) => g.passed).reduce((sum, g) => sum + g.credits, 0),
          totalCredits: all.reduce((sum, g) => sum + g.credits, 0),
        };
      },

      groupGrades: async (_, { groupId }) => {
        const docs = await grades.find({ groupId }).toArray();
        return docs.map(toGrade);
      },
    },

    Mutation: {
      registerGrade: async (_, { groupId, studentCode, grade }) => {
        if (grade < 0 || grade > 5) throw userError("La nota debe estar entre 0.0 y 5.0");

        // Validar con courses-service que el estudiante esté inscrito en este grupo
        const group = await fetchGroup(groupId);
        const isEnrolled = group.students.some((s) => s.code === studentCode);
        if (!isEnrolled) throw userError("El estudiante no está inscrito en este grupo");

        const doc = {
          groupId,
          studentCode,
          courseCode: group.course.code,
          courseName: group.course.name,
          credits: group.course.credits,
          period: group.period,
          grade: Math.round(grade * 10) / 10,
          updatedAt: new Date(),
        };
        await grades.updateOne({ groupId, studentCode }, { $set: doc }, { upsert: true });
        return toGrade(doc);
      },
    },
  };

  const server = new ApolloServer({ typeDefs, resolvers, includeStacktraceInErrorResponses: false });
  const { url } = await startStandaloneServer(server, { listen: { port: PORT } });
  console.log(`grades-service listo en ${url}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
