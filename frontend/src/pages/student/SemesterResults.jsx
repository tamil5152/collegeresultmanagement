import { Link } from 'react-router-dom';
import { Alert, EmptyState, Loading, PageHeader, StatusBadge } from '../../components/ui.jsx';
import { useFetch } from '../../hooks/useFetch.js';
import { fixed2, fmtDate, num } from '../../utils/format.js';

export default function SemesterResults() {
  const { data, loading, error, reload } = useFetch('/student/results');
  return (
    <>
      <PageHeader title="Semester Results" subtitle="Only results published by the examination office are shown" />
      <Alert onRetry={reload}>{error}</Alert>
      {loading && !data ? <Loading /> : data && data.length === 0 ? (
        <div className="card"><EmptyState title="No results published yet" /></div>
      ) : data && (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Examination</th><th>Year</th><th>Sem</th><th>SGPA</th><th>CGPA</th><th>Credits (earned / total)</th><th>Result</th><th>Published</th><th /></tr></thead>
            <tbody>
              {data.map((r) => (
                <tr key={r.resultId}>
                  <td>{r.sessionName}{r.revision > 1 && <span className="muted small"> (revision {r.revision})</span>}</td>
                  <td>{r.academicYear}</td><td>{r.semesterNumber}</td>
                  <td className="mono">{fixed2(r.sgpa)}</td><td className="mono">{fixed2(r.cgpa)}</td>
                  <td className="mono">{num(r.earnedCredits)} / {num(r.totalCredits)}</td>
                  <td><StatusBadge status={r.resultStatus} /></td><td className="small">{fmtDate(r.publishedAt)}</td>
                  <td className="actions"><Link className="btn sm primary" to={`/student/results/${r.resultId}`}>View details</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
