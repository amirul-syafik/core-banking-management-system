import {api} from './client';

export type Facility = {
    clFacId: number;
    clFacRef: string;
    custId: number;
    prodId: number;
    schmId: number;
    clFacTyp: string;
    ccyCd: string;
    apprLimit: number;
    utilizedAmt: number;
    availableLimit: number;
    effDt: string;
    expiryDt: string;
    rateTyp: string;
    effRate: number;
    clFacSts: string;
    remarks?: string
};
export type Monitoring = {
    facilityId: number;
    facilityReference: string;
    customerId: number;
    facilityType: string;
    facilityStatus: string;
    currency: string;
    approvedLimit: number;
    utilizedAmount: number;
    availableLimit: number;
    accruedInterest: number;
    effectiveDate: string;
    expiryDate: string;
    daysToExpiry: number;
    expired: boolean;
    eligibleForClosure: boolean
};
export type Limit = {
    approvedLimit: number;
    utilizedAmount: number;
    availableLimit: number;
    facilityStatus: string;
    currency: string
};
export type FloorStock = {
    stockId: number; stockReference: string; facilityId: number; vinNo: string;
    vehicleMake: string; vehicleModel: string; manufactureYear: number;
    purchasePrice: number; financedAmount: number; outstandingAmount: number;
    status: string; financeDate: string | null; settlementDate: string | null; remarks?: string | null
};
export type FloorStockCreate = {
    vinNo: string; vehicleMake: string; vehicleModel: string; manufactureYear: number;
    purchasePrice: number; financedAmount: number; remarks: string
};
export type Entry = Record<string, string | number | null>;
const base = '/credit-lines';
const facility = (id: number) => `${base}/facilities/${id}`;
export const creditLines = {
    stocks: async (id: number) => (await api.get<FloorStock[]>(`${facility(id)}/stocks`)).data,
    financeStock: async (id: number, body: FloorStockCreate) => (await api.post<FloorStock>(`${facility(id)}/stocks`, body)).data,
    settleStock: async (stockId: number, body: {
        settlementReference: string;
        reason: string
    }) => (await api.post<FloorStock>(`${base}/stocks/${stockId}/settle`, body)).data,
    monitoring: async () => (await api.get<Monitoring[]>(`${base}/monitoring`)).data,
    detail: async (id: number) => (await api.get<Facility>(facility(id))).data,
    create: async (body: {
        custId: number;
        prodId: number;
        schmId: number;
        clFacTyp: string;
        apprLimit: number;
        effDt: string;
        expiryDt: string;
        rateTyp: string;
        effRate: number;
        remarks: string
    }) => (await api.post<Facility>(`${base}/facilities`, body)).data,
    activate: async (id: number, reason: string) => (await api.post(`${facility(id)}/activate`, {reason})).data,
    adjust: async (id: number, newLimit: number, reason: string) => (await api.post(`${facility(id)}/limit-adjustments`, {
        newLimit,
        reason
    })).data,
    limit: async (id: number) => (await api.get<Limit>(`${facility(id)}/limit`)).data,
    limitHistory: async (id: number) => (await api.get<Entry[]>(`${facility(id)}/limit-adjustments`)).data,
    drawdown: async (id: number, drawAmount: number, drawReference: string, remarks: string) => (await api.post(`${facility(id)}/drawdowns`, {
        drawAmount,
        drawReference,
        remarks
    })).data,
    drawdowns: async (id: number) => (await api.get<Entry[]>(`${facility(id)}/drawdowns`)).data,
    repay: async (id: number, repayAmount: number, repayReference: string, remarks: string) => (await api.post(`${facility(id)}/repayments`, {
        repayAmount,
        repayReference,
        remarks
    })).data,
    repayments: async (id: number) => (await api.get<Entry[]>(`${facility(id)}/repayments`)).data,
    interest: async (id: number) => (await api.get<Entry>(`${facility(id)}/interest/summary`)).data,
    accruals: async (id: number) => (await api.get<Entry[]>(`${facility(id)}/interest/accruals`)).data,
    accrue: async (id: number, accrualDate: string) => (await api.post(`${facility(id)}/interest/accrue`, {accrualDate})).data,
    statusHistory: async (id: number) => (await api.get<Entry[]>(`${facility(id)}/status-history`)).data,
    status: async (id: number, action: 'suspend' | 'reactivate' | 'close', reason: string) => (await api.post(`${facility(id)}/${action}`, {reason})).data
};
