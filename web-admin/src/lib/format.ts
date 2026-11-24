import type { AccessMethod, AccessResult, Device, DeviceType, UserRole } from '../api/types';

export const dateTimeFmt = new Intl.DateTimeFormat('es', {
  dateStyle: 'short',
  timeStyle: 'medium',
});

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '—' : dateTimeFmt.format(d);
}

export function relativeTime(iso: string | null | undefined, nowMs: number = Date.now()): string {
  if (!iso) return 'nunca';
  const diff = Math.max(0, Math.round((nowMs - new Date(iso).getTime()) / 1000));
  if (diff < 60) return `hace ${diff} s`;
  if (diff < 3600) return `hace ${Math.floor(diff / 60)} min`;
  if (diff < 86400) return `hace ${Math.floor(diff / 3600)} h`;
  return `hace ${Math.floor(diff / 86400)} d`;
}

export const roleLabel: Record<UserRole, string> = {
  SUPER_ADMIN: 'Super administrador',
  CONDO_ADMIN: 'Administrador',
  RESIDENT: 'Residente',
};

export const deviceTypeLabel: Record<DeviceType, string> = {
  VEHICLE_BARRIER: 'Portón vehicular',
  PEDESTRIAN_GATE: 'Puerta peatonal',
  LOBBY_DOOR: 'Puerta de lobby',
  INTERCOM_PANEL: 'Panel de citofonía',
  ELEVATOR_CONTROLLER: 'Control de ascensor',
};

export const methodLabel: Record<AccessMethod, string> = {
  REMOTE_APP: 'App GPS',
  QR_ONLINE: 'QR Visita (online)',
  QR_OFFLINE: 'QR Visita (offline)',
  INTERCOM_UNLOCK: 'Citofonía',
  ADMIN_OVERRIDE: 'Administrador',
  MANUAL_EXIT: 'Salida manual',
};

/** Method groups shown in the audit filter ("App GPS, QR Visita, Citofonía"). */
export const methodGroups: { key: string; label: string; methods: AccessMethod[] }[] = [
  { key: 'app', label: 'App GPS', methods: ['REMOTE_APP'] },
  { key: 'qr', label: 'QR Visita', methods: ['QR_ONLINE', 'QR_OFFLINE'] },
  { key: 'intercom', label: 'Citofonía', methods: ['INTERCOM_UNLOCK'] },
  { key: 'admin', label: 'Administrador', methods: ['ADMIN_OVERRIDE'] },
  { key: 'exit', label: 'Salida manual', methods: ['MANUAL_EXIT'] },
];

export const resultLabel: Record<AccessResult, string> = {
  GRANTED: 'Concedido',
  DENIED_INVALID_HMAC: 'Firma QR inválida',
  DENIED_EXPIRED: 'QR expirado',
  DENIED_REVOKED: 'QR revocado',
  DENIED_WRONG_CONDO: 'QR de otro condominio',
  DENIED_MAX_USES: 'QR sin usos disponibles',
  DENIED_DEVICE_OFFLINE: 'Dispositivo sin respuesta',
  DENIED_OUTSIDE_GEOFENCE: 'Fuera del geofence',
  DENIED_USER_BLOCKED: 'Usuario bloqueado',
  DENIED_UNIT_BLOCKED: 'Unidad bloqueada',
  DENIED_UNAUTHORIZED: 'No autorizado',
  ERROR: 'Error',
};

export type EffectiveDeviceState = 'ONLINE' | 'STALE' | 'OFFLINE' | 'MAINTENANCE' | 'FAULT' | 'DISABLED';

const STALE_AFTER_MS = 120_000; // 4 missed 30 s heartbeats

export function effectiveDeviceState(device: Device, nowMs: number): EffectiveDeviceState {
  if (!device.active) return 'DISABLED';
  if (device.status === 'MAINTENANCE' || device.status === 'FAULT') return device.status;
  if (device.status === 'OFFLINE') return 'OFFLINE';
  if (!device.lastSeenAt || nowMs - new Date(device.lastSeenAt).getTime() > STALE_AFTER_MS) return 'STALE';
  return 'ONLINE';
}

/** Gate left open: backend keeps last_gate_event + door_sensor_open in last_telemetry. */
export function hasGateAlert(device: Device): boolean {
  const t = device.lastTelemetry;
  return !!t && t['last_gate_event'] === 'GATE_OPEN_TIMEOUT' && t['door_sensor_open'] === true;
}

export function doorOpen(device: Device): boolean | null {
  const v = device.lastTelemetry?.['door_sensor_open'];
  return typeof v === 'boolean' ? v : null;
}

export function csvEscape(value: unknown): string {
  const s = value === null || value === undefined ? '' : String(value);
  return /[",\n\r;]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

export function downloadCsv(filename: string, rows: unknown[][]): void {
  const body = rows.map((r) => r.map(csvEscape).join(',')).join('\r\n');
  // BOM so Excel opens UTF-8 (tildes, ñ) correctly
  const blob = new Blob(['\ufeff', body], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
