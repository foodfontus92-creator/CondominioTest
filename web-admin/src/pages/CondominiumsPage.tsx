import { FormEvent, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { condoApi } from '../api/endpoints';
import type { CommunityType, Condominium } from '../api/types';
import { Badge, ErrorBox, Field, Modal, PageHeader, Spinner } from '../components/ui';
import { useTenant } from '../context/TenantContext';
import { useToast } from '../context/ToastContext';
import {
  COMMUNITY_TYPE_OPTIONS,
  communityTypeLabel,
  getCommunityLabels,
  useCommunityLabels,
} from '../lib/communityLabels';
import { formatDateTime } from '../lib/format';
import { errMsg } from '../lib/hooks';

export default function CondominiumsPage() {
  const { condos, condo, isSuperAdmin, condoId, setCondoId } = useTenant();
  const labels = useCommunityLabels();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<Condominium | 'new' | null>(null);

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['condos'] });

  const rotate = useMutation({
    mutationFn: (c: Condominium) => condoApi.rotateHmac(c.id),
    onSuccess: (c) => {
      void refresh();
      void queryClient.invalidateQueries({ queryKey: ['units', c.id] });
      toast.push('success', `Clave HMAC rotada a v${c.hmacKeyVersion} y enviada a los dispositivos activos`);
    },
    onError: (e) => toast.push('error', errMsg(e)),
  });

  const confirmRotate = (c: Condominium) => {
    const targetLabels = getCommunityLabels(c.communityType);
    if (window.confirm(
      `¿Rotar la clave HMAC de "${c.name}"?\n\nSe regenerará el secreto de TODAS las ${targetLabels.unitsTitle.toLowerCase()}: los QR emitidos hasta ahora dejarán de ser válidos y los ${targetLabels.residentsTitle.toLowerCase()} deberán reabrir la app para descargar su nueva clave.`,
    )) rotate.mutate(c);
  };

  return (
    <div>
      <PageHeader
        title={isSuperAdmin ? 'Comunidades y Recintos' : labels.myCommunity}
        subtitle={`Datos ${labels.ofCommunity}, geofencing de la apertura remota y clave criptográfica de los QR.`}
        actions={isSuperAdmin && <button className="btn-primary" onClick={() => setEditing('new')}>{labels.newCommunityBtn}</button>}
      />

      {condos.length === 0 && <Spinner />}
      <div className="grid gap-4 lg:grid-cols-2">
        {condos.map((c) => (
          <div key={c.id} className={`card p-5 ${c.id === condoId ? 'ring-2 ring-brand-200' : ''}`}>
            <div className="flex items-start justify-between">
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="text-lg font-semibold text-slate-900">{c.name}</h3>
                  <Badge tone="blue">{communityTypeLabel(c.communityType)}</Badge>
                </div>
                <p className="text-xs text-slate-500">código: <code>{c.code}</code></p>
              </div>
              <Badge tone={c.active ? 'green' : 'slate'}>{c.active ? 'Activo' : 'Inactivo'}</Badge>
            </div>
            <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
              <dt className="text-slate-500">Tipo de recinto</dt><dd>{communityTypeLabel(c.communityType)}</dd>
              <dt className="text-slate-500">Dirección</dt><dd>{c.address ?? '—'}</dd>
              <dt className="text-slate-500">Zona horaria</dt><dd>{c.timezone}</dd>
              <dt className="text-slate-500">Coordenadas GPS</dt>
              <dd>{c.latitude !== null && c.longitude !== null ? `${c.latitude}, ${c.longitude}` : <Badge tone="amber">Sin configurar</Badge>}</dd>
              <dt className="text-slate-500">Radio geofence</dt><dd>{c.geofenceRadiusMeters} m</dd>
              <dt className="text-slate-500">Clave HMAC</dt><dd>v{c.hmacKeyVersion}</dd>
              <dt className="text-slate-500">Creado</dt><dd>{formatDateTime(c.createdAt)}</dd>
            </dl>
            <div className="mt-4 flex flex-wrap gap-2">
              <button className="btn-secondary btn-sm" onClick={() => setEditing(c)}>Editar / geofence</button>
              <button className="btn-warn btn-sm" disabled={rotate.isPending} onClick={() => confirmRotate(c)}>Rotar clave HMAC</button>
              {isSuperAdmin && c.id !== condoId && (
                <button className="btn-ghost btn-sm" onClick={() => setCondoId(c.id)}>Administrar este recinto</button>
              )}
            </div>
          </div>
        ))}
      </div>

      {editing && (
        <CondoFormModal
          condo={editing === 'new' ? null : editing}
          templateCondo={editing === 'new' ? condo : undefined}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            void refresh();
          }}
        />
      )}
      {rotate.error && <div className="mt-4"><ErrorBox error={rotate.error} /></div>}
    </div>
  );
}

