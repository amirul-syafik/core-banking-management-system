import {api} from './client';

export type FixedDeposit = {
    fdId: number;
    fdReference: string;
    customerId: number;
    productId: number;
    schemeId: number;
    fdType: string;
    currency: string;
    principalAmount: number;
    tenorMonths: number;
    placementDate: string;
    maturityDate: string;
    rateType: 'FIXED' | 'FLOATING';
    interestRate: number;
    status: string;
    remarks?: string
};
export type FdAction = 'place' | 'accrue' | 'payout' | 'maturity' | 'settle' | 'renew' | 'uplift';
export type FdMaturitySettlement = {
    fdId: number;
    fdReference: string;
    destinationAccountId: number;
    principalAmount: number;
    settlementAmount: number;
    transactionId: number;
    transactionReference: string;
    settlementDate: string;
    status: 'CLOSED';
    principalRepaid: boolean
};
export type FdPlacement = {
    placementId: number;
    placementReference: string;
    fixedDepositId: number;
    sourceAccountId: number;
    placementAmount: number;
    currency: string;
    placementDate: string;
    maturityDate: string;
    postingReference: string;
    placementStatus: string;
    fixedDepositStatus: string;
};
export type FdUpliftResult = {
    fdId: number;
    status: string;
    principalAmount: number;
    eligibleInterest: number;
    previouslyPaidInterest: number;
    interestToPay: number;
    interestRecovery: number;
    settlementAmount: number;
    destinationAccountId: number;
    transactionReference: string
};
export type FdApprovalRequest = {
    request_id: number;
    fd_id: number;
    action_type: string;
    request_reason: string;
    request_status: string;
    requested_by: string;
    requested_at: string;
    decided_by?: string;
    decision_reason?: string;
    assigned_checker?: string | null;
    assigned_by?: string | null;
    assigned_at?: string | null;
    posting_result?: string;
    fd_reference?: string | null
};
export const fdApprovals = {
    list: async () => (await api.get<FdApprovalRequest[]>('/fd-approval-requests')).data,
    get: async (id: number) => (await api.get<FdApprovalRequest>(`/fd-approval-requests/${id}`)).data,
    decide: async (id: number, approve: boolean, reason: string) => (await api.post(`/fd-approval-requests/${id}/${approve ? 'approve' : 'reject'}`, {reason})).data,
    assign: async (id: number, checkerUsername: string) => (await api.post(`/fd-approval-requests/${id}/assign`, {checkerUsername})).data,
};
export const fixedDeposits = {
    list: async () => (await api.get<FixedDeposit[]>('/fixed-deposits')).data,
    create: async (data: {
        custId: number;
        prodId: number;
        schmId: number;
        fdType: string;
        principalAmount: number;
        tenorMonths: number;
        interestRate: number;
        placementDate: string;
        remarks: string
    }) => (await api.post<FixedDeposit>('/fixed-deposits', data)).data,
    detail: async (id: number) => (await api.get<FixedDeposit>(`/fixed-deposits/${id}`)).data,
    placement: async (id: number) => (await api.get<FdPlacement>(`/fixed-deposits/${id}/placement`)).data,
    accruals: async (id: number) => (await api.get(`/fixed-deposits/${id}/interest/accruals`)).data,
    payouts: async (id: number) => (await api.get(`/fixed-deposits/${id}/interest/payouts`)).data,
    preview: async (id: number) => (await api.get(`/fixed-deposits/${id}/maturity/preview`)).data,
    validate: async (id: number) => (await api.get(`/fixed-deposits/${id}/maturity/validate`)).data,
    summary: async () => (await api.get('/fixed-deposits/monitoring/summary')).data,
    monitoring: async (view: 'upcoming-maturities' | 'overdue-maturities' | 'missing-accruals' | 'closures') => (await api.get(`/fixed-deposits/monitoring/${view}`)).data,
    act: async (id: number, action: FdAction, fields: Record<string, string>) => {
        if (action === 'place') return (await api.post(`/fixed-deposits/${id}/place`, {
            sourceAccountId: Number(fields.account),
            remarks: fields.remarks
        })).data;
        if (action === 'accrue') return (await api.post(`/fixed-deposits/${id}/interest/accrue`, null, {params: {accrualDate: fields.date}})).data;
        if (action === 'payout') return (await api.post(`/fixed-deposits/${id}/interest/payout`, {destinationAccountId: Number(fields.account)})).data;
        if (action === 'maturity') return (await api.post(`/fixed-deposits/${id}/maturity/process`)).data;
        if (action === 'settle') return (await api.post('/fd-approval-requests', {
            fdId: id,
            actionType: 'MATURITY_SETTLEMENT',
            payload: {destinationAccountId: Number(fields.account), remarks: fields.remarks || ''},
            reason: fields.remarks?.trim() || ''
        })).data;
        if (action === 'renew') return (await api.post('/fd-approval-requests', {
            fdId: id,
            actionType: 'RENEWAL',
            payload: {
                renewalType: fields.renewalType || 'PRINCIPAL_ONLY',
                schemeId: Number(fields.scheme),
                tenorMonths: Number(fields.tenor),
                interestRate: Number(fields.rate),
                remarks: fields.remarks || ''
            },
            reason: fields.remarks?.trim() || ''
        })).data;
        return (await api.post('/fd-approval-requests', {
            fdId: id,
            actionType: 'PREMATURE_UPLIFT',
            payload: {destinationAccountId: Number(fields.account), remarks: fields.remarks || ''},
            reason: fields.remarks?.trim() || ''
        })).data;
    }
};
