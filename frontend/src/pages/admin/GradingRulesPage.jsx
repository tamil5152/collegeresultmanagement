import { useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { CheckCircle2, Pencil, Plus, Trash2 } from 'lucide-react';
import { api, errorMessage } from '../../api/client.js';
import { Alert, Badge, ConfirmModal, EmptyState, FormField, Loading, Modal, PageHeader } from '../../components/ui.jsx';
import { useToast } from '../../components/Toast.jsx';
import { useFetch } from '../../hooks/useFetch.js';
import { num } from '../../utils/format.js';
import { numberStr, text } from '../../utils/schemas.js';

const bandSchema = z.object({
  grade: text('Grade', 5),
  minPercentage: numberStr('Minimum %', { min: 0, max: 100 }),
  gradePoint: numberStr('Grade point', { min: 0, max: 99.99 }),
  failGrade: z.boolean(),
});
const schema = z
  .object({
    name: text('Name', 100),
    description: z.string().max(500, 'At most 500 characters'),
    samplePlaceholder: z.boolean(),
    includeFailedInGpa: z.boolean(),
    bands: z.array(bandSchema).min(2, 'Add at least a passing and a failing grade'),
  })
  .superRefine((v, ctx) => {
    if (!v.bands.some((b) => b.failGrade)) ctx.addIssue({ code: 'custom', path: ['bands'], message: 'Mark at least one grade as the fail grade' });
    if (!v.bands.some((b) => !b.failGrade)) ctx.addIssue({ code: 'custom', path: ['bands'], message: 'Add at least one passing grade' });
    const grades = v.bands.map((b) => b.grade.trim().toUpperCase());
    if (new Set(grades).size !== grades.length) ctx.addIssue({ code: 'custom', path: ['bands'], message: 'Grades must be unique' });
    const mins = v.bands.map((b) => Number(b.minPercentage));
    if (new Set(mins).size !== mins.length) ctx.addIssue({ code: 'custom', path: ['bands'], message: 'Two grades share the same minimum percentage' });
  });

const emptyBand = { grade: '', minPercentage: '', gradePoint: '', failGrade: false };

function RuleForm({ rule, onClose, onSaved }) {
  const [serverError, setServerError] = useState('');
  const { register, control, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(schema),
    defaultValues: rule ? {
      name: rule.name, description: rule.description || '', samplePlaceholder: rule.samplePlaceholder,
      includeFailedInGpa: rule.includeFailedInGpa,
      bands: rule.bands.map((b) => ({ grade: b.grade, minPercentage: num(b.minPercentage), gradePoint: num(b.gradePoint), failGrade: b.failGrade })),
    } : {
      name: '', description: '', samplePlaceholder: false, includeFailedInGpa: true,
      bands: [{ ...emptyBand }, { grade: 'F', minPercentage: '0', gradePoint: '0', failGrade: true }],
    },
  });
  const { fields, append, remove } = useFieldArray({ control, name: 'bands' });

  const submit = async (v) => {
    setServerError('');
    const body = {
      name: v.name.trim(), description: v.description.trim() || null, samplePlaceholder: v.samplePlaceholder,
      includeFailedInGpa: v.includeFailedInGpa,
      bands: v.bands.map((b) => ({ grade: b.grade.trim(), minPercentage: Number(b.minPercentage), gradePoint: Number(b.gradePoint), failGrade: b.failGrade })),
    };
    try {
      if (rule) await api.put(`/admin/grading-rules/${rule.id}`, body);
      else await api.post('/admin/grading-rules', body);
      onSaved();
    } catch (e) {
      setServerError(errorMessage(e));
    }
  };

  return (
    <Modal title={rule ? 'Edit grading rule' : 'New grading rule'} wide onClose={onClose}
      footer={<>
        <button type="button" className="btn" onClick={onClose}>Cancel</button>
        <button type="submit" form="rule-form" className="btn primary" disabled={isSubmitting}>{isSubmitting ? 'Saving...' : 'Save rule'}</button>
      </>}>
      <Alert>{serverError}</Alert>
      <form id="rule-form" onSubmit={handleSubmit(submit)} noValidate>
        <div className="form-grid">
          <FormField label="Name" error={errors.name?.message} required><input className="input" {...register('name')} /></FormField>
          <FormField label="Description" error={errors.description?.message}><input className="input" {...register('description')} /></FormField>
        </div>
        <label className="row small" style={{ marginBottom: 6 }}><input type="checkbox" {...register('includeFailedInGpa')} /> Credits of failed subjects count in the GPA denominator</label>
        <label className="row small" style={{ marginBottom: 14 }}><input type="checkbox" {...register('samplePlaceholder')} /> Mark as a sample / placeholder (shown on mark sheets)</label>

        <h2 style={{ fontSize: 15, marginBottom: 6 }}>Grade boundaries</h2>
        <p className="muted small" style={{ marginTop: 0 }}>
          A subject earns the highest grade whose minimum percentage (of its total maximum marks) is reached. Failed subjects
          receive the grade flagged as the fail grade.
        </p>
        {errors.bands?.message && <Alert>{errors.bands.message}</Alert>}
        {errors.bands?.root?.message && <Alert>{errors.bands.root.message}</Alert>}
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Grade</th><th>Min %</th><th>Grade point</th><th>Fail grade</th><th /></tr></thead>
            <tbody>
              {fields.map((f, i) => (
                <tr key={f.id}>
                  <td><input className="cell-input input" style={{ width: 80, margin: 0 }} aria-label="Grade" {...register(`bands.${i}.grade`)} />
                    {errors.bands?.[i]?.grade && <div className="field-error">{errors.bands[i].grade.message}</div>}</td>
                  <td><input className="cell-input input" style={{ width: 90, margin: 0 }} aria-label="Minimum percentage" {...register(`bands.${i}.minPercentage`)} />
                    {errors.bands?.[i]?.minPercentage && <div className="field-error">{errors.bands[i].minPercentage.message}</div>}</td>
                  <td><input className="cell-input input" style={{ width: 90, margin: 0 }} aria-label="Grade point" {...register(`bands.${i}.gradePoint`)} />
                    {errors.bands?.[i]?.gradePoint && <div className="field-error">{errors.bands[i].gradePoint.message}</div>}</td>
                  <td><input type="checkbox" aria-label="Fail grade" {...register(`bands.${i}.failGrade`)} /></td>
                  <td className="actions"><button type="button" className="btn sm danger" onClick={() => remove(i)} aria-label="Remove grade"><Trash2 size={13} /></button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <button type="button" className="btn sm" style={{ marginTop: 8 }} onClick={() => append({ ...emptyBand })}><Plus size={13} /> Add grade</button>
      </form>
    </Modal>
  );
}

export default function GradingRulesPage() {
  const toast = useToast();
  const { data, loading, error, reload } = useFetch('/admin/grading-rules');
  const [form, setForm] = useState(null); // {rule?}
  const [activate, setActivate] = useState(null);
  const [busy, setBusy] = useState(false);

  const doActivate = async () => {
    setBusy(true);
    try {
      await api.post(`/admin/grading-rules/${activate.id}/activate`);
      toast.success(`"${activate.name}" is now the active rule`);
      reload();
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
      setActivate(null);
    }
  };

  return (
    <>
      <PageHeader title="Grading Rules" subtitle="Configurable grade boundaries, grade points and GPA behaviour">
        <button type="button" className="btn primary" onClick={() => setForm({})}><Plus size={16} /> New rule</button>
      </PageHeader>
      <Alert kind="warn">
        The seeded rule is a <strong>sample placeholder</strong>. Replace it with your institution's official regulation
        before publishing real results. Pass criteria and credits are configured per subject.
      </Alert>
      <Alert onRetry={reload}>{error}</Alert>
      {loading && !data ? <Loading /> : data && data.length === 0 ? (
        <div className="card"><EmptyState title="No grading rules yet" message="Create a rule and activate it before publishing results." /></div>
      ) : data && data.map((rule) => (
        <div className="card" key={rule.id}>
          <div className="row" style={{ marginBottom: 8 }}>
            <h2 style={{ margin: 0 }}>{rule.name}</h2>
            {rule.active && <Badge tone="green">Active</Badge>}
            {rule.samplePlaceholder && <Badge tone="amber">Sample placeholder</Badge>}
            {rule.usedByResults && <Badge tone="blue">Used by published results</Badge>}
            <div className="right row">
              {!rule.active && <button type="button" className="btn sm" onClick={() => setActivate(rule)}><CheckCircle2 size={13} /> Activate</button>}
              <button type="button" className="btn sm" disabled={rule.usedByResults} title={rule.usedByResults ? 'Rules used by published results are locked; create a new rule instead' : ''} onClick={() => setForm({ rule })}><Pencil size={13} /> Edit</button>
            </div>
          </div>
          {rule.description && <p className="muted small" style={{ marginTop: 0 }}>{rule.description}</p>}
          <p className="small" style={{ marginTop: 0 }}>Failed subjects' credits {rule.includeFailedInGpa ? 'are' : 'are not'} included in the GPA denominator.</p>
          <div className="table-wrap">
            <table className="table">
              <thead><tr><th>Grade</th><th>Minimum %</th><th>Grade point</th><th>Type</th></tr></thead>
              <tbody>
                {rule.bands.map((b) => (
                  <tr key={b.id}><td><strong>{b.grade}</strong></td><td>{num(b.minPercentage)}</td><td>{num(b.gradePoint)}</td>
                    <td>{b.failGrade ? <Badge tone="red">Fail grade</Badge> : <Badge tone="green">Pass</Badge>}</td></tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ))}
      {form && <RuleForm key={form.rule?.id ?? 'new'} rule={form.rule} onClose={() => setForm(null)}
        onSaved={() => { toast.success('Grading rule saved'); setForm(null); reload(); }} />}
      {activate && (
        <ConfirmModal title="Activate grading rule" busy={busy} confirmLabel="Activate" onConfirm={doActivate} onClose={() => setActivate(null)}
          message={`Use "${activate.name}" for all results published from now on? Results that are already published keep the rule they were published with.`} />
      )}
    </>
  );
}
