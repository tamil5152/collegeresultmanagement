import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Plus, Trash2, UserCog } from 'lucide-react';
import { api, errorMessage } from '../../api/client.js';
import {
  Alert, ConfirmModal, EmptyState, FormField, Loading, Modal, PageHeader, Pagination, SearchBox, SelectFilter,
} from '../../components/ui.jsx';
import { useToast } from '../../components/Toast.jsx';
import { useDebounced, useFetch } from '../../hooks/useFetch.js';
import { selectId } from '../../utils/schemas.js';

const createSchema = z.object({
  departmentId: z.string(),
  semesterId: selectId('Semester'),
  subjectId: selectId('Subject'),
  facultyId: selectId('Faculty member'),
});
const changeSchema = z.object({ facultyId: selectId('Faculty member') });

function AssignModal({ onClose, onSaved }) {
  const [serverError, setServerError] = useState('');
  const { register, handleSubmit, watch, setValue, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(createSchema),
    defaultValues: { departmentId: '', semesterId: '', subjectId: '', facultyId: '' },
  });
  const departmentId = watch('departmentId');
  const semesterId = watch('semesterId');

  const departments = useFetch('/admin/options/departments');
  const semesters = useFetch('/admin/options/semesters');
  const semesterNumber = semesters.data?.find((s) => String(s.id) === semesterId)?.meta ?? 0;
  const subjects = useFetch('/admin/options/subjects', { departmentId: departmentId || 0, semesterNumber });
  const faculty = useFetch('/admin/options/faculty', { departmentId: departmentId || 0 });

  useEffect(() => setValue('subjectId', ''), [departmentId, semesterId, setValue]);

  const submit = async (v) => {
    setServerError('');
    try {
      await api.post('/admin/assignments', {
        subjectId: Number(v.subjectId), facultyId: Number(v.facultyId), semesterId: Number(v.semesterId),
      });
      onSaved();
    } catch (e) {
      setServerError(errorMessage(e));
    }
  };

  const opts = (list) => (list || []).map((o) => <option key={o.id} value={o.id}>{o.label}</option>);

  return (
    <Modal title="Assign faculty to subject" onClose={onClose}
      footer={<>
        <button type="button" className="btn" onClick={onClose}>Cancel</button>
        <button type="submit" form="assign-form" className="btn primary" disabled={isSubmitting}>{isSubmitting ? 'Saving...' : 'Assign'}</button>
      </>}>
      <Alert>{serverError}</Alert>
      <form id="assign-form" onSubmit={handleSubmit(submit)} noValidate>
        <div className="form-grid">
          <FormField label="Department (narrows the lists)">
            <select className="input" {...register('departmentId')}>
              <option value="">All departments</option>{opts(departments.data)}
            </select>
          </FormField>
          <FormField label="Semester" error={errors.semesterId?.message} required>
            <select className="input" {...register('semesterId')}>
              <option value="">Select...</option>{opts(semesters.data)}
            </select>
          </FormField>
          <FormField label="Subject" error={errors.subjectId?.message} required
            help={semesterNumber ? `Showing subjects of curriculum semester ${semesterNumber}` : 'Choose a semester first'}>
            <select className="input" {...register('subjectId')} disabled={!semesterId}>
              <option value="">Select...</option>{opts(subjects.data)}
            </select>
          </FormField>
          <FormField label="Faculty member" error={errors.facultyId?.message} required>
            <select className="input" {...register('facultyId')}>
              <option value="">Select...</option>{opts(faculty.data)}
            </select>
          </FormField>
        </div>
      </form>
    </Modal>
  );
}

