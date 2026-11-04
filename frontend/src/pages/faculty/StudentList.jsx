import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { Alert, EmptyState, Loading, PageHeader } from '../../components/ui.jsx';
import { useFetch } from '../../hooks/useFetch.js';

export default function StudentList() {
  const { assignmentId } = useParams();
  const students = useFetch(`/faculty/assignments/${assignmentId}/students`);
  const assignments = useFetch('/faculty/assignments');
  const a = assignments.data?.find((x) => String(x.assignmentId) === assignmentId);
  return (
    <>
      <PageHeader title="Enrolled Students" subtitle={a ? `${a.subjectCode} - ${a.subjectName} (${a.semesterLabel})` : undefined}>
        <Link className="btn" to="/faculty/subjects"><ArrowLeft size={14} /> Back to subjects</Link>
      </PageHeader>
      <Alert onRetry={students.reload}>{students.error}</Alert>
      {students.loading && !students.data ? <Loading /> : students.data && students.data.length === 0 ? (
        <div className="card"><EmptyState title="No students enrolled" message="Enrollment is managed by the administrator." /></div>
      ) : students.data && (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>#</th><th>Roll number</th><th>Name</th></tr></thead>
            <tbody>{students.data.map((s, i) => <tr key={s.studentId}><td>{i + 1}</td><td>{s.rollNumber}</td><td>{s.fullName}</td></tr>)}</tbody>
          </table>
        </div>
      )}
    </>
  );
}
