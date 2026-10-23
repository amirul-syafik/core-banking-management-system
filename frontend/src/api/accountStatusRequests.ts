import {api} from './client';

export type AccountStatusRequest = {
    requestId: number;
    fromStatus: string;
    toStatus: string;
    requestStatus: 'PENDING' | 'APPROVED' | 'REJECTED';
    reason: string;
    canReview: boolean
};

export async function listAccountStatusRequests(id: number) {
    return (await api.get<AccountStatusRequest[]>(`/accounts/${id}/status-requests`)).data;
}

export async function proposeAccountStatus(id: number, status: string, reason: string) {
    await api.post(`/accounts/${id}/status-requests`, {status, reason});
}

export async function decideAccountStatus(id: number, requestId: number, approve: boolean) {
    await api.post(`/accounts/${id}/status-requests/${requestId}/decision`, {approve});
}
