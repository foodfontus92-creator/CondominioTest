import { FormEvent, useMemo, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { userApi } from '../api/endpoints';
import type { AppUser, UserRole } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Badge, EmptyState, ErrorBox, Field, Modal, PageHeader, Spinner } from '../components/ui';
import ReasonModal from '../components/ReasonModal';
import { useTenant, useUnits, useUsers } from '../context/TenantContext';
import { useToast } from '../context/ToastContext';
import { formatDateTime, roleLabel } from '../lib/format';
import { errMsg } from '../lib/hooks';

export default function ResidentsPage() {
  const { condoId } = useTenant();
  const { user: currentUser } = useAuth();
  const users = useUsers();
  const units = useUnits();
  const toast = useToast();
  const queryClient = useQueryClient();

  const [search, setSearch] = useState('');
  const [unitFilter, setUnitFilter] = useState('');
  const [roleFilter, setRoleFilter] = useState<'' | UserRole>('');
  const [editing, setEditing] = useState<AppUser | 'new' | null>(null);
  const [blocking, setBlocking] = useState<AppUser | null>(null);

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['users', condoId] });
  const unitName = useMemo(() => new Map((units.data ?? []).map((u) => [u.id, u.unitNumber])), [units.data]);

  const update = useMutation({
    mutationFn: ({ user, body }: { user: AppUser; body: Parameters<typeof userApi.update>[2] }) =>
      userApi.update(condoId, user.id, body),
    onSuccess: () => {
      void refresh();
      setBlocking(null);
    },
    onError: (e) => toast.push('error', errMsg(e)),
  });

  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (users.data ?? []).filter((u) => {
      if (unitFilter && u.unitId !== unitFilter) return false;
      if (roleFilter && u.role !== roleFilter) return false;
      if (!q) return true;
      return u.fullName.toLowerCase().includes(q) || u.email.toLowerCase().includes(q);
    });
  }, [users.data, search, unitFilter, roleFilter]);

  const toggleBlock = (u: AppUser) => {
    if (u.blocked) {
      update.mutate({ user: u, body: { blocked: false } }, { onSuccess: () => toast.push('success', `${u.fullName} desbloqueado`) });
    } else {
      setBlocking(u);
    }
  };

  const toggleActive = (u: AppUser) => {
    if (u.active && !window.confirm(`¿Desactivar la cuenta de ${u.fullName}? Se cerrarán sus sesiones.`)) return;
    update.mutate({ user: u, body: { active: !u.active } },
      { onSuccess: () => toast.push('success', `Cuenta ${u.active ? 'desactivada' : 'reactivada'}`) });
  };

  return (
    <div>
      <PageHeader
        title="Residentes"
        subtitle="Usuarios del condominio y su unidad asignada."
        actions={<button className="btn-primary" onClick={() => setEditing('new')}>+ Nuevo usuario</button>}
      />

      <div className="card mb-4 flex flex-wrap items-center gap-3 p-4">
        <input className="input max-w-xs" placeholder="Buscar nombre o correo…" value={search} onChange={(e) => setSearch(e.target.value)} />
        <select className="input !w-auto" value={unitFilter} onChange={(e) => setUnitFilter(e.target.value)}>
          <option value="">Todas las unidades</option>
          {(units.data ?? []).map((u) => <option key={u.id} value={u.id}>{u.unitNumber}</option>)}
        </select>
        <select className="input !w-auto" value={roleFilter} onChange={(e) => setRoleFilter(e.target.value as '' | UserRole)}>
          <option value="">Todos los roles</option>
          <option value="RESIDENT">Residentes</option>
          <option value="CONDO_ADMIN">Administradores</option>
          <option value="SUPER_ADMIN">Super administradores</option>
        </select>
        <span className="ml-auto text-sm text-slate-500">{rows.length} de {users.data?.length ?? 0}</span>
      </div>

      <div className="card overflow-x-auto">
        {users.isLoading && <Spinner />}
        {users.error && <ErrorBox error={users.error} />}
        {users.data && rows.length === 0 && <EmptyState>No hay usuarios que coincidan.</EmptyState>}
        {rows.length > 0 && (
          <table className="min-w-full divide-y divide-slate-200">
            <thead className="bg-slate-50">
              <tr>
                <th className="th">Nombre</th>
                <th className="th">Correo</th>
                <th className="th">Rol</th>
                <th className="th">Unidad</th>
                <th className="th">Último acceso</th>
                <th className="th">Estado</th>
                <th className="th text-right">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((u) => {
                const isSelf = u.id === currentUser?.id;
                return (
                <tr key={u.id} className={u.blocked ? 'bg-red-50/50' : ''}>
                  <td className="td font-semibold">
                    <div className="flex items-center gap-2">
                      <span>{u.fullName}</span>
                      {isSelf && <Badge tone="slate">Tú</Badge>}
                    </div>
                    {u.phone && <div className="text-xs font-normal text-slate-500">{u.phone}</div>}
                  </td>
                  <td className="td">{u.email}</td>
                  <td className="td"><Badge tone={u.role === 'RESIDENT' ? 'slate' : 'blue'}>{roleLabel[u.role]}</Badge></td>
                  <td className="td">{u.unitId ? unitName.get(u.unitId) ?? '—' : '—'}</td>
                  <td className="td">{formatDateTime(u.lastLoginAt)}</td>
                  <td className="td">
                    <div className="flex flex-wrap gap-1">
                      {!u.active && <Badge tone="slate">Inactivo</Badge>}
                      {u.blocked ? <Badge tone="red">Bloqueado</Badge> : u.active && <Badge tone="green">Activo</Badge>}
                    </div>
                    {u.blocked && u.blockReason && <div className="mt-1 text-xs text-red-700">{u.blockReason}</div>}
                  </td>
                  <td className="td">
                    <div className="flex justify-end gap-2">
                      {!isSelf && (
                        <button className={u.blocked ? 'btn-secondary btn-sm' : 'btn-warn btn-sm'} disabled={update.isPending} onClick={() => toggleBlock(u)}>
                          {u.blocked ? 'Desbloquear' : 'Bloquear'}
                        </button>
                      )}
                      <button className="btn-secondary btn-sm" onClick={() => setEditing(u)}>Editar</button>
                      {!isSelf && (
                        <button className="btn-ghost btn-sm" disabled={update.isPending} onClick={() => toggleActive(u)}>
                          {u.active ? 'Desactivar' : 'Reactivar'}
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      {editing && (
        <UserFormModal
          user={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            void refresh();
          }}
        />
      )}

      {blocking && (
        <ReasonModal
          title={`Bloquear a ${blocking.fullName}`}
          description="El usuario no podrá abrir accesos ni autorizar citofonía y se cerrarán sus sesiones activas."
          confirmLabel="Bloquear usuario"
          busy={update.isPending}
          onCancel={() => setBlocking(null)}
          onConfirm={(reason) =>
            update.mutate(
              { user: blocking, body: { blocked: true, blockReason: reason || 'Bloqueado por administración' } },
              { onSuccess: () => toast.push('success', `${blocking.fullName} bloqueado`) },
            )
          }
        />
      )}
    </div>
  );
}

function UserFormModal({ user, onClose, onSaved }: { user: AppUser | null; onClose: () => void; onSaved: () => void }) {
  const { condoId } = useTenant();
  const { user: me } = useAuth();
  const units = useUnits();
  const toast = useToast();

  const [fullName, setFullName] = useState(user?.fullName ?? '');
  const [email, setEmail] = useState(user?.email ?? '');
  const [phone, setPhone] = useState(user?.phone ?? '');
  const [role, setRole] = useState<UserRole>(user?.role ?? 'RESIDENT');
  const [unitId, setUnitId] = useState(user?.unitId ?? '');
  const [password, setPassword] = useState('');

  const roles: UserRole[] = me?.role === 'SUPER_ADMIN' ? ['RESIDENT', 'CONDO_ADMIN', 'SUPER_ADMIN'] : ['RESIDENT', 'CONDO_ADMIN'];
  const unitRequired = role === 'RESIDENT';

  const save = useMutation({
    mutationFn: () =>
      user
        ? userApi.update(condoId, user.id, {
            fullName: fullName.trim(),
            phone: phone.trim(),
            role,
            unitId: unitId || undefined,
            newPassword: password || undefined,
          })
        : userApi.create(condoId, {
            fullName: fullName.trim(),
            email: email.trim(),
            phone: phone.trim() || undefined,
            role,
            unitId: unitId || undefined,
            password,
          }),
    onSuccess: () => {
      toast.push('success', user ? 'Usuario actualizado' : 'Usuario creado');
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
      title={user ? `Editar ${user.fullName}` : 'Nuevo usuario'}
      onClose={onClose}
      footer={
        <>
          <button className="btn-secondary" onClick={onClose}>Cancelar</button>
          <button className="btn-primary" form="user-form" disabled={save.isPending}>
            {save.isPending ? 'Guardando…' : 'Guardar'}
          </button>
        </>
      }
    >
      <form id="user-form" onSubmit={submit} className="space-y-4">
        <Field label="Nombre completo">
          <input className="input" value={fullName} onChange={(e) => setFullName(e.target.value)} maxLength={150} required autoFocus />
        </Field>
        <Field label="Correo electrónico" hint={user ? 'El correo no se puede cambiar.' : undefined}>
          <input className="input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} disabled={!!user} required />
        </Field>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Teléfono">
            <input className="input" value={phone} onChange={(e) => setPhone(e.target.value)} maxLength={32} />
          </Field>
          <Field label="Rol">
            <select className="input" value={role} onChange={(e) => setRole(e.target.value as UserRole)}>
              {roles.map((r) => <option key={r} value={r}>{roleLabel[r]}</option>)}
            </select>
          </Field>
        </div>
        <Field label={`Unidad${unitRequired ? '' : ' (opcional)'}`}>
          <select className="input" value={unitId} onChange={(e) => setUnitId(e.target.value)} required={unitRequired}>
            <option value="">— Seleccionar —</option>
            {(units.data ?? []).filter((u) => u.active).map((u) => (
              <option key={u.id} value={u.id}>{u.unitNumber}{u.towerOrBlock ? ` · ${u.towerOrBlock}` : ''}</option>
            ))}
          </select>
        </Field>
        <Field label={user ? 'Nueva contraseña (opcional)' : 'Contraseña inicial'} hint="Mínimo 8 caracteres.">
          <input className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
            minLength={8} maxLength={128} required={!user} autoComplete="new-password" />
        </Field>
      </form>
    </Modal>
  );
}
