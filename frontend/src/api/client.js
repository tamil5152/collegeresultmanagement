import axios from 'axios';

// Relative "/api" by default (Vite proxy in dev, Vercel rewrite in production). Configurable, never a secret.
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api';

export const http = axios.create({ baseURL, withCredentials: true, timeout: 30000 });

// The CSRF token comes from the server (login / me) and lives only in memory.
let csrfToken = null;
export const setCsrfToken = (token) => {
  csrfToken = token;
};

let unauthorizedHandler = null;
export const onUnauthorized = (handler) => {
  unauthorizedHandler = handler;
};

const SAFE_METHODS = ['get', 'head', 'options'];
const AUTH_URLS = ['/auth/login', '/auth/me', '/auth/logout'];

http.interceptors.request.use((config) => {
  const method = (config.method || 'get').toLowerCase();
  if (csrfToken && !SAFE_METHODS.includes(method)) {
    config.headers['X-CSRF-Token'] = csrfToken;
  }
  return config;
});

function fallbackMessage(status, error) {
  if (!status) {
    return error.code === 'ECONNABORTED'
      ? 'The request timed out. Please try again.'
      : 'Cannot reach the server. Check your connection; a free-tier server may need about a minute to wake up.';
  }
  if (status === 401) return 'Your session has expired. Please sign in again.';
  if (status === 403) return 'You do not have permission to do that.';
  if (status === 404) return 'The requested item was not found.';
  if (status >= 500) return 'The server had a problem. Please try again shortly.';
  return 'The request could not be completed.';
}

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const response = error.response;
    let payload = response?.data;
    if (payload instanceof Blob) {
      try {
        payload = JSON.parse(await payload.text());
      } catch {
        payload = null;
      }
    }
    const status = response?.status;
    error.apiError = {
      status,
      code: payload?.code,
      message: payload?.message || fallbackMessage(status, error),
      fieldErrors: payload?.fieldErrors || {},
    };
    const url = error.config?.url || '';
    if (status === 401 && !AUTH_URLS.some((u) => url.startsWith(u))) {
      unauthorizedHandler?.();
    }
    return Promise.reject(error);
  },
);

// Every call returns the unwrapped `data` member of the { success, message, data } envelope.
export const api = {
  get: (url, params) => http.get(url, { params }).then((r) => r.data.data),
  post: (url, body) => http.post(url, body).then((r) => r.data.data),
  put: (url, body) => http.put(url, body).then((r) => r.data.data),
  patch: (url, body) => http.patch(url, body).then((r) => r.data.data),
  del: (url) => http.delete(url).then((r) => r.data.data),
  blob: (url) => http.get(url, { responseType: 'blob' }),
};

export const errorMessage = (error) => error?.apiError?.message || error?.message || 'Something went wrong';
export const fieldErrors = (error) => error?.apiError?.fieldErrors || {};
