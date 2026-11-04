import { Link } from 'react-router-dom';
import { Alert, EmptyState, Loading, PageHeader, StatCard } from '../../components/ui.jsx';
import { useFetch } from '../../hooks/useFetch.js';
import { fixed2, num } from '../../utils/format.js';

export default function StudentDashboard() {
  const { data: d, loading, error, reload } = useFetch('/student/dashboard');
  return (
    <>
      <PageHeader title={d ? `Welcome, ${d.fullName}` : 'Dashboard'} subtitle={d ? `${d.rollNumber} - ${d.departmentName} - Semester ${d.currentSemester}` : undefined} />
      <Alert onRetry={reload}>{error}</Alert>
      {loading && !d ? <Loading /> : d && (d.publishedResults === 0 ? (
        <div className="card"><EmptyState title="No results published yet" message="Results appear here as soon as the examination office publishes them." /></div>
      ) : (
        <>
          <div className="grid stats">
            <StatCard label="Latest SGPA" value={fixed2(d.latestSgpa)} hint={d.latestSessionName} />
            <StatCard label="CGPA" value={fixed2(d.cgpa)} hint="Across all published results" />
            <StatCard label="Credits earned" value={num(d.earnedCredits)} />
            <StatCard label="Published results" value={d.publishedResults} />
          </div>
          <Link className="btn primary" to="/student/results">View semester results</Link>
        </>
      ))}
    </>
  );
}
