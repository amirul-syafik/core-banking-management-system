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

export async function getCustomerProfilePicture(custId: number): Promise<Blob> {
    const {data} = await api.get<Blob>(`/customers/${custId}/profile-picture`, {responseType: 'blob'});
    return data;
}

export async function replaceCustomerProfilePicture(custId: number, file: File): Promise<void> {
    const form = new FormData();
    form.append('file', await normalizeProfilePicture(file));
    // The shared Axios client defaults to JSON; leave multipart headers to the browser
    // so it can include the FormData boundary required by Spring's multipart resolver.
    await api.put(`/customers/${custId}/profile-picture`, form, {
        timeout: 60000,
        headers: {'Content-Type': undefined},
    });
}

async function normalizeProfilePicture(file: File): Promise<File> {
    if (file.type !== 'image/webp') return file;
    let bitmap: ImageBitmap;
    try {
        bitmap = await createImageBitmap(file);
    } catch {
        throw new Error('The selected WebP image could not be decoded.');
    }
    try {
        const canvas = document.createElement('canvas');
        canvas.width = bitmap.width;
        canvas.height = bitmap.height;
        const context = canvas.getContext('2d');
        if (!context) throw new Error('Unable to prepare the selected image for upload.');
        context.drawImage(bitmap, 0, 0);
        const png = await new Promise<Blob>((resolve, reject) => canvas.toBlob(blob => {
            if (blob) resolve(blob);
            else reject(new Error('Unable to prepare the selected image for upload.'));
        }, 'image/png'));
        if (png.size > 5 * 1024 * 1024) {
            throw new Error('The converted image is larger than 5 MB. Choose a smaller image.');
        }
        const normalizedName = /\.[^.]+$/.test(file.name)
            ? file.name.replace(/\.[^.]+$/, '.png')
            : `${file.name}.png`;
        return new File([png], normalizedName, {type: 'image/png', lastModified: file.lastModified});
    } finally {
        bitmap.close();
    }
}

export async function removeCustomerProfilePicture(custId: number): Promise<void> {
    await api.delete(`/customers/${custId}/profile-picture`);
}
