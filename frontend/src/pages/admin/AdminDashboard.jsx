import { Link } from 'react-router-dom';
import { Alert, Loading, PageHeader, StatCard } from '../../components/ui.jsx';
import { useFetch } from '../../hooks/useFetch.js';

export default function AdminDashboard() {
  const { data, loading, error, reload } = useFetch('/admin/dashboard');
  return (
    <>
      <PageHeader title="Dashboard" subtitle="Live counts from the college database" />
      <Alert onRetry={reload}>{error}</Alert>
      {loading && !data && <Loading />}
      {data && (
        <>
          <div className="grid stats">
            <StatCard label="Active students" value={data.students} />
            <StatCard label="Active faculty" value={data.faculty} />
            <StatCard label="Departments" value={data.departments} />
            <StatCard label="Active subjects" value={data.subjects} />
            <StatCard label="Open examination sessions" value={data.openSessions} />
            <StatCard label="Marks awaiting review" value={data.marksAwaitingReview} hint="Submitted by faculty" />
            <StatCard label="Published results" value={data.publishedResults} />
          </div>
          {data.marksAwaitingReview > 0 && (
            <Alert kind="info">
              {data.marksAwaitingReview} mark entr{data.marksAwaitingReview === 1 ? 'y is' : 'ies are'} waiting for approval.{' '}
              <Link to="/admin/marks-review?status=SUBMITTED">Review now</Link>
            </Alert>
          )}
          {!data.activeGradingRule && (
            <Alert kind="warn">
              No grading rule is active, so results cannot be published. <Link to="/admin/grading-rules">Configure grading rules</Link>
            </Alert>
          )}
          {data.activeGradingRule && (
            <div className="card">
              <h2>Active grading rule</h2>
              <p style={{ margin: 0 }}>{data.activeGradingRule} - <Link to="/admin/grading-rules">view or change</Link></p>
            </div>
          )}
        </>
      )}
    </>
  );
}
