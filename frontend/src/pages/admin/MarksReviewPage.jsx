import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Check, PencilLine, X } from 'lucide-react';
import { api, errorMessage } from '../../api/client.js';
import {
  Alert, EmptyState, FormField, Loading, Modal, PageHeader, Pagination, SearchBox, SelectFilter, StatusBadge,
} from '../../components/ui.jsx';
import { useToast } from '../../components/Toast.jsx';
import { useDebounced, useFetch } from '../../hooks/useFetch.js';
import { num } from '../../utils/format.js';
import { numberStr } from '../../utils/schemas.js';

const STATUS_OPTIONS = ['DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'PUBLISHED'].map((s) => ({ id: s, label: s }));

function RejectModal({ count, onClose, onDone }) {
  const [error, setError] = useState('');
  const schema = z.object({ remarks: z.string().trim().min(3, 'Please give a reason (at least 3 characters)').max(500) });
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({ resolver: zodResolver(schema), defaultValues: { remarks: '' } });
  return (
    <Modal title={`Reject ${count} mark entr${count === 1 ? 'y' : 'ies'}`} onClose={onClose}
      footer={<>
        <button type="button" className="btn" onClick={onClose}>Cancel</button>
        <button type="submit" form="reject-form" className="btn danger solid" disabled={isSubmitting}>Reject</button>
      </>}>
      <Alert>{error}</Alert>
      <form id="reject-form" noValidate onSubmit={handleSubmit(async (v) => {
        try { await onDone(v.remarks); } catch (e) { setError(errorMessage(e)); }
      })}>
        <FormField label="Reason shown to the faculty member" error={errors.remarks?.message} required>
          <textarea className="input" rows={3} {...register('remarks')} />
        </FormField>
      </form>
    </Modal>
  );
}

function CorrectionModal({ row, onClose, onSaved }) {
  const [error, setError] = useState('');
  const schema = useMemo(() => z.object({
    internalMarks: numberStr('Internal marks', { max: Number(row.internalMax) }),
    externalMarks: numberStr('External marks', { max: Number(row.externalMax) }),
    reason: z.string().trim().min(10, 'Explain the reason (at least 10 characters)').max(500, 'At most 500 characters'),
  }), [row]);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { internalMarks: num(row.internalMarks), externalMarks: num(row.externalMarks), reason: '' },
  });
  const submit = async (v) => {
    setError('');
    try {
      await api.post(`/admin/marks/${row.id}/correct`, { internalMarks: Number(v.internalMarks), externalMarks: Number(v.externalMarks), reason: v.reason.trim() });
      onSaved();
    } catch (e) {
      setError(errorMessage(e));
    }
  };
  return (
    <Modal title={`Correct marks - ${row.rollNumber} / ${row.subjectCode}`} onClose={onClose}
      footer={<>
        <button type="button" className="btn" onClick={onClose}>Cancel</button>
        <button type="submit" form="correct-form" className="btn primary" disabled={isSubmitting}>{isSubmitting ? 'Saving...' : 'Record correction'}</button>
      </>}>
      <Alert kind="warn">
        {row.status === 'PUBLISHED'
          ? 'These marks are published. The correction is audited, the previous values are kept, and the student\'s result is recalculated and marked as a new revision.'
          : 'These marks are approved. The correction is audited and the previous values are kept.'}
      </Alert>
      <Alert>{error}</Alert>
      <form id="correct-form" noValidate onSubmit={handleSubmit(submit)}>
        <div className="form-grid">
          <FormField label={`Internal marks (max ${num(row.internalMax)})`} error={errors.internalMarks?.message} required>
            <input className="input" inputMode="decimal" {...register('internalMarks')} />
          </FormField>
          <FormField label={`External marks (max ${num(row.externalMax)})`} error={errors.externalMarks?.message} required>
            <input className="input" inputMode="decimal" {...register('externalMarks')} />
          </FormField>
        </div>
        <FormField label="Reason for correction" error={errors.reason?.message} required help="Recorded permanently in the audit log">
          <textarea className="input" rows={3} {...register('reason')} />
        </FormField>
      </form>
    </Modal>
  );
}

