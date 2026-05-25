/** Canonical agency labels stored on reports and user profiles (Firestore). */
export const CANONICAL_AGENCIES = ['DENR', 'PNP', 'BFP', 'Barangay'];

/**
 * Map free-text or shorthand from Firestore to a canonical agency key.
 * Returns null if the value cannot be matched (report then matches no viewer).
 */
export function toCanonicalAgency(raw) {
  const s = String(raw ?? '')
    .trim()
    .toLowerCase();
  if (!s || s === 'n/a') return null;

  if (s === 'denr' || s.includes('environment and natural resources') || s === 'env') {
    return 'DENR';
  }
  if (s === 'pnp' || s.includes('philippine national police') || s === 'police') {
    return 'PNP';
  }
  if (s === 'bfp' || s.includes('bureau of fire') || s === 'fire' || s === 'bfp ph') {
    return 'BFP';
  }
  if (s === 'barangay' || s === 'brgy' || s.includes('barangay ')) {
    return 'Barangay';
  }

  for (const c of CANONICAL_AGENCIES) {
    if (c.toLowerCase() === s) return c;
  }

  return null;
}

/** One or more canonical agencies from Firestore (comma-separated string or array). */
export function parseAssignedAgencies(raw) {
  if (raw == null) return [];
  if (Array.isArray(raw)) {
    return [...new Set(raw.map((part) => toCanonicalAgency(part)).filter(Boolean))];
  }
  const s = String(raw).trim();
  if (!s || s.toUpperCase() === 'N/A') return [];
  const parts = s.split(/[,;]/).map((part) => part.trim()).filter(Boolean);
  if (parts.length === 0) return [];
  const mapped = parts.map((part) => toCanonicalAgency(part)).filter(Boolean);
  if (mapped.length > 0) return [...new Set(mapped)];
  const single = toCanonicalAgency(s);
  return single ? [single] : [];
}

/** Display label for UI tables and cards (canonical list, comma-separated). */
export function formatAssignedAgenciesLabel(raw) {
  const list = parseAssignedAgencies(raw);
  if (list.length > 0) return list.join(', ');
  const trimmed = String(raw ?? '').trim();
  return trimmed || '—';
}

/**
 * Each admin dashboard is scoped to its own agency, so even when the AI
 * assigns a report to several agencies in Firestore we only show the
 * viewer's own agency in admin UIs. Resolution order:
 *
 *   1. If the viewer's canonical agency is one of the report's agencies,
 *      use it (most common path for an agency-specific admin).
 *   2. If we can resolve the viewer's agency but it isn't on the report,
 *      still prefer it — that mirrors the dashboard scoping rules so the
 *      header chip never reveals other agencies' assignments.
 *   3. Otherwise fall back to the first canonical agency on the report,
 *      then to the raw string, then to `''` for callers to format an em-dash.
 */
export function viewerScopedAgencyLabel(rawAgency, viewerAgencyKey) {
  const viewer = toCanonicalAgency(viewerAgencyKey);
  const reportAgencies = parseAssignedAgencies(rawAgency);
  if (viewer && reportAgencies.includes(viewer)) return viewer;
  if (viewer) return viewer;
  if (reportAgencies.length > 0) return reportAgencies[0];
  return String(rawAgency ?? '').trim();
}

export function agenciesMatch(reportAgencyRaw, viewerAgencyKeyRaw) {
  const v = toCanonicalAgency(viewerAgencyKeyRaw);
  if (!v) return false;
  const reportAgencies = parseAssignedAgencies(reportAgencyRaw);
  if (reportAgencies.length === 0) {
    return toCanonicalAgency(reportAgencyRaw) === v;
  }
  return reportAgencies.includes(v);
}

/**
 * When the Firestore user doc has no `agency` field, infer from the local part of the
 * sign-in email (e.g. denradmin123@… → DENR). Longer / more specific tokens first.
 */
export function inferAgencyFromEmail(email) {
  const local = String(email ?? '')
    .split('@')[0]
    ?.toLowerCase() ?? '';
  if (!local) return null;
  if (local.includes('barangay')) return 'Barangay';
  if (local.includes('denr')) return 'DENR';
  if (local.includes('bfp')) return 'BFP';
  if (local.includes('pnp')) return 'PNP';
  return null;
}
