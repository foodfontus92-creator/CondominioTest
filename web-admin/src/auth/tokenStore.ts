import type { AuthResponse, UserProfile } from '../api/types';

const KEY = 'accesolibre.admin.session';

export interface StoredSession {
  accessToken: string;
  refreshToken: string;
  accessExpiresAt: number; // epoch ms
  user: UserProfile;
}

function read(): StoredSession | null {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as StoredSession) : null;
  } catch {
    return null;
  }
}

export const tokenStore = {
  get: read,
  access: () => read()?.accessToken ?? null,
  refresh: () => read()?.refreshToken ?? null,
  accessExpiresAt: () => read()?.accessExpiresAt ?? 0,
  save(auth: AuthResponse) {
    const session: StoredSession = {
      accessToken: auth.accessToken,
      refreshToken: auth.refreshToken,
      accessExpiresAt: Date.now() + auth.accessTokenExpiresInSeconds * 1000,
      user: auth.user,
    };
    localStorage.setItem(KEY, JSON.stringify(session));
    return session;
  },
  clear() {
    localStorage.removeItem(KEY);
  },
};
