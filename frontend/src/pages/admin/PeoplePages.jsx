import { z } from 'zod';
import ResourcePage from '../../components/ResourcePage.jsx';
import { ActiveBadge } from '../../components/ui.jsx';
import {
  email, numberStr, optionalPassword, optionalText, password, selectId, text, toId, toNum, username,
} from '../../utils/schemas.js';

const ACTIVE_FILTER = {
  name: 'active', label: 'Status', allLabel: 'Any status',
  options: [{ id: '1', label: 'Active' }, { id: '0', label: 'Inactive' }],
};
const DEPARTMENT_FILTER = { name: 'departmentId', label: 'Department', optionsUrl: '/admin/options/departments' };

// ---------------------------------------------------------------- students
const studentBase = {
  fullName: text('Full name', 150),
  email,
  departmentId: selectId('Department'),
  rollNumber: text('Roll number', 30),
  batchYear: numberStr('Batch year', { min: 1990, max: 2200, decimals: 0 }),
  currentSemester: numberStr('Current semester', { min: 1, max: 12, decimals: 0 }),
  phone: optionalText('Phone', 20),
  dateOfBirth: z.string(),
};
const studentCreate = z.object({ username, password, ...studentBase });
const studentEdit = z.object({ password: optionalPassword, ...studentBase });

export function StudentsPage() {
  return (
    <ResourcePage
      title="Students" subtitle="Student accounts and academic profiles" singular="Student"
      endpoint="/admin/students" activeToggle searchPlaceholder="Roll number, name or username"
      filters={[DEPARTMENT_FILTER, { name: 'semester', label: 'Semester', allLabel: 'Any semester', options: Array.from({ length: 8 }, (_, i) => ({ id: String(i + 1), label: `Semester ${i + 1}` })) }, ACTIVE_FILTER]}
      columns={[
        { key: 'rollNumber', label: 'Roll no.' }, { key: 'fullName', label: 'Name' },
        { key: 'departmentCode', label: 'Dept' }, { key: 'currentSemester', label: 'Sem' },
        { key: 'batchYear', label: 'Batch' }, { key: 'email', label: 'Email' },
        { key: 'active', label: 'Status', render: (r) => <ActiveBadge active={r.active} /> },
      ]}
      fields={[
        { name: 'username', label: 'Username', edit: false },
        { name: 'password', label: 'Initial password', type: 'password', edit: false, help: '8+ characters with a letter and a digit' },
        { name: 'password', label: 'Reset password', type: 'password', create: false, optional: true, help: 'Leave blank to keep the current password' },
        { name: 'fullName', label: 'Full name' },
        { name: 'email', label: 'Email', type: 'email' },
        { name: 'rollNumber', label: 'Roll number' },
        { name: 'departmentId', label: 'Department', type: 'select', optionsUrl: '/admin/options/departments' },
        { name: 'batchYear', label: 'Batch (admission year)', type: 'number' },
        { name: 'currentSemester', label: 'Current semester (1-12)', type: 'number' },
        { name: 'phone', label: 'Phone', optional: true },
        { name: 'dateOfBirth', label: 'Date of birth', type: 'date', optional: true },
      ]}
      createSchema={studentCreate}
      editSchema={studentEdit}
      emptyValues={{ username: '', password: '', fullName: '', email: '', rollNumber: '', departmentId: '', batchYear: String(new Date().getFullYear()), currentSemester: '1', phone: '', dateOfBirth: '' }}
      toForm={(r) => ({
        password: '', fullName: r.fullName, email: r.email, rollNumber: r.rollNumber, departmentId: String(r.departmentId),
        batchYear: String(r.batchYear), currentSemester: String(r.currentSemester), phone: r.phone || '', dateOfBirth: r.dateOfBirth || '',
      })}
      toPayload={(v, mode) => ({
        ...(mode === 'create' ? { username: v.username.trim(), password: v.password } : v.password ? { password: v.password } : {}),
        fullName: v.fullName.trim(), email: v.email.trim(), rollNumber: v.rollNumber.trim(), departmentId: toId(v.departmentId),
        batchYear: toNum(v.batchYear), currentSemester: toNum(v.currentSemester), phone: v.phone.trim() || null,
        dateOfBirth: v.dateOfBirth || null,
      })}
    />
  );
}

// ---------------------------------------------------------------- faculty
const facultyBase = {
  fullName: text('Full name', 150),
  email,
  departmentId: selectId('Department'),
  employeeCode: text('Employee code', 30),
  designation: optionalText('Designation', 100),
  phone: optionalText('Phone', 20),
};
const facultyCreate = z.object({ username, password, ...facultyBase });
const facultyEdit = z.object({ password: optionalPassword, ...facultyBase });

export function FacultyPage() {
  return (
    <ResourcePage
      title="Faculty" subtitle="Faculty accounts. Subjects are assigned under Faculty Assignments." singular="Faculty member"
      endpoint="/admin/faculty" activeToggle searchPlaceholder="Employee code, name or username"
      filters={[DEPARTMENT_FILTER, ACTIVE_FILTER]}
      columns={[
        { key: 'employeeCode', label: 'Emp. code' }, { key: 'fullName', label: 'Name' },
        { key: 'departmentCode', label: 'Dept' }, { key: 'designation', label: 'Designation' },
        { key: 'email', label: 'Email' },
        { key: 'active', label: 'Status', render: (r) => <ActiveBadge active={r.active} /> },
      ]}
      fields={[
        { name: 'username', label: 'Username', edit: false },
        { name: 'password', label: 'Initial password', type: 'password', edit: false, help: '8+ characters with a letter and a digit' },
        { name: 'password', label: 'Reset password', type: 'password', create: false, optional: true, help: 'Leave blank to keep the current password' },
        { name: 'fullName', label: 'Full name' },
        { name: 'email', label: 'Email', type: 'email' },
        { name: 'employeeCode', label: 'Employee code' },
        { name: 'departmentId', label: 'Department', type: 'select', optionsUrl: '/admin/options/departments' },
        { name: 'designation', label: 'Designation', optional: true },
        { name: 'phone', label: 'Phone', optional: true },
      ]}
      createSchema={facultyCreate}
      editSchema={facultyEdit}
      emptyValues={{ username: '', password: '', fullName: '', email: '', employeeCode: '', departmentId: '', designation: '', phone: '' }}
      toForm={(r) => ({
        password: '', fullName: r.fullName, email: r.email, employeeCode: r.employeeCode, departmentId: String(r.departmentId),
        designation: r.designation || '', phone: r.phone || '',
      })}
      toPayload={(v, mode) => ({
        ...(mode === 'create' ? { username: v.username.trim(), password: v.password } : v.password ? { password: v.password } : {}),
        fullName: v.fullName.trim(), email: v.email.trim(), employeeCode: v.employeeCode.trim(), departmentId: toId(v.departmentId),
        designation: v.designation.trim() || null, phone: v.phone.trim() || null,
      })}
    />
  );
}
