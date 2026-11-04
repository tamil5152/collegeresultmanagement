import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { api, errorMessage } from '../api/client.js';
import { Alert, FormField, Modal } from './ui.jsx';
import { useToast } from './Toast.jsx';
import { password } from '../utils/schemas.js';

const schema = z
  .object({
    currentPassword: z.string().min(1, 'Current password is required'),
    newPassword: password,
    confirm: z.string().min(1, 'Please repeat the new password'),
  })
  .refine((v) => v.newPassword === v.confirm, { path: ['confirm'], message: 'Passwords do not match' });

export default function ChangePasswordModal({ onClose }) {
  const toast = useToast();
  const [error, setError] = useState('');
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: '', newPassword: '', confirm: '' },
  });

  const submit = async ({ currentPassword, newPassword }) => {
    setError('');
    try {
      await api.post('/auth/change-password', { currentPassword, newPassword });
      toast.success('Password changed');
      onClose();
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  return (
    <Modal
      title="Change password"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button type="submit" form="pw-form" className="btn primary" disabled={isSubmitting}>
            {isSubmitting ? 'Saving...' : 'Change password'}
          </button>
        </>
      }
    >
      <Alert>{error}</Alert>
      <form id="pw-form" onSubmit={handleSubmit(submit)} noValidate>
        <FormField label="Current password" error={errors.currentPassword?.message} required>
          <input className="input" type="password" autoComplete="current-password" {...register('currentPassword')} />
        </FormField>
        <FormField label="New password" error={errors.newPassword?.message} required help="At least 8 characters, with a letter and a digit">
          <input className="input" type="password" autoComplete="new-password" {...register('newPassword')} />
        </FormField>
        <FormField label="Repeat new password" error={errors.confirm?.message} required>
          <input className="input" type="password" autoComplete="new-password" {...register('confirm')} />
        </FormField>
      </form>
    </Modal>
  );
}
