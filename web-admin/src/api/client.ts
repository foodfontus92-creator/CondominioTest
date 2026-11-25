import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { tokenStore } from '../auth/tokenStore';
import type { AuthResponse } from './types';

export class ApiError extends Error {
  status: number;
  code: string | null;
  details: Record<string, unknown>;

  constructor(status: number, message: string, code: string | null = null, details: Record<string, unknown> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export const AUTH_LOGOUT_EVENT = 'auth:logout';

const PUBLIC_PATHS = ['/auth/login', '/auth/refresh', '/auth/activate-unit', '/setup/status', '/setup/initialize'];
const isPublicPath = (url?: string) => !!url && PUBLIC_PATHS.some((p) => url.endsWith(p));

export const api = axios.create({ baseURL: '/api/v1', timeout: 20000 });

api.interceptors.request.use((config) => {
  const token = tokenStore.access();
  if (token && !isPublicPath(config.url)) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshInFlight: Promise<string | null> | null = null;

/** Single-flight refresh: concurrent 401s share one /auth/refresh call. */
export function refreshAccessToken(): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const refreshToken = tokenStore.refresh();
      if (!refreshToken) return null;
      try {
        const { data } = await axios.post<AuthResponse>('/api/v1/auth/refresh', { refreshToken });
        tokenStore.save(data);
        return data.accessToken;
      } catch {
        return null;
      }
    })().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

/** Used before opening the STOMP socket so it never connects with an expired JWT. */
export async function ensureFreshAccessToken(): Promise<string | null> {
  const token = tokenStore.access();
  if (!token) return null;
  if (tokenStore.accessExpiresAt() - Date.now() > 30_000) return token;
  return refreshAccessToken();
}

type Retriable = InternalAxiosRequestConfig & { _retry?: boolean };

function toApiError(error: AxiosError): ApiError {
  const data = error.response?.data as
    | { status?: number; code?: string; message?: string; details?: Record<string, unknown> }
    | undefined;
  if (!error.response) {
    return new ApiError(0, 'No hay conexión con el servidor local', 'NETWORK_ERROR');
  }
  return new ApiError(
    error.response.status,
    data?.message || `Error HTTP ${error.response.status}`,
    data?.code ?? null,
    data?.details ?? {},
  );
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as Retriable | undefined;
    if (error.response?.status === 401 && original && !original._retry && !isPublicPath(original.url)) {
      original._retry = true;
      const token = await refreshAccessToken();
      if (token) {
        original.headers.Authorization = `Bearer ${token}`;
        return api(original);
      }
      tokenStore.clear();
      window.dispatchEvent(new Event(AUTH_LOGOUT_EVENT));
    }
    return Promise.reject(error instanceof ApiError ? error : toApiError(error));
  },
);
