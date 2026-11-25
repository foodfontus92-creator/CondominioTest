import { FormEvent, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { setupApi } from '../api/endpoints';
import type { SetupStatusResponse } from '../api/types';

export default function SetupPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [condoCode, setCondoCode] = useState('');
  const [condoName, setCondoName] = useState('');
  const [address, setAddress] = useState('');
  const [timezone, setTimezone] = useState(() => Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC');

  const [adminFullName, setAdminFullName] = useState('');
  const [adminEmail, setAdminEmail] = useState('');
  const [adminPassword, setAdminPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    const normalizedCode = condoCode.trim().toLowerCase();
    if (!/^[a-z0-9\-_]{2,32}$/.test(normalizedCode)) {
      setError('El código del condominio debe tener entre 2 y 32 caracteres (minúsculas, números, guion o guion bajo).');
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
        address: address.trim() || undefined,
        timezone: timezone.trim() || 'UTC',
        adminFullName: adminFullName.trim(),
        adminEmail: adminEmail.trim(),
        adminPassword,
      });

      queryClient.setQueryData<SetupStatusResponse>(['setup-status'], { initialized: true });
      localStorage.setItem('accesolibre.admin.lastCondoCode', res.condoCode);
      navigate('/login', {
        replace: true,
        state: { condoCode: res.condoCode, setupCompleted: true },
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
            Crea el primer condominio y la cuenta de Super Administrador local para comenzar a operar.
          </p>
        </div>

        <div className="space-y-4 border-t border-slate-200 pt-5">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-600">Datos del condominio</h2>
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="label" htmlFor="condoCode">Código del condominio</label>
              <input
                id="condoCode"
                className="input"
                value={condoCode}
                onChange={(e) => setCondoCode(e.target.value.toLowerCase())}
                placeholder="ej. torre-norte"
                minLength={2}
                maxLength={32}
                required
                autoFocus
              />
            </div>
            <div>
              <label className="label" htmlFor="condoName">Nombre del condominio</label>
              <input
                id="condoName"
                className="input"
                value={condoName}
                onChange={(e) => setCondoName(e.target.value)}
                placeholder="ej. Residencial Torre Norte"
                maxLength={150}
                required
              />
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="label" htmlFor="address">Dirección (opcional)</label>
              <input
                id="address"
                className="input"
                value={address}
                onChange={(e) => setAddress(e.target.value)}
                placeholder="ej. Av. Principal 123"
                maxLength={255}
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
        </div>

        <div className="space-y-4 border-t border-slate-200 pt-5">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-600">
            Cuenta Super Administrador
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
