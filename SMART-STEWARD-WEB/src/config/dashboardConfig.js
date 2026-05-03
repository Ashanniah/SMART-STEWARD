/**
 * Single dashboard for all agencies — swap fields here or load from auth/API later.
 */
export function getDashboardConfig(userKey = 'default') {
  const profiles = {
    default: {
      pageTitle: 'DENR DASHBOARD',
      pageSubtitle: 'Summary of all reports.',
      userDisplayName: 'DENR',
      userRole: 'Administrator',
      filterAgencyDefault: 'DENR',
    },
    // Example: bfp: { pageTitle: 'BFP DASHBOARD', ... }
  };

  return profiles[userKey] ?? profiles.default;
}
