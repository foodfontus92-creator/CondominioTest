import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { logApi } from '../api/endpoints';
import type { AccessLog, AccessLogFilters, AccessResult } from '../api/types';
import { Badge, EmptyState, ErrorBox, Field, PageHeader, Spinner } from '../components/ui';
import { useDevices, useTenant, useUnits, useUsers } from '../context/TenantContext';
import { useToast } from '../context/ToastContext';
import { formatUnitLabel, useCommunityLabels } from '../lib/communityLabels';
import { downloadCsv, formatDateTime, methodGroups, methodLabel, resultLabel } from '../lib/format';
import { errMsg } from '../lib/hooks';

const PAGE_SIZE = 25;
const EXPORT_PAGE_SIZE = 500;
const EXPORT_MAX_PAGES = 100; // 50k rows safety cap

interface Draft {
  from: string; // yyyy-mm-dd (local)
  to: string; // yyyy-mm-dd (local, inclusive)
  unitId: string;
  deviceId: string;
  methodKey: string;
  result: '' | AccessResult;
}

const emptyDraft: Draft = { from: '', to: '', unitId: '', deviceId: '', methodKey: '', result: '' };

/** yyyy-mm-dd (local browser timezone) -> ISO-8601 UTC start of day (00:00:00.000). */
function localStartOfDayUtcIso(day: string): string {
  const [y, m, d] = day.split('-').map(Number);
  return new Date(y, m - 1, d, 0, 0, 0, 0).toISOString();
}

/** yyyy-mm-dd (local browser timezone) -> ISO-8601 UTC end of day (23:59:59.999). */
function localEndOfDayUtcIso(day: string): string {
  const [y, m, d] = day.split('-').map(Number);
  return new Date(y, m - 1, d, 23, 59, 59, 999).toISOString();
}

function toFilters(draft: Draft): AccessLogFilters {
  const group = methodGroups.find((g) => g.key === draft.methodKey);
  return {
    unitId: draft.unitId || undefined,
    deviceId: draft.deviceId || undefined,
    result: draft.result || undefined,
    method: group?.methods,
    from: draft.from ? localStartOfDayUtcIso(draft.from) : undefined,
    to: draft.to ? localEndOfDayUtcIso(draft.to) : undefined,
  };
}

function pad(n: number) {
  return String(n).padStart(2, '0');
}