function CondoFormModal({
  condo,
  templateCondo,
  onClose,
  onSaved,
}: {
  condo: Condominium | null;
  templateCondo?: Condominium;
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const [code, setCode] = useState(condo?.code ?? '');
  const [name, setName] = useState(condo?.name ?? '');
  const [communityType, setCommunityType] = useState<CommunityType>(
    condo?.communityType ?? templateCondo?.communityType ?? 'CONDOMINIUM',
  );
  const [address, setAddress] = useState(condo?.address ?? templateCondo?.address ?? '');
  const [timezone, setTimezone] = useState(
    condo?.timezone ?? templateCondo?.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone,
  );
  const [lat, setLat] = useState(
    condo?.latitude?.toString() ?? templateCondo?.latitude?.toString() ?? '',
  );
  const [lon, setLon] = useState(
    condo?.longitude?.toString() ?? templateCondo?.longitude?.toString() ?? '',
  );
  const [radius, setRadius] = useState(
    String(condo?.geofenceRadiusMeters ?? templateCondo?.geofenceRadiusMeters ?? 150),
  );
  const [active, setActive] = useState(condo?.active ?? true);

  const formLabels = getCommunityLabels(communityType);
  const num = (v: string) => (v.trim() === '' ? undefined : Number(v));

  const save = useMutation({
    mutationFn: () =>
      condo
        ? condoApi.update(condo.id, {
            name: name.trim(),
            communityType,
            address: address.trim(),
            timezone: timezone.trim(),
            latitude: num(lat),
            longitude: num(lon),
            geofenceRadiusMeters: Number(radius),
            active,
          })
        : condoApi.create({
            code: code.trim().toLowerCase(),
            name: name.trim(),
            communityType,
            address: address.trim() || undefined,
            timezone: timezone.trim() || undefined,
            latitude: num(lat),
            longitude: num(lon),
            geofenceRadiusMeters: Number(radius),
          }),
    onSuccess: () => {
      toast.push(
        'success',
        condo
          ? `${formLabels.communityName} actualizado`
          : `${formLabels.communityName} creado. Crea ahora su primer administrador en ${formLabels.residentsNav}.`,
      );
      onSaved();
    },
    onError: (e) => toast.push('error', errMsg(e)),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const la = num(lat);
    const lo = num(lon);
    if ((la === undefined) !== (lo === undefined)) {
      toast.push('error', 'Ingresa latitud y longitud juntas');
      return;
    }
    save.mutate();
  };

  return (
    <Modal
      wide
      title={condo ? `Editar ${condo.name}` : formLabels.newCommunityBtn.replace(/^\+\s*/, '')}
      onClose={onClose}
      footer={
        <>
          <button className="btn-secondary" onClick={onClose}>Cancelar</button>
          <button className="btn-primary" form="condo-form" disabled={save.isPending}>
            {save.isPending ? 'Guardando…' : 'Guardar'}
          </button>
        </>
      }
    >
      <form id="condo-form" onSubmit={submit} className="space-y-4">
        <Field label="Tipo de comunidad / recinto">
          <select className="input" value={communityType} onChange={(e) => setCommunityType(e.target.value as CommunityType)}>
            {COMMUNITY_TYPE_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
        </Field>
        {!condo && (
          <Field label={formLabels.codeLabel} hint="Minúsculas, números, - y _. Se usa para iniciar sesión.">
            <input className="input" value={code} onChange={(e) => setCode(e.target.value)} pattern="^[a-z0-9\-_]+$" minLength={2} maxLength={32} required />
          </Field>
        )}
        <Field label="Nombre">
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} maxLength={150} required />
        </Field>
        <Field label="Dirección">
          <input className="input" value={address} onChange={(e) => setAddress(e.target.value)} maxLength={255} />
        </Field>
        <Field label="Zona horaria">
          <input className="input" value={timezone} onChange={(e) => setTimezone(e.target.value)} maxLength={64} />
        </Field>
        <div className="grid grid-cols-3 gap-4">
          <Field label="Latitud">
            <input className="input" type="number" step="any" min={-90} max={90} value={lat} onChange={(e) => setLat(e.target.value)} />
          </Field>
          <Field label="Longitud">
            <input className="input" type="number" step="any" min={-180} max={180} value={lon} onChange={(e) => setLon(e.target.value)} />
          </Field>
          <Field label="Radio (m)" hint="10 – 5000">
            <input className="input" type="number" min={10} max={5000} value={radius} onChange={(e) => setRadius(e.target.value)} required />
          </Field>
        </div>
        <p className="text-xs text-slate-500">
          La app valida la ubicación GPS del {formLabels.residentSingularLower} contra estas coordenadas y este radio antes de abrir portones.
          Obtén las coordenadas desde un mapa (clic derecho sobre la entrada {formLabels.ofCommunity}).
        </p>
        {condo && (
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} /> {formLabels.activeCheckboxLabel}
          </label>
        )}
      </form>
    </Modal>
  );
}
