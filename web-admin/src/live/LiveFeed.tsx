import { createContext, ReactNode, useContext, useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { useQueryClient } from '@tanstack/react-query';
import { ensureFreshAccessToken } from '../api/client';
import type { Device, DeviceLiveEvent } from '../api/types';
import { useTenant } from '../context/TenantContext';
import { useToast } from '../context/ToastContext';

export type LiveState = 'connecting' | 'connected' | 'disconnected';

const LiveContext = createContext<LiveState>('disconnected');

const PRESERVED_GATE_KEYS = ['last_gate_event', 'last_gate_event_at'];

/** Mirrors the backend merge rules so the UI reflects each live event instantly. */
function applyEvent(device: Device, evt: DeviceLiveEvent): Device {
  switch (evt.eventType) {
    case 'HEARTBEAT': {
      const merged: Record<string, unknown> = {};
      for (const key of PRESERVED_GATE_KEYS) {
        if (device.lastTelemetry?.[key] !== undefined) merged[key] = device.lastTelemetry[key];
      }
      Object.assign(merged, evt.payload);
      const p = evt.payload;
      return {
        ...device,
        status: 'ONLINE',
        lastSeenAt: evt.occurredAt,
        lastTelemetry: merged,
        firmwareVersion: typeof p.firmware_version === 'string' ? p.firmware_version : device.firmwareVersion,
        ipAddress: typeof p.ip_address === 'string' ? p.ip_address : device.ipAddress,
        hmacKeyVersionSynced:
          typeof p.hmac_key_version_synced === 'number' ? p.hmac_key_version_synced : device.hmacKeyVersionSynced,
      };
    }
    case 'DEVICE_OFFLINE':
      return { ...device, status: 'OFFLINE' };
    case 'GATE_OPEN_TIMEOUT':
    case 'GATE_CLOSED':
      return {
        ...device,
        lastSeenAt: evt.occurredAt,
        lastTelemetry: {
          ...(device.lastTelemetry ?? {}),
          last_gate_event: evt.eventType,
          last_gate_event_at: evt.occurredAt,
          door_sensor_open: evt.eventType === 'GATE_OPEN_TIMEOUT',
        },
      };
    default:
      return device;
  }
}

export function LiveFeedProvider({ children }: { children: ReactNode }) {
  const { condoId } = useTenant();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [state, setState] = useState<LiveState>('disconnected');

  useEffect(() => {
    if (!condoId) return;
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';

    const client: Client = new Client({
      brokerURL: `${proto}://${window.location.host}/ws/intercom`,
      reconnectDelay: 5000,
      heartbeatIncoming: 0,
      heartbeatOutgoing: 0,
      beforeConnect: async (c) => {
        setState('connecting');
        const token = await ensureFreshAccessToken();
        c.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
      },
      onConnect: () => {
        setState('connected');
        client.subscribe(`/topic/condo/${condoId}/devices`, (message) => {
          let evt: DeviceLiveEvent;
          try {
            evt = JSON.parse(message.body) as DeviceLiveEvent;
          } catch {
            return;
          }
          const key = ['devices', condoId];
          if (evt.eventType === 'ACCESS_EVENT') {
            void queryClient.invalidateQueries({ queryKey: ['access-logs', condoId] });
            return;
          }
          let deviceName = 'Dispositivo';
          queryClient.setQueryData<Device[]>(key, (old) =>
            old?.map((d) => {
              if (d.id !== evt.deviceId) return d;
              deviceName = d.name;
              return applyEvent(d, evt);
            }),
          );
          if (evt.eventType === 'GATE_OPEN_TIMEOUT') {
            const secs = typeof evt.payload.open_seconds === 'number' ? evt.payload.open_seconds : null;
            toast.push(
              'warning',
              `⚠ ${deviceName}: portón abierto${secs ? ` hace más de ${secs} s` : ''}`,
              15000,
            );
          } else if (evt.eventType === 'GATE_CLOSED') {
            toast.push('success', `${deviceName}: portón cerrado`);
          } else if (evt.eventType === 'DEVICE_OFFLINE') {
            toast.push('error', `${deviceName} se desconectó`);
          }
        });
      },
      onWebSocketClose: () => setState('disconnected'),
      onStompError: () => setState('disconnected'),
    });

    client.activate();
    return () => {
      void client.deactivate();
      setState('disconnected');
    };
  }, [condoId, queryClient, toast]);

  return <LiveContext.Provider value={state}>{children}</LiveContext.Provider>;
}

export function useLiveState(): LiveState {
  return useContext(LiveContext);
}
