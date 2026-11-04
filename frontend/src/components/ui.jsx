import { useEffect } from 'react';
import { AlertTriangle, ChevronLeft, ChevronRight, Inbox, X } from 'lucide-react';

export function Loading({ label = 'Loading...', fullPage = false }) {
  return (
    <div className={`loading${fullPage ? ' full' : ''}`} role="status" aria-live="polite">
      <div className="spinner" />
      <span>{label}</span>
    </div>
  );
}

export function EmptyState({ title = 'Nothing to show', message, action }) {
  return (
    <div className="empty">
      <Inbox size={30} aria-hidden="true" />
      <strong>{title}</strong>
      {message && <div>{message}</div>}
      {action && <div style={{ marginTop: 10 }}>{action}</div>}
    </div>
  );
}

export function Alert({ kind = 'error', children, onRetry }) {
  if (!children) return null;
  return (
    <div className={`alert ${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      {kind === 'error' && <AlertTriangle size={14} style={{ verticalAlign: '-2px', marginRight: 6 }} aria-hidden="true" />}
      {children}
      {onRetry && (
        <button type="button" className="btn sm" style={{ marginLeft: 12 }} onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}

export function PageHeader({ title, subtitle, children }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {children && <div className="row">{children}</div>}
    </div>
  );
}

export function Badge({ tone, children }) {
  return <span className={`badge ${tone || ''}`}>{children}</span>;
}

const STATUS_TONE = {
  DRAFT: 'amber', SUBMITTED: 'blue', APPROVED: 'green', REJECTED: 'red', PUBLISHED: 'green',
  NOT_ENTERED: '', OPEN: 'green', CLOSED: '', PASS: 'green', FAIL: 'red',
};

export function StatusBadge({ status }) {
  return <Badge tone={STATUS_TONE[status]}>{String(status).replace('_', ' ')}</Badge>;
}

export function ActiveBadge({ active }) {
  return <Badge tone={active ? 'green' : 'red'}>{active ? 'Active' : 'Inactive'}</Badge>;
}

export function StatCard({ label, value, hint }) {
  return (
    <div className="stat">
      <div className="label">{label}</div>
      <div className="value mono">{value ?? '-'}</div>
      {hint && <div className="hint">{hint}</div>}
    </div>
  );
}

export function Pagination({ page, totalPages, totalElements, onPage }) {
  if (!totalElements) return null;
  return (
    <div className="pagination">
      <span>
        {totalElements} record{totalElements === 1 ? '' : 's'} - page {page + 1} of {Math.max(totalPages, 1)}
      </span>
      <div className="row">
        <button type="button" className="btn sm" disabled={page <= 0} onClick={() => onPage(page - 1)}>
          <ChevronLeft size={14} /> Prev
        </button>
        <button type="button" className="btn sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
          Next <ChevronRight size={14} />
        </button>
      </div>
    </div>
  );
}

export function Modal({ title, onClose, children, footer, wide = false }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-label={title}>
      <div className={`modal${wide ? ' wide' : ''}`}>
        <div className="modal-head">
          <h2>{title}</h2>
          <button type="button" className="btn icon" onClick={onClose} aria-label="Close">
            <X size={16} />
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

export function ConfirmModal({ title, message, confirmLabel = 'Confirm', danger = false, busy = false, onConfirm, onClose }) {
  return (
    <Modal
      title={title}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose} disabled={busy}>Cancel</button>
          <button type="button" className={`btn ${danger ? 'danger solid' : 'primary'}`} onClick={onConfirm} disabled={busy}>
            {busy ? 'Working...' : confirmLabel}
          </button>
        </>
      }
    >
      <p style={{ margin: 0 }}>{message}</p>
    </Modal>
  );
}

/** Label + control + validation message. */
export function FormField({ label, error, help, required, children }) {
  return (
    <label className="field">
      {label} {required && <span className="req" aria-hidden="true">*</span>}
      {children}
      {error && <div className="field-error" role="alert">{error}</div>}
      {!error && help && <div className="field-help">{help}</div>}
    </label>
  );
}

export function SelectFilter({ label, value, onChange, options, allLabel = 'All' }) {
  return (
    <div className="filter">
      <label>
        {label}
        <select className="input" value={value} onChange={(e) => onChange(e.target.value)}>
          <option value="">{allLabel}</option>
          {(options || []).map((o) => (
            <option key={o.id ?? o.value} value={o.id ?? o.value}>{o.label}</option>
          ))}
        </select>
      </label>
    </div>
  );
}

export function SearchBox({ value, onChange, placeholder = 'Search...' }) {
  return (
    <div className="filter grow">
      <label>
        Search
        <input className="input" type="search" value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
      </label>
    </div>
  );
}
