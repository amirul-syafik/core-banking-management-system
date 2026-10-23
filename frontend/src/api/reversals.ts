import {api} from './client';

export type ReversalRequest = {
    id: number;
    originalTxnId: number;
    originalTxnRef: string;
    reason: string;
    status: string;
    requestedBy: string;
    requestedAt: string;
    decidedBy: string | null;
    decidedAt: string | null;
    decisionReason: string | null;
    reversalTxnId: number | null
};
export type ReversalPage = { content: ReversalRequest[]; number: number; totalPages: number; totalElements: number };
export const listReversals = async (page = 0) => (await api.get<ReversalPage>('/transaction-reversal-requests', {
    params: {
        page,
        size: 10
    }
})).data;
export const submitReversal = async (txnId: number, reason: string) => (await api.post<ReversalRequest>(`/transaction-reversal-requests/${txnId}`, {reason})).data;
export const decideReversal = async (id: number, decision: 'approve' | 'reject', reason: string) => (await api.post<ReversalRequest>(`/transaction-reversal-requests/${id}/${decision}`, {reason})).data;
export const directReversal = async (txnId: number, reason: string) => (await api.post(`/transactions/${txnId}/reversals`, {reason}, {headers: {'Idempotency-Key': crypto.randomUUID()}})).data;

export type ReversalEligibility = 'REVERSIBLE' | 'REVERSED' | 'PENDING' | 'NOT_REVERSIBLE';
export const getReversalEligibility = async (ids: number[]) =>
    (await api.get<Record<string, ReversalEligibility>>('/transaction-reversal-requests/eligibility', {
        params: {ids: ids.join(',')}
    })).data;
