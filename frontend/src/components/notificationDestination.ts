import type {ApprovalNotification} from '../api/notifications';

/** Notification identities refer to approval requests, not necessarily to banking records. */
export function notificationDestination(item: ApprovalNotification): string | null {
    const id = item.targetId ?? item.sourceId;
    if (!Number.isSafeInteger(id) || id <= 0) return null;
    const request = encodeURIComponent(String(item.sourceId));
    if (item.sourceType === 'CUSTOMER_STATUS')
        return item.targetId ? `/customers/${id}?requestId=${request}` : null;
    if (item.sourceType === 'ACCOUNT_STATUS')
        return item.targetId ? `/accounts?accountId=${id}&requestId=${request}` : null;
    if (item.sourceType === 'ACCOUNT_APPLICATION') return `/accounts?applicationId=${id}`;
    if (item.sourceType === 'FINANCING_APPLICATION') return `/financing/applications?applicationId=${id}`;
    if (item.sourceType === 'FD_ISLAMIC_TERMS') return item.targetId
        ? `/fixed-deposits?fd=${id}&tab=profit-terms` : null;
    if (item.sourceType === 'FD_ISLAMIC_TIMELINE') return item.targetId
        ? `/fixed-deposits?fd=${id}&tab=execution-timeline` : null;
    if (item.sourceType === 'FD_ISLAMIC_LEDGER_MAPPING') return item.targetId
        ? `/fixed-deposits?fd=${id}&tab=ledger-mapping` : null;
    if (item.sourceType === 'FD_ISLAMIC_EXECUTION') return item.targetId
        ? `/fixed-deposits?fd=${id}&tab=execution-evidence` : null;
    if (item.sourceType === 'FD_APPROVAL') return `/fixed-deposits?view=approvals&requestId=${request}`;
    if (item.sourceType === 'ISLAMIC_CONTRACT') return `/operations/schemes?schemeId=${id}&profileId=${request}`;
    if (item.sourceType === 'REVERSAL_REQUEST') return `/transactions/reversal?requestId=${request}`;
    if (item.sourceType === 'WORKFLOW_TASK') {
        if (item.moduleCode === 'ACCOUNT_APPLICATIONS') return `/accounts?applicationId=${id}`;
        if (item.moduleCode === 'FINANCING') return `/financing/applications?applicationId=${id}`;
        if (item.moduleCode === 'FIXED_DEPOSITS') return `/fixed-deposits?fd=${id}`;
    }
    return null;
}
