import {api} from './client';

export type Customer = {
    custId: number;
    cifNo: string;
    custNm: string;
    custTyp: string;
    sts: string;
    crtDt?: string;
    crtBy?: string;
    updDt?: string;
    updBy?: string;
};
export type CustomerPage = {
    content: Customer[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
};
export type CustomerOverview = {
    custId: number;
    custNm: string;
    custTyp: string;
    customerStatus: string;
    accounts?: unknown;
    financing?: unknown;
    fixedDeposits?: unknown;
    collateral?: unknown;
};
export type CustomerSearch = {
    name?: string;
    type?: string;
    status?: string;
    page: number;
    size: number;
};

export async function searchCustomers(filters: CustomerSearch, signal?: AbortSignal) {
    const {data} = await api.get<CustomerPage>('/customers/search', {
        params: {
            ...filters,
            name: filters.name || undefined,
            type: filters.type || undefined,
            status: filters.status || undefined
        },
        signal,
    });
    return data;
}

export async function getCustomer(custId: number) {
    const {data} = await api.get<Customer>(`/customers/${custId}`);
    return data;
}

export async function getCustomerOverview(custId: number) {
    const {data} = await api.get<CustomerOverview>(`/customers/${custId}/overview`);
    return data;
}

export async function createCustomer(request: { custNm: string; custTyp: string }) {
    const {data} = await api.post<Customer>('/customers', request);
    return data;
}
