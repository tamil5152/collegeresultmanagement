import { z } from 'zod';

// Form values are strings; payload builders convert them. These helpers keep messages consistent.
export const text = (label, max = 150) =>
  z.string().trim().min(1, `${label} is required`).max(max, `${label} must be at most ${max} characters`);

export const optionalText = (label, max = 150) =>
  z.string().trim().max(max, `${label} must be at most ${max} characters`);

export const email = z.string().trim().min(1, 'Email is required').email('Enter a valid email address');

export const selectId = (label) => z.string().min(1, `${label} is required`);

export const dateStr = (label) => z.string().min(1, `${label} is required`);

export const numberStr = (label, { min = 0, max, decimals = 2 } = {}) =>
  z
    .string()
    .trim()
    .min(1, `${label} is required`)
    .refine((v) => v === '' || !Number.isNaN(Number(v)), 'Enter a valid number')
    .refine((v) => Number.isNaN(Number(v)) || Number(v) >= min, `${label} must be at least ${min}`)
    .refine((v) => max === undefined || Number.isNaN(Number(v)) || Number(v) <= max, `${label} must be at most ${max}`)
    .refine((v) => new RegExp(`^-?\\d*(\\.\\d{0,${decimals}})?$`).test(v), `At most ${decimals} decimal places`);

export const PASSWORD_MESSAGE = 'Password must be 8-100 characters and contain a letter and a digit';
export const password = z.string().regex(/^(?=.*[A-Za-z])(?=.*\d).{8,100}$/, PASSWORD_MESSAGE);
export const optionalPassword = z.union([z.literal(''), password]);
export const username = z
  .string()
  .trim()
  .regex(/^[A-Za-z0-9._-]{3,50}$/, "3-50 characters: letters, digits, '.', '_' or '-'");

export const toNum = (v) => (v === '' || v === null || v === undefined ? null : Number(v));
export const toId = (v) => Number(v);
