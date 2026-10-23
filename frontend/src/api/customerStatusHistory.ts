import {api} from './client';

export type CustomerStatusHistoryRow = {
    cifNo: string;
    customerName: string;
    fromStatus: string;
    toStatus: string;
    requestStatus: string;
    reason: string;
    requestedAt: string;
    reviewedAt: string | null;
};
export type CustomerStatusHistoryPage = {
    content: CustomerStatusHistoryRow[];
    number: number;
    totalPages: number;
    totalElements: number;
};

export async function getCustomerStatusHistory(page: number, size = 10, signal?: AbortSignal) {
    return (await api.get<CustomerStatusHistoryPage>('/customer-status-history', {params: {page, size}, signal})).data;
}