export default function MarksReviewPage() {
  const toast = useToast();
  const [params] = useSearchParams();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [sessionId, setSessionId] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [status, setStatus] = useState(params.get('status') || '');
  const [selected, setSelected] = useState(new Set());
  const [modal, setModal] = useState(null); // {type:'reject'} | {type:'correct', row}
  const [busy, setBusy] = useState(false);
  const debounced = useDebounced(search);
  const sessions = useFetch('/admin/options/examination-sessions');
  const departments = useFetch('/admin/options/departments');

  const query = { page, size: 20, search: debounced, ...(sessionId && { sessionId }), ...(departmentId && { departmentId }), ...(status && { status }) };
  const { data, loading, error, reload } = useFetch('/admin/marks', query);
  useEffect(() => { setPage(0); }, [debounced, sessionId, departmentId, status]);
  useEffect(() => { setSelected(new Set()); }, [page, debounced, sessionId, departmentId, status]);

  const selectable = (data?.items || []).filter((r) => r.status === 'SUBMITTED');
  const allSelected = selectable.length > 0 && selectable.every((r) => selected.has(r.id));
  const toggle = (id) => setSelected((s) => { const n = new Set(s); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  const toggleAll = () => setSelected(allSelected ? new Set() : new Set(selectable.map((r) => r.id)));

  const review = async (action, remarks) => {
    const r = await api.post('/admin/marks/review', { markIds: [...selected], action, remarks: remarks || null });
    toast.success(`${r.reviewed} mark entr${r.reviewed === 1 ? 'y' : 'ies'} ${action === 'APPROVE' ? 'approved' : 'rejected'}`);
    setSelected(new Set());
    setModal(null);
    reload();
  };

  const approve = async () => {
    setBusy(true);
    try { await review('APPROVE'); } catch (e) { toast.error(errorMessage(e)); } finally { setBusy(false); }
  };

  return (
    <>
      <PageHeader title="Marks Review" subtitle="Approve or reject marks submitted by faculty. Approved marks are locked for faculty." />
      <div className="toolbar">
        <SearchBox value={search} onChange={setSearch} placeholder="Roll number, name or subject code" />
        <SelectFilter label="Examination" value={sessionId} onChange={setSessionId} options={sessions.data} />
        <SelectFilter label="Department" value={departmentId} onChange={setDepartmentId} options={departments.data} />
        <SelectFilter label="Status" value={status} onChange={setStatus} options={STATUS_OPTIONS} allLabel="Any status" />
      </div>
      {selected.size > 0 && (
        <div className="alert info row">
          <strong>{selected.size} selected</strong>
          <button type="button" className="btn sm primary" onClick={approve} disabled={busy}><Check size={14} /> Approve</button>
          <button type="button" className="btn sm danger" onClick={() => setModal({ type: 'reject' })} disabled={busy}><X size={14} /> Reject...</button>
        </div>
      )}
      <Alert onRetry={reload}>{error}</Alert>
      {loading && !data ? <Loading /> : data && data.items.length === 0 ? (
        <div className="card"><EmptyState title="No marks found" message="Marks appear here once faculty save or submit them." /></div>
      ) : data && (
        <>
          <div className="table-wrap" style={{ opacity: loading ? 0.6 : 1 }}>
            <table className="table">
              <thead>
                <tr>
                  <th><input type="checkbox" aria-label="Select all submitted on this page" checked={allSelected} onChange={toggleAll} disabled={selectable.length === 0} /></th>
                  <th>Student</th><th>Subject</th><th>Examination</th><th>Internal</th><th>External</th><th>Status</th><th>Entered by</th><th />
                </tr>
              </thead>
              <tbody>
                {data.items.map((r) => (
                  <tr key={r.id}>
                    <td><input type="checkbox" aria-label={`Select ${r.rollNumber} ${r.subjectCode}`} disabled={r.status !== 'SUBMITTED'} checked={selected.has(r.id)} onChange={() => toggle(r.id)} /></td>
                    <td>{r.rollNumber}<div className="muted small">{r.studentName} - {r.departmentCode}</div></td>
                    <td><strong>{r.subjectCode}</strong><div className="muted small">{r.subjectName}</div></td>
                    <td className="small">{r.sessionName}</td>
                    <td className="mono">{num(r.internalMarks)} <span className="muted small">/ {num(r.internalMax)}</span></td>
                    <td className="mono">{num(r.externalMarks)} <span className="muted small">/ {num(r.externalMax)}</span></td>
                    <td><StatusBadge status={r.status} />{r.reviewRemarks && <div className="muted small">{r.reviewRemarks}</div>}</td>
                    <td className="small">{r.facultyName}</td>
                    <td className="actions">
                      {(r.status === 'APPROVED' || r.status === 'PUBLISHED') && (
                        <button type="button" className="btn sm" onClick={() => setModal({ type: 'correct', row: r })}><PencilLine size={13} /> Correct</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
        </>
      )}
      {modal?.type === 'reject' && <RejectModal count={selected.size} onClose={() => setModal(null)} onDone={(remarks) => review('REJECT', remarks)} />}
      {modal?.type === 'correct' && <CorrectionModal row={modal.row} onClose={() => setModal(null)}
        onSaved={() => { toast.success('Correction recorded'); setModal(null); reload(); }} />}
    </>
  );
}
