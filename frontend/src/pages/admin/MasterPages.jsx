import { z } from 'zod';
import ResourcePage from '../../components/ResourcePage.jsx';
import { ActiveBadge, Badge, StatusBadge } from '../../components/ui.jsx';
import { fmtDate, num } from '../../utils/format.js';
import { dateStr, numberStr, selectId, text, toId, toNum } from '../../utils/schemas.js';

const ACTIVE_FILTER = {
  name: 'active', label: 'Status', allLabel: 'Any status',
  options: [{ id: '1', label: 'Active' }, { id: '0', label: 'Inactive' }],
};

// ---------------------------------------------------------------- departments
const departmentSchema = z.object({ code: text('Code', 20), name: text('Name', 150) });

export function DepartmentsPage() {
  return (
    <ResourcePage
      title="Departments" subtitle="Academic departments of the college" singular="Department"
      endpoint="/admin/departments" activeToggle searchPlaceholder="Code or name"
      columns={[
        { key: 'code', label: 'Code' }, { key: 'name', label: 'Name' },
        { key: 'active', label: 'Status', render: (r) => <ActiveBadge active={r.active} /> },
      ]}
      fields={[
        { name: 'code', label: 'Code', help: 'Short code such as CSE' },
        { name: 'name', label: 'Name' },
      ]}
      createSchema={departmentSchema}
      emptyValues={{ code: '', name: '' }}
      toForm={(r) => ({ code: r.code, name: r.name })}
      toPayload={(v) => ({ code: v.code.trim(), name: v.name.trim() })}
    />
  );
}

// ---------------------------------------------------------------- academic years
const yearSchema = z
  .object({ label: text('Label', 20), startDate: dateStr('Start date'), endDate: dateStr('End date') })
  .refine((v) => !v.startDate || !v.endDate || v.endDate > v.startDate, { path: ['endDate'], message: 'End date must be after the start date' });

export function AcademicYearsPage() {
  return (
    <ResourcePage
      title="Academic Years" subtitle="Years under which semesters and examinations run" singular="Academic year"
      endpoint="/admin/academic-years" activeToggle searchPlaceholder="Label, e.g. 2025-26"
      columns={[
        { key: 'label', label: 'Label' },
        { key: 'startDate', label: 'Starts', render: (r) => fmtDate(r.startDate) },
        { key: 'endDate', label: 'Ends', render: (r) => fmtDate(r.endDate) },
        { key: 'active', label: 'Status', render: (r) => <ActiveBadge active={r.active} /> },
      ]}
      fields={[
        { name: 'label', label: 'Label', help: 'For example 2025-26' },
        { name: 'startDate', label: 'Start date', type: 'date' },
        { name: 'endDate', label: 'End date', type: 'date' },
      ]}
      createSchema={yearSchema}
      emptyValues={{ label: '', startDate: '', endDate: '' }}
      toForm={(r) => ({ label: r.label, startDate: r.startDate, endDate: r.endDate })}
      toPayload={(v) => ({ label: v.label.trim(), startDate: v.startDate, endDate: v.endDate })}
    />
  );
}

// ---------------------------------------------------------------- semesters
const semesterSchema = z
  .object({
    academicYearId: selectId('Academic year'),
    semesterNumber: numberStr('Semester number', { min: 1, max: 12, decimals: 0 }),
    name: text('Name', 100),
    startDate: dateStr('Start date'),
    endDate: dateStr('End date'),
  })
  .refine((v) => !v.startDate || !v.endDate || v.endDate > v.startDate, { path: ['endDate'], message: 'End date must be after the start date' });

