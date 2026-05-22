import { toCanonicalAgency } from '../utils/agencyScope';

/**
 * Header / chrome copy per canonical agency. Resolved from the signed-in user's Firestore profile.
 */
const PROFILES = {
  DENR: {
    pageTitle: 'DENR DASHBOARD',
    pageSubtitle: 'Summary of reports assigned to DENR.',
    userDisplayName: 'DENR',
    userRole: 'Administrator',
    filterAgencyDefault: 'DENR',
  },
  PNP: {
    pageTitle: 'PNP DASHBOARD',
    pageSubtitle: 'Summary of reports assigned to PNP.',
    userDisplayName: 'PNP',
    userRole: 'Administrator',
    filterAgencyDefault: 'PNP',
  },
  BFP: {
    pageTitle: 'BFP DASHBOARD',
    pageSubtitle: 'Summary of reports assigned to BFP.',
    userDisplayName: 'BFP',
    userRole: 'Administrator',
    filterAgencyDefault: 'BFP',
  },
  Barangay: {
    pageTitle: 'BARANGAY DASHBOARD',
    pageSubtitle: 'Summary of reports assigned to Barangay.',
    userDisplayName: 'Barangay',
    userRole: 'Administrator',
    filterAgencyDefault: 'Barangay',
  },
};

export function getDashboardConfig(agencyKey) {
  const k = toCanonicalAgency(agencyKey);
  if (k && PROFILES[k]) return PROFILES[k];
  return {
    pageTitle: 'Agency Dashboard',
    pageSubtitle: 'Summary of reports for your organization.',
    userDisplayName: 'Agency',
    userRole: 'Administrator',
    filterAgencyDefault: '',
  };
}
