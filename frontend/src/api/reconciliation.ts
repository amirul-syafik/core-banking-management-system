import {api} from './client';

export type ReconciliationRow = {
    acctId: number;
    acctNo: string;
    ccyCd: string;
    storedBalance: number;
    calculatedBalance: number;
    difference: number;
    reconciled: boolean;
};
export type ReconciliationPage = {
    content: ReconciliationRow[];
    number: number;
    totalPages: number;
    totalElements: number;
};

export async function searchReconciliation(accountNo: string, page: number): Promise<ReconciliationPage> {
    return (await api.get<ReconciliationPage>('/reconciliation/accounts', {
        params: {accountNo: accountNo.trim(), page, size: 10}
    })).data;
}

export type ReportRow = {
    acctId: number;
    acctNo: string;
    ccyCd: string;
    storedBalance: number | null;
    calculatedBalance: number;
    difference: number | null;
    status: 'MATCHED' | 'DISCREPANCY' | 'UNAVAILABLE'
};
export type ReportPage = {
    content: ReportRow[];
    number: number;
    totalPages: number;
    totalElements: number;
    summary: { total: number; matched: number; discrepancies: number; unavailable: number }
};
export type LedgerEntry = {
    entryId: number;
    txnId: number;
    reference: string;
    type: string;
    status: string;
    reversalOfTxnId: number | null;
    side: string;
    amount: number;
    signedAmount: number;
    dateTime: string
};
export type InvestigationPage = { entries: LedgerEntry[]; number: number; totalPages: number; totalElements: number };

export async function getReconciliationReport(accountNo: string, status: string, page: number): Promise<ReportPage> {
    return (await api.get<ReportPage>('/reconciliation/report', {params: {accountNo, status, page, size: 10}})).data;
}

export async function getReconciliationInvestigation(acctId: number, page: number): Promise<InvestigationPage> {
    return (await api.get<InvestigationPage>(`/reconciliation/investigation/${acctId}`, {
        params: {
            page,
            size: 20
        }
    })).data;
}

export async function downloadReconciliationCsv(accountNo: string, status: string): Promise<Blob> {
    return (await api.get('/reconciliation/export', {params: {accountNo, status}, responseType: 'blob'})).data as Blob;
}

export type ExceptionRow = {
    type: string;
    severity: string;
    txnId: number;
    acctId: number | null;
    description: string;
    detail: string
};
export type ExceptionPage = {
    content: ExceptionRow[];
    number: number;
    size: number;
    totalPages: number;
    totalElements: number
};

export async function getTransactionExceptions(type: string, page: number): Promise<ExceptionPage> {
    return (await api.get<ExceptionPage>('/reconciliation/exceptions', {params: {type, page, size: 10}})).data;
}

export async function downloadTransactionExceptions(type: string): Promise<Blob> {
    return (await api.get('/reconciliation/exceptions/export', {params: {type}, responseType: 'blob'})).data as Blob;
}
