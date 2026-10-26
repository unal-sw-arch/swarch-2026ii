import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import {
  User as FirebaseUser,
  onAuthStateChanged,
  signInWithPopup,
  signOut,
} from 'firebase/auth';
import { auth, googleProvider } from '../config/firebase';
import { BackendProfile, RoleName } from '../types/auth';
import { getCurrentUserProfile } from '../api/users';

export interface AuthContextType {
  firebaseUser: FirebaseUser | null;
  token: string | null;
  profile: BackendProfile | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  signInWithGoogle: () => Promise<void>;
  logout: () => Promise<void>;
  refreshProfile: () => Promise<void>;
  hasRole: (roles: RoleName | RoleName[]) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }): React.JSX.Element {
  const [firebaseUser, setFirebaseUser] = useState<FirebaseUser | null>(null);
  const [token, setToken] = useState<string | null>(() => {
    return localStorage.getItem('undesparche_auth_token');
  });
  const [profile, setProfile] = useState<BackendProfile | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(() => {
    return Boolean(localStorage.getItem('undesparche_auth_token'));
  });
  const [error, setError] = useState<string | null>(null);

  const fetchProfile = useCallback(async (authToken: string) => {
    try {
      const data = await getCurrentUserProfile(authToken);
      setProfile(data);
      return data;
    } catch (err) {
      console.error('Error al obtener perfil del backend:', err);
      setProfile(null);
      setToken(null);
      localStorage.removeItem('undesparche_auth_token');
      throw err;
    }
  }, []);

  const refreshProfile = useCallback(async () => {
    if (token) {
      await fetchProfile(token);
    }
  }, [token, fetchProfile]);

  useEffect(() => {
    let isMounted = true;
    const storedToken = localStorage.getItem('undesparche_auth_token');

    if (storedToken) {
      fetchProfile(storedToken)
        .catch((err) => {
          console.warn('Sesión previa no válida o expirada:', err);
        })
        .finally(() => {
          if (isMounted) setIsLoading(false);
        });
    } else {
      setIsLoading(false);
    }

    try {
      const unsubscribe = onAuthStateChanged(auth, async (user) => {
        if (!isMounted) return;
        if (user) {
          setFirebaseUser(user);
          try {
            const idToken = await user.getIdToken();
            setToken(idToken);
            localStorage.setItem('undesparche_auth_token', idToken);
            await fetchProfile(idToken);
          } catch (err) {
            console.warn('Error al sincronizar token de Firebase:', err);
          }
        } else if (!storedToken) {
          setFirebaseUser(null);
          setToken(null);
          setProfile(null);
        }
      });
      return () => {
        isMounted = false;
        unsubscribe();
      };
    } catch (err) {
      console.warn('Error al configurar observador de autenticación:', err);
      return () => {
        isMounted = false;
      };
    }
  }, [fetchProfile]);

  const signInWithGoogle = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await signInWithPopup(auth, googleProvider);
      const user = result.user;
      setFirebaseUser(user);
      const idToken = await user.getIdToken();
      setToken(idToken);
      localStorage.setItem('undesparche_auth_token', idToken);
      await fetchProfile(idToken);
    } catch (err: any) {
      console.error('Error de autenticación con Google:', err);
      const errorMessage =
        err?.code === 'auth/popup-closed-by-user'
          ? 'El inicio de sesión fue cancelado.'
          : err?.message || 'Error al iniciar sesión con Google';
      setError(errorMessage);
      throw err;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    setIsLoading(true);
    try {
      await signOut(auth);
    } catch {
      // ignore
    }
    setFirebaseUser(null);
    setToken(null);
    setProfile(null);
    localStorage.removeItem('undesparche_auth_token');
    setIsLoading(false);
  };

  const hasRole = useCallback(
    (roles: RoleName | RoleName[]): boolean => {
      if (!profile || !profile.roles) return false;
      const checkRoles = Array.isArray(roles) ? roles : [roles];
      return checkRoles.some((r) => profile.roles.includes(r));
    },
    [profile]
  );

  const value = useMemo<AuthContextType>(
    () => ({
      firebaseUser,
      token,
      profile,
      isAuthenticated: Boolean(profile && token),
      isLoading,
      error,
      signInWithGoogle,
      logout,
      refreshProfile,
      hasRole,
    }),
    [firebaseUser, token, profile, isLoading, error, refreshProfile, hasRole]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth debe ser utilizado dentro de un AuthProvider');
  }
  return context;
}
