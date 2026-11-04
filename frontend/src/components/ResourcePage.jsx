import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Pencil, Plus, Power } from 'lucide-react';
import { api, errorMessage, fieldErrors } from '../api/client.js';
import { useDebounced, useFetch } from '../hooks/useFetch.js';
import {
  ActiveBadge, Alert, ConfirmModal, EmptyState, FormField, Loading, Modal, PageHeader, Pagination, SearchBox, SelectFilter,
} from './ui.jsx';
import { useToast } from './Toast.jsx';

function FieldInput({ field, register, error, mode }) {
  const { data } = useFetch(field.optionsUrl || null);
  const options = field.options || data || [];
  const readOnly = mode === 'edit' && field.readOnlyOnEdit;
  const common = { className: `input${error ? ' invalid' : ''}`, disabled: readOnly, ...register(field.name) };

  let control;
  if (field.type === 'select') {
    control = (
      <select {...common}>
        <option value="">Select...</option>
        {options.map((o) => (
          <option key={o.id ?? o.value} value={o.id ?? o.value}>{o.label}</option>
        ))}
      </select>
    );
  } else if (field.type === 'textarea') {
    control = <textarea rows={3} {...common} />;
  } else {
    control = (
      <input
        type={field.type || 'text'}
        step={field.type === 'number' ? 'any' : undefined}
        autoComplete={field.type === 'password' ? 'new-password' : 'off'}
        {...common}
      />
    );
  }
  return (
    <div style={field.full ? { gridColumn: '1 / -1' } : undefined}>
      <FormField label={field.label} error={error} help={field.help} required={field.required !== false && !field.optional}>
        {control}
      </FormField>
    </div>
  );
}

function ResourceForm({ title, mode, fields, schema, defaults, onSubmit, onClose }) {
  const [serverError, setServerError] = useState('');
  const visible = fields.filter((f) => (mode === 'create' ? f.create !== false : f.edit !== false));
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(schema),
    defaultValues: defaults,
  });

  const submit = async (values) => {
    setServerError('');
    try {
      await onSubmit(values);
    } catch (e) {
      const fe = fieldErrors(e);
      let matched = false;
      Object.entries(fe).forEach(([name, message]) => {
        if (visible.some((f) => f.name === name || f.serverName === name)) {
          const target = visible.find((f) => f.name === name || f.serverName === name).name;
          setError(target, { message });
          matched = true;
        }
      });
      setServerError(matched ? 'Please correct the highlighted fields.' : errorMessage(e));
    }
  };

  return (
    <Modal
      title={title}
      onClose={onClose}
      wide
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" form="resource-form" className="btn primary" disabled={isSubmitting}>
            {isSubmitting ? 'Saving...' : 'Save'}
          </button>
        </>
      }
    >
      <Alert>{serverError}</Alert>
      <form id="resource-form" onSubmit={handleSubmit(submit)} noValidate>
        <div className="form-grid">
          {visible.map((f) => (
            <FieldInput key={f.name} field={f} register={register} error={errors[f.name]?.message} mode={mode} />
          ))}
        </div>
      </form>
    </Modal>
  );
}

/**
 * Config driven list + create/edit page used by the simple admin screens. Every row, filter and mutation goes
 * through the real REST endpoint; nothing is held locally.
 */
