import { Link } from 'react-router-dom';
import { ListChecks, Users } from 'lucide-react';
import { Alert, Badge, EmptyState, Loading, PageHeader } from '../../components/ui.jsx';
import { useFetch } from '../../hooks/useFetch.js';

export default function AssignedSubjects() {
  const { data, loading, error, reload } = useFetch('/faculty/assignments');
  return (
    <>
      <PageHeader title="Assigned Subjects" subtitle="You can only see and edit marks for these subjects" />
      <Alert onRetry={reload}>{error}</Alert>
      {loading && !data ? <Loading /> : data && data.length === 0 ? (
        <div className="card"><EmptyState title="No subjects assigned" message="Ask the administrator to assign subjects to you." /></div>
      ) : data && (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Subject</th><th>Semester</th><th>Enrolled</th><th>Examinations</th><th /></tr></thead>
            <tbody>
              {data.map((a) => (
                <tr key={a.assignmentId}>
                  <td><strong>{a.subjectCode}</strong><div className="muted small">{a.subjectName}</div></td>
                  <td>{a.semesterLabel}</td><td>{a.enrolledStudents}</td>
                  <td>{a.sessions.length === 0 ? <span className="muted">None scheduled</span> : a.sessions.map((s) => (
                    <div key={s.id} className="small">{s.name} <Badge tone={s.status === 'OPEN' ? 'green' : ''}>{s.status}</Badge></div>
                  ))}</td>
                  <td className="actions">
                    <div className="row" style={{ justifyContent: 'flex-end', flexWrap: 'nowrap' }}>
                      <Link className="btn sm" to={`/faculty/subjects/${a.assignmentId}/students`}><Users size={13} /> Students</Link>
                      <Link className="btn sm primary" to={`/faculty/marks-entry?assignment=${a.assignmentId}`}><ListChecks size={13} /> Marks</Link>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
