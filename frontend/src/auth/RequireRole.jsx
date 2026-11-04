import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { homePathFor, useAuth } from './AuthContext.jsx';
import { Loading } from '../components/ui.jsx';

/** Route guard. This is only a convenience: the server enforces roles on every request. */
export default function RequireRole({ role }) {
  const { loading, user } = useAuth();
  const location = useLocation();
  if (loading) return <Loading label="Checking your session..." fullPage />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (user.role !== role) return <Navigate to={homePathFor(user.role)} replace />;
  return <Outlet />;
}
