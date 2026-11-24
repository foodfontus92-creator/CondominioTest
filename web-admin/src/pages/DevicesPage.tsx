import { FormEvent, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { deviceApi } from '../api/endpoints';
import type { Device, DeviceType } from '../api/types';
import { Badge, EmptyState, ErrorBox, Field, Modal, PageHeader, Spinner } from '../components/ui';
import { useDevices, useTenant } from '../context/TenantContext';
import { useToast } from '../context/ToastContext';
import {
  deviceTypeLabel,
  doorOpen,
  effectiveDeviceState,
  EffectiveDeviceState,
  formatDateTime,
  hasGateAlert,
  relativeTime,
} from '../lib/format';
import { errMsg, useNow } from '../lib/hooks';

const stateBadge: Record<EffectiveDeviceState, { tone: 'green' | 'red' | 'amber' | 'slate'; label: string }> = {
  ONLINE: { tone: 'green', label: 'Online' },
  STALE: { tone: 'amber', label: 'Sin señal reciente' },
  OFFLINE: { tone: 'red', label: 'Offline' },
  MAINTENANCE: { tone: 'amber', label: 'Mantenimiento' },
  FAULT: { tone: 'red', label: 'Falla' },
  DISABLED: { tone: 'slate', label: 'Deshabilitado' },
};

const deviceTypes = Object.keys(deviceTypeLabel) as DeviceType[];

export default function DevicesPage() {
  const { condoId, condo } = useTenant();
  const devices = useDevices();
  const toast = useToast();
  const queryClient = useQueryClient();
  const now = useNow();

  const [editing, setEditing] = useState<Device | 'new' | null>(null);
  const [provisioning, setProvisioning] = useState<Device | null>(null);

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['devices', condoId] });

  const sync = useMutation({
    mutationFn: (d: Device) => deviceApi.syncConfig(condoId, d.id),
    onSuccess: (_r, d) => toast.push('success', `Configuración HMAC enviada a ${d.name} por MQTT`),
    onError: (e) => toast.push('error', errMsg(e)),
  });

  const toggleActive = useMutation({
    mutationFn: (d: Device) => deviceApi.update(condoId, d.id, { active: !d.active }),
    onSuccess: () => void refresh(),
    onError: (e) => toast.push('error', errMsg(e)),
  });

  const list = devices.data ?? [];
  const online = list.filter((d) => effectiveDeviceState(d, now) === 'ONLINE').length;
  const alerts = list.filter(hasGateAlert).length;

  return (
    <div>
      <PageHeader
        title="Dispositivos IoT"
        subtitle="Controladores ESP32 de portones y puertas. El estado se actualiza en tiempo real por MQTT/STOMP."
        actions={<button className="btn-primary" onClick={() => setEditing('new')}>+ Alta de dispositivo</button>}
      />

      <div className="mb-4 grid grid-cols-2 gap-3 md:grid-cols-4">
        <Stat label="Dispositivos" value={list.length} />
        <Stat label="Online" value={online} tone="green" />
        <Stat label="Offline / sin señal" value={list.filter((d) => d.active).length - online} tone="red" />
        <Stat label="Alertas de portón" value={alerts} tone={alerts > 0 ? 'red' : undefined} />
      </div>

      {devices.isLoading && <Spinner />}
      {devices.error && <ErrorBox error={devices.error} />}
      {devices.data && list.length === 0 && (
        <div className="card"><EmptyState>No hay dispositivos registrados. Da de alta el primer controlador ESP32.</EmptyState></div>
      )}

      <div className="grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
        {list.map((d) => {
          const state = effectiveDeviceState(d, now);
          const badge = stateBadge[state];
          const alert = hasGateAlert(d);
          const open = doorOpen(d);
          const hmacOutdated = condo && d.hmacKeyVersionSynced !== condo.hmacKeyVersion;
          return (
            <div key={d.id} className={`card p-5 ${alert ? 'border-red-400 ring-2 ring-red-200' : ''}`}>
              <div className="flex items-start justify-between gap-2">
                <div>
                  <h3 className="font-semibold text-slate-900">{d.name}</h3>
                  <p className="text-xs text-slate-500">{deviceTypeLabel[d.deviceType]}{d.locationDescription ? ` · ${d.locationDescription}` : ''}</p>
                </div>
                <Badge tone={badge.tone}>{badge.label}</Badge>
              </div>

              {alert && (
                <div className="mt-3 rounded-lg bg-red-100 px-3 py-2 text-sm font-semibold text-red-800">
                  ⚠ Portón abierto más de 45 s
                </div>
              )}

              <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                <dt className="text-slate-500">Visto por última vez</dt>
                <dd title={formatDateTime(d.lastSeenAt)}>{relativeTime(d.lastSeenAt, now)}</dd>
                <dt className="text-slate-500">Sensor de puerta</dt>
                <dd>{open === null ? '—' : open ? <Badge tone="amber">Abierta</Badge> : <Badge tone="green">Cerrada</Badge>}</dd>
                <dt className="text-slate-500">Firmware</dt>
                <dd>{d.firmwareVersion ?? '—'}</dd>
                <dt className="text-slate-500">IP</dt>
                <dd>{d.ipAddress ?? '—'}</dd>
                <dt className="text-slate-500">Clave HMAC</dt>
                <dd>
                  v{d.hmacKeyVersionSynced}
                  {condo && <span className="text-slate-400"> / v{condo.hmacKeyVersion}</span>}
                  {hmacOutdated && <span className="ml-1"><Badge tone="amber">Desactualizada</Badge></span>}
                </dd>
              </dl>

              <div className="mt-4 flex flex-wrap gap-2">
                <button className="btn-secondary btn-sm" disabled={sync.isPending} onClick={() => sync.mutate(d)}>
                  Sincronizar configuración
                </button>
                <button className="btn-secondary btn-sm" onClick={() => setProvisioning(d)}>Datos de aprovisionamiento</button>
                <button className="btn-secondary btn-sm" onClick={() => setEditing(d)}>Editar</button>
                <button className="btn-ghost btn-sm" disabled={toggleActive.isPending} onClick={() => toggleActive.mutate(d)}>
                  {d.active ? 'Deshabilitar' : 'Habilitar'}
                </button>
              </div>
            </div>
          );
        })}
      </div>

      {editing && (
        <DeviceFormModal
          device={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(created) => {
            setEditing(null);
            void refresh();
            if (created) setProvisioning(created);
          }}
        />
      )}
      {provisioning && <ProvisioningModal device={provisioning} onClose={() => setProvisioning(null)} />}
    </div>
  );
}

