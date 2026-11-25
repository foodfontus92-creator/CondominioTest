export type UserRole = 'SUPER_ADMIN' | 'CONDO_ADMIN' | 'RESIDENT';

export type CommunityType = 'CONDOMINIUM' | 'POBLACION' | 'VILLA' | 'PASAJE';

export interface UserProfile {
  id: string;
  condominiumId: string;
  unitId: string | null;
  unitNumber?: string | null;
  blockName?: string | null;
  email: string;
  fullName: string;
  role: UserRole;
  communityType?: CommunityType;
}

export interface AuthResponse {
  tokenType: string;
  accessToken: string;
  accessTokenExpiresInSeconds: number;
  refreshToken: string;
  refreshTokenExpiresInSeconds: number;
  communityType?: CommunityType;
  user: UserProfile;
}

export interface Condominium {
  id: string;
  code: string;
  name: string;
  communityType: CommunityType;
  address: string | null;
  timezone: string;
  hmacKeyVersion: number;
  latitude: number | null;
  longitude: number | null;
  geofenceRadiusMeters: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCondominiumBody {
  code: string;
  name: string;
  communityType?: CommunityType;
  address?: string;
  timezone?: string;
  latitude?: number;
  longitude?: number;
  geofenceRadiusMeters?: number;
}

export interface UpdateCondominiumBody {
  name?: string;
  communityType?: CommunityType;
  address?: string;
  timezone?: string;
  latitude?: number;
  longitude?: number;
  geofenceRadiusMeters?: number;
  active?: boolean;
}

export interface Unit {
  id: string;
  condominiumId: string;
  unitNumber: string;
  blockName: string | null;
  towerOrBlock: string | null;
  floor: string | null;
  intercomExtension: string | null;
  active: boolean;
  blocked: boolean;
  blockReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateUnitBody {
  unitNumber: string;
  blockName?: string;
  towerOrBlock?: string;
  floor?: string;
  intercomExtension?: string;
}

export interface BatchCreateUnitsBody {
  blockName: string;
  unitNumbers: string[];
}

export interface BatchCreateUnitsResponse {
  blockName: string;
  createdCount: number;
  units: Unit[];
}

export interface UpdateUnitBody {
  unitNumber?: string;
  blockName?: string;
  towerOrBlock?: string;
  floor?: string;
  intercomExtension?: string;
  active?: boolean;
  blocked?: boolean;
  blockReason?: string;
}

export interface AppUser {
  id: string;
  condominiumId: string;
  unitId: string | null;
  email: string;
  fullName: string;
  phone: string | null;
  role: UserRole;
  active: boolean;
  blocked: boolean;
  blockReason: string | null;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateUserBody {
  unitId?: string;
  email: string;
  password: string;
  fullName: string;
  phone?: string;
  role: UserRole;
}

export interface UpdateUserBody {
  unitId?: string;
  fullName?: string;
  phone?: string;
  role?: UserRole;
  active?: boolean;
  blocked?: boolean;
  blockReason?: string;
  newPassword?: string;
}

export type DeviceType =
  | 'PEDESTRIAN_GATE'
  | 'VEHICLE_BARRIER'
  | 'LOBBY_DOOR'
  | 'INTERCOM_PANEL'
  | 'ELEVATOR_CONTROLLER';

export type DeviceStatus = 'ONLINE' | 'OFFLINE' | 'MAINTENANCE' | 'FAULT';

export interface Device {
  id: string;
  condominiumId: string;
  hardwareUid: string;
  name: string;
  deviceType: DeviceType;
  locationDescription: string | null;
  mqttUsername: string;
  publicQrToken: string;
  firmwareVersion: string | null;
  ipAddress: string | null;
  status: DeviceStatus;
  relayPulseMs: number;
  hmacKeyVersionSynced: number;
  lastSeenAt: string | null;
  lastTelemetry: Record<string, unknown> | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDeviceBody {
  hardwareUid: string;
  name: string;
  deviceType: DeviceType;
  locationDescription?: string;
  mqttUsername: string;
  relayPulseMs?: number;
}

export interface UpdateDeviceBody {
  name?: string;
  deviceType?: DeviceType;
  locationDescription?: string;
  relayPulseMs?: number;
  status?: DeviceStatus;
  active?: boolean;
}

export type AccessMethod =
  | 'QR_OFFLINE'
  | 'QR_ONLINE'
  | 'REMOTE_APP'
  | 'INTERCOM_UNLOCK'
  | 'ADMIN_OVERRIDE'
  | 'MANUAL_EXIT';

export type AccessResult =
  | 'GRANTED'
  | 'DENIED_INVALID_HMAC'
  | 'DENIED_EXPIRED'
  | 'DENIED_REVOKED'
  | 'DENIED_WRONG_CONDO'
  | 'DENIED_MAX_USES'
  | 'DENIED_DEVICE_OFFLINE'
  | 'DENIED_OUTSIDE_GEOFENCE'
  | 'DENIED_USER_BLOCKED'
  | 'DENIED_UNIT_BLOCKED'
  | 'DENIED_UNAUTHORIZED'
  | 'ERROR';

export interface AccessLog {
  id: string;
  condominiumId: string;
  deviceId: string | null;
  unitId: string | null;
  accessPassId: string | null;
  triggeredByUserId: string | null;
  accessMethod: AccessMethod;
  result: AccessResult;
  offlineValidated: boolean;
  qrPassIdRaw: string | null;
  details: Record<string, unknown> | null;
  occurredAt: string;
  syncedAt: string;
}

export interface AccessLogFilters {
  unitId?: string;
  deviceId?: string;
  result?: AccessResult;
  method?: AccessMethod[];
  from?: string; // ISO instant (inclusive)
  to?: string; // ISO instant (exclusive)
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/** Live device event pushed on /topic/condo/{condoId}/devices */
export interface DeviceLiveEvent {
  eventType: 'HEARTBEAT' | 'DEVICE_OFFLINE' | 'ACCESS_EVENT' | 'GATE_OPEN_TIMEOUT' | 'GATE_CLOSED';
  condominiumId: string;
  deviceId: string;
  occurredAt: string;
  payload: Record<string, unknown>;
}

export interface ResidentsSummary {
  totalResidents: number;
  activeResidents: number;
  blockedResidents: number;
  inactiveResidents: number;
  blockedOrInactiveResidents: number;
  occupiedUnits: number;
  totalUnits: number;
}

export interface SetupStatusResponse {
  initialized: boolean;
  defaultCommunityType?: CommunityType;
}

export interface SetupInitializeBody {
  condoCode: string;
  condoName: string;
  communityType?: CommunityType;
  address?: string;
  timezone?: string;
  latitude?: number;
  longitude?: number;
  geofenceRadiusMeters?: number;
  adminFullName: string;
  adminEmail: string;
  adminPassword: string;
}

export interface SetupInitializeResponse {
  initialized: boolean;
  condominiumId: string;
  condoCode: string;
  condoName: string;
  communityType: CommunityType;
  adminUserId: string;
  adminEmail: string;
}

export interface CreateActivationCodeBody {
  maxUses?: number;
  expiresInHours?: number;
  revokePrevious?: boolean;
}

export interface UnitActivationCodeResponse {
  id: string;
  condominiumId: string;
  condominiumName: string;
  unitId: string;
  unitNumber: string;
  code: string;
  maxUses: number;
  usedCount: number;
  expiresAt: string;
  active: boolean;
  createdAt: string;
}

export interface RevokeResidentsResponse {
  unitId: string;
  unitNumber: string;
  deactivatedResidents: number;
  invalidatedCodes: number;
}
