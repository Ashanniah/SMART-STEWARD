/** Firestore collection for user profile + role (document id = Firebase Auth uid). */
export const USERS_COLLECTION = 'users';

/**
 * Roles allowed to use the agency web dashboard (match Firestore `role` field, case-insensitive).
 * Adjust to match your Firestore schema.
 */
export const AGENCY_ALLOWED_ROLES = [
  'admin',
  'agency',
  'administrator',
  'denr',
  'bfp',
  'pnp',
  'barangay',
];
