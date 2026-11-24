import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useTenant, useDevices } from '../context/TenantContext';
import { useLiveState } from '../live/LiveFeed';
import { hasGateAlert, roleLabel } from '../lib/format';

const navItems = [
  { to: '/', label: 'Resumen', icon: '📊', end: true },
  { to: '/unidades', label: 'Unidades', icon: '🏠' },
  { to: '/residentes', label: 'Residentes', icon: '👥' },
  { to: '/dispositivos', label: 'Dispositivos', icon: '📡' },
  { to: '/bitacora', label: 'Bitácora', icon: '📋' },
  { to: '/condominios', label: 'Condominio', icon: '🏢' },
];

const liveStyle = {
  connected: { dot: 'bg-emerald-500', text: 'En vivo' },
  connecting: { dot: 'bg-amber-500 animate-pulse', text: 'Conectando…' },
  disconnected: { dot: 'bg-red-500', text: 'Sin tiempo real' },
} as const;

export default function Layout() {
  const { user, logout } = useAuth();
  const { condoId, condo, condos, isSuperAdmin, setCondoId } = useTenant();
  const live = useLiveState();
  const devices = useDevices();
  const alerts = (devices.data ?? []).filter(hasGateAlert);

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-60 shrink-0 flex-col bg-brand-900 text-white md:flex">
        <div className="px-5 py-5">
          <div className="text-xl font-bold">AccesoLibre</div>
          <div className="text-xs text-brand-200">Panel administrativo</div>
        </div>
        <nav className="flex-1 space-y-1 px-3">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition ${
                  isActive ? 'bg-white/15 text-white' : 'text-brand-100 hover:bg-white/10'
                }`
              }
            >
              <span>{item.icon}</span>
              {item.label}
              {item.to === '/dispositivos' && alerts.length > 0 && (
                <span className="ml-auto rounded-full bg-red-500 px-2 py-0.5 text-xs">{alerts.length}</span>
              )}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-white/10 p-4 text-xs text-brand-200">
          <div className="truncate font-semibold text-white">{user?.fullName}</div>
          <div className="truncate">{user?.email}</div>
          <div className="mt-1">{user ? roleLabel[user.role] : ''}</div>
          <button className="btn-ghost btn-sm mt-3 w-full !text-brand-100 hover:!bg-white/10" onClick={() => void logout()}>
            Cerrar sesión
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 bg-white px-6 py-3">
          <div className="flex items-center gap-3">
            {isSuperAdmin ? (
              <select
                className="input !w-auto"
                value={condoId}
                onChange={(e) => setCondoId(e.target.value)}
                aria-label="Condominio activo"
              >
                {condos.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name} ({c.code})
                  </option>
                ))}
              </select>
            ) : (
              <div className="font-semibold text-slate-800">{condo?.name ?? '…'}</div>
            )}
          </div>
          <div className="flex items-center gap-2 text-sm text-slate-600">
            <span className={`h-2.5 w-2.5 rounded-full ${liveStyle[live].dot}`} />
            {liveStyle[live].text}
          </div>
        </header>

        {alerts.length > 0 && (
          <div className="border-b border-red-200 bg-red-50 px-6 py-3 text-sm text-red-800">
            <strong>⚠ Portón abierto:</strong> {alerts.map((d) => d.name).join(', ')} lleva más de 45 s abierto.
          </div>
        )}
        {condo && (condo.latitude === null || condo.longitude === null) && (
          <div className="border-b border-amber-200 bg-amber-50 px-6 py-3 text-sm text-amber-800">
            Este condominio no tiene coordenadas GPS configuradas: la apertura remota desde la app no funcionará.
            Configúralas en <NavLink className="font-semibold underline" to="/condominios">Condominio</NavLink>.
          </div>
        )}

        <nav className="flex gap-1 overflow-x-auto border-b border-slate-200 bg-white px-3 py-2 md:hidden">
          {navItems.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end}
              className={({ isActive }) =>
                `whitespace-nowrap rounded-lg px-3 py-1.5 text-sm ${isActive ? 'bg-brand-600 text-white' : 'text-slate-600'}`}>
              {item.icon} {item.label}
            </NavLink>
          ))}
        </nav>

        <main className="flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