function Stat({ label, value, tone }: { label: string; value: number; tone?: 'green' | 'red' }) {
  const color = tone === 'green' ? 'text-emerald-600' : tone === 'red' ? 'text-red-600' : 'text-slate-900';
  return (
    <div className="card p-4">
      <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</div>
      <div className={`mt-1 text-3xl font-bold ${color}`}>{value}</div>
    </div>
  );
}

function DeviceFormModal({
  device,
  onClose,
  onSaved,
}: {
  device: Device | null;
  onClose: () => void;
  onSaved: (created: Device | null) => void;
}) {
  const { condoId } = useTenant();
  const toast = useToast();
  const [hardwareUid, setHardwareUid] = useState('');
  const [name, setName] = useState(device?.name ?? '');
  const [type, setType] = useState<DeviceType>(device?.deviceType ?? 'VEHICLE_BARRIER');
  const [location, setLocation] = useState(device?.locationDescription ?? '');
  const [mqttUser, setMqttUser] = useState('');
  const [pulse, setPulse] = useState(String(device?.relayPulseMs ?? 1000));

  const save = useMutation({
    mutationFn: () =>
      device
        ? deviceApi.update(condoId, device.id, {
            name: name.trim(),
            deviceType: type,
            locationDescription: location.trim(),
            relayPulseMs: Number(pulse),
          })
        : deviceApi.create(condoId, {
            hardwareUid: hardwareUid.trim(),
            name: name.trim(),
            deviceType: type,
            locationDescription: location.trim() || undefined,
            mqttUsername: mqttUser.trim() || `esp32-${hardwareUid.trim().toLowerCase()}`,
            relayPulseMs: Number(pulse),
          }),
    onSuccess: (saved) => {
      toast.push('success', device ? 'Dispositivo actualizado' : 'Dispositivo registrado');
      onSaved(device ? null : saved);
    },
    onError: (e) => toast.push('error', errMsg(e)),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };

  return (
    <Modal
      title={device ? `Editar ${device.name}` : 'Alta de controlador ESP32'}
      onClose={onClose}
      footer={
        <>
          <button className="btn-secondary" onClick={onClose}>Cancelar</button>
          <button className="btn-primary" form="device-form" disabled={save.isPending}>
            {save.isPending ? 'Guardando…' : 'Guardar'}
          </button>
        </>
      }
    >
      <form id="device-form" onSubmit={submit} className="space-y-4">
        {!device && (
          <Field label="ID de hardware (MAC / chip ID)" hint="Único en toda la plataforma. Ej. 24:6F:28:AB:CD:EF">
            <input className="input" value={hardwareUid} onChange={(e) => setHardwareUid(e.target.value)} maxLength={64} required autoFocus />
          </Field>
        )}
        <Field label="Nombre">
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} maxLength={120} required placeholder="Portón principal" />
        </Field>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Tipo">
            <select className="input" value={type} onChange={(e) => setType(e.target.value as DeviceType)}>
              {deviceTypes.map((t) => <option key={t} value={t}>{deviceTypeLabel[t]}</option>)}
            </select>
          </Field>
          <Field label="Pulso del relé (ms)" hint="200 – 30000">
            <input className="input" type="number" min={200} max={30000} value={pulse} onChange={(e) => setPulse(e.target.value)} required />
          </Field>
        </div>
        <Field label="Ubicación">
          <input className="input" value={location} onChange={(e) => setLocation(e.target.value)} maxLength={255} placeholder="Acceso norte" />
        </Field>
        {!device && (
          <Field label="Usuario MQTT" hint="Vacío = esp32-<id de hardware>. Debe ser único.">
            <input className="input" value={mqttUser} onChange={(e) => setMqttUser(e.target.value)} maxLength={128} />
          </Field>
        )}
      </form>
    </Modal>
  );
}

