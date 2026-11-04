import { useEffect, useState } from 'react';
import { Send } from 'lucide-react';
import { api, errorMessage } from '../../api/client.js';
import {
  Alert, Badge, ConfirmModal, EmptyState, Loading, PageHeader, Pagination, SelectFilter, StatCard,
} from '../../components/ui.jsx';
import { useToast } from '../../components/Toast.jsx';
import { useFetch } from '../../hooks/useFetch.js';
import { fixed2, fmtDateTime } from '../../utils/format.js';
import { StatusBadge } from '../../components/ui.jsx';

export default function PublicationPage() {
  const toast = useToast();
  const [sessionId, setSessionId] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [page, setPage] = useState(0);
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [publishError, setPublishError] = useState('');
  const sessions = useFetch('/admin/options/examination-sessions');
  const departments = useFetch('/admin/options/departments');
  const ready = sessionId && departmentId;
  const status = useFetch(ready ? '/admin/results/publication-status' : null, { sessionId, departmentId });
  const published = useFetch('/admin/results', { page, size: 15, ...(sessionId && { sessionId }), ...(departmentId && { departmentId }) });
  useEffect(() => setPage(0), [sessionId, departmentId]);

  const publish = async () => {
    setBusy(true);
    setPublishError('');
    try {
      const r = await api.post('/admin/results/publish', { sessionId: Number(sessionId), departmentId: Number(departmentId) });
      toast.success(`${r.published} result(s) published`);
      setConfirm(false);
      status.reload();
      published.reload();
    } catch (e) {
      setConfirm(false);
      setPublishError(errorMessage(e));
      status.reload();
    } finally {
      setBusy(false);
    }
  };

  const s = status.data;
  return (
    <>
      <PageHeader title="Results Publication" subtitle="Only an administrator can publish. All marks must be complete and approved first." />
      <div className="toolbar">
        <SelectFilter label="Examination session" value={sessionId} onChange={setSessionId} options={sessions.data} allLabel="Select session" />
        <SelectFilter label="Department" value={departmentId} onChange={setDepartmentId} options={departments.data} allLabel="Select department" />
      </div>
      <Alert>{publishError}</Alert>

      {!ready && <div className="card"><EmptyState title="Select a session and department" message="The readiness check for that scope will appear here." /></div>}
      {ready && status.loading && !s && <Loading label="Checking readiness..." />}
      <Alert onRetry={status.reload}>{status.error}</Alert>
      {s && (
        <div className="card">
          <h2>{s.sessionName} - {s.departmentName}</h2>
          <div className="grid stats">
            <StatCard label="Students to publish" value={s.studentsToPublish} />
            <StatCard label="Ready (all marks approved)" value={s.readyStudents} />
            <StatCard label="Missing / not submitted" value={s.studentsWithMissingMarks} />
            <StatCard label="Awaiting approval" value={s.studentsWithUnapprovedMarks} />
            <StatCard label="Already published" value={s.studentsAlreadyPublished} />
          </div>
          <p className="small">Grading rule: {s.activeGradingRule ? <strong>{s.activeGradingRule}</strong> : <Badge tone="red">none active</Badge>}</p>
          <Alert kind={s.canPublish ? 'success' : 'warn'}>{s.message}</Alert>
          {s.blockedStudents.length > 0 && (
            <details style={{ marginBottom: 12 }}>
              <summary>Students blocking publication ({s.blockedStudents.length}{s.studentsToPublish - s.readyStudents > s.blockedStudents.length ? '+' : ''})</summary>
              <ul className="small">{s.blockedStudents.map((b) => <li key={b}>{b}</li>)}</ul>
            </details>
          )}
          <button type="button" className="btn primary" disabled={!s.canPublish} onClick={() => setConfirm(true)}>
            <Send size={15} /> Publish results
          </button>
        </div>
      )}

      <div className="card">
        <h2>Published results{ready ? ' for this selection' : ''}</h2>
        <Alert onRetry={published.reload}>{published.error}</Alert>
        {published.loading && !published.data ? <Loading /> : published.data && published.data.items.length === 0 ? (
          <EmptyState title="Nothing published yet" />
        ) : published.data && (
          <>
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>Roll no.</th><th>Student</th><th>Dept</th><th>Examination</th><th>SGPA</th><th>Result</th><th>Rev.</th><th>Published</th></tr></thead>
                <tbody>
                  {published.data.items.map((r) => (
                    <tr key={r.resultId}>
                      <td>{r.rollNumber}</td><td>{r.studentName}</td><td>{r.departmentCode}</td><td className="small">{r.sessionName}</td>
                      <td className="mono">{fixed2(r.sgpa)}</td><td><StatusBadge status={r.resultStatus} /></td>
                      <td>{r.revision}</td><td className="small">{fmtDateTime(r.publishedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={published.data.page} totalPages={published.data.totalPages} totalElements={published.data.totalElements} onPage={setPage} />
          </>
        )}
      </div>

      {confirm && (
        <ConfirmModal title="Publish results" busy={busy} confirmLabel="Publish" onConfirm={publish} onClose={() => setConfirm(false)}
          message={`Publish ${s?.studentsToPublish} result(s) for ${s?.departmentName} - ${s?.sessionName}? Students will be able to see them immediately, and the marks become locked. Later changes require an audited correction.`} />
      )}
    </>
  );
}
