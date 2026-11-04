import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Plus, Trash2, Users } from 'lucide-react';
import { api, errorMessage } from '../../api/client.js';
import {
  Alert, ConfirmModal, EmptyState, FormField, Loading, Modal, PageHeader, Pagination, SearchBox, SelectFilter,
} from '../../components/ui.jsx';
import { useToast } from '../../components/Toast.jsx';
import { useDebounced, useFetch } from '../../hooks/useFetch.js';
import { selectId } from '../../utils/schemas.js';

const schema = z.object({
  departmentId: z.string(),
  semesterId: selectId('Semester'),
  studentId: selectId('Student'),
  subjectId: selectId('Subject'),
});

function EnrollModal({ onClose, onSaved }) {
  const [serverError, setServerError] = useState('');
  const { register, handleSubmit, watch, setValue, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(schema), defaultValues: { departmentId: '', semesterId: '', studentId: '', subjectId: '' },
  });
  const departmentId = watch('departmentId');
  const semesterId = watch('semesterId');
  const departments = useFetch('/admin/options/departments');
  const semesters = useFetch('/admin/options/semesters');
  const semesterNumber = semesters.data?.find((s) => String(s.id) === semesterId)?.meta ?? 0;
  const students = useFetch('/admin/options/students', { departmentId: departmentId || 0 });
  const subjects = useFetch('/admin/options/subjects', { departmentId: departmentId || 0, semesterNumber });
  useEffect(() => { setValue('studentId', ''); setValue('subjectId', ''); }, [departmentId, setValue]);
  useEffect(() => setValue('subjectId', ''), [semesterId, setValue]);

  const submit = async (v) => {
    setServerError('');
    try {
      await api.post('/admin/enrollments', { studentId: Number(v.studentId), subjectId: Number(v.subjectId), semesterId: Number(v.semesterId) });
      onSaved();
    } catch (e) {
      setServerError(errorMessage(e));
    }
  };
  const opts = (list) => (list || []).map((o) => <option key={o.id} value={o.id}>{o.label}</option>);

  return (
    <Modal title="Enroll a student in a subject" onClose={onClose}
      footer={<>
        <button type="button" className="btn" onClick={onClose}>Cancel</button>
        <button type="submit" form="enroll-form" className="btn primary" disabled={isSubmitting}>Enroll</button>
      </>}>
      <Alert>{serverError}</Alert>
      <form id="enroll-form" onSubmit={handleSubmit(submit)} noValidate>
        <div className="form-grid">
          <FormField label="Department (narrows the lists)">
            <select className="input" {...register('departmentId')}><option value="">All departments</option>{opts(departments.data)}</select>
          </FormField>
          <FormField label="Semester" error={errors.semesterId?.message} required>
            <select className="input" {...register('semesterId')}><option value="">Select...</option>{opts(semesters.data)}</select>
          </FormField>
          <FormField label="Student" error={errors.studentId?.message} required>
            <select className="input" {...register('studentId')}><option value="">Select...</option>{opts(students.data)}</select>
          </FormField>
          <FormField label="Subject" error={errors.subjectId?.message} required help={semesterNumber ? `Curriculum semester ${semesterNumber}` : 'Choose a semester first'}>
            <select className="input" {...register('subjectId')} disabled={!semesterId}><option value="">Select...</option>{opts(subjects.data)}</select>
          </FormField>
        </div>
      </form>
    </Modal>
  );
}

