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
  if (!s) return null;

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

export function agenciesMatch(reportAgencyRaw, viewerAgencyKeyRaw) {
  const v = toCanonicalAgency(viewerAgencyKeyRaw);
  const r = toCanonicalAgency(reportAgencyRaw);
  if (!v || !r) return false;
  return r === v;
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
