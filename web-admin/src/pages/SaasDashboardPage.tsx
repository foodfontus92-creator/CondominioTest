import { FormEvent, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { condoApi, saasApi } from '../api/endpoints';
import type {
  AdminActivationCodeResponse,
  CommunityType,
  QuickLicenseAction,
  SaasCondoOverviewItem,
  SubscriptionStatus,
} from '../api/types';
import { Badge, EmptyState, ErrorBox, Field, Modal, PageHeader, Spinner } from '../components/ui';
import { useTenant } from '../context/TenantContext';
import { useToast } from '../context/ToastContext';
import { COMMUNITY_TYPE_OPTIONS, communityTypeLabel } from '../lib/communityLabels';
import { formatDateTime } from '../lib/format';
import { errMsg } from '../lib/hooks';

type LicenseFilter = 'all' | 'active' | 'expiring' | 'inactive';

const statusBadge = (item: SaasCondoOverviewItem) => {
  if (!item.licenseActive || item.effectiveStatus === 'SUSPENDED' || item.effectiveStatus === 'EXPIRED') {
    return (
      <Badge tone="red">
        {item.effectiveStatus === 'SUSPENDED' ? 'Suspendida' : 'Vencida'}
      </Badge>
    );
  }
  if (item.daysRemaining !== null && item.daysRemaining <= 7) {
    return <Badge tone="amber">Por vencer ({item.daysRemaining} d)</Badge>;
  }
  if (item.effectiveStatus === 'TRIAL') {
    return <Badge tone="blue">Prueba</Badge>;
  }
  return <Badge tone="green">Activa</Badge>;
};

export default function SaasDashboardPage() {
  const { setCondoId } = useTenant();
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();

  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<LicenseFilter>('all');
  const [adminCodeModal, setAdminCodeModal] = useState<AdminActivationCodeResponse | null>(null);
  const [licenseTarget, setLicenseTarget] = useState<SaasCondoOverviewItem | null>(null);
  const [creatingClient, setCreatingClient] = useState(false);

  const overviewQuery = useQuery({
    queryKey: ['saas-overview'],
    queryFn: saasApi.overview,
  });

  const refreshAll = () => {
    void queryClient.invalidateQueries({ queryKey: ['saas-overview'] });
    void queryClient.invalidateQueries({ queryKey: ['condos'] });
  };

  const generateCode = useMutation({
    mutationFn: (condoId: string) => saasApi.createAdminCode(condoId),
    onSuccess: (res) => {
      setAdminCodeModal(res);
      toast.push('success', `Código Admin ${res.code} generado para ${res.condoName}`);
    },
    onError: (e) => toast.push('error', errMsg(e)),
  });

  const data = overviewQuery.data;

  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (data?.condominiums ?? []).filter((c) => {
      const expiring = c.licenseActive && c.daysRemaining !== null && c.daysRemaining <= 7;
      if (filter === 'active' && !c.licenseActive) return false;
      if (filter === 'expiring' && !expiring) return false;
      if (filter === 'inactive' && c.licenseActive) return false;
      if (!q) return true;
      return (
        c.name.toLowerCase().includes(q) ||
        c.code.toLowerCase().includes(q) ||
        (c.address ?? '').toLowerCase().includes(q) ||
        c.planName.toLowerCase().includes(q)
      );
    });
  }, [data?.condominiums, search, filter]);

  const enterCondo = (item: SaasCondoOverviewItem) => {
    setCondoId(item.id);
    toast.push('info', `Administrando ${item.name}`);
    navigate('/');
  };

  return (
    <div>
      <PageHeader
        title="Mis Clientes y Licencias SaaS"
        subtitle="Control maestro multi-tenant: suscripciones, vencimientos, cupos de viviendas y códigos de alta para administradores."
        actions={
          <button className="btn-primary" onClick={() => setCreatingClient(true)}>
            + Nuevo recinto cliente
          </button>
        }
      />

      {overviewQuery.isLoading && <Spinner />}
      {overviewQuery.error && <ErrorBox error={overviewQuery.error} />}

      {data && (
        <>
          <div className="mb-6 grid grid-cols-2 gap-4 xl:grid-cols-6">
            <div className="card p-4">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">Total Recintos</div>
              <div className="mt-1 text-3xl font-bold text-slate-900">{data.totalCondos}</div>
            </div>
            <div className="card p-4">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">Activos</div>
              <div className="mt-1 text-3xl font-bold text-emerald-600">{data.activeCondos}</div>
            </div>
            <div className="card p-4">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">Por vencer (≤ 7 d)</div>
              <div className={`mt-1 text-3xl font-bold ${data.expiringSoonCondos > 0 ? 'text-amber-600' : 'text-slate-900'}`}>
                {data.expiringSoonCondos}
              </div>
            </div>
            <div className="card p-4">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">Vencidos / Susp.</div>
              <div className={`mt-1 text-3xl font-bold ${data.expiredOrSuspendedCondos > 0 ? 'text-red-600' : 'text-slate-900'}`}>
                {data.expiredOrSuspendedCondos}
              </div>
            </div>
            <div className="card p-4">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">Total Viviendas</div>
              <div className="mt-1 text-3xl font-bold text-slate-900">{data.totalUnits}</div>
            </div>
            <div className="card p-4">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">Total Usuarios</div>
              <div className="mt-1 text-3xl font-bold text-brand-700">{data.totalUsers}</div>
              <div className="mt-1 text-xs text-slate-500">{data.totalResidents} residentes</div>
            </div>
          </div>

          <div className="card mb-4 flex flex-wrap items-center gap-3 p-4">
            <input
              className="input max-w-xs"
              placeholder="Buscar cliente, código o plan…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <select
              className="input !w-auto"
              value={filter}
              onChange={(e) => setFilter(e.target.value as LicenseFilter)}
            >
              <option value="all">Todas las licencias</option>
              <option value="active">Activas</option>
              <option value="expiring">Por vencer (≤ 7 días)</option>
              <option value="inactive">Vencidas o suspendidas</option>
            </select>
            <span className="ml-auto text-sm text-slate-500">
              {rows.length} de {data.condominiums.length} recintos
            </span>
          </div>

          <div className="card overflow-x-auto">
            {rows.length === 0 ? (
              <EmptyState>No hay recintos que coincidan con el filtro.</EmptyState>
            ) : (
              <table className="min-w-full divide-y divide-slate-200">
                <thead className="bg-slate-50">
                  <tr>
                    <th className="th">Recinto</th>
                    <th className="th">Tipo</th>
                    <th className="th">Unidades / Usuarios</th>
                    <th className="th">Plan y Licencia</th>
                    <th className="th">Vencimiento</th>
                    <th className="th text-right">Acciones rápidas</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {rows.map((c) => (
                    <tr key={c.id} className={!c.licenseActive ? 'bg-red-50/40' : ''}>
                      <td className="td">
                        <div className="font-semibold text-slate-900">{c.name}</div>
                        <div className="text-xs text-slate-500">
                          <code>{c.code}</code>
                          {c.address ? ` · ${c.address}` : ''}
                        </div>
                      </td>
                      <td className="td">
                        <Badge tone="slate">{communityTypeLabel(c.communityType)}</Badge>
                      </td>
                      <td className="td text-sm">
                        <div className="font-medium">
                          {c.unitCount} / {c.maxUnits} viv.
                        </div>
                        <div className="text-xs text-slate-500">
                          {c.residentCount} vecinos · {c.adminCount} admin
                        </div>
                      </td>
                      <td className="td">
                        <div className="mb-1 text-xs font-medium text-slate-700">{c.planName}</div>
                        {statusBadge(c)}
                      </td>
                      <td className="td text-sm">
                        {c.licenseExpiresAt ? (
                          <div>
                            <div>{formatDateTime(c.licenseExpiresAt)}</div>
                            <div className="text-xs text-slate-500">
                              {c.daysRemaining !== null && c.daysRemaining >= 0
                                ? `${c.daysRemaining} día(s) restantes`
                                : `Vencida hace ${Math.abs(c.daysRemaining ?? 0)} día(s)`}
                            </div>
                          </div>
                        ) : (
                          <span className="text-slate-600">Ilimitada</span>
                        )}
                      </td>
                      <td className="td">
                        <div className="flex flex-wrap justify-end gap-2">
                          <button
                            className="btn-secondary btn-sm"
                            disabled={generateCode.isPending}
                            onClick={() => generateCode.mutate(c.id)}
                          >
                            Generar código Admin
                          </button>
                          <button
                            className="btn-warn btn-sm"
                            onClick={() => setLicenseTarget(c)}
                          >
                            Gestionar Licencia
                          </button>
                          <button
                            className="btn-primary btn-sm"
                            onClick={() => enterCondo(c)}
                          >
                            Entrar a administrar
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}

      {adminCodeModal && (
        <AdminCodeModal invite={adminCodeModal} onClose={() => setAdminCodeModal(null)} />
      )}

      {licenseTarget && (
        <LicenseManageModal
          item={licenseTarget}
          onClose={() => setLicenseTarget(null)}
          onUpdated={() => {
            setLicenseTarget(null);
            refreshAll();
          }}
        />
      )}

      {creatingClient && (
        <NewClientCondoModal
          onClose={() => setCreatingClient(false)}
          onCreated={() => {
            setCreatingClient(false);
            refreshAll();
          }}
        />
      )}
    </div>
  );
}

function AdminCodeModal({
  invite,
  onClose,
}: {
  invite: AdminActivationCodeResponse;
  onClose: () => void;
}) {
  const toast = useToast();

  const copyCode = async () => {
    try {
      await navigator.clipboard.writeText(invite.code);
      toast.push('success', `Código ${invite.code} copiado`);
    } catch {
      toast.push('error', 'No se pudo copiar al portapapeles');
    }
  };

  const copyWhatsApp = async () => {
    const loginUrl = `${window.location.origin}/login`;
    const msg = [
      `¡Hola! Tu recinto *${invite.condoName}* ya está habilitado en *AccesoLibre*.`,
      '',
      'Para crear tu cuenta de Administrador:',
      `1. Ingresa al panel: ${loginUrl}`,
      '2. Toca *"¿Tienes código de administrador? Activar recinto aquí"*.',
      `3. Ingresa tu código único: *${invite.code}*`,
      '',
      `⏳ Código válido hasta: ${formatDateTime(invite.expiresAt)} (1 solo uso)`,
    ].join('\n');
    try {
      await navigator.clipboard.writeText(msg);
      toast.push('success', 'Invitación de WhatsApp copiada');
    } catch {
      toast.push('error', 'No se pudo acceder al portapapeles');
    }
  };

  return (
    <Modal
      title={`Código Administrador · ${invite.condoName}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn-secondary" onClick={onClose}>Cerrar</button>
          <button className="btn-secondary" onClick={() => void copyCode()}>Copiar código</button>
          <button className="btn-primary" onClick={() => void copyWhatsApp()}>Copiar invitación WhatsApp</button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="rounded-xl border border-brand-200 bg-brand-50 p-4 text-center">
          <div className="text-xs font-semibold uppercase tracking-wide text-brand-700">
            Código único de alta (72 horas)
          </div>
          <div className="mt-1 select-all font-mono text-3xl font-bold tracking-wider text-brand-900">
            {invite.code}
          </div>
        </div>
        <p className="text-sm text-slate-600">
          El administrador deberá ingresar este código en el enlace{' '}
          <strong>"¿Tienes código de administrador? Activar recinto aquí"</strong> de la pantalla de inicio de sesión.
          Válido hasta <strong>{formatDateTime(invite.expiresAt)}</strong>.
        </p>
      </div>
    </Modal>
  );
}

function LicenseManageModal({
  item,
  onClose,
  onUpdated,
}: {
  item: SaasCondoOverviewItem;
  onClose: () => void;
  onUpdated: () => void;
}) {
  const toast = useToast();
  const [subscriptionStatus, setSubscriptionStatus] = useState<SubscriptionStatus>(item.subscriptionStatus);
  const [planName, setPlanName] = useState(item.planName);
  const [maxUnits, setMaxUnits] = useState(String(item.maxUnits));
  const [expiresDate, setExpiresDate] = useState(() =>
    item.licenseExpiresAt ? item.licenseExpiresAt.slice(0, 10) : '',
  );

  const updateMutation = useMutation({
    mutationFn: (body: Parameters<typeof saasApi.updateLicense>[1]) =>
      saasApi.updateLicense(item.id, body),
    onSuccess: (updated) => {
      toast.push('success', `Licencia de ${updated.name} actualizada`);
      onUpdated();
    },
    onError: (e) => toast.push('error', errMsg(e)),
  });

  const runQuick = (quickAction: QuickLicenseAction) => {
    updateMutation.mutate({
      quickAction,
      planName: planName.trim() || undefined,
      maxUnits: Number(maxUnits) || item.maxUnits,
    });
  };

  const submitCustom = (e: FormEvent) => {
    e.preventDefault();
    const isoExpiration = expiresDate.trim()
      ? new Date(`${expiresDate.trim()}T23:59:59.000Z`).toISOString()
      : undefined;
    updateMutation.mutate({
      subscriptionStatus,
      planName: planName.trim() || 'Mensual',
      maxUnits: Number(maxUnits) || 200,
      unlimitedLicense: expiresDate.trim() === '',
      licenseExpiresAt: isoExpiration,
    });
  };

  return (
    <Modal
      wide
      title={`Licencia · ${item.name}`}
      onClose={onClose}
      footer={
        <>
          <button className="btn-secondary" onClick={onClose}>Cancelar</button>
          <button className="btn-primary" form="license-form" disabled={updateMutation.isPending}>
            {updateMutation.isPending ? 'Guardando…' : 'Guardar cambios'}
          </button>
        </>
      }
    >
      <div className="space-y-5">
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-600">
            Acciones rápidas de renovación
          </div>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className="btn-primary btn-sm"
              disabled={updateMutation.isPending}
              onClick={() => runQuick('EXTEND_30_DAYS')}
            >
              +30 días
            </button>
            <button
              type="button"
              className="btn-primary btn-sm"
              disabled={updateMutation.isPending}
              onClick={() => runQuick('EXTEND_1_YEAR')}
            >
              +1 año
            </button>
            <button
              type="button"
              className="btn-secondary btn-sm"
              disabled={updateMutation.isPending}
              onClick={() => runQuick('ACTIVATE_UNLIMITED')}
            >
              Licencia ilimitada
            </button>
            <button
              type="button"
              className="btn-danger btn-sm"
              disabled={updateMutation.isPending}
              onClick={() => runQuick('SUSPEND')}
            >
              Suspender servicio
            </button>
          </div>
        </div>

        <form id="license-form" onSubmit={submitCustom} className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <Field label="Estado de suscripción">
              <select
                className="input"
                value={subscriptionStatus}
                onChange={(e) => setSubscriptionStatus(e.target.value as SubscriptionStatus)}
              >
                <option value="ACTIVE">ACTIVE (Activa)</option>
                <option value="TRIAL">TRIAL (Prueba)</option>
                <option value="EXPIRED">EXPIRED (Vencida)</option>
                <option value="SUSPENDED">SUSPENDED (Suspendida)</option>
              </select>
            </Field>
            <Field label="Nombre del plan">
              <input
                className="input"
                value={planName}
                onChange={(e) => setPlanName(e.target.value)}
                maxLength={64}
                placeholder="ej. Mensual, Anual, Pro"
                required
              />
            </Field>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <Field label="Fecha de vencimiento" hint="Déjala vacía para licencia ilimitada.">
              <input
                type="date"
                className="input"
                value={expiresDate}
                onChange={(e) => setExpiresDate(e.target.value)}
              />
            </Field>
            <Field label="Máximo de viviendas" hint={`Actualmente ${item.unitCount} creadas.`}>
              <input
                type="number"
                className="input"
                min={1}
                max={50000}
                value={maxUnits}
                onChange={(e) => setMaxUnits(e.target.value)}
                required
              />
            </Field>
          </div>
        </form>
      </div>
    </Modal>
  );
}

function NewClientCondoModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const toast = useToast();
  const [communityType, setCommunityType] = useState<CommunityType>('CONDOMINIUM');
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [planName, setPlanName] = useState('Mensual');
  const [maxUnits, setMaxUnits] = useState('200');
  const [trialDays, setTrialDays] = useState('30');

  const create = useMutation({
    mutationFn: () => {
      const days = Number(trialDays);
      const expiresAt = days > 0 ? new Date(Date.now() + days * 86_400_000).toISOString() : undefined;
      return condoApi.create({
        code: code.trim().toLowerCase(),
        name: name.trim(),
        communityType,
        address: address.trim() || undefined,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
        subscriptionStatus: 'ACTIVE',
        planName: planName.trim() || 'Mensual',
        licenseExpiresAt: expiresAt,
        maxUnits: Number(maxUnits) || 200,
      });
    },
    onSuccess: (created) => {
      toast.push('success', `${created.name} creado. Genera ahora su código Admin.`);
      onCreated();
    },
    onError: (e) => toast.push('error', errMsg(e)),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    create.mutate();
  };

  return (
    <Modal
      wide
      title="Nuevo recinto cliente"
      onClose={onClose}
      footer={
        <>
          <button className="btn-secondary" onClick={onClose}>Cancelar</button>
          <button className="btn-primary" form="new-client-form" disabled={create.isPending}>
            {create.isPending ? 'Creando…' : 'Crear recinto'}
          </button>
        </>
      }
    >
      <form id="new-client-form" onSubmit={submit} className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2">
          <Field label="Tipo de recinto">
            <select
              className="input"
              value={communityType}
              onChange={(e) => setCommunityType(e.target.value as CommunityType)}
            >
              {COMMUNITY_TYPE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </Field>
          <Field label="Código único" hint="Minúsculas, números, - o _">
            <input
              className="input"
              value={code}
              onChange={(e) => setCode(e.target.value.toLowerCase())}
              pattern="^[a-z0-9\-_]+$"
              minLength={2}
              maxLength={32}
              required
              autoFocus
            />
          </Field>
        </div>
        <Field label="Nombre del recinto">
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} maxLength={150} required />
        </Field>
        <Field label="Dirección (opcional)">
          <input className="input" value={address} onChange={(e) => setAddress(e.target.value)} maxLength={255} />
        </Field>
        <div className="grid gap-4 md:grid-cols-3">
          <Field label="Plan">
            <input className="input" value={planName} onChange={(e) => setPlanName(e.target.value)} maxLength={64} />
          </Field>
          <Field label="Vigencia inicial (días)" hint="0 = ilimitada">
            <input type="number" className="input" min={0} max={3650} value={trialDays} onChange={(e) => setTrialDays(e.target.value)} />
          </Field>
          <Field label="Máx. viviendas">
            <input type="number" className="input" min={1} max={50000} value={maxUnits} onChange={(e) => setMaxUnits(e.target.value)} />
          </Field>
        </div>
      </form>
    </Modal>
  );
}
