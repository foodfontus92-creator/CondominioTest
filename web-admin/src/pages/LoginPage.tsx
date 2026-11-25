import { FormEvent, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

interface LoginLocationState {
  condoCode?: string;
  setupCompleted?: boolean;
}

export default function LoginPage() {
  const { user, login } = useAuth();
  const location = useLocation();
  const locationState = (location.state as LoginLocationState | null) ?? null;
  const [condoCode, setCondoCode] = useState(
    () => locationState?.condoCode ?? localStorage.getItem('accesolibre.admin.lastCondoCode') ?? '',
  );
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
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

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-brand-900 to-brand-600 p-4">
      <form onSubmit={onSubmit} className="card w-full max-w-md space-y-5 p-8">
        <div className="text-center">
          <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-600 text-2xl text-white">
            🔐
          </div>
          <h1 className="text-2xl font-bold text-slate-900">AccesoLibre</h1>
          <p className="text-sm text-slate-500">Panel administrativo del condominio</p>
        </div>

        {locationState?.setupCompleted && (
          <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
            Servidor inicializado correctamente. Inicia sesión con tu cuenta de administrador.
          </div>
        )}

        <div>
          <label className="label" htmlFor="condo">Código del condominio</label>
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
        <p className="text-center text-xs text-slate-400">Acceso para CONDO_ADMIN y SUPER_ADMIN</p>
      </form>
    </div>
  );
}