export function SemestersPage() {
  return (
    <ResourcePage
      title="Semesters" subtitle="A semester running in a particular academic year" singular="Semester"
      endpoint="/admin/semesters" searchPlaceholder="Name or academic year"
      columns={[
        { key: 'academicYearLabel', label: 'Academic year' },
        { key: 'semesterNumber', label: 'No.' },
        { key: 'name', label: 'Name' },
        { key: 'startDate', label: 'Starts', render: (r) => fmtDate(r.startDate) },
        { key: 'endDate', label: 'Ends', render: (r) => fmtDate(r.endDate) },
      ]}
      fields={[
        { name: 'academicYearId', label: 'Academic year', type: 'select', optionsUrl: '/admin/options/academic-years' },
        { name: 'semesterNumber', label: 'Semester number (1-12)', type: 'number' },
        { name: 'name', label: 'Name', full: true, help: 'For example "Semester 3 (Odd)"' },
        { name: 'startDate', label: 'Start date', type: 'date' },
        { name: 'endDate', label: 'End date', type: 'date' },
      ]}
      createSchema={semesterSchema}
      emptyValues={{ academicYearId: '', semesterNumber: '', name: '', startDate: '', endDate: '' }}
      toForm={(r) => ({
        academicYearId: String(r.academicYearId), semesterNumber: String(r.semesterNumber), name: r.name,
        startDate: r.startDate, endDate: r.endDate,
      })}
      toPayload={(v) => ({
        academicYearId: toId(v.academicYearId), semesterNumber: toNum(v.semesterNumber), name: v.name.trim(),
        startDate: v.startDate, endDate: v.endDate,
      })}
    />
  );
}

// ---------------------------------------------------------------- subjects
const subjectSchema = z
  .object({
    departmentId: selectId('Department'),
    code: text('Code', 20),
    name: text('Name', 150),
    semesterNumber: numberStr('Semester number', { min: 1, max: 12, decimals: 0 }),
    credits: numberStr('Credits', { min: 0.01, max: 99.99 }),
    internalMax: numberStr('Internal maximum', { max: 9999.99 }),
    externalMax: numberStr('External maximum', { max: 9999.99 }),
    internalPass: numberStr('Internal pass mark', { max: 9999.99 }),
    externalPass: numberStr('External pass mark', { max: 9999.99 }),
    totalPass: numberStr('Total pass mark', { max: 9999.99 }),
  })
  .superRefine((v, ctx) => {
    const n = (x) => Number(x);
    if (n(v.internalMax) + n(v.externalMax) <= 0) {
      ctx.addIssue({ code: 'custom', path: ['internalMax'], message: 'Internal and external maximum cannot both be zero' });
    }
    if (n(v.internalPass) > n(v.internalMax)) {
      ctx.addIssue({ code: 'custom', path: ['internalPass'], message: 'Cannot exceed the internal maximum' });
    }
    if (n(v.externalPass) > n(v.externalMax)) {
      ctx.addIssue({ code: 'custom', path: ['externalPass'], message: 'Cannot exceed the external maximum' });
    }
    if (n(v.totalPass) > n(v.internalMax) + n(v.externalMax)) {
      ctx.addIssue({ code: 'custom', path: ['totalPass'], message: 'Cannot exceed the total maximum' });
    }
  });