function localTimestamp(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

export default function AuditPage() {
  const { condoId } = useTenant();
  const labels = useCommunityLabels();
  const units = useUnits();
  const devices = useDevices();
  const users = useUsers();
  const toast = useToast();

  const [draft, setDraft] = useState<Draft>(emptyDraft);
  const [applied, setApplied] = useState<Draft>(emptyDraft);
  const [page, setPage] = useState(0);
  const [exporting, setExporting] = useState<string | null>(null);

  const filters = useMemo(() => toFilters(applied), [applied]);

  const logs = useQuery({
    queryKey: ['access-logs', condoId, filters, page],
    queryFn: () => logApi.page(condoId, filters, page, PAGE_SIZE),
    enabled: !!condoId,
    placeholderData: keepPreviousData,
  });

  const unitName = useMemo(() => new Map((units.data ?? []).map((u) => [u.id, formatUnitLabel(u)])), [units.data]);
  const deviceName = useMemo(() => new Map((devices.data ?? []).map((d) => [d.id, d.name])), [devices.data]);
  const userName = useMemo(() => new Map((users.data ?? []).map((u) => [u.id, u.fullName])), [users.data]);

  const apply = () => {
    if (draft.from && draft.to && draft.from > draft.to) {
      toast.push('error', 'La fecha "desde" no puede ser posterior a "hasta"');
      return;
    }
    setPage(0);
    setApplied(draft);
  };

  const reset = () => {
    setDraft(emptyDraft);
    setApplied(emptyDraft);
    setPage(0);
  };

  const exportCsv = async () => {
    try {
      const rows: unknown[][] = [[
        'Fecha local', 'Fecha UTC', 'Unidad', 'Dispositivo', 'Método', 'Resultado',
        'Usuario', 'Validado offline', 'Pase QR', 'ID registro',
      ]];
      let current = 0;
      let last = false;
      let total = 0;
      while (!last && current < EXPORT_MAX_PAGES) {
        setExporting(`Exportando… ${rows.length - 1}${total ? ` de ${total}` : ''}`);
        const res = await logApi.page(condoId, filters, current, EXPORT_PAGE_SIZE);
        total = res.totalElements;
        for (const l of res.content) rows.push(toCsvRow(l));
        last = res.last;
        current += 1;
      }
      downloadCsv(`bitacora-accesos-${new Date().toISOString().slice(0, 10)}.csv`, rows);
      toast.push(last ? 'success' : 'warning',
        last ? `CSV exportado (${rows.length - 1} registros)` : `Exportación truncada a ${rows.length - 1} registros; acota los filtros`);
    } catch (e) {
      toast.push('error', errMsg(e));
    } finally {
      setExporting(null);
    }
  };

  const toCsvRow = (l: AccessLog): unknown[] => [
    localTimestamp(l.occurredAt),
    l.occurredAt,
    l.unitId ? unitName.get(l.unitId) ?? l.unitId : '',
    l.deviceId ? deviceName.get(l.deviceId) ?? l.deviceId : '',
    methodLabel[l.accessMethod] ?? l.accessMethod,
    resultLabel[l.result] ?? l.result,
    l.triggeredByUserId ? userName.get(l.triggeredByUserId) ?? l.triggeredByUserId : '',
    l.offlineValidated ? 'Sí' : 'No',
    l.qrPassIdRaw ?? '',
    l.id,
  ];

  const data = logs.data;

  return (
    <div>
      <PageHeader
        title="Bitácora de auditoría"
        subtitle="Registro inmutable de accesos: app GPS, QR de visitas y citofonía."
        actions={
          <button className="btn-primary" disabled={!!exporting || logs.isLoading} onClick={() => void exportCsv()}>
            {exporting ?? '⬇ Exportar CSV'}
          </button>
        }
      />

      <div className="card mb-4 p-4">
        <div className="grid gap-4 md:grid-cols-3 xl:grid-cols-6">
          <Field label="Desde">
            <input type="date" className="input" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} />
          </Field>
          <Field label="Hasta (inclusive)">
            <input type="date" className="input" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} />
          </Field>
          <Field label={labels.unitSingular}>
            <select className="input" value={draft.unitId} onChange={(e) => setDraft({ ...draft, unitId: e.target.value })}>
              <option value="">Todas</option>
              {(units.data ?? []).map((u) => <option key={u.id} value={u.id}>{formatUnitLabel(u)}</option>)}
            </select>
          </Field>
          <Field label="Método de acceso">
            <select className="input" value={draft.methodKey} onChange={(e) => setDraft({ ...draft, methodKey: e.target.value })}>
              <option value="">Todos</option>
              {methodGroups.map((g) => <option key={g.key} value={g.key}>{g.label}</option>)}
            </select>
          </Field>
          <Field label="Dispositivo">
            <select className="input" value={draft.deviceId} onChange={(e) => setDraft({ ...draft, deviceId: e.target.value })}>
              <option value="">Todos</option>
              {(devices.data ?? []).map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
          </Field>
          <Field label="Resultado">
            <select className="input" value={draft.result} onChange={(e) => setDraft({ ...draft, result: e.target.value as '' | AccessResult })}>
              <option value="">Todos</option>
              {(Object.keys(resultLabel) as AccessResult[]).map((r) => <option key={r} value={r}>{resultLabel[r]}</option>)}
            </select>
          </Field>
        </div>
        <div className="mt-4 flex gap-2">
          <button className="btn-primary" onClick={apply}>Aplicar filtros</button>
          <button className="btn-secondary" onClick={reset}>Limpiar</button>
        </div>
      </div>

      <div className="card overflow-x-auto">
        {logs.isLoading && <Spinner />}
        {logs.error && <ErrorBox error={logs.error} />}
        {data && data.content.length === 0 && <EmptyState>No hay registros para los filtros seleccionados.</EmptyState>}
        {data && data.content.length > 0 && (
          <table className="min-w-full divide-y divide-slate-200">
            <thead className="bg-slate-50">
              <tr>
                <th className="th">Fecha</th>
                <th className="th">{labels.unitSingular}</th>
                <th className="th">Dispositivo</th>
                <th className="th">Método</th>
                <th className="th">Resultado</th>
                <th className="th">Usuario</th>
                <th className="th">Detalle</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((l) => (
                <tr key={l.id}>
                  <td className="td whitespace-nowrap">{formatDateTime(l.occurredAt)}</td>
                  <td className="td">{l.unitId ? unitName.get(l.unitId) ?? '—' : '—'}</td>
                  <td className="td">{l.deviceId ? deviceName.get(l.deviceId) ?? '—' : '—'}</td>
                  <td className="td">{methodLabel[l.accessMethod] ?? l.accessMethod}</td>
                  <td className="td">
                    <Badge tone={l.result === 'GRANTED' ? 'green' : 'red'}>{resultLabel[l.result] ?? l.result}</Badge>
                  </td>
                  <td className="td">{l.triggeredByUserId ? userName.get(l.triggeredByUserId) ?? '—' : '—'}</td>
                  <td className="td text-xs text-slate-500">
                    {l.offlineValidated && <Badge tone="blue">Validado offline</Badge>}
                    {typeof l.details?.distance_meters === 'number' && (
                      <span className="ml-1">{Math.round(l.details.distance_meters)} m {labels.ofCommunity}</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {data && (
        <div className="mt-4 flex items-center justify-between text-sm text-slate-600">
          <span>
            {data.totalElements} registros · página {data.page + 1} de {Math.max(1, data.totalPages)}
          </span>
          <div className="flex gap-2">
            <button className="btn-secondary btn-sm" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>← Anterior</button>
            <button className="btn-secondary btn-sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>Siguiente →</button>
          </div>
        </div>
      )}
    </div>
  );
}
