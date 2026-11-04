import { useCallback, useEffect, useRef, useState } from 'react';
import { api, errorMessage } from '../api/client.js';

/** GET a URL with params; re-fetches when url/params change. `url` may be null to skip. */
export function useFetch(url, params) {
  const [state, setState] = useState({ data: null, loading: Boolean(url), error: null });
  const key = JSON.stringify([url, params]);
  const latest = useRef(0);

  const load = useCallback(() => {
    if (!url) {
      setState({ data: null, loading: false, error: null });
      return Promise.resolve();
    }
    const id = ++latest.current;
    setState((s) => ({ ...s, loading: true, error: null }));
    return api
      .get(url, params)
      .then((data) => id === latest.current && setState({ data, loading: false, error: null }))
      .catch((e) => id === latest.current && setState({ data: null, loading: false, error: errorMessage(e) }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  useEffect(() => {
    load();
  }, [load]);

  return { ...state, reload: load };
}

export function useDebounced(value, delay = 350) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}
