import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Save, Send } from 'lucide-react';
import { api, errorMessage } from '../../api/client.js';
import { Alert, ConfirmModal, EmptyState, FormField, Loading, PageHeader, StatusBadge } from '../../components/ui.jsx';
import { useToast } from '../../components/Toast.jsx';
import { useFetch } from '../../hooks/useFetch.js';
import { num } from '../../utils/format.js';

/** Returns an error message for one mark input, or '' when it is acceptable (blank is allowed for drafts). */
function validate(value, max) {
  if (value === '' || value === null || value === undefined) return '';
  if (!/^\d+(\.\d{0,2})?$/.test(value.trim())) return 'Use a number with at most 2 decimals';
  if (Number(value) > Number(max)) return `Maximum is ${num(max)}`;
  return '';
}

const toInput = (v) => (v === null || v === undefined ? '' : String(Number(v)));

export default function MarksEntry() {
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const assignmentId = params.get('assignment') || '';
  const sessionId = params.get('session') || '';
  const assignments = useFetch('/faculty/assignments');
  const assignment = assignments.data?.find((a) => String(a.assignmentId) === assignmentId);
  const sheetState = useFetch(assignmentId && sessionId ? `/faculty/assignments/${assignmentId}/sessions/${sessionId}/marks` : null);
  const sheet = sheetState.data;

  const [values, setValues] = useState({});
  const [busy, setBusy] = useState(false);
  const [confirmSubmit, setConfirmSubmit] = useState(false);
  const [message, setMessage] = useState('');

  // (Re)initialise the editable grid whenever a sheet is loaded.
  useEffect(() => {
    if (!sheet) return;
    setValues(Object.fromEntries(sheet.rows.map((r) => [r.studentId, { internal: toInput(r.internalMarks), external: toInput(r.externalMarks) }])));
    setMessage('');
  }, [sheet]);

  const select = (patch) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    setParams(next, { replace: true });
  };

  const analysis = useMemo(() => {
    if (!sheet) return null;
    let dirty = 0; let invalid = 0; let incomplete = 0; let editable = 0;
    const rowState = {};
    sheet.rows.forEach((r) => {
      const v = values[r.studentId] || { internal: '', external: '' };
      const errI = r.editable ? validate(v.internal, sheet.internalMax) : '';
      const errE = r.editable ? validate(v.external, sheet.externalMax) : '';
      const changed = r.editable && (v.internal !== toInput(r.internalMarks) || v.external !== toInput(r.externalMarks));
      if (r.editable) editable += 1;
      if (changed) dirty += 1;
      if (errI || errE) invalid += 1;
      if (r.editable && (v.internal === '' || v.external === '')) incomplete += 1;
      rowState[r.studentId] = { errI, errE, changed };
    });
    return { dirty, invalid, incomplete, editable, rowState };
  }, [sheet, values]);

  const setCell = (studentId, field, value) => setValues((s) => ({ ...s, [studentId]: { ...s[studentId], [field]: value } }));

  const entriesToSave = () => sheet.rows
    .filter((r) => r.editable && analysis.rowState[r.studentId].changed)
    .map((r) => ({
      studentId: r.studentId,
      internalMarks: values[r.studentId].internal === '' ? null : Number(values[r.studentId].internal),
      externalMarks: values[r.studentId].external === '' ? null : Number(values[r.studentId].external),
    }));

  const saveDraft = async () => {
    const entries = entriesToSave();
    if (entries.length === 0) return true;
    setBusy(true);
    setMessage('');
    try {
      await api.put(`/faculty/assignments/${assignmentId}/sessions/${sessionId}/marks`, { entries });
      toast.success(`Draft saved (${entries.length} student${entries.length === 1 ? '' : 's'})`);
      await sheetState.reload();
      return true;
    } catch (e) {
      setMessage(errorMessage(e));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const submit = async () => {
    setBusy(true);
    setMessage('');
    try {
      const entries = entriesToSave();
      if (entries.length > 0) {
        await api.put(`/faculty/assignments/${assignmentId}/sessions/${sessionId}/marks`, { entries });
      }
      const r = await api.post(`/faculty/assignments/${assignmentId}/sessions/${sessionId}/submit`);
      toast.success(`${r.submitted} mark(s) submitted for approval`);
      setConfirmSubmit(false);
      await sheetState.reload();
    } catch (e) {
      setConfirmSubmit(false);
      setMessage(errorMessage(e));
      sheetState.reload();
    } finally {
      setBusy(false);
    }
  };

  const canSubmit = sheet && sheet.sessionOpen && analysis.editable > 0 && analysis.invalid === 0 && analysis.incomplete === 0;

  return (
    <>
      <PageHeader title="Marks Entry" subtitle="Save drafts as often as you like, then submit for administrator approval" />
      <div className="toolbar">
        <div className="filter" style={{ minWidth: 260 }}>
          <label>Subject
            <select className="input" value={assignmentId} onChange={(e) => select({ assignment: e.target.value, session: '' })}>
              <option value="">Select subject...</option>
              {(assignments.data || []).map((a) => <option key={a.assignmentId} value={a.assignmentId}>{a.subjectCode} - {a.subjectName} ({a.semesterLabel})</option>)}
            </select>
          </label>
        </div>
        <div className="filter" style={{ minWidth: 260 }}>
          <label>Examination session
            <select className="input" value={sessionId} onChange={(e) => select({ session: e.target.value })} disabled={!assignment}>
              <option value="">Select session...</option>
              {(assignment?.sessions || []).map((s) => <option key={s.id} value={s.id}>{s.name} [{s.status}]</option>)}
            </select>
          </label>
        </div>
      </div>
      <Alert onRetry={assignments.reload}>{assignments.error}</Alert>
      <Alert onRetry={sheetState.reload}>{sheetState.error}</Alert>
      <Alert>{message}</Alert>

      {!assignmentId || !sessionId ? (
        <div className="card"><EmptyState title="Choose a subject and an examination session" message={assignments.data?.length === 0 ? 'No subjects are assigned to you.' : undefined} /></div>
      ) : sheetState.loading && !sheet ? <Loading /> : sheet && (
        <>
          <div className="card">
            <div className="row">
              <strong>{sheet.subjectCode} - {sheet.subjectName}</strong>
              <span className="muted small">{sheet.sessionName}</span>
              <StatusBadge status={sheet.sessionStatus} />
            </div>
            <p className="small muted" style={{ marginBottom: 0 }}>
              Maximum: internal {num(sheet.internalMax)}, external {num(sheet.externalMax)}. Pass marks: internal {num(sheet.internalPass)},
              external {num(sheet.externalPass)}, total {num(sheet.totalPass)}.
            </p>
          </div>
          {!sheet.sessionOpen && <Alert kind="warn">This examination session is closed. Marks are read-only.</Alert>}
          {sheet.rows.length === 0 ? <div className="card"><EmptyState title="No students enrolled" /></div> : (
            <>
              <div className="table-wrap">
                <table className="table">
                  <thead><tr><th>Roll no.</th><th>Student</th><th>Internal (max {num(sheet.internalMax)})</th><th>External (max {num(sheet.externalMax)})</th><th>Status</th></tr></thead>
                  <tbody>
                    {sheet.rows.map((r) => {
                      const st = analysis.rowState[r.studentId];
                      const v = values[r.studentId] || { internal: '', external: '' };
                      return (
                        <tr key={r.studentId}>
                          <td>{r.rollNumber}</td><td>{r.studentName}</td>
                          <td>
                            <input className={`cell-input${st?.errI ? ' invalid' : ''}`} inputMode="decimal" aria-label={`Internal marks for ${r.rollNumber}`}
                              value={v.internal} readOnly={!r.editable} onChange={(e) => setCell(r.studentId, 'internal', e.target.value)} />
                            {st?.errI && <div className="field-error">{st.errI}</div>}
                          </td>
                          <td>
                            <input className={`cell-input${st?.errE ? ' invalid' : ''}`} inputMode="decimal" aria-label={`External marks for ${r.rollNumber}`}
                              value={v.external} readOnly={!r.editable} onChange={(e) => setCell(r.studentId, 'external', e.target.value)} />
                            {st?.errE && <div className="field-error">{st.errE}</div>}
                          </td>
                          <td><StatusBadge status={r.status} />{st?.changed && <span className="muted small"> unsaved</span>}
                            {r.reviewRemarks && <div className="small" style={{ color: 'var(--danger)' }}>Reviewer: {r.reviewRemarks}</div>}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              {analysis.editable > 0 && (
                <div className="row" style={{ marginTop: 14 }}>
                  <button type="button" className="btn" onClick={saveDraft} disabled={busy || analysis.dirty === 0 || analysis.invalid > 0}>
                    <Save size={15} /> {busy ? 'Working...' : 'Save draft'}{analysis.dirty > 0 ? ` (${analysis.dirty})` : ''}
                  </button>
                  <button type="button" className="btn primary" onClick={() => setConfirmSubmit(true)} disabled={busy || !canSubmit}>
                    <Send size={15} /> Submit for approval
                  </button>
                  {analysis.incomplete > 0 && <span className="small muted">{analysis.incomplete} student(s) still need both marks before you can submit.</span>}
                  {analysis.invalid > 0 && <span className="small" style={{ color: 'var(--danger)' }}>Fix the highlighted values first.</span>}
                </div>
              )}
              {analysis.editable === 0 && sheet.sessionOpen && <Alert kind="info">All marks have been submitted or approved and are locked for editing.</Alert>}
            </>
          )}
        </>
      )}
      {confirmSubmit && (
        <ConfirmModal title="Submit marks for approval" busy={busy} confirmLabel="Submit" onConfirm={submit} onClose={() => setConfirmSubmit(false)}
          message="After submission you cannot edit these marks unless the administrator rejects them. Continue?" />
      )}
    </>
  );
}
