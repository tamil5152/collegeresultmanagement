import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import {
  BarChart3, BookOpen, Building2, CalendarDays, CheckSquare, ClipboardList, FileText, GraduationCap, LayoutDashboard,
  Layers, ListChecks, ScrollText, Send, Settings2, UserCheck, UserPlus, Users,
} from 'lucide-react';
import { homePathFor, useAuth } from './auth/AuthContext.jsx';
import RequireRole from './auth/RequireRole.jsx';
import PortalLayout from './layouts/PortalLayout.jsx';
import Login from './pages/Login.jsx';
import { EmptyState, Loading } from './components/ui.jsx';

import AdminDashboard from './pages/admin/AdminDashboard.jsx';
import { DepartmentsPage, AcademicYearsPage, SemestersPage, SubjectsPage, ExamSessionsPage } from './pages/admin/MasterPages.jsx';
import { StudentsPage, FacultyPage } from './pages/admin/PeoplePages.jsx';
import AssignmentsPage from './pages/admin/AssignmentsPage.jsx';
import EnrollmentsPage from './pages/admin/EnrollmentsPage.jsx';
import GradingRulesPage from './pages/admin/GradingRulesPage.jsx';
import MarksReviewPage from './pages/admin/MarksReviewPage.jsx';
import PublicationPage from './pages/admin/PublicationPage.jsx';
// Recharts is heavy, so the reports page is loaded on demand.
const ReportsPage = lazy(() => import('./pages/admin/ReportsPage.jsx'));
import AuditLogPage from './pages/admin/AuditLogPage.jsx';

import FacultyDashboard from './pages/faculty/FacultyDashboard.jsx';
import AssignedSubjects from './pages/faculty/AssignedSubjects.jsx';
import StudentList from './pages/faculty/StudentList.jsx';
import MarksEntry from './pages/faculty/MarksEntry.jsx';
import Submissions from './pages/faculty/Submissions.jsx';

import StudentDashboard from './pages/student/StudentDashboard.jsx';
import StudentProfile from './pages/student/StudentProfile.jsx';
import SemesterResults from './pages/student/SemesterResults.jsx';
import ResultDetails from './pages/student/ResultDetails.jsx';
import DownloadMarksheet from './pages/student/DownloadMarksheet.jsx';

const adminNav = [
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/students', label: 'Students', icon: Users },
  { to: '/admin/faculty', label: 'Faculty', icon: UserCheck },
  { to: '/admin/departments', label: 'Departments', icon: Building2 },
  { to: '/admin/academic-years', label: 'Academic Years', icon: CalendarDays },
  { to: '/admin/semesters', label: 'Semesters', icon: Layers },
  { to: '/admin/subjects', label: 'Subjects', icon: BookOpen },
  { to: '/admin/assignments', label: 'Faculty Assignments', icon: UserPlus },
  { to: '/admin/enrollments', label: 'Enrollments', icon: ClipboardList },
  { to: '/admin/exam-sessions', label: 'Examination Sessions', icon: FileText },
  { to: '/admin/grading-rules', label: 'Grading Rules', icon: Settings2 },
  { to: '/admin/marks-review', label: 'Marks Review', icon: CheckSquare },
  { to: '/admin/publication', label: 'Results Publication', icon: Send },
  { to: '/admin/reports', label: 'Reports', icon: BarChart3 },
  { to: '/admin/audit-logs', label: 'Audit Log', icon: ScrollText },
];

const facultyNav = [
  { to: '/faculty', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/faculty/subjects', label: 'Assigned Subjects', icon: BookOpen },
  { to: '/faculty/marks-entry', label: 'Marks Entry', icon: ListChecks },
  { to: '/faculty/submissions', label: 'Submitted Marks & Status', icon: Send },
];

const studentNav = [
  { to: '/student', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/student/profile', label: 'Profile', icon: Users },
  { to: '/student/results', label: 'Semester Results', icon: GraduationCap },
  { to: '/student/marksheets', label: 'Download Mark Sheet', icon: FileText },
];

function Home() {
  const { user, loading } = useAuth();
  if (loading) return <Loading fullPage />;
  return <Navigate to={user ? homePathFor(user.role) : '/login'} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<Home />} />

      <Route element={<RequireRole role="ADMIN" />}>
        <Route path="/admin" element={<PortalLayout portalName="Admin" nav={adminNav} />}>
          <Route index element={<AdminDashboard />} />
          <Route path="students" element={<StudentsPage />} />
          <Route path="faculty" element={<FacultyPage />} />
          <Route path="departments" element={<DepartmentsPage />} />
          <Route path="academic-years" element={<AcademicYearsPage />} />
          <Route path="semesters" element={<SemestersPage />} />
          <Route path="subjects" element={<SubjectsPage />} />
          <Route path="assignments" element={<AssignmentsPage />} />
          <Route path="enrollments" element={<EnrollmentsPage />} />
          <Route path="exam-sessions" element={<ExamSessionsPage />} />
          <Route path="grading-rules" element={<GradingRulesPage />} />
          <Route path="marks-review" element={<MarksReviewPage />} />
          <Route path="publication" element={<PublicationPage />} />
          <Route path="reports" element={<Suspense fallback={<Loading />}><ReportsPage /></Suspense>} />
          <Route path="audit-logs" element={<AuditLogPage />} />
          <Route path="*" element={<EmptyState title="Page not found" />} />
        </Route>
      </Route>

      <Route element={<RequireRole role="FACULTY" />}>
        <Route path="/faculty" element={<PortalLayout portalName="Faculty" nav={facultyNav} />}>
          <Route index element={<FacultyDashboard />} />
          <Route path="subjects" element={<AssignedSubjects />} />
          <Route path="subjects/:assignmentId/students" element={<StudentList />} />
          <Route path="marks-entry" element={<MarksEntry />} />
          <Route path="submissions" element={<Submissions />} />
          <Route path="*" element={<EmptyState title="Page not found" />} />
        </Route>
      </Route>

      <Route element={<RequireRole role="STUDENT" />}>
        <Route path="/student" element={<PortalLayout portalName="Student" nav={studentNav} />}>
          <Route index element={<StudentDashboard />} />
          <Route path="profile" element={<StudentProfile />} />
          <Route path="results" element={<SemesterResults />} />
          <Route path="results/:resultId" element={<ResultDetails />} />
          <Route path="marksheets" element={<DownloadMarksheet />} />
          <Route path="*" element={<EmptyState title="Page not found" />} />
        </Route>
      </Route>

      <Route path="*" element={<Home />} />
    </Routes>
  );
}
