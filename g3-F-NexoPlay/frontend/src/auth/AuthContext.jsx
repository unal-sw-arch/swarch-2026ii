import { useState } from "react";
import { request } from "../api/client";
import { getSessionUserId } from "./session";
import { AuthContext } from "./useAuth";

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem("token"));

  const save = (t) => {
    localStorage.setItem("token", t);
    setToken(t);
  };

  const login = async (email, password) => {
    const data = await request("/auth/login", {
      method: "POST",
      body: { email, password },
    });
    save(data.token);
  };

  const register = async (name, email, password) => {
    const data = await request("/auth/register", {
      method: "POST",
      body: { name, email, password },
    });
    save(data.token);
  };

  const logout = () => {
    localStorage.removeItem("token");
    setToken(null);
  };

  return (
    <AuthContext.Provider
      value={{ token, userId: getSessionUserId(token), login, register, logout, setSession: save }}
    >
      {children}
    </AuthContext.Provider>
  );
}
