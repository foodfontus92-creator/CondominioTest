import { createContext, ReactNode, useCallback, useContext, useMemo, useState } from 'react';

type ToastKind = 'success' | 'error' | 'warning' | 'info';
interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

interface ToastContextValue {
  push: (kind: ToastKind, message: string, durationMs?: number) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const styles: Record<ToastKind, string> = {
  success: 'bg-emerald-600',
  error: 'bg-red-600',
  warning: 'bg-amber-500',
  info: 'bg-slate-700',
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const push = useCallback((kind: ToastKind, message: string, durationMs = 5000) => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t.slice(-4), { id, kind, message }]);
    window.setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), durationMs);
  }, []);

  const value = useMemo(() => ({ push }), [push]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-80 flex-col gap-2">
        {toasts.map((t) => (
          <div key={t.id} className={`pointer-events-auto rounded-lg px-4 py-3 text-sm text-white shadow-lg ${styles[t.kind]}`}>
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside ToastProvider');
  return ctx;
}
