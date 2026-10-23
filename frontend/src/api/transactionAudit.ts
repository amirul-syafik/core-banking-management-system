import {api} from './client';

export type TransactionAuditEvent = {
    eventKey: string;
    reference: string;
    accountId: number;
    action: string;
    actor: string;
    eventAt: string;
    description: string;
    relatedReference: string | null;
};
export type TransactionAuditPage = {
    content: TransactionAuditEvent[];
    number: number;
    totalPages: number;
    totalElements: number;
};
export type TransactionAuditFilters = {
    accountId?: number;
    reference?: string;
    action?: string;
    actor?: string;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
};

export async function searchTransactionAudit(filters: TransactionAuditFilters): Promise<TransactionAuditPage> {
    return (await api.get<TransactionAuditPage>('/transaction-audit', {params: filters})).data;
}
