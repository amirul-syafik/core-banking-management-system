import {api} from './client';

export type FinancingApplication = {
    applId: number;
    applRef: string;
    custId: number;
    prodId: number;
    schmId: number;
    reqAmt: number;
    reqTenor: number;
    financingPurpose: string | null;
    remarks: string | null;
    applSts: string;
    apprAmt: number | null;
    apprTenor: number | null;
    apprRate: number | null;
    facId: number | null;
    crtBy: string;
    submittedBy: string | null;
    reviewedBy: string | null;
    reviewRemarks: string | null;
    crtDt: string
};
export type FinancingHistory = {
    histId: number;
    action: string;
    fromSts: string;
    toSts: string;
    actionDt: string;
    actionBy: string;
    remarks: string | null
};
export type FinancingValidation = { valid: boolean; errors: string[] };
const base = '/financing/applications';
export const listFinancing = async (params: {
    custId?: number;
    status?: string
} = {}) => (await api.get<FinancingApplication[]>(base, {params})).data;
export const createFinancing = async (body: {
    custId: number;
    prodId: number;
    schmId: number;
    reqAmt: number;
    reqTenor: number;
    financingPurpose: string;
    remarks: string
}) => (await api.post<FinancingApplication>(base, body)).data;
export const validateFinancing = async (id: number) => (await api.get<FinancingValidation>(`${base}/${id}/validate`)).data;
export const submitFinancing = async (id: number) => (await api.post<FinancingApplication>(`${base}/${id}/submit`)).data;
export const approveFinancing = async (id: number, body: {
    apprAmt: number;
    apprTenor: number;
    apprRate: number;
    remarks: string
}) => (await api.post<FinancingApplication>(`${base}/${id}/approve`, body)).data;
export const rejectFinancing = async (id: number, remarks: string) => (await api.post<FinancingApplication>(`${base}/${id}/reject`, {remarks})).data;
export const bookFinancing = async (id: number) => (await api.post(`${base}/${id}/book-facility`)).data;
export const financingHistory = async (id: number) => (await api.get<FinancingHistory[]>(`${base}/${id}/history`)).data;

// R2 — post-booking financing facility lifecycle.
// Amounts are represented as numbers in the API payloads; backend uses BigDecimal.
export type FinancingFacility = {
    facId: number; facRef: string; custId: number; prodId: number; schmId: number;
    applId: number | null; facTyp: string; ccyCd: string; apprAmt: number;
    tenorMth: number; rateTyp: string; effRate: number; facSts: string;
    apprDt: string | null; bookDt: string | null; maturityDt: string | null;
    remarks: string | null;
};
export type FinancingOutstanding = {
    facId: number; facRef: string; facSts: string; ccyCd: string;
    approvedAmount: number; disbursedPrincipal: number; principalPaid: number;
    principalOutstanding: number; totalScheduledInterest: number;
    interestPaid: number; remainingScheduledInterest: number;
    totalScheduledRepayment: number; totalPaid: number; totalUnpaid: number;
    overdueAmount: number; overduePrincipal: number; overdueInterest: number;
    totalInstalments: number; paidInstalments: number; partialInstalments: number;
    pendingInstalments: number; overdueInstalments: number;
    nextInstalmentNo: number | null; nextDueDate: string | null;
    nextDueAmount: number | null; repaymentProgressPct: number;
    maturityDate: string | null;
};
export type FinancingScheduleEntry = {
    schedId: number; facId: number; instNo: number; dueDt: string;
    openingPrincipal: number; principalAmt: number; interestAmt: number;
    instAmt: number; closingPrincipal: number; paidPrincipal: number;
    paidInterest: number; instSts: string;
};
export type FinancingDisbursement = {
    disbId: number; facId: number; disbRef: string; disbAmt: number;
    disbSts: string; beneficiaryName: string; beneficiaryRef: string;
    postedDt: string | null;
};
export type FinancingRepayment = {
    repayId: number; facId: number; repayRef: string; repayAmt: number;
    repaySts: string; paymentRef: string; paymentChannel: string;
    allocations: unknown[];
};
export type FinancingClosure = {
    closureId: number; facId: number; closureRef: string; closureTyp: string;
    facilityStatus: string; principalOutstanding: number;
    interestOutstanding: number; remarks: string | null;
    closedDt: string | null; closedBy: string | null;
};
const facilityBase = '/financing/facilities';
const facilityUrl = (id: number) => `${facilityBase}/${id}`;
export const financingFacilities = {
    list: async (params: { custId?: number; status?: string } = {}) =>
        (await api.get<FinancingFacility[]>(facilityBase, {params})).data,
    get: async (id: number) =>
        (await api.get<FinancingFacility>(facilityUrl(id))).data,
    outstanding: async (id: number) =>
        (await api.get<FinancingOutstanding>(`${facilityUrl(id)}/outstanding`)).data,
    summary: async (id: number) =>
        (await api.get(`${facilityUrl(id)}/summary`)).data,
    schedule: async (id: number) =>
        (await api.get<FinancingScheduleEntry[]>(`${facilityUrl(id)}/schedule`)).data,
    generateSchedule: async (id: number, firstDueDate: string) =>
        (await api.post<FinancingScheduleEntry[]>(`${facilityUrl(id)}/schedule/generate`, {firstDueDate})).data,
    disbursement: async (id: number) =>
        (await api.get<FinancingDisbursement>(`${facilityUrl(id)}/disbursement`)).data,
    disburse: async (id: number, body: {
        amount: number; idempotencyKey: string; beneficiaryName: string;
        beneficiaryRef: string; remarks: string;
    }) => (await api.post<FinancingDisbursement>(`${facilityUrl(id)}/disbursement`, body)).data,
    repayments: async (id: number) =>
        (await api.get<FinancingRepayment[]>(`${facilityUrl(id)}/repayments`)).data,
    repay: async (id: number, body: {
        amount: number; idempotencyKey: string; paymentRef: string;
        paymentChannel: string; remarks: string;
    }) => (await api.post<FinancingRepayment>(`${facilityUrl(id)}/repayments`, body)).data,
    closure: async (id: number) =>
        (await api.get<FinancingClosure>(`${facilityUrl(id)}/closure`)).data,
    close: async (id: number, remarks: string) =>
        (await api.post<FinancingClosure>(`${facilityUrl(id)}/close`, {remarks})).data,
};
