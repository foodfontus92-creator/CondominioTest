import { Navigate, Route, Routes } from 'react-router-dom';
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
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<Protected />}>
        <Route index element={<DashboardPage />} />
        <Route path="unidades" element={<UnitsPage />} />
        <Route path="residentes" element={<ResidentsPage />} />
        <Route path="dispositivos" element={<DevicesPage />} />
        <Route path="bitacora" element={<AuditPage />} />
        <Route path="condominios" element={<CondominiumsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
