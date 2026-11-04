import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Download, Printer } from 'lucide-react';
import { errorMessage } from '../../api/client.js';
import { Alert, Badge, Loading, PageHeader, StatusBadge } from '../../components/ui.jsx';
import { useToast } from '../../components/Toast.jsx';
import { useFetch } from '../../hooks/useFetch.js';
import { fixed2, fmtDate, num } from '../../utils/format.js';
import { downloadMarksheet } from '../../utils/marksheet.js';

export default function ResultDetails() {
  const { resultId } = useParams();
  const toast = useToast();
  const { data: r, loading, error, reload } = useFetch(`/student/results/${resultId}`);
  const [busy, setBusy] = useState(false);

  const download = async () => {
    setBusy(true);
    try {
      await downloadMarksheet(resultId);
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader title="Result Details" subtitle={r ? `${r.sessionName} - ${r.academicYear}, semester ${r.semesterNumber}` : undefined}>
        <Link className="btn no-print" to="/student/results"><ArrowLeft size={14} /> All results</Link>
        <button type="button" className="btn no-print" onClick={() => window.print()} disabled={!r}><Printer size={14} /> Print</button>
        <button type="button" className="btn primary no-print" onClick={download} disabled={!r || busy}><Download size={14} /> {busy ? 'Preparing...' : 'Download mark sheet (PDF)'}</button>
      </PageHeader>
      <Alert onRetry={reload}>{error}</Alert>
      {loading && !r ? <Loading /> : r && (
        <>
          <div className="card">
            <dl className="kv">
              <dt>Name</dt><dd>{r.studentName}</dd>
              <dt>Roll number</dt><dd>{r.rollNumber}</dd>
              <dt>Department</dt><dd>{r.departmentName}</dd>
              <dt>Published</dt><dd>{fmtDate(r.publishedAt)}{r.revision > 1 && <> <Badge tone="amber">Revision {r.revision}</Badge></>}</dd>
            </dl>
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead><tr><th>Code</th><th>Subject</th><th>Credits</th><th>Internal</th><th>External</th><th>Total</th><th>%</th><th>Grade</th><th>Grade point</th><th>Result</th></tr></thead>
              <tbody>
                {r.subjects.map((s) => (
                  <tr key={s.subjectCode}>
                    <td>{s.subjectCode}</td><td>{s.subjectName}</td><td className="mono">{num(s.credits)}</td>
                    <td className="mono">{num(s.internalMarks)}</td><td className="mono">{num(s.externalMarks)}</td>
                    <td className="mono">{num(s.totalMarks)} / {num(s.maxMarks)}</td><td className="mono">{fixed2(s.percentage)}</td>
                    <td><strong>{s.grade}</strong></td><td className="mono">{fixed2(s.gradePoint)}</td>
                    <td>{s.passed ? <Badge tone="green">Pass</Badge> : <Badge tone="red">Fail</Badge>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="card" style={{ marginTop: 16 }}>
            <div className="result-total">
              <div><span className="muted small">Credits earned / total</span><strong>{num(r.earnedCredits)} / {num(r.totalCredits)}</strong></div>
              <div><span className="muted small">SGPA</span><strong>{fixed2(r.sgpa)}</strong></div>
              <div><span className="muted small">CGPA</span><strong>{fixed2(r.cgpa)}</strong></div>
              <div><span className="muted small">Result</span><StatusBadge status={r.resultStatus} /></div>
            </div>
            {r.gradingRuleIsSample && <p className="small muted" style={{ marginBottom: 0 }}>Grading rule "{r.gradingRule}" is a sample placeholder, not an official regulation.</p>}
          </div>
        </>
      )}
    </>
  );
}
