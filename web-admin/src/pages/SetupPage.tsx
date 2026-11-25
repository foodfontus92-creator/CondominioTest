import { FormEvent, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { setupApi } from '../api/endpoints';
import type { CommunityType, SetupStatusResponse } from '../api/types';
import { COMMUNITY_TYPE_OPTIONS, getCommunityLabels } from '../lib/communityLabels';

export default function SetupPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [communityType, setCommunityType] = useState<CommunityType>('CONDOMINIUM');
  const [condoCode, setCondoCode] = useState('');
  const [condoName, setCondoName] = useState('');
  const [address, setAddress] = useState('');
  const [timezone, setTimezone] = useState(() => Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC');
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [geofenceRadiusMeters, setGeofenceRadiusMeters] = useState('150');
  const [locatingGps, setLocatingGps] = useState(false);

  const [adminFullName, setAdminFullName] = useState('');
  const [adminEmail, setAdminEmail] = useState('');
  const [adminPassword, setAdminPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const labels = getCommunityLabels(communityType);

  const captureBrowserGps = () => {
    if (!navigator.geolocation) {
      setError('Tu navegador no soporta geolocalización; puedes ingresar latitud y longitud manualmente.');
      return;
    }
    setLocatingGps(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLatitude(pos.coords.latitude.toFixed(6));
        setLongitude(pos.coords.longitude.toFixed(6));
        setLocatingGps(false);
      },
      (err) => {
        setLocatingGps(false);
        setError(`No se pudo obtener ubicación GPS (${err.message}). Puedes ingresarla manualmente.`);
      },
      { enableHighAccuracy: true, timeout: 10_000 },
    );
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    const normalizedCode = condoCode.trim().toLowerCase();
    if (!/^[a-z0-9\-_]{2,32}$/.test(normalizedCode)) {
      setError(`El código ${labels.ofCommunity} debe tener entre 2 y 32 caracteres (minúsculas, números, guion o guion bajo).`);
      return;
    }

    const latVal = latitude.trim() === '' ? undefined : Number(latitude);
    const lonVal = longitude.trim() === '' ? undefined : Number(longitude);
    if ((latVal === undefined) !== (lonVal === undefined)) {
      setError('Si ingresas coordenadas GPS, debes completar latitud y longitud juntas.');
      return;
    }
    if ((latVal !== undefined && Number.isNaN(latVal)) || (lonVal !== undefined && Number.isNaN(lonVal))) {
      setError('Latitud o longitud inválida.');
      return;
    }

    if (adminPassword.length < 8) {
      setError('La contraseña del administrador debe tener al menos 8 caracteres.');
      return;
    }
    if (adminPassword !== confirmPassword) {
      setError('Las contraseñas no coinciden.');
      return;
    }

    setLoading(true);
    try {
      const res = await setupApi.initialize({
        condoCode: normalizedCode,
        condoName: condoName.trim(),
        communityType,
        address: address.trim() || undefined,
        timezone: timezone.trim() || 'UTC',
        latitude: latVal,
        longitude: lonVal,
        geofenceRadiusMeters: Number(geofenceRadiusMeters) || 150,
        adminFullName: adminFullName.trim(),
        adminEmail: adminEmail.trim(),
        adminPassword,
      });

      queryClient.setQueryData<SetupStatusResponse>(['setup-status'], {
        initialized: true,
        defaultCommunityType: res.communityType,
      });
      localStorage.setItem('accesolibre.admin.lastCondoCode', res.condoCode);
      navigate('/login', {
        replace: true,
        state: { condoCode: res.condoCode, communityType: res.communityType, setupCompleted: true },
      });
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        queryClient.setQueryData<SetupStatusResponse>(['setup-status'], { initialized: true });
        navigate('/login', { replace: true });
        return;
      }
      setError(err instanceof Error ? err.message : 'No se pudo inicializar el servidor');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-brand-900 to-brand-600 p-4">
      <form onSubmit={onSubmit} className="card w-full max-w-2xl space-y-6 p-8">
        <div className="text-center">
          <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-600 text-2xl text-white">
            🏢
          </div>
          <h1 className="text-2xl font-bold text-slate-900">Configuración Inicial del Servidor AccesoLibre</h1>
          <p className="mt-1 text-sm text-slate-500">
            Paso único: registra el <strong>Administrador</strong>, el <strong>tipo y nombre general del recinto</strong> y su{' '}
            <strong>dirección + GPS única</strong>, que aplicará automáticamente a todos los blocks, viviendas y portones.
          </p>
        </div>

        <div className="space-y-4 border-t border-slate-200 pt-5">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-600">
              1. Recinto general y ubicación única
            </h2>
            <span className="text-xs text-slate-500">Heredada por todos los Blocks</span>
          </div>
          <div>
            <label className="label" htmlFor="communityType">Tipo de recinto</label>
            <select
              id="communityType"
              className="input"
              value={communityType}
              onChange={(e) => setCommunityType(e.target.value as CommunityType)}
            >
              {COMMUNITY_TYPE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="label" htmlFor="condoCode">{labels.codeLabel}</label>
              <input
                id="condoCode"
                className="input"
                value={condoCode}
                onChange={(e) => setCondoCode(e.target.value.toLowerCase())}
                placeholder={communityType === 'CONDOMINIUM' ? 'ej. torre-norte' : 'ej. villa-los-robles'}
                minLength={2}
                maxLength={32}
                required
                autoFocus
              />
            </div>
            <div>
              <label className="label" htmlFor="condoName">Nombre general {labels.ofCommunity}</label>
              <input
                id="condoName"
                className="input"
                value={condoName}
                onChange={(e) => setCondoName(e.target.value)}
                placeholder={communityType === 'CONDOMINIUM' ? 'ej. Condominio Torre Norte' : 'ej. Población Los Aromos'}
                maxLength={150}
                required
              />
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="label" htmlFor="address">Dirección única del recinto</label>
              <input
                id="address"
                className="input"
                value={address}
                onChange={(e) => setAddress(e.target.value)}
                placeholder="ej. Av. Principal 123, Comuna"
                maxLength={255}
                required
              />
            </div>
            <div>
              <label className="label" htmlFor="timezone">Zona horaria</label>
              <input
                id="timezone"
                className="input"
                value={timezone}
                onChange={(e) => setTimezone(e.target.value)}
                maxLength={64}
                required
              />
            </div>
          </div>

          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-600">
                Ubicación GPS del recinto (geofence compartido)
              </div>
              <button
                type="button"
                className="btn-secondary btn-sm"
                onClick={captureBrowserGps}
                disabled={locatingGps}
              >
                {locatingGps ? 'Obteniendo GPS…' : '📍 Usar mi ubicación actual'}
              </button>
            </div>
            <div className="grid gap-3 md:grid-cols-3">
              <div>
                <label className="label" htmlFor="latitude">Latitud</label>
                <input
                  id="latitude"
                  type="number"
                  step="any"
                  min={-90}
                  max={90}
                  className="input"
                  value={latitude}
                  onChange={(e) => setLatitude(e.target.value)}
                  placeholder="ej. -33.448890"
                />
              </div>
              <div>
                <label className="label" htmlFor="longitude">Longitud</label>
                <input
                  id="longitude"
                  type="number"
                  step="any"
                  min={-180}
                  max={180}
                  className="input"
                  value={longitude}
                  onChange={(e) => setLongitude(e.target.value)}
                  placeholder="ej. -70.669265"
                />
              </div>
              <div>
                <label className="label" htmlFor="geofenceRadiusMeters">Radio (metros)</label>
                <input
                  id="geofenceRadiusMeters"
                  type="number"
                  min={10}
                  max={5000}
                  className="input"
                  value={geofenceRadiusMeters}
                  onChange={(e) => setGeofenceRadiusMeters(e.target.value)}
                  required
                />
              </div>
            </div>
            <p className="text-xs text-slate-500">
              Todos los portones y blocks creados posteriormente usarán esta dirección y punto GPS como referencia.
            </p>
          </div>
        </div>

        <div className="space-y-4 border-t border-slate-200 pt-5">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-600">
            2. Cuenta del Administrador
          </h2>
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="label" htmlFor="adminFullName">Nombre completo</label>
              <input
                id="adminFullName"
                className="input"
                value={adminFullName}
                onChange={(e) => setAdminFullName(e.target.value)}
                placeholder="ej. María González"
                maxLength={150}
                required
              />
            </div>
            <div>
              <label className="label" htmlFor="adminEmail">Correo electrónico</label>
              <input
                id="adminEmail"
                type="email"
                className="input"
                value={adminEmail}
                onChange={(e) => setAdminEmail(e.target.value)}
                placeholder="admin@condominio.local"
                maxLength={255}
                required
              />
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="label" htmlFor="adminPassword">Contraseña (mín. 8 caracteres)</label>
              <input
                id="adminPassword"
                type="password"
                className="input"
                value={adminPassword}
                onChange={(e) => setAdminPassword(e.target.value)}
                minLength={8}
                maxLength={128}
                autoComplete="new-password"
                required
              />
            </div>
            <div>
              <label className="label" htmlFor="confirmPassword">Confirmar contraseña</label>
              <input
                id="confirmPassword"
                type="password"
                className="input"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                minLength={8}
                maxLength={128}
                autoComplete="new-password"
                required
              />
            </div>
          </div>
        </div>

        {error && (
          <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>
        )}

        <button className="btn-primary w-full py-2.5" disabled={loading}>
          {loading ? 'Inicializando servidor…' : 'Inicializar servidor'}
        </button>
      </form>
    </div>
  );
}
