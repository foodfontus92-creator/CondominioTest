import { useEffect, useState } from 'react';

/** Re-renders every `intervalMs` so relative times / stale detection stay fresh. */
export function useNow(intervalMs = 10_000): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), intervalMs);
    return () => window.clearInterval(id);
  }, [intervalMs]);
  return now;
}

export function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : 'Error inesperado';
}
