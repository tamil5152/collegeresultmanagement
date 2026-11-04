import { Alert, Loading, PageHeader } from '../../components/ui.jsx';
import { useFetch } from '../../hooks/useFetch.js';
import { fmtDate } from '../../utils/format.js';

export default function StudentProfile() {
  const { data: p, loading, error, reload } = useFetch('/student/profile');
  return (
    <>
      <PageHeader title="My Profile" subtitle="Contact the administration office to correct any detail" />
      <Alert onRetry={reload}>{error}</Alert>
      {loading && !p ? <Loading /> : p && (
        <div className="card">
          <dl className="kv">
            <dt>Name</dt><dd>{p.fullName}</dd>
            <dt>Roll number</dt><dd>{p.rollNumber}</dd>
            <dt>Username</dt><dd>{p.username}</dd>
            <dt>Email</dt><dd>{p.email}</dd>
            <dt>Department</dt><dd>{p.departmentName} ({p.departmentCode})</dd>
            <dt>Batch</dt><dd>{p.batchYear}</dd>
            <dt>Current semester</dt><dd>{p.currentSemester}</dd>
            <dt>Phone</dt><dd>{p.phone || '-'}</dd>
            <dt>Date of birth</dt><dd>{fmtDate(p.dateOfBirth)}</dd>
          </dl>
        </div>
      )}
    </>
  );
}
