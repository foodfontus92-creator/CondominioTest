import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { condoApi, deviceApi, unitApi, userApi } from '../api/endpoints';
import type { Condominium } from '../api/types';
import { useAuth } from '../auth/AuthContext';

const SELECTED_KEY = 'accesolibre.admin.condo';

interface TenantContextValue {
  condoId: string;
  condo: Condominium | undefined;
  condos: Condominium[];
  isSuperAdmin: boolean;
  setCondoId: (id: string) => void;
}

const TenantContext = createContext<TenantContextValue | null>(null);

/**
 * Multi-tenant scope. CONDO_ADMIN is pinned to their own condominium; SUPER_ADMIN can
 * switch between condominiums (backend enforces isolation on every request regardless).
 */
export function TenantProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const isSuperAdmin = user?.role === 'SUPER_ADMIN';
  const homeCondoId = user?.condominiumId ?? '';

  const [selected, setSelected] = useState<string>(() => localStorage.getItem(SELECTED_KEY) ?? '');

  const condosQuery = useQuery({
    queryKey: ['condos'],
    queryFn: condoApi.list,
    enabled: !!user,
  });
  const condos = useMemo(() => condosQuery.data ?? [], [condosQuery.data]);

  const condoId = isSuperAdmin
    ? condos.some((c) => c.id === selected)
      ? selected
      : homeCondoId
    : homeCondoId;

  useEffect(() => {
    if (isSuperAdmin && condoId) localStorage.setItem(SELECTED_KEY, condoId);
  }, [isSuperAdmin, condoId]);

  const setCondoId = useCallback((id: string) => setSelected(id), []);

  const value = useMemo<TenantContextValue>(
    () => ({
      condoId,
      condo: condos.find((c) => c.id === condoId),
      condos,
      isSuperAdmin,
      setCondoId,
    }),
    [condoId, condos, isSuperAdmin, setCondoId],
  );

  return <TenantContext.Provider value={value}>{children}</TenantContext.Provider>;
}

export function useTenant(): TenantContextValue {
  const ctx = useContext(TenantContext);
  if (!ctx) throw new Error('useTenant must be used inside TenantProvider');
  return ctx;
}

// ---- Tenant-scoped data hooks (all data comes from the backend) -------------------------
export function useUnits() {
  const { condoId } = useTenant();
  return useQuery({ queryKey: ['units', condoId], queryFn: () => unitApi.list(condoId), enabled: !!condoId });
}

export function useUsers() {
  const { condoId } = useTenant();
  return useQuery({ queryKey: ['users', condoId], queryFn: () => userApi.list(condoId), enabled: !!condoId });
}

export function useDevices() {
  const { condoId } = useTenant();
  return useQuery({
    queryKey: ['devices', condoId],
    queryFn: () => deviceApi.list(condoId),
    enabled: !!condoId,
    refetchInterval: 30_000, // safety net in case the live feed drops
  });
}
