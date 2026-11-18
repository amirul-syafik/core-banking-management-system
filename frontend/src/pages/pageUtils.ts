export type ApiPage<T> = { content: T[]; totalElements: number; totalPages: number; number?: number; page?: number };

export function explainError(error: unknown): string {
    if (typeof error === 'object' && error !== null && 'response' in error) {
        const response = (error as {
            response?: { data?: { message?: string; detail?: string; error?: string } }
        }).response;
        return response?.data?.message || response?.data?.detail || response?.data?.error || 'Request failed';
    }
    return error instanceof Error ? error.message : 'Request failed';
}

export function dateTime(value?: string | null) {
    if (!value) return '—';
    const date = new Date(value);
    return Number.isNaN(date.valueOf()) ? value : date.toLocaleString('en-MY', {
        dateStyle: 'medium',
        timeStyle: 'short'
    });
}

/** Date-only UI representation. Dates are formatted from their ISO date component,
 * without converting a calendar date across time zones. The API values are unchanged. */
export function dateOnly(value?: string | null): string {
    if (!value) return '—';
    const part = value.slice(0, 10);
    if (/^\d{4}-\d{2}-\d{2}$/.test(part)) {
        const [year, month, day] = part.split('-');
        return `${day}/${month}/${year}`;
    }
    return value;
}

export function money(value?: number | string | null) {
    if (value === null || value === undefined || value === '') return '—';
    const number = Number(value);
    return Number.isFinite(number) ? number.toLocaleString('en-MY', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }) : '—';
}

export function readable(value: unknown): string {
    if (value === null || value === undefined || value === '') return '—';
    if (typeof value === 'boolean') return value ? 'Yes' : 'No';
    if (typeof value === 'number') return value.toLocaleString('en-MY');
    if (Array.isArray(value)) return value.map(readable).join(', ');
    if (typeof value === 'object') return 'See individual fields';
    return String(value).replaceAll('_', ' ');
}

export function titleCase(value: string) {
    return value.replaceAll('_', ' ').replace(/([a-z])([A-Z])/g, '$1 $2').replace(/\b\w/g, match => match.toUpperCase());
}

