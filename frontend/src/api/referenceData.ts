import {api} from './client';

export type ReferenceCodeType = 'countries' | 'currencies';
export type ReferenceOption = { code: string; name: string };

// Share in-flight and resolved lookups so multiple fields do not refetch the same catalog.
// A failed request is evicted, so reopening a form can retry without a page refresh.
const cache: Partial<Record<ReferenceCodeType, Promise<ReferenceOption[]>>> = {};

export function getReferenceOptions(kind: ReferenceCodeType): Promise<ReferenceOption[]> {
    const cached = cache[kind];
    if (cached) return cached;
    const request = api.get<ReferenceOption[]>(`/reference-data/${kind}`)
        .then(({data}) => {
            if (!Array.isArray(data) || !data.every(item => typeof item.code === 'string' && typeof item.name === 'string')) {
                throw new Error('Invalid reference catalog response');
            }
            return data;
        })
        .catch(error => {
            delete cache[kind];
            throw error;
        });
    cache[kind] = request;
    return request;
}
