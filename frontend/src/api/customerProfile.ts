import {api} from './client';

export type ProfileSection =
    'individual-details'
    | 'corporate-details'
    | 'identifications'
    | 'contacts'
    | 'addresses'
    | 'employment'
    | 'kyc-assessments'
    | 'audit-history'
    | 'documents';
export type ProfileRecord = Record<string, unknown>;

export async function getProfileSection(custId: number, section: ProfileSection, signal?: AbortSignal): Promise<unknown> {
    const {data} = await api.get(`/customers/${custId}/${section}`, {signal});
    return data;
}

export async function createProfileRecord(custId: number, section: ProfileSection, payload: Record<string, unknown>): Promise<void> {
    await api.post(`/customers/${custId}/${section}`, payload);
}

export async function updateProfileRecord(custId: number, section: ProfileSection, recordId: number | undefined, payload: Record<string, unknown>): Promise<void> {
    await api.put(`/customers/${custId}/${section}${recordId === undefined ? '' : `/${recordId}`}`, payload);
}

export async function registerIdentification(custId: number, payload: Record<string, unknown>, file: File): Promise<void> {
    const form = new FormData();
    form.append('details', JSON.stringify(payload));
    form.append('file', file);
    // Override the shared JSON default so Axios/browser can set the multipart boundary.
    await api.post(`/customers/${custId}/identifications`, form, {
        timeout: 60000,
        headers: {'Content-Type': undefined},
    });
}
