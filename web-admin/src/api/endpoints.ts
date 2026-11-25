import { api } from './client';
import type {
  AccessLog,
  AccessLogFilters,
  AppUser,
  AuthResponse,
  Condominium,
  CreateActivationCodeBody,
  CreateCondominiumBody,
  CreateDeviceBody,
  CreateUnitBody,
  CreateUserBody,
  Device,
  Page,
  RevokeResidentsResponse,
  SetupInitializeBody,
  SetupInitializeResponse,
  SetupStatusResponse,
  UnitActivationCodeResponse,
  UpdateCondominiumBody,
  UpdateDeviceBody,
  UpdateUnitBody,
  UpdateUserBody,
  Unit,
} from './types';

export const setupApi = {
  status: () => api.get<SetupStatusResponse>('/setup/status').then((r) => r.data),
  initialize: (body: SetupInitializeBody) =>
    api.post<SetupInitializeResponse>('/setup/initialize', body).then((r) => r.data),
};

export const authApi = {
  login: (condominiumCode: string, email: string, password: string) =>
    api.post<AuthResponse>('/auth/login', { condominiumCode, email, password }).then((r) => r.data),
  logout: (refreshToken: string) => api.post('/auth/logout', { refreshToken }).then(() => undefined),
};

export const condoApi = {
  list: () => api.get<Condominium[]>('/condominiums').then((r) => r.data),
  create: (body: CreateCondominiumBody) => api.post<Condominium>('/condominiums', body).then((r) => r.data),
  update: (condoId: string, body: UpdateCondominiumBody) =>
    api.patch<Condominium>(`/condominiums/${condoId}`, body).then((r) => r.data),
  rotateHmac: (condoId: string) =>
    api.post<Condominium>(`/condominiums/${condoId}/rotate-hmac`).then((r) => r.data),
};

export const unitApi = {
  list: (condoId: string) => api.get<Unit[]>(`/condos/${condoId}/units`).then((r) => r.data),
  create: (condoId: string, body: CreateUnitBody) =>
    api.post<Unit>(`/condos/${condoId}/units`, body).then((r) => r.data),
  update: (condoId: string, unitId: string, body: UpdateUnitBody) =>
    api.patch<Unit>(`/condos/${condoId}/units/${unitId}`, body).then((r) => r.data),
  createActivationCode: (condoId: string, unitId: string, body: CreateActivationCodeBody) =>
    api.post<UnitActivationCodeResponse>(`/condos/${condoId}/units/${unitId}/activation-codes`, body).then((r) => r.data),
  revokeResidents: (condoId: string, unitId: string) =>
    api.post<RevokeResidentsResponse>(`/condos/${condoId}/units/${unitId}/revoke-residents`).then((r) => r.data),
};

export const userApi = {
  list: (condoId: string) => api.get<AppUser[]>(`/condos/${condoId}/users`).then((r) => r.data),
  create: (condoId: string, body: CreateUserBody) =>
    api.post<AppUser>(`/condos/${condoId}/users`, body).then((r) => r.data),
  update: (condoId: string, userId: string, body: UpdateUserBody) =>
    api.patch<AppUser>(`/condos/${condoId}/users/${userId}`, body).then((r) => r.data),
};

export const deviceApi = {
  list: (condoId: string) => api.get<Device[]>(`/condos/${condoId}/devices`).then((r) => r.data),
  create: (condoId: string, body: CreateDeviceBody) =>
    api.post<Device>(`/condos/${condoId}/devices`, body).then((r) => r.data),
  update: (condoId: string, deviceId: string, body: UpdateDeviceBody) =>
    api.patch<Device>(`/condos/${condoId}/devices/${deviceId}`, body).then((r) => r.data),
  syncConfig: (condoId: string, deviceId: string) =>
    api
      .post<{ commandId: string; status: string }>(`/condos/${condoId}/devices/${deviceId}/sync-config`)
      .then((r) => r.data),
};

export const logApi = {
  page: (condoId: string, filters: AccessLogFilters, page: number, size: number) =>
    api
      .get<Page<AccessLog>>(`/condos/${condoId}/access-logs`, {
        params: {
          page,
          size,
          unitId: filters.unitId || undefined,
          deviceId: filters.deviceId || undefined,
          result: filters.result || undefined,
          method: filters.method && filters.method.length > 0 ? filters.method.join(',') : undefined,
          from: filters.from || undefined,
          to: filters.to || undefined,
        },
      })
      .then((r) => r.data),
};
