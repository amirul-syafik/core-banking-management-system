import {api} from './client';

export type StatusRequest = {
    requestId: number;
    fromStatus: string;
    toStatus: string;
    requestStatus: 'PENDING' | 'APPROVED' | 'REJECTED';
    reason: string;
    canReview: boolean;
};

export async function getStatusRequests(customerId: number): Promise<StatusRequest[]> {
    const {data} = await api.get<StatusRequest[]>(`/customers/${customerId}/status-requests`);
    return data;
}

export async function proposeStatus(customerId: number, status: string, reason: string): Promise<void> {
    await api.post(`/customers/${customerId}/status-requests`, {status, reason});
}

export async function decideStatus(customerId: number, requestId: number, approve: boolean): Promise<void> {
    await api.post(`/customers/${customerId}/status-requests/${requestId}/decision`, {approve});
}
