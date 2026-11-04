export const fmtDate = (value) => {
  if (!value) return '-';
  const d = new Date(value.length === 10 ? `${value}T00:00:00` : value);
  return d.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' });
};

export const fmtDateTime = (value) => {
  if (!value) return '-';
  return new Date(value).toLocaleString(undefined, {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
};

/** Trim trailing zeros from a decimal that came from the API (e.g. 40.00 -> 40). */
export const num = (value) => {
  if (value === null || value === undefined || value === '') return '-';
  return String(Number(value));
};

export const fixed2 = (value) => (value === null || value === undefined ? '-' : Number(value).toFixed(2));

export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export const filenameFrom = (response, fallback) => {
  const header = response.headers?.['content-disposition'] || '';
  const match = /filename="?([^";]+)"?/i.exec(header);
  return match ? match[1] : fallback;
};
