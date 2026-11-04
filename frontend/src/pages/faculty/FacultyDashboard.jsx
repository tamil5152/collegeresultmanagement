import { Link } from 'react-router-dom';
import { Alert, EmptyState, Loading, PageHeader, StatCard } from '../../components/ui.jsx';
import { useFetch } from '../../hooks/useFetch.js';

export default function FacultyDashboard() {
  const stats = useFetch('/faculty/dashboard');
  const assignments = useFetch('/faculty/assignments');
  const d = stats.data;
  return (
    <>
      <PageHeader title="Dashboard" subtitle="Your subjects and marks status" />
      <Alert onRetry={stats.reload}>{stats.error}</Alert>
      {stats.loading && !d ? <Loading /> : d && (
        <div className="grid stats">
          <StatCard label="Assigned subjects" value={d.assignedSubjects} />
          <StatCard label="Open examination sessions" value={d.openSessions} />
          <StatCard label="Drafts / sent back" value={d.draftOrRejected} hint="Still editable by you" />
          <StatCard label="Awaiting approval" value={d.submitted} />
          <StatCard label="Approved" value={d.approved} />
          <StatCard label="Published" value={d.published} />
        </div>
      )}
      <div className="card">
        <h2>Marks entry shortcuts</h2>
        <Alert onRetry={assignments.reload}>{assignments.error}</Alert>
        {assignments.loading && !assignments.data ? <Loading /> : assignments.data && assignments.data.length === 0 ? (
          <EmptyState title="No subjects assigned" message="The administrator has not assigned any subject to you yet." />
        ) : assignments.data && (
          <div className="table-wrap">
            <table className="table">
              <thead><tr><th>Subject</th><th>Semester</th><th>Students</th><th>Open sessions</th></tr></thead>
              <tbody>
                {assignments.data.map((a) => (
                  <tr key={a.assignmentId}>
                    <td><strong>{a.subjectCode}</strong> {a.subjectName}</td><td>{a.semesterLabel}</td><td>{a.enrolledStudents}</td>
                    <td>{a.sessions.filter((s) => s.status === 'OPEN').length === 0 ? <span className="muted">None open</span> :
                      a.sessions.filter((s) => s.status === 'OPEN').map((s) => (
                        <Link key={s.id} to={`/faculty/marks-entry?assignment=${a.assignmentId}&session=${s.id}`}>{s.name}</Link>
                      ))}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}
