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
