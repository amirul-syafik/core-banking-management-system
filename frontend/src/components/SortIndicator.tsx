export type SortDirection = 'asc' | 'desc' | null;

/** Compact dual-triangle indicator for shared sortable table headers. */
export function SortIndicator({direction}: {direction: SortDirection}) {
    return <span className="cbms-sort-indicator" aria-hidden="true">
        <span className={`cbms-sort-triangle cbms-sort-triangle-up${direction === 'asc' ? ' is-active' : ''}`}/>
        <span className={`cbms-sort-triangle cbms-sort-triangle-down${direction === 'desc' ? ' is-active' : ''}`}/>
    </span>;
}
