import type {TableSortState} from '../components/SortableTable';

function sortableValue(raw: string): {kind: 'date' | 'number' | 'text'; value: number | string} {
    const text = raw.trim();
    const isoDate = /^(\d{4})-(\d{1,2})-(\d{1,2})(?:[T\s].*)?$/.exec(text);
    if (isoDate) {
        const timestamp = Date.parse(text);
        return {kind: 'date', value: Number.isNaN(timestamp)
            ? Date.UTC(Number(isoDate[1]), Number(isoDate[2]) - 1, Number(isoDate[3]))
            : timestamp};
    }
    const localDate = /^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})$/.exec(text);
    if (localDate) return {kind: 'date', value: Date.UTC(Number(localDate[3]), Number(localDate[2]) - 1, Number(localDate[1]))};

    const numeric = text.replace(/[\s,]/g, '').replace(/^(?:MYR|RM|USD|EUR|GBP|JPY)/i, '')
        .replace(/%$/, '').replace(/[()]/g, match => match === '(' ? '-' : '');
    if (/^[+-]?(?:\d+\.?\d*|\.\d+)$/.test(numeric)) return {kind: 'number', value: Number(numeric)};
    return {kind: 'text', value: text};
}

function compareValues(left: unknown, right: unknown, direction: 'asc' | 'desc'): number {
    const a = left == null ? '' : left instanceof Date ? left.toISOString() : String(left);
    const b = right == null ? '' : right instanceof Date ? right.toISOString() : String(right);
    if (!a.trim() || !b.trim()) {
        if (!a.trim() && !b.trim()) return 0;
        return !a.trim() ? 1 : -1;
    }
    const parsedA = sortableValue(a);
    const parsedB = sortableValue(b);
    const sign = direction === 'asc' ? 1 : -1;
    if (parsedA.kind === parsedB.kind && parsedA.kind !== 'text')
        return (Number(parsedA.value) - Number(parsedB.value)) * sign;
    return String(parsedA.value).localeCompare(String(parsedB.value), undefined, {numeric: true, sensitivity: 'base'}) * sign;
}

/** Sort a complete in-memory result set before the caller applies pagination. */
export function sortTableRecords<T>(
    records: readonly T[],
    sort: TableSortState,
    columns: readonly ((record: T) => unknown)[]
): T[] {
    if (!sort || !columns[sort.column]) return [...records];
    const getValue = columns[sort.column];
    return records.map((record, index) => ({record, index, value: getValue(record)}))
        .sort((left, right) => compareValues(left.value, right.value, sort.direction) || left.index - right.index)
        .map(item => item.record);
}
