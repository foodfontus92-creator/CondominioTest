import { useState } from 'react';
import { Field, Modal } from './ui';

/** Asks for an optional reason (e.g. "morosidad") before blocking something. */
export default function ReasonModal({
  title,
  description,
  confirmLabel,
  busy,
  onCancel,
  onConfirm,
}: {
  title: string;
  description: string;
  confirmLabel: string;
  busy?: boolean;
  onCancel: () => void;
  onConfirm: (reason: string) => void;
}) {
  const [reason, setReason] = useState('');
  return (
    <Modal
      title={title}
      onClose={onCancel}
      footer={
        <>
          <button className="btn-secondary" onClick={onCancel}>Cancelar</button>
          <button className="btn-danger" disabled={busy} onClick={() => onConfirm(reason.trim())}>
            {busy ? 'Guardando…' : confirmLabel}
          </button>
        </>
      }
    >
      <p className="mb-4 text-sm text-slate-600">{description}</p>
      <Field label="Motivo (visible en la administración)">
        <input
          className="input"
          maxLength={255}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="ej. Morosidad, incumplimiento de reglamento…"
          autoFocus
        />
      </Field>
    </Modal>
  );
}
