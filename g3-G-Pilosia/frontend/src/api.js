// Funciones para comunicarse con los servicios.
// REST  -> courses-service (/api/...)
// GraphQL -> grades-service (/graphql)

async function rest(path, options = {}) {
  const response = await fetch(`/api${path}`, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.detail || "Ocurrió un error en la solicitud");
  }
  return data;
}

async function graphql(query, variables = {}) {
  const response = await fetch("/graphql", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ query, variables }),
  });
  const data = await response.json();
  if (data.errors) {
    throw new Error(data.errors[0].message);
  }
  return data.data;
}

// ---------- REST: courses-service ----------

export const getStudents = () => rest("/students");
export const getProfessors = () => rest("/professors");
export const getCourses = (search = "") => rest(`/courses?search=${encodeURIComponent(search)}`);
export const getEnrollments = (code) => rest(`/students/${code}/enrollments`);
export const getSchedule = (code) => rest(`/students/${code}/schedule`);
export const getProfessorGroups = (id) => rest(`/professors/${id}/groups`);
export const getGroupStudents = (groupId) => rest(`/groups/${groupId}/students`);

export const enroll = (code, groupId) =>
  rest(`/students/${code}/enrollments`, {
    method: "POST",
    body: JSON.stringify({ group_id: groupId }),
  });

export const cancelEnrollment = (code, enrollmentId) =>
  rest(`/students/${code}/enrollments/${enrollmentId}`, { method: "DELETE" });

// ---------- GraphQL: grades-service ----------

export async function getAcademicHistory(studentCode) {
  const data = await graphql(
    `query ($studentCode: String!) {
      academicHistory(studentCode: $studentCode) {
        average
        approvedCredits
        totalCredits
        periods {
          period
          average
          credits
          grades { courseCode courseName credits grade passed }
        }
      }
    }`,
    { studentCode }
  );
  return data.academicHistory;
}

export async function getGroupGrades(groupId) {
  const data = await graphql(
    `query ($groupId: Int!) {
      groupGrades(groupId: $groupId) { studentCode grade updatedAt }
    }`,
    { groupId }
  );
  return data.groupGrades;
}

export async function registerGrade(groupId, studentCode, grade) {
  const data = await graphql(
    `mutation ($groupId: Int!, $studentCode: String!, $grade: Float!) {
      registerGrade(groupId: $groupId, studentCode: $studentCode, grade: $grade) {
        studentCode grade
      }
    }`,
    { groupId, studentCode, grade }
  );
  return data.registerGrade;
}
