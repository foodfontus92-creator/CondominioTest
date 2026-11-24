import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import { ApiError, AUTH_LOGOUT_EVENT } from '../api/client';
import { authApi } from '../api/endpoints';
import type { UserProfile } from '../api/types';
import { tokenStore } from './tokenStore';

interface AuthContextValue {
  user: UserProfile | null;
  login: (condominiumCode: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<UserProfile | null>(() => tokenStore.get()?.user ?? null);

  useEffect(() => {
    const onForcedLogout = () => {
      setUser(null);
      queryClient.clear();
    };
    window.addEventListener(AUTH_LOGOUT_EVENT, onForcedLogout);
    return () => window.removeEventListener(AUTH_LOGOUT_EVENT, onForcedLogout);
  }, [queryClient]);

  const login = useCallback(async (condominiumCode: string, email: string, password: string) => {
    const auth = await authApi.login(condominiumCode.trim(), email.trim(), password);
    if (auth.user.role === 'RESIDENT') {
      // Residents use the mobile app; revoke the session we just created (token not stored yet).
      await axios
        .post(
          '/api/v1/auth/logout',
          { refreshToken: auth.refreshToken },
          { headers: { Authorization: `Bearer ${auth.accessToken}` } },
        )
        .catch(() => undefined);
      throw new ApiError(403, 'Este panel es solo para administradores (CONDO_ADMIN / SUPER_ADMIN)', 'ROLE_NOT_ALLOWED');
    }
    tokenStore.save(auth);
    setUser(auth.user);
  }, []);

  const logout = useCallback(async () => {
    const refresh = tokenStore.refresh();
    if (refresh) {
      await authApi.logout(refresh).catch(() => undefined);
    }
    tokenStore.clear();
    setUser(null);
    queryClient.clear();
  }, [queryClient]);

  const value = useMemo(() => ({ user, login, logout }), [user, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
