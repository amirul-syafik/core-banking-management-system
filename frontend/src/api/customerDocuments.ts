import {api} from './client';

export interface CustomerDocument {
    docId: number;
    custId: number;
    fileNm: string;
    fileTyp: string;
    fileSize: number;
    uploadedDt: string;
}

export async function listCustomerDocuments(custId: number, signal?: AbortSignal): Promise<CustomerDocument[]> {
    const {data} = await api.get<CustomerDocument[]>(`/customers/${custId}/documents`, {signal});
    return data;
}

export async function uploadCustomerDocument(custId: number, file: File): Promise<CustomerDocument> {
    const form = new FormData();
    form.append('file', file);
    const {data} = await api.post<CustomerDocument>(`/customers/${custId}/documents`, form, {timeout: 60000});
    return data;
}

export async function downloadCustomerDocument(custId: number, doc: CustomerDocument): Promise<void> {
    const {data} = await api.get<Blob>(`/customers/${custId}/documents/${doc.docId}/download`, {
        responseType: 'blob',
        timeout: 60000
    });
    const url = URL.createObjectURL(data);
    try {
        const link = document.createElement('a');
        link.href = url;
        link.download = doc.fileNm;
        document.body.appendChild(link);
        link.click();
        link.remove();
    } finally {
        window.setTimeout(() => URL.revokeObjectURL(url), 60000);
    }
}
