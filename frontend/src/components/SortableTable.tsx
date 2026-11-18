import {Children, cloneElement, isValidElement, useMemo, useState, type KeyboardEvent, type ReactElement, type ReactNode, type TableHTMLAttributes, type ThHTMLAttributes} from 'react';
import './UiStandards.css';
import {SortIndicator} from './SortIndicator';

export type TableSortState = {column: number; direction: 'asc' | 'desc'} | null;
type SortableTableProps = TableHTMLAttributes<HTMLTableElement> & {
    children: ReactNode;
    sortState?: TableSortState;
    onSortChange?: (sort: TableSortState) => void;
    remoteSort?: TableSortState;
    remoteSortColumns?: number[];
    onRemoteSortChange?: (sort: TableSortState) => void;
};
type SortHeaderProps = ThHTMLAttributes<HTMLTableCellElement>;

function textValue(node: ReactNode): string {
    if (node == null || typeof node === 'boolean') return '';
    if (typeof node === 'string' || typeof node === 'number') return String(node);
    if (Array.isArray(node)) return node.map(textValue).join(' ');
    if (!isValidElement(node)) return '';

    const props = node.props as {children?: ReactNode; value?: unknown; status?: unknown; title?: unknown; dateTime?: unknown};
    if (node.type === 'time' && typeof props.dateTime === 'string') return props.dateTime;
    const scalar = props.value ?? props.status ?? props.title;
    if (typeof scalar === 'string' || typeof scalar === 'number') return String(scalar);
    return textValue(props.children);
}

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
    if (/^[+-]?(?:\d+\.?\d*|\.\d+)$/.test(numeric)) {
        return {kind: 'number', value: Number(numeric)};
    }
    return {kind: 'text', value: text};
}

function compareRows(left: string, right: string, direction: 'asc' | 'desc'): number {
    if (!left.trim() || !right.trim()) {
        if (!left.trim() && !right.trim()) return 0;
        return !left.trim() ? 1 : -1;
    }
    const a = sortableValue(left);
    const b = sortableValue(right);
    const sign = direction === 'asc' ? 1 : -1;
    if (a.kind === b.kind && a.kind !== 'text') return (Number(a.value) - Number(b.value)) * sign;
    return String(a.value).localeCompare(String(b.value), undefined, {numeric: true, sensitivity: 'base'}) * sign;
}

function isActionHeader(label: string): boolean {
    return /^(actions?|options?|select)$/i.test(label.trim());
}

export function SortableTable({children, sortState, onSortChange, remoteSort, remoteSortColumns = [], onRemoteSortChange, ...props}: SortableTableProps) {
    const [localSort, setLocalSort] = useState<TableSortState>(null);
    const sort = sortState ?? remoteSort ?? localSort;
    const childrenArray = Children.toArray(children);
    const headings = childrenArray.find(child => isValidElement(child) && child.type === 'thead');

    const sortedHead = useMemo(() => {
        if (!isValidElement(headings)) return headings;
        let column = 0;
        const decorate = (node: ReactNode): ReactNode => {
            if (Array.isArray(node)) return node.map(decorate);
            if (!isValidElement(node)) return node;
            const element = node as ReactElement<SortHeaderProps>;
            const label = textValue(element.props.children);
            if (element.type === 'th') {
                const currentColumn = column++;
                if (!label.trim() || isActionHeader(label)) return cloneElement(element, {scope: 'col'});
                const active = sort?.column === currentColumn;
                const onSort = () => {
                    const nextSort: TableSortState = {
                        column: currentColumn,
                        direction: active && sort?.direction === 'asc' ? 'desc' : 'asc'
                    };
                    if (onSortChange) {
                        onSortChange(nextSort);
                        return;
                    }
                    if (remoteSortColumns.includes(currentColumn)) {
                        setLocalSort(null);
                        onRemoteSortChange?.(nextSort);
                    } else {
                        setLocalSort(nextSort);
                        if (remoteSort) onRemoteSortChange?.(null);
                    }
                };
                const onKeyDown = (event: KeyboardEvent<HTMLTableCellElement>) => {
                    element.props.onKeyDown?.(event);
                    if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        onSort();
                    }
                };
                return cloneElement(element, {
                    scope: 'col',
                    tabIndex: 0,
                    'aria-sort': active ? (sort?.direction === 'asc' ? 'ascending' : 'descending') : 'none',
                    className: `${(element.props as SortHeaderProps).className ?? ''} cbms-sortable-heading`.trim(),
                    title: `Sort by ${label}`,
                    onClick: event => { element.props.onClick?.(event); onSort(); },
                    onKeyDown,
                    children: <span className="cbms-sort-header-content">
                        {element.props.children}
                        <SortIndicator direction={active ? sort?.direction ?? null : null}/>
                    </span>
                });
            }
            return cloneElement(element, {children: decorate(element.props.children)});
        };
        return decorate(headings);
    }, [headings, onRemoteSortChange, onSortChange, remoteSort, remoteSortColumns, sort]);

    const sortableChildren = useMemo(() => {
        if (!sort || remoteSortColumns.includes(sort.column)) return childrenArray;
        return childrenArray.map(child => {
            if (!isValidElement(child) || child.type !== 'tbody') return child;
            const tbody = child as ReactElement<{children?: ReactNode}>;
            const rows = Children.toArray(tbody.props.children);
            const sortedRows = [...rows].sort((left, right) => {
                const leftCells = isValidElement(left) ? Children.toArray((left.props as {children?: ReactNode}).children) : [];
                const rightCells = isValidElement(right) ? Children.toArray((right.props as {children?: ReactNode}).children) : [];
                return compareRows(textValue(leftCells[sort.column]), textValue(rightCells[sort.column]), sort.direction);
            });
            return cloneElement(tbody, {children: sortedRows});
        });
    }, [childrenArray, remoteSortColumns, sort]);

    const finalChildren = sortableChildren.map(child => child === headings ? sortedHead : child);
    return <table {...props}>{finalChildren}</table>;
}
