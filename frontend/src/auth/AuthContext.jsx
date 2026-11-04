import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, onUnauthorized, setCsrfToken } from '../api/client.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [state, setState] = useState({ loading: true, user: null });

  const applySession = useCallback((session) => {
    setCsrfToken(session?.csrfToken ?? null);
    setState({ loading: false, user: session?.user ?? null });
  }, []);

  // Restore the session from the HttpOnly cookie on page load.
  useEffect(() => {
    api.get('/auth/me').then(applySession).catch(() => applySession(null));
  }, [applySession]);

  // Any 401 from a data call means the cookie expired: drop to the login screen.
  useEffect(() => {
    onUnauthorized(() => applySession(null));
    return () => onUnauthorized(null);
  }, [applySession]);

  const login = useCallback(
    async (username, password) => {
      const session = await api.post('/auth/login', { username, password });
      applySession(session);
      return session.user;
    },
    [applySession],
  );

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout');
    } finally {
      applySession(null);
    }
  }, [applySession]);

  const value = useMemo(() => ({ ...state, login, logout }), [state, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);

export const homePathFor = (role) => `/${String(role || '').toLowerCase()}`;
