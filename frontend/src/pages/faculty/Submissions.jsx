import { Link } from 'react-router-dom';
import { Alert, EmptyState, Loading, PageHeader, StatusBadge } from '../../components/ui.jsx';
import { useFetch } from '../../hooks/useFetch.js';

export default function Submissions() {
  const { data, loading, error, reload } = useFetch('/faculty/submissions');
  return (
    <>
      <PageHeader title="Submitted Marks & Status" subtitle="Where each subject stands in the approval workflow" />
      <Alert onRetry={reload}>{error}</Alert>
      {loading && !data ? <Loading /> : data && data.length === 0 ? (
        <div className="card"><EmptyState title="Nothing to show" message="You have no assigned subjects with examination sessions yet." /></div>
      ) : data && (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Subject</th><th>Examination</th><th>Enrolled</th><th>Not entered</th><th>Draft</th><th>Submitted</th><th>Approved</th><th>Rejected</th><th>Published</th><th /></tr></thead>
            <tbody>
              {data.map((s) => (
                <tr key={`${s.assignmentId}-${s.sessionId}`}>
                  <td><strong>{s.subjectCode}</strong><div className="muted small">{s.subjectName}</div></td>
                  <td className="small">{s.sessionName} <StatusBadge status={s.sessionStatus} /></td>
                  <td>{s.enrolled}</td><td>{s.notEntered}</td><td>{s.draft}</td><td>{s.submitted}</td><td>{s.approved}</td>
                  <td style={s.rejected ? { color: 'var(--danger)', fontWeight: 600 } : undefined}>{s.rejected}</td><td>{s.published}</td>
                  <td className="actions"><Link className="btn sm" to={`/faculty/marks-entry?assignment=${s.assignmentId}&session=${s.sessionId}`}>Open</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
