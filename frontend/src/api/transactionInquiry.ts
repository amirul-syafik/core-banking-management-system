import {api} from './client';

export type TransactionHistory = {
    txnId: number; txnRef: string; acctId: number; destAcctId: number | null;
    txnTyp: string; direction: string; txnAmt: number; signedAmount: number;
    ccyCd: string; txnSts: string; crtDt: string; crtBy: string;
};
export type TransactionPage = {
    content: TransactionHistory[];
    number: number;
    totalPages: number;
    totalElements: number
};
export type TransactionLedgerEntry = {
    entryId: number;
    glCd: string;
    glNm: string;
    entrySide: string;
    entryAmt: number;
    acctId: number | null
};
export type TransactionDetail = {
    txnId: number;
    txnRef: string;
    acctId: number;
    destAcctId: number | null;
    txnTyp: string;
    txnAmt: number;
    ccyCd: string;
    txnSts: string;
    crtDt: string;
    crtBy: string;
    totalDebit: number;
    totalCredit: number;
    balanced: boolean;
    ledgerEntries: TransactionLedgerEntry[]
};
export type InquiryFilters = {
    reference?: string;
    type?: string;
    status?: string;
    from?: string;
    to?: string;
    page?: number;
    size?: number
};
export const inquireTransactions = async (accountId: number, filters: InquiryFilters) =>
    (await api.get<TransactionPage>(`/accounts/${accountId}/transactions/inquiry`, {params: filters})).data;
export const getTransactionDetail = async (id: number) =>
    (await api.get<TransactionDetail>(`/transactions/${id}`)).data;
