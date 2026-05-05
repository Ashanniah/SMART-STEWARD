/**
 * Maps workflow keys (see `data/reportsMock` WORKFLOW_STATUS_ORDER) to Firestore `status`
 * strings read by `normalizeReportDocument` and the Android `UserReport.fromSnapshot` mapper.
 */
export function workflowKeyToFirestoreStatus(key) {
  switch (key) {
    case 'pending':
      return 'pending';
    case 'review':
      return 'under_review';
    case 'in_progress':
      return 'in_progress';
    case 'resolved':
      return 'resolved';
    case 'rejected':
      return 'rejected';
    default:
      return 'pending';
  }
}
