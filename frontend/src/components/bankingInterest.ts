/** Calendar month arithmetic that clamps the day of month, as Java LocalDate.plusMonths does. */
export function addCalendarMonths(isoDate: string, months: number): string {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(isoDate) || !Number.isInteger(months) || months < 1) return '';
    const [year, month, day] = isoDate.split('-').map(Number);
    const targetFirst = new Date(Date.UTC(year, month - 1 + months, 1));
    const last = new Date(Date.UTC(targetFirst.getUTCFullYear(), targetFirst.getUTCMonth() + 1, 0)).getUTCDate();
    targetFirst.setUTCDate(Math.min(day, last));
    return targetFirst.toISOString().slice(0, 10);
}

function dateDays(start: string, end: string): number | null {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(start) || !/^\d{4}-\d{2}-\d{2}$/.test(end)) return null;
    const a = Date.parse(`${start}T00:00:00Z`);
    const b = Date.parse(`${end}T00:00:00Z`);
    if (!Number.isFinite(a) || !Number.isFinite(b) || b <= a) return null;
    return Math.round((b - a) / 86400000);
}

export type InterestMethod = 'financing-flat' | 'deposit-actual-365' | 'credit-full-utilization';

/** Non-posting preview: match current CBMS fixed-rate formulas; server remains authoritative. */
export function interestEstimate(amount: string, annualRate: string, tenor: string, method: InterestMethod, startDate = ''): number | null {
    if (!amount.trim() || !annualRate.trim() || !tenor.trim()) return null;
    const principal = Number(amount), rate = Number(annualRate), months = Number(tenor);
    if (![principal, rate, months].every(Number.isFinite) || principal <= 0 || rate < 0 || !Number.isInteger(months) || months <= 0) return null;
    if (method === 'financing-flat') return Math.round((principal * rate * months / 1200 + Number.EPSILON) * 100) / 100;
    let end = addCalendarMonths(startDate, months);
    // FD maturity maintains the month-end convention even for short months.
    if (method === 'deposit-actual-365' && end) {
        const [y, m, d] = startDate.split('-').map(Number);
        const sourceLastDay = new Date(Date.UTC(y, m, 0)).getUTCDate();
        if (d === sourceLastDay) {
            const [toYear, toMonth] = end.split('-').map(Number);
            end = new Date(Date.UTC(toYear, toMonth, 0)).toISOString().slice(0, 10);
        }
    }
    const days = dateDays(startDate, end);
    if (days == null) return null;
    return Math.round((principal * rate * days / 36500 + Number.EPSILON) * 100) / 100;
}

