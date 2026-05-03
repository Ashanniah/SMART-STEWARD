/** Initial notification feed (top bar dropdown) */
export const NOTIFICATIONS_SEED = [
  {
    id: 'n1',
    kind: 'new_report',
    title: 'New report submitted',
    body: 'A new report has been submitted in Brgy. San Isidro, Cebu City',
    timeLabel: '5 mins ago',
    dot: 'red',
    unread: true,
  },
  {
    id: 'n2',
    kind: 'urgent',
    title: 'Urgent Report',
    body: 'A report in Brgy. Payatas requires immediate attention',
    timeLabel: '1 hour ago',
    dot: 'blue',
    unread: true,
  },
  {
    id: 'n3',
    kind: 'new_report_blue',
    title: 'New report submitted',
    body: 'A new report has been submitted in Brgy. Project 8, Quezon City',
    timeLabel: '1 hour ago',
    dot: 'yellow',
    unread: true,
  },
  {
    id: 'n4',
    kind: 'status_update',
    title: 'Status updated',
    body: 'Report RPT - 2025-000216 has been marked as resolved',
    timeLabel: '1 hour ago',
    dot: 'green',
    unread: true,
  },
];
