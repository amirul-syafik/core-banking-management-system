import {api} from './client';

export type Account = {
    acctId: number;
    custId: number;
    acctNo: string;
    acctTyp: string;
    ccyCd: string;
    acctSts: string;
    openDt: string | null;
    closeDt: string | null
};
export type Application = {
    applId: number;
    applRef: string;
    custId: number;
    prodId: number;
    schmId: number;
    applSts: string;
    reqAmt: number | null;
    reqTenor: number | null;
    remarks: string | null;
    acctId: number | null
};
export type Product = { prodId: number; prodCd: string; prodNm: string; prodSts: string; prodCat: string };
export type Scheme = {
    schmId: number;
    schmCd: string;
    schmNm: string;
    schmSts: string;
    prodId: number;
    minAmt: number | null;
    maxAmt: number | null;
    minTenor: number | null;
    maxTenor: number | null
};
export type Validation = { valid: boolean; errors: string[] };
export const listApplications = async (custId: number) => (await api.get<Application[]>('/account-applications', {params: {custId}})).data;
export const listAccounts = async (custId: number) => (await api.get<Account[]>(`/customers/${custId}/accounts`)).data;
export const listProducts = async () => (await api.get<Product[]>('/products')).data;
export const listSchemes = async (prodId: number) => (await api.get<Scheme[]>('/schemes', {params: {prodId}})).data;
export const createApplication = async (body: {
    custId: number;
    prodId: number;
    schmId: number;
    reqAmt: number | null;
    reqTenor: number | null;
    remarks: string
}) => (await api.post<Application>('/account-applications', body)).data;
export const validateApplication = async (id: number) => (await api.get<Validation>(`/account-applications/${id}/validate`)).data;
export const submitApplication = async (id: number) => api.post(`/account-applications/${id}/submit`);
export const approveApplication = async (id: number, remarks: string) => api.post(`/account-applications/${id}/approve`, {remarks});
export const rejectApplication = async (id: number, remarks: string) => api.post(`/account-applications/${id}/reject`, {remarks});
export const completeApplication = async (id: number) => api.post(`/account-applications/${id}/create-account`);

export type AccountSearchPage = {
    content: Account[];
    number: number;
    totalPages: number;
    totalElements: number;
};
export const searchAccounts = async (params: {
    accountNo?: string;
    cifNo?: string;
    status?: string;
    page?: number;
    size?: number
}) =>
    (await api.get<AccountSearchPage>('/accounts/search', {params})).data;

// Phase 23.8: read-only account balances and transaction activity.
export type AccountBalance = {
    acctNo: string;
    ccyCd: string;
    ledgerBal: number | null;
    availBal: number | null;
};
export type AccountTransaction = {
    txnRef: string;
    txnTyp: string;
    direction: string;
    txnAmt: number;
    signedAmount: number;
    ccyCd: string;
    txnSts: string;
    crtDt: string;
};
export type AccountTransactionPage = {
    content: AccountTransaction[];
    number: number;
    totalPages: number;
    totalElements: number;
};
export const getAccountBalance = async (accountId: number) =>
    (await api.get<AccountBalance>(`/accounts/${accountId}/balance`)).data;
export const getAccountTransactions = async (accountId: number, page = 0) =>
    (await api.get<AccountTransactionPage>(`/accounts/${accountId}/transactions`, {params: {page, size: 10}})).data;

// Phase 23.9: posting uses existing secured endpoints and a unique key per confirmed operation.
export type PostedTransaction = { txnRef: string; status: string; amount: number; currency: string };
export type PostedTransfer = { txnRef: string; status: string; amount: number; currency: string };
export const postAccountTransaction = async (accountId: number, kind: 'deposit' | 'withdrawal', amount: string, key: string) =>
    (await api.post<PostedTransaction>(`/accounts/${accountId}/${kind === 'deposit' ? 'deposits' : 'withdrawals'}`, {amount}, {headers: {'Idempotency-Key': key}})).data;
export const postAccountTransfer = async (accountId: number, destinationAccountId: number, amount: string, key: string) =>
    (await api.post<PostedTransfer>(`/accounts/${accountId}/transfers`, {
        destinationAccountId,
        amount
    }, {headers: {'Idempotency-Key': key}})).data;
