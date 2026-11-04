import { useEffect, useState } from 'react';
import { Alert, EmptyState, Loading, PageHeader, Pagination, SearchBox, SelectFilter } from '../../components/ui.jsx';
import { useDebounced, useFetch } from '../../hooks/useFetch.js';
import { fmtDateTime } from '../../utils/format.js';

const ACTIONS = [
  'MARKS_CREATED', 'MARKS_UPDATED', 'MARKS_SUBMITTED', 'MARKS_APPROVED', 'MARKS_REJECTED', 'MARKS_CORRECTED',
  'RESULT_PUBLISHED', 'RESULTS_PUBLISHED_BATCH', 'RESULT_REVISED', 'GRADING_RULE_CREATED', 'GRADING_RULE_UPDATED',
  'GRADING_RULE_ACTIVATED', 'STUDENT_CREATED', 'STUDENT_UPDATED', 'STUDENT_DEACTIVATED', 'FACULTY_CREATED',
  'FACULTY_UPDATED', 'FACULTY_DEACTIVATED', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'ASSIGNMENT_CREATED', 'LOGIN',
].map((a) => ({ id: a, label: a }));

export default function AuditLogPage() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [action, setAction] = useState('');
  const debounced = useDebounced(search);
  const { data, loading, error, reload } = useFetch('/admin/audit-logs', { page, size: 20, search: debounced, action });
  useEffect(() => setPage(0), [debounced, action]);

  return (
    <>
      <PageHeader title="Audit Log" subtitle="Append-only record of marks entry, review, publication and corrections" />
      <div className="toolbar">
        <SearchBox value={search} onChange={setSearch} placeholder="User or entity type" />
        <SelectFilter label="Action" value={action} onChange={setAction} options={ACTIONS} allLabel="Any action" />
      </div>
      <Alert onRetry={reload}>{error}</Alert>
      {loading && !data ? <Loading /> : data && data.items.length === 0 ? (
        <div className="card"><EmptyState title="No audit entries found" /></div>
      ) : data && (
        <>
          <div className="table-wrap" style={{ opacity: loading ? 0.6 : 1 }}>
            <table className="table">
              <thead><tr><th>When</th><th>User</th><th>Action</th><th>Entity</th><th>Details</th></tr></thead>
              <tbody>
                {data.items.map((a) => (
                  <tr key={a.id}>
                    <td className="small" style={{ whiteSpace: 'nowrap' }}>{fmtDateTime(a.createdAt)}</td>
                    <td>{a.actorUsername || '-'}</td>
                    <td><code className="small">{a.action}</code></td>
                    <td className="small">{a.entityType}{a.entityId ? ` #${a.entityId}` : ''}</td>
                    <td className="small mono" style={{ maxWidth: 420, wordBreak: 'break-word' }}>{a.details || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
        </>
      )}
    </>
  );
}
