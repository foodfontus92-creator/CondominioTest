import { FormEvent, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Navigate, useLocation } from 'react-router-dom';
import type { CommunityType, SetupStatusResponse } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { getCommunityLabels } from '../lib/communityLabels';

interface LoginLocationState {
  condoCode?: string;
  communityType?: CommunityType;
  setupCompleted?: boolean;
}

export default function LoginPage() {
  const { user, login, activateAdmin } = useAuth();
  const queryClient = useQueryClient();
  const location = useLocation();
  const locationState = (location.state as LoginLocationState | null) ?? null;
  const setupStatus = queryClient.getQueryData<SetupStatusResponse>(['setup-status']);
  const labels = getCommunityLabels(locationState?.communityType ?? setupStatus?.defaultCommunityType);
  const [mode, setMode] = useState<'login' | 'activate-admin'>('login');
  const [condoCode, setCondoCode] = useState(
    () => locationState?.condoCode ?? localStorage.getItem('accesolibre.admin.lastCondoCode') ?? '',
  );
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const [inviteCode, setInviteCode] = useState('');
  const [adminName, setAdminName] = useState('');
  const [adminEmail, setAdminEmail] = useState('');
  const [adminPhone, setAdminPhone] = useState('');
  const [adminPassword, setAdminPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  if (user) return <Navigate to="/" replace />;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(condoCode, email, password);
      localStorage.setItem('accesolibre.admin.lastCondoCode', condoCode.trim());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo iniciar sesión');
    } finally {
      setLoading(false);
    }
  };

  const onActivateSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (adminPassword.length < 8) {
      setError('La contraseña debe tener al menos 8 caracteres.');
      return;
    }
    if (adminPassword !== confirmPassword) {
      setError('Las contraseñas no coinciden.');
      return;
    }
    setLoading(true);
    try {
      await activateAdmin({
        code: inviteCode.trim().toUpperCase(),
        fullName: adminName.trim(),
        email: adminEmail.trim(),
        phone: adminPhone.trim() || undefined,
        password: adminPassword,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo activar la cuenta de administrador');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-brand-900 to-brand-600 p-4">
      <div className="card w-full max-w-md space-y-5 p-8">
        <div className="text-center">
          <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-600 text-2xl text-white">
            🔐
          </div>
          <h1 className="text-2xl font-bold text-slate-900">AccesoLibre</h1>
          <p className="text-sm text-slate-500">
            {mode === 'login'
              ? `Panel administrativo ${labels.ofCommunity}`
              : 'Activar recinto con código de administrador'}
          </p>
        </div>

        {locationState?.setupCompleted && mode === 'login' && (
          <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
            Servidor inicializado correctamente. Inicia sesión con tu cuenta de administrador.
          </div>
        )}

        {mode === 'login' ? (
          <form onSubmit={onSubmit} className="space-y-4">
            <div>
              <label className="label" htmlFor="condo">{labels.codeLabel}</label>
              <input id="condo" className="input" value={condoCode} onChange={(e) => setCondoCode(e.target.value)}
                placeholder="ej. torre-norte" required autoFocus />
            </div>
            <div>
              <label className="label" htmlFor="email">Correo electrónico</label>
              <input id="email" type="email" className="input" value={email}
                onChange={(e) => setEmail(e.target.value)} required />
            </div>
            <div>
              <label className="label" htmlFor="password">Contraseña</label>
              <input id="password" type="password" className="input" value={password}
                onChange={(e) => setPassword(e.target.value)} required />
            </div>

            {error && <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}

            <button className="btn-primary w-full py-2.5" disabled={loading}>
              {loading ? 'Ingresando…' : 'Ingresar'}
            </button>
            <div className="border-t border-slate-100 pt-3 text-center">
              <button
                type="button"
                className="text-sm font-medium text-brand-700 hover:text-brand-900 hover:underline"
                onClick={() => {
                  setError(null);
                  setMode('activate-admin');
                }}
              >
                ¿Tienes código de administrador? Activar recinto aquí
              </button>
            </div>
          </form>
        ) : (
          <form onSubmit={onActivateSubmit} className="space-y-4">
            <div>
              <label className="label" htmlFor="inviteCode">Código de administrador</label>
              <input
                id="inviteCode"
                className="input font-mono uppercase tracking-wider"
                value={inviteCode}
                onChange={(e) => setInviteCode(e.target.value.toUpperCase())}
                placeholder="ej. ADM-4829"
                maxLength={16}
                required
                autoFocus
              />
            </div>
            <div>
              <label className="label" htmlFor="adminName">Nombre completo</label>
              <input
                id="adminName"
                className="input"
                value={adminName}
                onChange={(e) => setAdminName(e.target.value)}
                maxLength={150}
                required
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="label" htmlFor="adminEmail">Correo</label>
                <input
                  id="adminEmail"
                  type="email"
                  className="input"
                  value={adminEmail}
                  onChange={(e) => setAdminEmail(e.target.value)}
                  maxLength={255}
                  required
                />
              </div>
              <div>
                <label className="label" htmlFor="adminPhone">Teléfono (opc.)</label>
                <input
                  id="adminPhone"
                  className="input"
                  value={adminPhone}
                  onChange={(e) => setAdminPhone(e.target.value)}
                  maxLength={32}
                />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="label" htmlFor="adminPassword">Contraseña (mín. 8)</label>
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
                <label className="label" htmlFor="confirmPassword">Confirmar</label>
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

            {error && <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}

            <button className="btn-primary w-full py-2.5" disabled={loading}>
              {loading ? 'Activando cuenta…' : 'Activar cuenta e ingresar'}
            </button>
            <div className="text-center">
              <button
                type="button"
                className="text-sm text-slate-600 hover:text-slate-900 hover:underline"
                onClick={() => {
                  setError(null);
                  setMode('login');
                }}
              >
                ← Volver a iniciar sesión
              </button>
            </div>
          </form>
        )}

        <p className="text-center text-xs text-slate-400">Acceso para CONDO_ADMIN y SUPER_ADMIN</p>
      </div>
    </div>
  );
}
