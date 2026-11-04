import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Alert, EmptyState, Loading, PageHeader, SelectFilter } from '../../components/ui.jsx';
import { useFetch } from '../../hooks/useFetch.js';
import { fixed2, num } from '../../utils/format.js';

function Section({ title, state, children, emptyText }) {
  return (
    <div className="card">
      <h2>{title}</h2>
      <Alert onRetry={state.reload}>{state.error}</Alert>
      {state.loading && !state.data ? <Loading /> : state.data && state.data.length === 0 ? <EmptyState title="No data" message={emptyText} /> : state.data ? children : null}
    </div>
  );
}

export default function ReportsPage() {
  const [sessionId, setSessionId] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const sessions = useFetch('/admin/options/examination-sessions');
  const departments = useFetch('/admin/options/departments');
  const q = sessionId ? { sessionId } : null;
  const deptSummary = useFetch(q ? '/admin/reports/department-summary' : null, q);
  const subjects = useFetch(q ? '/admin/reports/subject-analysis' : null, q && { ...q, ...(departmentId && { departmentId }) });
  const grades = useFetch(q ? '/admin/reports/grade-distribution' : null, q && { ...q, ...(departmentId && { departmentId }) });
  const marksStatus = useFetch(q ? '/admin/reports/marks-status' : null, q);
  const none = 'Results must be published for this examination before result statistics appear.';

  return (
    <>
      <PageHeader title="Reports" subtitle="Calculated from published results and recorded marks" />
      <div className="toolbar">
        <SelectFilter label="Examination session" value={sessionId} onChange={setSessionId} options={sessions.data} allLabel="Select session" />
        <SelectFilter label="Department (subject & grade reports)" value={departmentId} onChange={setDepartmentId} options={departments.data} allLabel="All departments" />
      </div>
      {!sessionId ? <div className="card"><EmptyState title="Select an examination session" /></div> : (
        <>
          <Section title="Department result summary" state={deptSummary} emptyText={none}>
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>Department</th><th>Students</th><th>Passed</th><th>Failed</th><th>Pass %</th><th>Average SGPA</th></tr></thead>
                <tbody>
                  {(deptSummary.data || []).map((d) => (
                    <tr key={d.departmentId}><td>{d.departmentCode} - {d.departmentName}</td><td>{d.students}</td><td>{d.passed}</td><td>{d.failed}</td>
                      <td className="mono">{fixed2(d.passPercentage)}</td><td className="mono">{fixed2(d.averageSgpa)}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="chart-box" style={{ marginTop: 14 }} role="img" aria-label="Pass percentage by department">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={(deptSummary.data || []).map((d) => ({ name: d.departmentCode, 'Pass %': Number(d.passPercentage) }))}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="name" /><YAxis domain={[0, 100]} unit="%" /><Tooltip />
                  <Bar dataKey="Pass %" fill="#1f6feb" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </Section>

          <Section title="Subject-wise performance" state={subjects} emptyText={none}>
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>Subject</th><th>Appeared</th><th>Passed</th><th>Failed</th><th>Pass %</th><th>Average total</th><th>Highest</th></tr></thead>
                <tbody>
                  {(subjects.data || []).map((s) => (
                    <tr key={s.subjectCode}><td><strong>{s.subjectCode}</strong> {s.subjectName}</td><td>{s.appeared}</td><td>{s.passed}</td><td>{s.failed}</td>
                      <td className="mono">{fixed2(s.passPercentage)}</td><td className="mono">{fixed2(s.averageTotal)} / {num(s.maxTotal)}</td><td className="mono">{num(s.highestTotal)}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Section>

          <Section title="Grade distribution" state={grades} emptyText={none}>
            <div className="chart-box" role="img" aria-label="Number of subject results per grade">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={grades.data || []}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="grade" /><YAxis allowDecimals={false} /><Tooltip />
                  <Bar dataKey="count" name="Subject results" fill="#14305a" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </Section>

          <Section title="Marks submission progress" state={marksStatus} emptyText="No marks have been entered for this examination yet.">
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>Department</th><th>Draft</th><th>Submitted</th><th>Approved</th><th>Rejected</th><th>Published</th></tr></thead>
                <tbody>
                  {(marksStatus.data || []).map((m) => (
                    <tr key={m.departmentCode}><td>{m.departmentCode} - {m.departmentName}</td><td>{m.draft}</td><td>{m.submitted}</td><td>{m.approved}</td><td>{m.rejected}</td><td>{m.published}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Section>
        </>
      )}
    </>
  );
}
