import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { GraduationCap } from 'lucide-react';
import { errorMessage } from '../api/client.js';
import { homePathFor, useAuth } from '../auth/AuthContext.jsx';
import { Alert, FormField, Loading } from '../components/ui.jsx';

const schema = z.object({
  username: z.string().trim().min(1, 'Username is required'),
  password: z.string().min(1, 'Password is required'),
});

export default function Login() {
  const { user, loading, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState('');
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { username: '', password: '' },
  });

  if (loading) return <Loading label="Checking your session..." fullPage />;
  if (user) return <Navigate to={homePathFor(user.role)} replace />;

  const submit = async ({ username, password }) => {
    setError('');
    try {
      const u = await login(username.trim(), password);
      const from = location.state?.from;
      const home = homePathFor(u.role);
      navigate(from && from.startsWith(home) ? from : home, { replace: true });
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={handleSubmit(submit)} noValidate>
        <GraduationCap size={36} color="#14305a" aria-hidden="true" />
        <h1>College Result Management</h1>
        <p>Sign in with the account issued by your college.</p>
        <Alert>{error}</Alert>
        <FormField label="Username" error={errors.username?.message} required>
          <input className={`input${errors.username ? ' invalid' : ''}`} autoComplete="username" autoFocus {...register('username')} />
        </FormField>
        <FormField label="Password" error={errors.password?.message} required>
          <input className={`input${errors.password ? ' invalid' : ''}`} type="password" autoComplete="current-password" {...register('password')} />
        </FormField>
        <button type="submit" className="btn primary" style={{ width: '100%', justifyContent: 'center' }} disabled={isSubmitting}>
          {isSubmitting ? 'Signing in...' : 'Sign in'}
        </button>
        <p className="small muted" style={{ marginTop: 14, marginBottom: 0 }}>
          Forgot your password? Contact the examination office to have it reset.
        </p>
      </form>
    </div>
  );
}
