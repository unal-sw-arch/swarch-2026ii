// Used only to decide which controls to display. The API verifies the token
// signature and ownership independently before allowing a deletion.
export function getSessionUserId(token) {
  if (!token) return null;
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const base64 = parts[1].replaceAll("-", "+").replaceAll("_", "/");
    const payload = JSON.parse(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, "=")));
    if (
      typeof payload.sub !== "string" || !payload.sub ||
      typeof payload.exp !== "number" || payload.exp * 1000 <= Date.now()
    ) {
      return null;
    }
    return payload.sub;
  } catch {
    return null;
  }
}
