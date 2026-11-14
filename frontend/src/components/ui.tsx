import { useId, type ReactNode } from "react";

export function ErrorNotice({
  message,
  retry,
}: {
  message: string;
  retry?: () => void;
}) {
  return message ? (
    <div className="notice error" role="alert">
      <span>{message}</span>
      {retry && (
        <button className="text-button" onClick={retry}>
          Try again
        </button>
      )}
    </div>
  ) : null;
}
export function Loading() {
  return (
    <div className="loading" role="status">
      <span className="spinner" />
      Loading…
    </div>
  );
}
export function Empty({
  title,
  children,
}: {
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="empty">
      <span className="empty-symbol" aria-hidden="true">
        ↗
      </span>
      <h2>{title}</h2>
      {children}
    </div>
  );
}
export function Badge({ children }: { children: string }) {
  return (
    <span className={`badge ${children.toLowerCase()}`}>
      {children.toLowerCase()}
    </span>
  );
}
export function dateTime(raw: string) {
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "UTC",
  }).format(new Date(raw.endsWith("Z") ? raw : raw + "Z"));
}
export function dateParts(raw: string) {
  const date = new Date(raw.endsWith("Z") ? raw : raw + "Z");
  return {
    month: date.toLocaleDateString("en-US", {
      month: "short",
      timeZone: "UTC",
    }),
    day: date.getUTCDate(),
    time: date.toLocaleTimeString("en-US", {
      hour: "numeric",
      minute: "2-digit",
      timeZone: "UTC",
    }),
    weekday: date.toLocaleDateString("en-US", {
      weekday: "long",
      timeZone: "UTC",
    }),
  };
}
export function ConfirmDialog({
  title,
  children,
  busy,
  onConfirm,
  onClose,
  label = "Confirm",
}: {
  title: string;
  children: ReactNode;
  busy: boolean;
  onConfirm: () => void;
  onClose: () => void;
  label?: string;
}) {
  const id = useId();
  return (
    <dialog
      open
      aria-modal="true"
      aria-labelledby={id}
      ref={(node) => {
        if (node && !node.matches(":modal")) {
          node.close();
          node.showModal();
        }
      }}
      onCancel={(e) => {
        if (busy) e.preventDefault();
        else onClose();
      }}
    >
      <h2 id={id}>{title}</h2>
      {children}
      <div className="actions">
        <button disabled={busy} onClick={onConfirm}>
          {busy ? "Working…" : label}
        </button>
        <button className="secondary" disabled={busy} onClick={onClose}>
          Go back
        </button>
      </div>
    </dialog>
  );
}