function ChangeModal({ row, onClose, onSaved }) {
  const [serverError, setServerError] = useState('');
  const faculty = useFetch('/admin/options/faculty', { departmentId: 0 });
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(changeSchema), defaultValues: { facultyId: String(row.facultyId) },
  });
  const submit = async (v) => {
    setServerError('');
    try {
      await api.put(`/admin/assignments/${row.id}`, { facultyId: Number(v.facultyId) });
      onSaved();
    } catch (e) {
      setServerError(errorMessage(e));
    }
  };
  return (
    <Modal title={`Change faculty for ${row.subjectCode}`} onClose={onClose}
      footer={<>
        <button type="button" className="btn" onClick={onClose}>Cancel</button>
        <button type="submit" form="change-form" className="btn primary" disabled={isSubmitting}>Save</button>
      </>}>
      <Alert>{serverError}</Alert>
      <form id="change-form" onSubmit={handleSubmit(submit)} noValidate>
        <FormField label="Faculty member" error={errors.facultyId?.message} required>
          <select className="input" {...register('facultyId')}>
            <option value="">Select...</option>
            {(faculty.data || []).map((o) => <option key={o.id} value={o.id}>{o.label}</option>)}
          </select>
        </FormField>
      </form>
    </Modal>
  );
}

export default function AssignmentsPage() {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [semesterId, setSemesterId] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [modal, setModal] = useState(null); // {type:'new'} | {type:'change',row} | {type:'delete',row}
  const [busy, setBusy] = useState(false);
  const debounced = useDebounced(search);
  const semesters = useFetch('/admin/options/semesters');
  const departments = useFetch('/admin/options/departments');
  const params = { page, size: 10, search: debounced, ...(semesterId && { semesterId }), ...(departmentId && { departmentId }) };
  const { data, loading, error, reload } = useFetch('/admin/assignments', params);
  useEffect(() => setPage(0), [debounced, semesterId, departmentId]);

  const remove = async () => {
    setBusy(true);
    try {
      await api.del(`/admin/assignments/${modal.row.id}`);
      toast.success('Assignment removed');
      reload();
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
      setModal(null);
    }
  };

  const saved = (msg) => () => { toast.success(msg); setModal(null); reload(); };

  return (
    <>
      <PageHeader title="Faculty Assignments" subtitle="Who teaches (and therefore enters marks for) each subject in a semester">
        <button type="button" className="btn primary" onClick={() => setModal({ type: 'new' })}><Plus size={16} /> Assign faculty</button>
      </PageHeader>
      <div className="toolbar">
        <SearchBox value={search} onChange={setSearch} placeholder="Subject or faculty name" />
        <SelectFilter label="Semester" value={semesterId} onChange={setSemesterId} options={semesters.data} />
        <SelectFilter label="Department" value={departmentId} onChange={setDepartmentId} options={departments.data} />
      </div>
      <Alert onRetry={reload}>{error}</Alert>
      {loading && !data ? <Loading /> : data && data.items.length === 0 ? (
        <div className="card"><EmptyState title="No assignments found" message="Assign a faculty member to a subject to let them enter marks." /></div>
      ) : data && (
        <>
          <div className="table-wrap" style={{ opacity: loading ? 0.6 : 1 }}>
            <table className="table">
              <thead><tr><th>Subject</th><th>Semester</th><th>Faculty</th><th>Enrolled</th><th /></tr></thead>
              <tbody>
                {data.items.map((a) => (
                  <tr key={a.id}>
                    <td><strong>{a.subjectCode}</strong> {a.subjectName}</td>
                    <td>{a.semesterLabel}</td>
                    <td>{a.facultyName} <span className="muted small">({a.employeeCode})</span></td>
                    <td>{a.enrolledStudents}</td>
                    <td className="actions">
                      <div className="row" style={{ justifyContent: 'flex-end', flexWrap: 'nowrap' }}>
                        <button type="button" className="btn sm" onClick={() => setModal({ type: 'change', row: a })}><UserCog size={13} /> Change</button>
                        <button type="button" className="btn sm danger" onClick={() => setModal({ type: 'delete', row: a })}><Trash2 size={13} /> Remove</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
        </>
      )}
      {modal?.type === 'new' && <AssignModal onClose={() => setModal(null)} onSaved={saved('Faculty assigned')} />}
      {modal?.type === 'change' && <ChangeModal row={modal.row} onClose={() => setModal(null)} onSaved={saved('Assignment updated')} />}
      {modal?.type === 'delete' && (
        <ConfirmModal title="Remove assignment" danger busy={busy} confirmLabel="Remove" onConfirm={remove} onClose={() => setModal(null)}
          message={`Remove ${modal.row.facultyName} from ${modal.row.subjectCode}? This is blocked once marks exist for the subject.`} />
      )}
    </>
  );
}