export default function ResourcePage({
  title, subtitle, singular, endpoint, columns, fields, createSchema, editSchema, emptyValues, toForm, toPayload,
  activeToggle = false, filters = [], searchPlaceholder, pageSize = 10, canEdit = true, canCreate = true,
}) {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [filterValues, setFilterValues] = useState({});
  const [form, setForm] = useState(null); // { mode, row }
  const [confirm, setConfirm] = useState(null);
  const [busy, setBusy] = useState(false);
  const debounced = useDebounced(search);

  const params = { page, size: pageSize, search: debounced, ...Object.fromEntries(Object.entries(filterValues).filter(([, v]) => v !== '')) };
  const { data, loading, error, reload } = useFetch(endpoint, params);

  useEffect(() => setPage(0), [debounced, JSON.stringify(filterValues)]);

  const save = async (values) => {
    const payload = toPayload(values, form.mode);
    if (form.mode === 'create') {
      await api.post(endpoint, payload);
      toast.success(`${singular} created`);
    } else {
      await api.put(`${endpoint}/${form.row.id}`, payload);
      toast.success(`${singular} updated`);
    }
    setForm(null);
    reload();
  };

  const toggleActive = async () => {
    setBusy(true);
    try {
      await api.patch(`${endpoint}/${confirm.id}/active`, { active: !confirm.active });
      toast.success(`${singular} ${confirm.active ? 'deactivated' : 'activated'}`);
      setConfirm(null);
      reload();
    } catch (e) {
      toast.error(errorMessage(e));
      setConfirm(null);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader title={title} subtitle={subtitle}>
        {canCreate && (
          <button type="button" className="btn primary" onClick={() => setForm({ mode: 'create' })}>
            <Plus size={16} /> Add {singular.toLowerCase()}
          </button>
        )}
      </PageHeader>

      <div className="toolbar">
        <SearchBox value={search} onChange={setSearch} placeholder={searchPlaceholder} />
        {filters.map((f) => (
          <FilterControl key={f.name} filter={f} value={filterValues[f.name] ?? ''}
            onChange={(v) => setFilterValues((s) => ({ ...s, [f.name]: v }))} />
        ))}
      </div>

      <Alert onRetry={reload}>{error}</Alert>
      {loading && !data ? (
        <Loading />
      ) : data && data.items.length === 0 ? (
        <div className="card"><EmptyState title={`No ${title.toLowerCase()} found`}
          message={search || Object.keys(filterValues).length ? 'Try changing the search or filters.' : `Add the first ${singular.toLowerCase()} to get started.`} /></div>
      ) : data ? (
        <>
          <div className="table-wrap" style={{ opacity: loading ? 0.6 : 1 }}>
            <table className="table">
              <thead>
                <tr>
                  {columns.map((c) => <th key={c.key}>{c.label}</th>)}
                  {(canEdit || activeToggle) && <th aria-label="Actions" />}
                </tr>
              </thead>
              <tbody>
                {data.items.map((row) => (
                  <tr key={row.id}>
                    {columns.map((c) => <td key={c.key}>{c.render ? c.render(row) : (row[c.key] ?? '-')}</td>)}
                    {(canEdit || activeToggle) && (
                      <td className="actions">
                        <div className="row" style={{ justifyContent: 'flex-end', flexWrap: 'nowrap' }}>
                          {canEdit && (
                            <button type="button" className="btn sm" onClick={() => setForm({ mode: 'edit', row })}>
                              <Pencil size={13} /> Edit
                            </button>
                          )}
                          {activeToggle && (
                            <button type="button" className={`btn sm${row.active ? ' danger' : ''}`} onClick={() => setConfirm(row)}>
                              <Power size={13} /> {row.active ? 'Deactivate' : 'Activate'}
                            </button>
                          )}
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
        </>
      ) : null}

      {form && (
        <ResourceForm
          key={`${form.mode}-${form.row?.id ?? 'new'}`}
          title={form.mode === 'create' ? `Add ${singular.toLowerCase()}` : `Edit ${singular.toLowerCase()}`}
          mode={form.mode}
          fields={fields}
          schema={form.mode === 'create' ? createSchema : editSchema || createSchema}
          defaults={form.mode === 'create' ? emptyValues : toForm(form.row)}
          onSubmit={save}
          onClose={() => setForm(null)}
        />
      )}
      {confirm && (
        <ConfirmModal
          title={`${confirm.active ? 'Deactivate' : 'Activate'} ${singular.toLowerCase()}`}
          message={confirm.active
            ? `Deactivate this ${singular.toLowerCase()}? ${activeNote(singular)}`
            : `Activate this ${singular.toLowerCase()} again?`}
          confirmLabel={confirm.active ? 'Deactivate' : 'Activate'}
          danger={confirm.active}
          busy={busy}
          onConfirm={toggleActive}
          onClose={() => setConfirm(null)}
        />
      )}
    </>
  );
}

function activeNote(singular) {
  if (['Student', 'Faculty member'].includes(singular)) return 'They will no longer be able to sign in. Historical records are kept.';
  return 'It will no longer be offered in new selections. Existing records are kept.';
}

function FilterControl({ filter, value, onChange }) {
  const { data } = useFetch(filter.optionsUrl || null);
  return <SelectFilter label={filter.label} value={value} onChange={onChange} options={filter.options || data || []} allLabel={filter.allLabel} />;
}

export { ActiveBadge };
