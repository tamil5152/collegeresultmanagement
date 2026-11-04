import { useState } from 'react';
import { Download } from 'lucide-react';
import { errorMessage } from '../../api/client.js';
import { Alert, EmptyState, Loading, PageHeader } from '../../components/ui.jsx';
import { useToast } from '../../components/Toast.jsx';
import { useFetch } from '../../hooks/useFetch.js';
import { fixed2 } from '../../utils/format.js';
import { downloadMarksheet } from '../../utils/marksheet.js';

export default function DownloadMarksheet() {
  const toast = useToast();
  const { data, loading, error, reload } = useFetch('/student/results');
  const [busyId, setBusyId] = useState(null);

  const download = async (id) => {
    setBusyId(id);
    try {
      await downloadMarksheet(id);
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <>
      <PageHeader title="Download Mark Sheet" subtitle="Printable PDF statement of marks for each published semester" />
      <Alert onRetry={reload}>{error}</Alert>
      {loading && !data ? <Loading /> : data && data.length === 0 ? (
        <div className="card"><EmptyState title="No mark sheets available" message="A mark sheet becomes available once your result is published." /></div>
      ) : data && (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Examination</th><th>Academic year</th><th>Semester</th><th>SGPA</th><th /></tr></thead>
            <tbody>
              {data.map((r) => (
                <tr key={r.resultId}>
                  <td>{r.sessionName}</td><td>{r.academicYear}</td><td>{r.semesterNumber}</td><td className="mono">{fixed2(r.sgpa)}</td>
                  <td className="actions">
                    <button type="button" className="btn sm primary" onClick={() => download(r.resultId)} disabled={busyId === r.resultId}>
                      <Download size={13} /> {busyId === r.resultId ? 'Preparing...' : 'Download PDF'}
                    </button>
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
