import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { logApi } from '../api/endpoints';
import { Badge, ErrorBox, PageHeader, Spinner } from '../components/ui';
import { useDevices, useTenant, useUnits, useUsers } from '../context/TenantContext';
import { formatUnitLabel, useCommunityLabels } from '../lib/communityLabels';
import { effectiveDeviceState, formatDateTime, hasGateAlert, methodLabel, resultLabel } from '../lib/format';
import { useNow } from '../lib/hooks';

function Card({ label, value, hint, tone, to }: { label: string; value: string | number; hint?: string; tone?: 'red' | 'green'; to?: string }) {
  const color = tone === 'red' ? 'text-red-600' : tone === 'green' ? 'text-emerald-600' : 'text-slate-900';
  const body = (
    <div className="card h-full p-5 transition hover:shadow-md">
      <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</div>
      <div className={`mt-1 text-3xl font-bold ${color}`}>{value}</div>
      {hint && <div className="mt-1 text-xs text-slate-500">{hint}</div>}
    </div>
  );
  return to ? <Link to={to}>{body}</Link> : body;
}

export default function DashboardPage() {
  const { condoId, condo } = useTenant();
  const labels = useCommunityLabels();
  const units = useUnits();
  const users = useUsers();
  const devices = useDevices();
  const now = useNow();

  const since = new Date(Date.now() - 24 * 3600 * 1000).toISOString();
  const last24 = useQuery({
    queryKey: ['access-logs', condoId, 'dash-24h'],
    queryFn: () => logApi.page(condoId, { from: since }, 0, 1),
    enabled: !!condoId,
  });
  const granted24 = useQuery({
    queryKey: ['access-logs', condoId, 'dash-24h-granted'],
    queryFn: () => logApi.page(condoId, { from: since, result: 'GRANTED' }, 0, 1),
    enabled: !!condoId,
  });
  const recent = useQuery({
    queryKey: ['access-logs', condoId, 'dash-recent'],
    queryFn: () => logApi.page(condoId, {}, 0, 8),
    enabled: !!condoId,
  });

  const unitList = units.data ?? [];
  const deviceList = devices.data ?? [];
  const unitName = new Map(unitList.map((u) => [u.id, formatUnitLabel(u)]));
  const online = deviceList.filter((d) => effectiveDeviceState(d, now) === 'ONLINE').length;
  const alerts = deviceList.filter(hasGateAlert);
  const total24 = last24.data?.totalElements ?? 0;
  const ok24 = granted24.data?.totalElements ?? 0;

  if (units.isLoading || devices.isLoading) return <Spinner />;
  const error = units.error ?? devices.error ?? users.error;

  return (
    <div>
      <PageHeader title={condo ? condo.name : 'Resumen'} subtitle={`Estado general ${labels.ofCommunity} en tiempo real.`} />
      {error && <ErrorBox error={error} />}

      <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
        <Card label={labels.unitsNav} value={unitList.length} hint={`${unitList.filter((u) => u.blocked).length} bloqueadas`} to="/unidades" />
        <Card label={labels.residentsNav} value={(users.data ?? []).filter((u) => u.role === 'RESIDENT').length} to="/residentes" />
        <Card label="Dispositivos online" value={`${online} / ${deviceList.filter((d) => d.active).length}`}
          tone={online === deviceList.filter((d) => d.active).length && deviceList.length > 0 ? 'green' : 'red'} to="/dispositivos" />
        <Card label="Alertas de portón" value={alerts.length} tone={alerts.length > 0 ? 'red' : 'green'}
          hint={alerts.map((a) => a.name).join(', ') || 'Sin alertas'} to="/dispositivos" />
        <Card label="Accesos últimas 24 h" value={total24} to="/bitacora" />
        <Card label="Concedidos" value={ok24} tone="green" />
        <Card label="Denegados" value={Math.max(0, total24 - ok24)} tone={total24 - ok24 > 0 ? 'red' : undefined} />
        <Card label="Geofence" value={condo ? `${condo.geofenceRadiusMeters} m` : '—'}
          hint={condo?.latitude == null ? 'Sin coordenadas GPS' : `${condo.latitude.toFixed(5)}, ${condo.longitude?.toFixed(5)}`} to="/condominios" />
      </div>

      <h2 className="mb-3 mt-8 text-lg font-semibold text-slate-900">Últimos accesos</h2>
      <div className="card overflow-x-auto">
        {recent.isLoading && <Spinner />}
        {recent.error && <ErrorBox error={recent.error} />}
        {recent.data && recent.data.content.length === 0 && <div className="p-6 text-sm text-slate-500">Aún no hay accesos registrados.</div>}
        {recent.data && recent.data.content.length > 0 && (
          <table className="min-w-full divide-y divide-slate-200">
            <thead className="bg-slate-50">
              <tr><th className="th">Fecha</th><th className="th">{labels.unitSingular}</th><th className="th">Método</th><th className="th">Resultado</th></tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {recent.data.content.map((l) => (
                <tr key={l.id}>
                  <td className="td">{formatDateTime(l.occurredAt)}</td>
                  <td className="td">{l.unitId ? unitName.get(l.unitId) ?? '—' : '—'}</td>
                  <td className="td">{methodLabel[l.accessMethod]}</td>
                  <td className="td"><Badge tone={l.result === 'GRANTED' ? 'green' : 'red'}>{resultLabel[l.result]}</Badge></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
