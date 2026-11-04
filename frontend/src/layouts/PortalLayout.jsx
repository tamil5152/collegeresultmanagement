import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { KeyRound, LogOut, Menu } from 'lucide-react';
import { useAuth } from '../auth/AuthContext.jsx';
import ChangePasswordModal from '../components/ChangePasswordModal.jsx';

/** Shared shell: sidebar + top navigation. Each role passes its own navigation items. */
export default function PortalLayout({ portalName, nav }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const signOut = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="portal">
      <aside className={`sidebar${open ? ' open' : ''}`} aria-label="Main navigation">
        <div className="brand">
          <strong>Result Management</strong>
          <span>{portalName}</span>
        </div>
        <nav>
          {nav.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} onClick={() => setOpen(false)}>
              <Icon size={17} aria-hidden="true" /> {label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className={`overlay${open ? ' open' : ''}`} onClick={() => setOpen(false)} />
      <div className="main">
        <header className="topbar">
          <div className="left">
            <button type="button" className="btn icon menu-btn" onClick={() => setOpen(true)} aria-label="Open menu">
              <Menu size={18} />
            </button>
            <strong>{portalName} portal</strong>
          </div>
          <div className="user">
            <div className="who">
              {user.fullName}
              <small>{user.role}</small>
            </div>
            <button type="button" className="btn icon" onClick={() => setShowPassword(true)} title="Change password" aria-label="Change password">
              <KeyRound size={16} />
            </button>
            <button type="button" className="btn" onClick={signOut}>
              <LogOut size={16} /> Sign out
            </button>
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
      {showPassword && <ChangePasswordModal onClose={() => setShowPassword(false)} />}
    </div>
  );
}
