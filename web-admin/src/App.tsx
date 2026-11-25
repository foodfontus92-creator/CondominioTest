import { useQuery } from '@tanstack/react-query';
import { Navigate, Route, Routes } from 'react-router-dom';
import { setupApi } from './api/endpoints';
import { useAuth } from './auth/AuthContext';
import Layout from './components/Layout';
import { TenantProvider } from './context/TenantContext';
import { LiveFeedProvider } from './live/LiveFeed';
import AuditPage from './pages/AuditPage';
import CondominiumsPage from './pages/CondominiumsPage';
import DashboardPage from './pages/DashboardPage';
import DevicesPage from './pages/DevicesPage';
import LoginPage from './pages/LoginPage';
import ResidentsPage from './pages/ResidentsPage';
import SetupPage from './pages/SetupPage';
import UnitsPage from './pages/UnitsPage';

function Protected() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return (
    <TenantProvider>
      <LiveFeedProvider>
        <Layout />
      </LiveFeedProvider>
    </TenantProvider>
  );
}

export default function App() {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['setup-status'],
    queryFn: setupApi.status,
    staleTime: 30_000,
    retry: 1,
  });

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 p-4">
        <div className="card flex items-center gap-3 px-6 py-4 text-sm text-slate-600">
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-brand-600" />
          Verificando estado del servidor…
        </div>
      </div>
    );
  }

  if (isError || !data) {
    const message = error instanceof Error ? error.message : 'No se pudo consultar el estado del servidor';
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 p-4">
        <div className="card w-full max-w-md space-y-4 p-6 text-center">
          <h1 className="text-lg font-semibold text-slate-900">Error de conexión</h1>
          <p className="text-sm text-red-700">{message}</p>
          <button className="btn-primary w-full" onClick={() => void refetch()}>
            Reintentar
          </button>
        </div>
      </div>
    );
  }

  const initialized = data.initialized;

  return (
    <Routes>
      <Route path="/setup" element={initialized ? <Navigate to="/login" replace /> : <SetupPage />} />
      <Route path="/login" element={initialized ? <LoginPage /> : <Navigate to="/setup" replace />} />
      <Route element={initialized ? <Protected /> : <Navigate to="/setup" replace />}>
        <Route index element={<DashboardPage />} />
        <Route path="unidades" element={<UnitsPage />} />
        <Route path="residentes" element={<ResidentsPage />} />
        <Route path="dispositivos" element={<DevicesPage />} />
        <Route path="bitacora" element={<AuditPage />} />
        <Route path="condominios" element={<CondominiumsPage />} />
      </Route>
      <Route path="*" element={<Navigate to={initialized ? '/' : '/setup'} replace />} />
    </Routes>
  );
}