export function SubjectsPage() {
  return (
    <ResourcePage
      title="Subjects" subtitle="Curriculum subjects with credits, maximum marks and pass criteria" singular="Subject"
      endpoint="/admin/subjects" activeToggle searchPlaceholder="Code or name"
      filters={[{ name: 'departmentId', label: 'Department', optionsUrl: '/admin/options/departments' }]}
      columns={[
        { key: 'code', label: 'Code' }, { key: 'name', label: 'Name' },
        { key: 'departmentCode', label: 'Dept' }, { key: 'semesterNumber', label: 'Sem' },
        { key: 'credits', label: 'Credits', render: (r) => num(r.credits) },
        { key: 'max', label: 'Max (int/ext)', render: (r) => `${num(r.internalMax)} / ${num(r.externalMax)}` },
        { key: 'pass', label: 'Pass (int/ext/total)', render: (r) => `${num(r.internalPass)} / ${num(r.externalPass)} / ${num(r.totalPass)}` },
        { key: 'active', label: 'Status', render: (r) => <ActiveBadge active={r.active} /> },
      ]}
      fields={[
        { name: 'departmentId', label: 'Department', type: 'select', optionsUrl: '/admin/options/departments' },
        { name: 'code', label: 'Code' },
        { name: 'name', label: 'Name', full: true },
        { name: 'semesterNumber', label: 'Curriculum semester (1-12)', type: 'number' },
        { name: 'credits', label: 'Credits', type: 'number' },
        { name: 'internalMax', label: 'Internal maximum marks', type: 'number' },
        { name: 'externalMax', label: 'External maximum marks', type: 'number' },
        { name: 'internalPass', label: 'Internal pass mark', type: 'number', help: 'Minimum internal marks to pass (0 = none)' },
        { name: 'externalPass', label: 'External pass mark', type: 'number', help: 'Minimum external marks to pass (0 = none)' },
        { name: 'totalPass', label: 'Total pass mark', type: 'number', help: 'Minimum internal + external to pass' },
      ]}
      createSchema={subjectSchema}
      emptyValues={{
        departmentId: '', code: '', name: '', semesterNumber: '', credits: '', internalMax: '', externalMax: '',
        internalPass: '0', externalPass: '0', totalPass: '0',
      }}
      toForm={(r) => ({
        departmentId: String(r.departmentId), code: r.code, name: r.name, semesterNumber: String(r.semesterNumber),
        credits: num(r.credits), internalMax: num(r.internalMax), externalMax: num(r.externalMax),
        internalPass: num(r.internalPass), externalPass: num(r.externalPass), totalPass: num(r.totalPass),
      })}
      toPayload={(v) => ({
        departmentId: toId(v.departmentId), code: v.code.trim(), name: v.name.trim(), semesterNumber: toNum(v.semesterNumber),
        credits: toNum(v.credits), internalMax: toNum(v.internalMax), externalMax: toNum(v.externalMax),
        internalPass: toNum(v.internalPass), externalPass: toNum(v.externalPass), totalPass: toNum(v.totalPass),
      })}
    />
  );
}

// ---------------------------------------------------------------- examination sessions
const sessionSchema = z
  .object({
    name: text('Name', 150),
    semesterId: selectId('Semester'),
    startDate: dateStr('Start date'),
    endDate: dateStr('End date'),
    status: z.enum(['OPEN', 'CLOSED']),
  })
  .refine((v) => !v.startDate || !v.endDate || v.endDate >= v.startDate, { path: ['endDate'], message: 'End date cannot be before the start date' });

export function ExamSessionsPage() {
  return (
    <ResourcePage
      title="Examination Sessions" subtitle="Marks can be entered by faculty only while a session is OPEN" singular="Examination session"
      endpoint="/admin/examination-sessions" searchPlaceholder="Session, semester or year"
      columns={[
        { key: 'name', label: 'Session' },
        { key: 'academicYearLabel', label: 'Year' },
        { key: 'semesterNumber', label: 'Sem' },
        { key: 'startDate', label: 'Starts', render: (r) => fmtDate(r.startDate) },
        { key: 'endDate', label: 'Ends', render: (r) => fmtDate(r.endDate) },
        { key: 'status', label: 'Marks entry', render: (r) => <StatusBadge status={r.status} /> },
      ]}
      fields={[
        { name: 'name', label: 'Name', full: true, help: 'For example "End Semester Examination - Dec 2025"' },
        { name: 'semesterId', label: 'Semester', type: 'select', optionsUrl: '/admin/options/semesters' },
        { name: 'status', label: 'Marks entry status', type: 'select', options: [{ id: 'OPEN', label: 'OPEN' }, { id: 'CLOSED', label: 'CLOSED' }] },
        { name: 'startDate', label: 'Start date', type: 'date' },
        { name: 'endDate', label: 'End date', type: 'date' },
      ]}
      createSchema={sessionSchema}
      emptyValues={{ name: '', semesterId: '', startDate: '', endDate: '', status: 'OPEN' }}
      toForm={(r) => ({ name: r.name, semesterId: String(r.semesterId), startDate: r.startDate, endDate: r.endDate, status: r.status })}
      toPayload={(v) => ({ name: v.name.trim(), semesterId: toId(v.semesterId), startDate: v.startDate, endDate: v.endDate, status: v.status })}
    />
  );
}

export { Badge };
