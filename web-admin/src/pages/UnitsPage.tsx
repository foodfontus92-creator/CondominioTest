import { FormEvent, useMemo, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { API_ORIGIN } from '../api/client';
import { unitApi } from '../api/endpoints';
import type { Unit, UnitActivationCodeResponse } from '../api/types';
import { Badge, EmptyState, ErrorBox, Field, Modal, PageHeader, Spinner } from '../components/ui';
import ReasonModal from '../components/ReasonModal';
import { useTenant, useUnits, useUsers } from '../context/TenantContext';
import { useToast } from '../context/ToastContext';
import { formatUnitLabel, useCommunityLabels } from '../lib/communityLabels';
import { formatDateTime } from '../lib/format';
import { errMsg } from '../lib/hooks';

type StatusFilter = 'all' | 'blocked' | 'inactive';

export default function UnitsPage() {
  const { condoId, condo } = useTenant();
  const labels = useCommunityLabels();
  const units = useUnits();
  const users = useUsers();
  const toast = useToast();
  const queryClient = useQueryClient();

  const [search, setSearch] = useState('');
  const [blockFilter, setBlockFilter] = useState('');
  const [filter, setFilter] = useState<StatusFilter>('all');
  const [editing, setEditing] = useState<Unit | 'new' | null>(null);
  const [creatingBatch, setCreatingBatch] = useState(false);
  const [blocking, setBlocking] = useState<Unit | null>(null);
  const [familyCodeUnit, setFamilyCodeUnit] = useState<Unit | null>(null);
  const [turnoverUnit, setTurnoverUnit] = useState<Unit | null>(null);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['units', condoId] });
    void queryClient.invalidateQueries({ queryKey: ['users-summary', condoId] });
  };

  const blockOptions = useMemo(() => {
    const names = new Set<string>();
    for (const u of units.data ?? []) {
      const b = (u.blockName ?? u.towerOrBlock ?? '').trim();
      if (b) names.add(b);
    }
    return Array.from(names).sort((a, b) => a.localeCompare(b, 'es'));
  }, [units.data]);

  const residentsByUnit = useMemo(() => {
    const map = new Map<string, string[]>();
    for (const u of users.data ?? []) {
      if (u.unitId && u.role === 'RESIDENT' && u.active) {
        map.set(u.unitId, [...(map.get(u.unitId) ?? []), u.fullName]);
      }
    }
    return map;
  }, [users.data]);

  const update = useMutation({
    mutationFn: ({ unit, body }: { unit: Unit; body: Parameters<typeof unitApi.update>[2] }) =>
      unitApi.update(condoId, unit.id, body),
    onSuccess: () => {
      refresh();
      setBlocking(null);
    },
    onError: (e) => toast.push('error', errMsg(e)),
  });

  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (units.data ?? []).filter((u) => {
      const block = (u.blockName ?? u.towerOrBlock ?? '').trim();
      if (blockFilter === '__NONE__' && block !== '') return false;
      if (blockFilter && blockFilter !== '__NONE__' && block !== blockFilter) return false;
      if (filter === 'blocked' && !u.blocked) return false;
      if (filter === 'inactive' && u.active) return false;
      if (!q) return true;
      return (
        u.unitNumber.toLowerCase().includes(q) ||
        block.toLowerCase().includes(q) ||
        (residentsByUnit.get(u.id) ?? []).some((n) => n.toLowerCase().includes(q))
      );
    });
  }, [units.data, search, blockFilter, filter, residentsByUnit]);

  const toggleBlock = (unit: Unit) => {
    if (unit.blocked) {
      update.mutate(
        { unit, body: { blocked: false } },
        { onSuccess: () => toast.push('success', `Unidad ${unit.unitNumber} desbloqueada`) },
      );
    } else {
      setBlocking(unit);
    }
  };

  const toggleActive = (unit: Unit) => {
    if (unit.active && !window.confirm(`¿Dar de baja la unidad ${unit.unitNumber}? Dejará de recibir llamadas y generar pases.`)) {
      return;
    }
    update.mutate(
      { unit, body: { active: !unit.active } },
      { onSuccess: () => toast.push('success', `Unidad ${unit.unitNumber} ${unit.active ? 'dada de baja' : 'reactivada'}`) },
    );
  };

  return (
    <div>
      <PageHeader
        title={labels.unitsTitle}
        subtitle={`Viviendas y blocks ${labels.ofCommunity}. Heredan automáticamente la dirección y GPS del recinto.`}
        actions={
          <div className="flex flex-wrap gap-2">
            <button className="btn-primary" onClick={() => setCreatingBatch(true)}>
              + Nuevo Block / Pasaje
            </button>
            <button className="btn-secondary" onClick={() => setEditing('new')}>
              {labels.newUnitBtn}
            </button>
          </div>
        }
      />

      <div className="card mb-4 flex flex-wrap items-center gap-3 p-4">
        <input className="input max-w-xs" placeholder={`Buscar ${labels.unitSingularLower}, block o ${labels.residentSingularLower}…`} value={search}
          onChange={(e) => setSearch(e.target.value)} />
        <select className="input !w-auto" value={blockFilter} onChange={(e) => setBlockFilter(e.target.value)}>
          <option value="">Todos los blocks / pasajes</option>
          {blockOptions.map((b) => <option key={b} value={b}>{b}</option>)}
          <option value="__NONE__">Sin block asignado</option>
        </select>
        <select className="input !w-auto" value={filter} onChange={(e) => setFilter(e.target.value as StatusFilter)}>
          <option value="all">Todas</option>
          <option value="blocked">Bloqueadas</option>
          <option value="inactive">Dadas de baja</option>
        </select>
        <span className="ml-auto text-sm text-slate-500">{rows.length} de {units.data?.length ?? 0}</span>
      </div>

      <div className="card overflow-x-auto">
        {units.isLoading && <Spinner />}
        {units.error && <ErrorBox error={units.error} />}
        {units.data && rows.length === 0 && <EmptyState>No hay {labels.unitsTitle.toLowerCase()} que coincidan.</EmptyState>}
        {rows.length > 0 && (
          <table className="min-w-full divide-y divide-slate-200">
            <thead className="bg-slate-50">
              <tr>
                <th className="th">Block / Pasaje</th>
                <th className="th">{labels.unitSingular}</th>
                <th className="th">Piso</th>
                <th className="th">Ext. citofonía</th>
                <th className="th">{labels.residentsNav}</th>
                <th className="th">Estado</th>
                <th className="th text-right">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((u) => {
                const block = (u.blockName ?? u.towerOrBlock ?? '').trim();
                return (
                <tr key={u.id} className={u.blocked ? 'bg-red-50/50' : ''}>
                  <td className="td">{block ? <Badge tone="slate">{block}</Badge> : <span className="text-slate-400">—</span>}</td>
                  <td className="td font-semibold">{u.unitNumber}</td>
                  <td className="td">{u.floor ?? '—'}</td>
                  <td className="td">{u.intercomExtension ?? '—'}</td>
                  <td className="td">{(residentsByUnit.get(u.id) ?? []).join(', ') || <span className="text-slate-400">Sin {labels.residentsNav.toLowerCase()}</span>}</td>
                  <td className="td">
                    <div className="flex flex-wrap gap-1">
                      {!u.active && <Badge tone="slate">Baja</Badge>}
                      {u.blocked ? <Badge tone="red">Bloqueada</Badge> : u.active && <Badge tone="green">Activa</Badge>}
                    </div>
                    {u.blocked && u.blockReason && <div className="mt-1 text-xs text-red-700">{u.blockReason}</div>}
                  </td>
                  <td className="td">
                    <div className="flex flex-wrap justify-end gap-2">
                      <button
                        className="btn-primary btn-sm"
                        disabled={!u.active}
                        title={!u.active ? 'Reactiva la unidad para generar códigos' : undefined}
                        onClick={() => setFamilyCodeUnit(u)}
                      >
                        Código familia
                      </button>
                      <button
                        className="btn-secondary btn-sm"
                        disabled={update.isPending}
                        onClick={() => setTurnoverUnit(u)}
                      >
                        Nueva familia
                      </button>
                      <button className={u.blocked ? 'btn-secondary btn-sm' : 'btn-warn btn-sm'}
                        disabled={update.isPending} onClick={() => toggleBlock(u)}>
                        {u.blocked ? 'Desbloquear' : 'Bloquear'}
                      </button>
                      <button className="btn-secondary btn-sm" onClick={() => setEditing(u)}>Editar</button>
                      <button className="btn-ghost btn-sm" disabled={update.isPending} onClick={() => toggleActive(u)}>
                        {u.active ? 'Dar de baja' : 'Reactivar'}
                      </button>
                    </div>
                  </td>
                </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      {creatingBatch && (
        <BatchBlockModal
          onClose={() => setCreatingBatch(false)}
          onCreated={() => {
            setCreatingBatch(false);
            refresh();
          }}
        />
      )}

      {editing && (
        <UnitFormModal
          unit={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            refresh();
          }}
        />
      )}

      {blocking && (
        <ReasonModal
          title={`Bloquear unidad ${blocking.unitNumber}`}
          description="La unidad no podrá abrir portones desde la app, emitir pases QR ni recibir llamadas de citofonía. Sus usuarios seguirán pudiendo iniciar sesión."
          confirmLabel="Bloquear unidad"
          busy={update.isPending}
          onCancel={() => setBlocking(null)}
          onConfirm={(reason) =>
            update.mutate(
              { unit: blocking, body: { blocked: true, blockReason: reason || 'Bloqueada por administración' } },
              { onSuccess: () => toast.push('success', `Unidad ${blocking.unitNumber} bloqueada`) },
            )
          }
        />
      )}

      {familyCodeUnit && (
        <FamilyCodeModal
          unit={familyCodeUnit}
          condoName={condo?.name ?? 'Condominio'}
          onClose={() => setFamilyCodeUnit(null)}
        />
      )}

      {turnoverUnit && (
        <TurnoverResidentsModal
          unit={turnoverUnit}
          onClose={() => setTurnoverUnit(null)}
          onCompleted={() => {
            setTurnoverUnit(null);
            void queryClient.invalidateQueries({ queryKey: ['users', condoId] });
            void queryClient.invalidateQueries({ queryKey: ['users-summary', condoId] });
            void refresh();
          }}
        />
      )}
    </div>
  );
}

function UnitFormModal({ unit, onClose, onSaved }: { unit: Unit | null; onClose: () => void; onSaved: () => void }) {
  const { condoId } = useTenant();
  const labels = useCommunityLabels();
  const toast = useToast();
  const [unitNumber, setUnitNumber] = useState(unit?.unitNumber ?? '');
  const [blockName, setBlockName] = useState(unit?.blockName ?? unit?.towerOrBlock ?? '');
  const [floor, setFloor] = useState(unit?.floor ?? '');
  const [ext, setExt] = useState(unit?.intercomExtension ?? '');

  const save = useMutation({
    mutationFn: () =>
      unit
        ? unitApi.update(condoId, unit.id, {
            unitNumber: unitNumber.trim(),
            blockName: blockName.trim(),
            towerOrBlock: blockName.trim(),
            floor: floor.trim(),
            intercomExtension: ext.trim(),
          })
        : unitApi.create(condoId, {
            unitNumber: unitNumber.trim(),
            blockName: blockName.trim() || undefined,
            towerOrBlock: blockName.trim() || undefined,
            floor: floor.trim() || undefined,
            intercomExtension: ext.trim() || undefined,
          }),
    onSuccess: () => {
      toast.push('success', unit ? `${labels.unitSingular} actualizada` : `${labels.unitSingular} creada`);
      onSaved();
    },
    onError: (e) => toast.push('error', errMsg(e)),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };

  return (
    <Modal
      title={unit ? `Editar ${formatUnitLabel(unit)}` : labels.newUnitBtn.replace(/^\+\s*/, '')}
      onClose={onClose}
      footer={
        <>
          <button className="btn-secondary" onClick={onClose}>Cancelar</button>
          <button className="btn-primary" form="unit-form" disabled={save.isPending}>
            {save.isPending ? 'Guardando…' : 'Guardar'}
          </button>
        </>
      }
    >
      <form id="unit-form" onSubmit={submit} className="space-y-4">
        <Field label="Block / Pasaje (opcional)" hint="ej. Block B, Torre 1, Pasaje Los Aromos">
          <input className="input" value={blockName} onChange={(e) => setBlockName(e.target.value)} maxLength={64} />
        </Field>
        <div className="grid grid-cols-2 gap-4">
          <Field label={`Número de ${labels.unitSingularLower}`}>
            <input className="input" value={unitNumber} onChange={(e) => setUnitNumber(e.target.value)} maxLength={32} required autoFocus />
          </Field>
          <Field label="Piso (opcional)">
            <input className="input" value={floor} onChange={(e) => setFloor(e.target.value)} maxLength={16} />
          </Field>
        </div>
        <Field label="Extensión de citofonía (opcional)" hint={`Única ${labels.ofCommunity}. Los paneles de puerta llaman a esta extensión.`}>
          <input className="input" value={ext} onChange={(e) => setExt(e.target.value)} maxLength={32} />
        </Field>
      </form>
    </Modal>
  );
}

function BatchBlockModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const { condoId, condo } = useTenant();
  const labels = useCommunityLabels();
  const toast = useToast();
  const [blockName, setBlockName] = useState('');
  const [rawNumbers, setRawNumbers] = useState('');

  const parsedNumbers = useMemo(() => {
    const seen = new Set<string>();
    const list: string[] = [];
    for (const part of rawNumbers.split(/[\s,;]+/)) {
      const cleaned = part.trim();
      if (cleaned && !seen.has(cleaned.toLowerCase())) {
        seen.add(cleaned.toLowerCase());
        list.push(cleaned);
      }
    }
    return list;
  }, [rawNumbers]);

  const createBatch = useMutation({
    mutationFn: () => unitApi.createBatch(condoId, { blockName: blockName.trim(), unitNumbers: parsedNumbers }),
    onSuccess: (res) => {
      toast.push('success', `${res.blockName}: ${res.createdCount} vivienda(s) creadas`);
      onCreated();
    },
    onError: (e) => toast.push('error', errMsg(e)),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!blockName.trim()) {
      toast.push('error', 'Ingresa el nombre del Block / Pasaje');
      return;
    }
    if (parsedNumbers.length === 0) {
      toast.push('error', 'Ingresa al menos un número de departamento o casa');
      return;
    }
    createBatch.mutate();
  };

  return (
    <Modal
      title="Nuevo Block / Pasaje"
      onClose={onClose}
      footer={
        <>
          <button className="btn-secondary" onClick={onClose}>Cancelar</button>
          <button
            className="btn-primary"
            form="batch-block-form"
            disabled={createBatch.isPending || parsedNumbers.length === 0 || !blockName.trim()}
          >
            {createBatch.isPending ? 'Creando…' : `Crear ${parsedNumbers.length || ''} ${labels.unitsTitle.toLowerCase()}`}
          </button>
        </>
      }
    >
      <form id="batch-block-form" onSubmit={submit} className="space-y-4">
        <p className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600">
          Solo necesitas el nombre del Block y los números de vivienda: la dirección
          {condo?.address ? ` (${condo.address})` : ''} y ubicación GPS del recinto aplican automáticamente.
        </p>
        <Field label="Nombre del Block / Pasaje">
          <input
            className="input"
            value={blockName}
            onChange={(e) => setBlockName(e.target.value)}
            placeholder="ej. Block C"
            maxLength={64}
            required
            autoFocus
          />
        </Field>
        <Field
          label="Números de departamentos / casas"
          hint={`Separados por coma, espacio o salto de línea (${parsedNumbers.length} detectados).`}
        >
          <textarea
            className="input min-h-[110px]"
            value={rawNumbers}
            onChange={(e) => setRawNumbers(e.target.value)}
            placeholder="ej. 101, 102, 201, 202, 301, 302"
            required
          />
        </Field>
      </form>
    </Modal>
  );
}

function FamilyCodeModal({
  unit,
  condoName,
  onClose,
}: {
  unit: Unit;
  condoName: string;
  onClose: () => void;
}) {
  const { condoId } = useTenant();
  const labels = useCommunityLabels();
  const toast = useToast();
  const [maxUses, setMaxUses] = useState(4);
  const [expiresInHours, setExpiresInHours] = useState(48);
  const [generated, setGenerated] = useState<UnitActivationCodeResponse | null>(null);

  const createCode = useMutation({
    mutationFn: () =>
      unitApi.createActivationCode(condoId, unit.id, {
        maxUses,
        expiresInHours,
        revokePrevious: true,
      }),
    onSuccess: (res) => {
      setGenerated(res);
      toast.push('success', `Código familiar ${res.code} generado`);
    },
    onError: (e) => toast.push('error', errMsg(e)),
  });

  const copyWhatsAppInvite = async () => {
    if (!generated) return;
    const serverOrigin = API_ORIGIN || window.location.origin;
    const message = [
      `¡Hola! Bienvenidos a *${condoName}* (${labels.unitSingular} *${formatUnitLabel(unit)}*).`,
      '',
      'Para activar el acceso autónomo en tu teléfono:',
      '1. Abre la app *AccesoLibre* en Android.',
      `2. En servidor local ingresa: ${serverOrigin}`,
      `3. Toca *"${labels.activateAppBtnText}"* y completa tus datos.`,
      '',
      `🔑 Código familiar: *${generated.code}*`,
      `👥 Cupos disponibles: ${generated.maxUses} integrantes`,
      `⏳ Vigente hasta: ${formatDateTime(generated.expiresAt)}`,
    ].join('\n');

    try {
      await navigator.clipboard.writeText(message);
      toast.push('success', 'Invitación de WhatsApp copiada al portapapeles');
    } catch {
      toast.push('error', 'No se pudo acceder al portapapeles del navegador');
    }
  };

  return (
    <Modal
      title={`Código familia · Unidad ${unit.unitNumber}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn-secondary" onClick={onClose}>Cerrar</button>
          {!generated ? (
            <button
              className="btn-primary"
              disabled={createCode.isPending}
              onClick={() => createCode.mutate()}
            >
              {createCode.isPending ? 'Generando…' : 'Generar código'}
            </button>
          ) : (
            <button className="btn-primary" onClick={() => void copyWhatsAppInvite()}>
              Copiar invitación para WhatsApp
            </button>
          )}
        </>
      }
    >
      {!generated ? (
        <div className="space-y-4">
          <p className="text-sm text-slate-600">
            Genera un código corto para que los integrantes de la unidad se registren solos desde la app Android.
            Los códigos anteriores de esta unidad se desactivarán automáticamente.
          </p>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Límite de integrantes">
              <select
                className="input"
                value={maxUses}
                onChange={(e) => setMaxUses(Number(e.target.value))}
              >
                {Array.from({ length: 10 }, (_, idx) => idx + 1).map((n) => (
                  <option key={n} value={n}>
                    {n} {n === 1 ? 'integrante' : 'integrantes'}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Vigencia">
              <select
                className="input"
                value={expiresInHours}
                onChange={(e) => setExpiresInHours(Number(e.target.value))}
              >
                <option value={24}>24 horas</option>
                <option value={48}>48 horas</option>
                <option value={168}>7 días</option>
              </select>
            </Field>
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          <div className="rounded-xl border border-brand-200 bg-brand-50 p-4 text-center">
            <div className="text-xs font-semibold uppercase tracking-wide text-brand-700">
              Código de activación
            </div>
            <div className="mt-1 select-all font-mono text-2xl font-bold tracking-wider text-brand-900">
              {generated.code}
            </div>
          </div>
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <dt className="text-slate-500">Unidad</dt>
            <dd className="font-medium">{generated.unitNumber}</dd>
            <dt className="text-slate-500">Integrantes permitidos</dt>
            <dd className="font-medium">{generated.maxUses}</dd>
            <dt className="text-slate-500">Válido hasta</dt>
            <dd className="font-medium">{formatDateTime(generated.expiresAt)}</dd>
          </dl>
        </div>
      )}
    </Modal>
  );
}

function TurnoverResidentsModal({
  unit,
  onClose,
  onCompleted,
}: {
  unit: Unit;
  onClose: () => void;
  onCompleted: () => void;
}) {
  const { condoId } = useTenant();
  const toast = useToast();

  const revoke = useMutation({
    mutationFn: () => unitApi.revokeResidents(condoId, unit.id),
    onSuccess: (res) => {
      toast.push(
        'success',
        `Unidad ${res.unitNumber}: ${res.deactivatedResidents} residente(s) dado(s) de baja y ${res.invalidatedCodes} código(s) invalidado(s)`,
      );
      onCompleted();
    },
    onError: (e) => toast.push('error', errMsg(e)),
  });

  return (
    <Modal
      title={`Nueva familia · Unidad ${unit.unitNumber}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn-secondary" onClick={onClose}>Cancelar</button>
          <button
            className="btn-danger"
            disabled={revoke.isPending}
            onClick={() => revoke.mutate()}
          >
            {revoke.isPending ? 'Procesando…' : 'Confirmar baja masiva'}
          </button>
        </>
      }
    >
      <p className="text-sm text-slate-600">
        Esta acción desactivará todas las cuentas de residentes actuales de la unidad{' '}
        <strong>{unit.unitNumber}</strong>, revocará sus sesiones activas e invalidará cualquier código de activación
        vigente antes de entregar el departamento a una nueva familia.
      </p>
    </Modal>
  );
}