function BulkEnroll({ onDone }) {
  const toast = useToast();
  const semesters = useFetch('/admin/options/semesters');
  const departments = useFetch('/admin/options/departments');
  const [semesterId, setSemesterId] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [busy, setBusy] = useState(false);

  const run = async () => {
    setBusy(true);
    try {
      const r = await api.post('/admin/enrollments/bulk', { semesterId: Number(semesterId), departmentId: Number(departmentId) });
      toast.success(r.newEnrollments ? `${r.newEnrollments} enrollment(s) created` : 'Everyone eligible is already enrolled');
      onDone();
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="card">
      <h2><Users size={16} style={{ verticalAlign: '-3px' }} /> Bulk enrollment</h2>
      <p className="muted small" style={{ marginTop: 0 }}>
        Enrolls every active student of the chosen department whose current semester equals the semester number, in all
        active subjects of that semester. Students who are already enrolled are skipped.
      </p>
      <div className="toolbar" style={{ marginBottom: 0 }}>
        <SelectFilter label="Semester" value={semesterId} onChange={setSemesterId} options={semesters.data} allLabel="Select semester" />
        <SelectFilter label="Department" value={departmentId} onChange={setDepartmentId} options={departments.data} allLabel="Select department" />
        <button type="button" className="btn primary" disabled={!semesterId || !departmentId || busy} onClick={run}>
          {busy ? 'Enrolling...' : 'Enroll eligible students'}
        </button>
      </div>
    </div>
  );
}

export default function EnrollmentsPage() {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [semesterId, setSemesterId] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [modal, setModal] = useState(null);
  const [busy, setBusy] = useState(false);
  const debounced = useDebounced(search);
  const semesters = useFetch('/admin/options/semesters');
  const departments = useFetch('/admin/options/departments');
  const params = { page, size: 15, search: debounced, ...(semesterId && { semesterId }), ...(departmentId && { departmentId }) };
  const { data, loading, error, reload } = useFetch('/admin/enrollments', params);
  useEffect(() => setPage(0), [debounced, semesterId, departmentId]);

  const remove = async () => {
    setBusy(true);
    try {
      await api.del(`/admin/enrollments/${modal.row.id}`);
      toast.success('Enrollment removed');
      reload();
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
      setModal(null);
    }
  };

  return (
    <>
      <PageHeader title="Enrollments" subtitle="Which students take which subjects in a semester (drives marks entry and result publication)">
        <button type="button" className="btn primary" onClick={() => setModal({ type: 'new' })}><Plus size={16} /> Enroll student</button>
      </PageHeader>
      <BulkEnroll onDone={reload} />
      <div className="toolbar">
        <SearchBox value={search} onChange={setSearch} placeholder="Roll number or name" />
        <SelectFilter label="Semester" value={semesterId} onChange={setSemesterId} options={semesters.data} />
        <SelectFilter label="Department" value={departmentId} onChange={setDepartmentId} options={departments.data} />
      </div>
      <Alert onRetry={reload}>{error}</Alert>
      {loading && !data ? <Loading /> : data && data.items.length === 0 ? (
        <div className="card"><EmptyState title="No enrollments found" message="Use bulk enrollment or enroll a student individually." /></div>
      ) : data && (
        <>
          <div className="table-wrap" style={{ opacity: loading ? 0.6 : 1 }}>
            <table className="table">
              <thead><tr><th>Roll no.</th><th>Student</th><th>Subject</th><th>Semester</th><th /></tr></thead>
              <tbody>
                {data.items.map((e) => (
                  <tr key={e.id}>
                    <td>{e.rollNumber}</td><td>{e.studentName}</td>
                    <td><strong>{e.subjectCode}</strong> {e.subjectName}</td><td>{e.semesterLabel}</td>
                    <td className="actions">
                      <button type="button" className="btn sm danger" onClick={() => setModal({ type: 'delete', row: e })}><Trash2 size={13} /> Remove</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
        </>
      )}
      {modal?.type === 'new' && <EnrollModal onClose={() => setModal(null)} onSaved={() => { toast.success('Student enrolled'); setModal(null); reload(); }} />}
      {modal?.type === 'delete' && (
        <ConfirmModal title="Remove enrollment" danger busy={busy} confirmLabel="Remove" onConfirm={remove} onClose={() => setModal(null)}
          message={`Remove ${modal.row.studentName} from ${modal.row.subjectCode}? This is blocked once marks exist.`} />
      )}
    </>
  );
}
