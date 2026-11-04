import { api } from '../api/client.js';
import { downloadBlob, filenameFrom } from './format.js';

/** Downloads the PDF mark sheet through the authenticated API (the server checks ownership). */
export async function downloadMarksheet(resultId) {
  const response = await api.blob(`/student/results/${resultId}/marksheet`);
  downloadBlob(response.data, filenameFrom(response, `marksheet-${resultId}.pdf`));
}