function ProvisioningModal({ device, onClose }: { device: Device; onClose: () => void }) {
  const rows: [string, string][] = [
    ['CONDO_ID', device.condominiumId],
    ['DEVICE_ID', device.id],
    ['MQTT_USER (broker)', device.mqttUsername],
    ['Token QR de citofonía', device.publicQrToken],
  ];
  return (
    <Modal title={`Aprovisionamiento · ${device.name}`} onClose={onClose} wide
      footer={<button className="btn-primary" onClick={onClose}>Cerrar</button>}>
      <p className="mb-4 text-sm text-slate-600">
        Copia estos valores en <code className="rounded bg-slate-100 px-1">firmware-esp32/include/secrets.h</code>.
        El secreto HMAC del condominio no se muestra: se entrega al dispositivo por MQTT con
        «Sincronizar configuración».
      </p>
      <dl className="space-y-3">
        {rows.map(([k, v]) => (
          <div key={k}>
            <dt className="label">{k}</dt>
            <dd className="flex items-center gap-2">
              <code className="block flex-1 break-all rounded bg-slate-100 px-3 py-2 text-xs">{v}</code>
              <button className="btn-secondary btn-sm" onClick={() => void navigator.clipboard?.writeText(v)}>Copiar</button>
            </dd>
          </div>
        ))}
      </dl>
    </Modal>
  );
}
