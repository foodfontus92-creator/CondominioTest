import { FormEvent, useMemo, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { unitApi } from '../api/endpoints';
import type { Unit } from '../api/types';
import { Badge, EmptyState, ErrorBox, Field, Modal, PageHeader, Spinner } from '../components/ui';
import ReasonModal from '../components/ReasonModal';
import { useTenant, useUnits, useUsers } from '../context/TenantContext';
import { useToast } from '../context/ToastContext';
import { errMsg } from '../lib/hooks';

type StatusFilter = 'all' | 'blocked' | 'inactive';

export default function UnitsPage() {
  const { condoId } = useTenant();
  const units = useUnits();
  const users = useUsers();
  const toast = useToast();
  const queryClient = useQueryClient();

  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<StatusFilter>('all');
  const [editing, setEditing] = useState<Unit | 'new' | null>(null);
  const [blocking, setBlocking] = useState<Unit | null>(null);

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['units', condoId] });

  const residentsByUnit = useMemo(() => {
    const map = new Map<string, string[]>();
    for (const u of users.data ?? []) {
      if (u.unitId && u.role === 'RESIDENT') {
        map.set(u.unitId, [...(map.get(u.unitId) ?? []), u.fullName]);
      }
    }
    return map;
  }, [users.data]);

  const update = useMutation({
    mutationFn: ({ unit, body }: { unit: Unit; body: Parameters<typeof unitApi.update>[2] }) =>
      unitApi.update(condoId, unit.id, body),
    onSuccess: () => {
      void refresh();
      setBlocking(null);
    },
    onError: (e) => toast.push('error', errMsg(e)),
  });

  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (units.data ?? []).filter((u) => {
      if (filter === 'blocked' && !u.blocked) return false;
      if (filter === 'inactive' && u.active) return false;
      if (!q) return true;
      return (
        u.unitNumber.toLowerCase().includes(q) ||
        (u.towerOrBlock ?? '').toLowerCase().includes(q) ||
        (residentsByUnit.get(u.id) ?? []).some((n) => n.toLowerCase().includes(q))
      );
    });
  }, [units.data, search, filter, residentsByUnit]);

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
        title="Unidades"
        subtitle="Casas y departamentos del condominio. Bloquear una unidad impide apertura remota, pases QR y citofonía."
        actions={<button className="btn-primary" onClick={() => setEditing('new')}>+ Nueva unidad</button>}
      />

      <div className="card mb-4 flex flex-wrap items-center gap-3 p-4">
        <input className="input max-w-xs" placeholder="Buscar unidad, torre o residente…" value={search}
          onChange={(e) => setSearch(e.target.value)} />
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
        {units.data && rows.length === 0 && <EmptyState>No hay unidades que coincidan.</EmptyState>}
        {rows.length > 0 && (
          <table className="min-w-full divide-y divide-slate-200">
            <thead className="bg-slate-50">
              <tr>
                <th className="th">Unidad</th>
                <th className="th">Torre / Bloque</th>
                <th className="th">Piso</th>
                <th className="th">Ext. citofonía</th>
                <th className="th">Residentes</th>
                <th className="th">Estado</th>
                <th className="th text-right">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((u) => (
                <tr key={u.id} className={u.blocked ? 'bg-red-50/50' : ''}>
                  <td className="td font-semibold">{u.unitNumber}</td>
                  <td className="td">{u.towerOrBlock ?? '—'}</td>
                  <td className="td">{u.floor ?? '—'}</td>
                  <td className="td">{u.intercomExtension ?? '—'}</td>
                  <td className="td">{(residentsByUnit.get(u.id) ?? []).join(', ') || <span className="text-slate-400">Sin residentes</span>}</td>
                  <td className="td">
                    <div className="flex flex-wrap gap-1">
                      {!u.active && <Badge tone="slate">Baja</Badge>}
                      {u.blocked ? <Badge tone="red">Bloqueada</Badge> : u.active && <Badge tone="green">Activa</Badge>}
                    </div>
                    {u.blocked && u.blockReason && <div className="mt-1 text-xs text-red-700">{u.blockReason}</div>}
                  </td>
                  <td className="td">
                    <div className="flex justify-end gap-2">
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
              ))}
            </tbody>
          </table>
        )}
      </div>

      {editing && (
        <UnitFormModal
          unit={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            void refresh();
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
    </div>
  );
}

function UnitFormModal({ unit, onClose, onSaved }: { unit: Unit | null; onClose: () => void; onSaved: () => void }) {
  const { condoId } = useTenant();
  const toast = useToast();
  const [unitNumber, setUnitNumber] = useState(unit?.unitNumber ?? '');
  const [tower, setTower] = useState(unit?.towerOrBlock ?? '');
  const [floor, setFloor] = useState(unit?.floor ?? '');
  const [ext, setExt] = useState(unit?.intercomExtension ?? '');

  const save = useMutation({
    mutationFn: () =>
      unit
        ? unitApi.update(condoId, unit.id, {
            unitNumber: unitNumber.trim(),
            towerOrBlock: tower.trim(),
            floor: floor.trim(),
            intercomExtension: ext.trim(),
          })
        : unitApi.create(condoId, {
            unitNumber: unitNumber.trim(),
            towerOrBlock: tower.trim() || undefined,
            floor: floor.trim() || undefined,
            intercomExtension: ext.trim() || undefined,
          }),
    onSuccess: () => {
      toast.push('success', unit ? 'Unidad actualizada' : 'Unidad creada');
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
      title={unit ? `Editar unidad ${unit.unitNumber}` : 'Nueva unidad'}
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
        <Field label="Número / nombre de la unidad">
          <input className="input" value={unitNumber} onChange={(e) => setUnitNumber(e.target.value)} maxLength={32} required autoFocus />
        </Field>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Torre / bloque">
            <input className="input" value={tower} onChange={(e) => setTower(e.target.value)} maxLength={64} />
          </Field>
          <Field label="Piso">
            <input className="input" value={floor} onChange={(e) => setFloor(e.target.value)} maxLength={16} />
          </Field>
        </div>
        <Field label="Extensión de citofonía" hint="Única por condominio. Los paneles de puerta llaman a esta extensión.">
          <input className="input" value={ext} onChange={(e) => setExt(e.target.value)} maxLength={32} />
        </Field>
      </form>
    </Modal>
  );
}
