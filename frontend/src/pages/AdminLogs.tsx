import {SortableTable, type TableSortState} from '../components/SortableTable';
import {PageHeader, TablePagination, EmptyState, RefreshButton} from '../components/UiStandards';
import {memo, useCallback, useEffect, useRef, useState, type FormEvent} from 'react';
import {api} from '../api/client';
import {useToast} from '../components/toastContext';
import './admin.css';

type Entry = { timestamp: string; thread: string; level: string; logger: string; message: string };
type Result = { page: number; size: number; totalElements: number; content: Entry[]; source: string };
type Summary = {
    total: number;
    trace: number;
    debug: number;
    info: number;
    warn: number;
    error: number;
    source: string
};
const msg = (e: unknown) => e instanceof Error ? e.message : 'Unable to load application logs';
const emptyEntries: Entry[] = [];
function sameLogPage(left: Result | null, right: Result): boolean {
    return Boolean(left && left.page === right.page && left.size === right.size &&
        left.totalElements === right.totalElements && left.source === right.source &&
        left.content.length === right.content.length && left.content.every((entry, index) => {
            const next = right.content[index];
            return entry.timestamp === next.timestamp && entry.thread === next.thread && entry.level === next.level &&
                entry.logger === next.logger && entry.message === next.message;
        }));
}
const ApplicationLogTable = memo(function ApplicationLogTable({entries, sort, initialLoading, onSortChange}: {
    entries: Entry[];
    sort: TableSortState;
    initialLoading: boolean;
    onSortChange: (sort: TableSortState) => void;
}) {
    return <SortableTable remoteSort={sort} remoteSortColumns={[0, 1, 2, 3, 4]} onRemoteSortChange={onSortChange}>
        <thead><tr><th>Timestamp</th><th>Level</th><th>Thread</th><th>Logger</th><th>Message</th></tr></thead>
        <tbody>{entries.length ? entries.map((entry, index) => <tr key={`${entry.timestamp}-${index}`}>
            <td><time dateTime={entry.timestamp.replace(' ', 'T')}>{entry.timestamp}</time></td>
            <td>{entry.level}</td><td>{entry.thread}</td><td>{entry.logger}</td>
            <td className="admin-log-message">{entry.message}</td>
        </tr>) : <EmptyState message={initialLoading ? 'Loading…' : 'No matching log entries.'} columns={5}/>}</tbody>
    </SortableTable>;
});

export default function AdminLogs() {
    const notify = useToast();
    const [level, setLevel] = useState(''), [logger, setLogger] = useState(''), [keyword, setKeyword] = useState('');
    const [filters, setFilters] = useState({level: '', logger: '', keyword: ''});
    const [data, setData] = useState<Result | null>(null), [summary, setSummary] = useState<Summary | null>(null), [busy, setBusy] = useState(false);
    const [sort, setSort] = useState<TableSortState>(null);
    const [appliedSort, setAppliedSort] = useState<TableSortState>(null);
    const requestSequence = useRef(0);
    const onSortChange = useCallback((next: TableSortState) => setSort(next), []);
    const load = useCallback(async (page = 0, filter = filters, currentSort = sort) => {
        const sequence = ++requestSequence.current;
        setBusy(true);
        try {
            const [result, stats] = await Promise.all([api.get<Result>('/admin/logs', {
                params: {
                    ...filter,
                    page,
                    size: 10,
                    ...(currentSort ? {
                        sortBy: (['timestamp', 'level', 'thread', 'logger', 'message'] as const)[currentSort.column],
                        sortDirection: currentSort.direction
                    } : {})
                }
            }), api.get<Summary>('/admin/logs/summary')]);
            if (sequence !== requestSequence.current) return;
            setData(previous => sameLogPage(previous, result.data) ? previous : result.data);
            setSummary(stats.data);
            setAppliedSort(currentSort);
        } catch (e) {
            if (sequence === requestSequence.current) notify('error', msg(e));
        } finally {
            if (sequence === requestSequence.current) setBusy(false);
        }
    }, [filters, notify, sort]);
    useEffect(() => {
        void Promise.resolve().then(() => load(0))
    }, [load]);
    const search = (e: FormEvent) => {
        e.preventDefault();
        setFilters({level, logger, keyword})
    };
    const clear = () => {
        setLevel('');
        setLogger('');
        setKeyword('');
        setFilters({level: '', logger: '', keyword: ''})
    };
    const download = async () => {
        try {
            const response = await api.get<Blob>('/admin/logs/download', {params: filters, responseType: 'blob'});
            const url = URL.createObjectURL(response.data);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'cbms-logs.txt';
            a.click();
            URL.revokeObjectURL(url)
        } catch (e) {
            notify('error', msg(e))
        }
    };
    return <main className="admin-page">
        <PageHeader
            title="Application Logging"
            description="Search server logs by message or request ID, inspect errors and export filtered entries."
            actions={<RefreshButton loading={busy} onClick={() => void load(data?.page ?? 0)}/>}
        />
        <section className="admin-panel">
            <form className="admin-toolbar" onSubmit={search}><label>Level<select value={level}
                                                                                  onChange={e => setLevel(e.target.value)}>
                <option value="">All Levels</option>
                {['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'].map(x => <option key={x}>{x}</option>)}
            </select></label><label>Logger<input value={logger} onChange={e => setLogger(e.target.value)}
                                                 placeholder="Logger name"/></label><label>Keyword<input value={keyword}
                                                                                                         onChange={e => setKeyword(e.target.value)}
                                                                                                         placeholder="Message or Request ID"/></label>
                <div className="cbms-search-actions">
                    <button className="admin-primary cbms-action-search" disabled={busy}>Search</button>
                    <button className="cbms-action-clear" type="button" onClick={clear}>Clear</button>
                </div>
            </form>
        </section>
        <section className="admin-panel">
            <div className="admin-panel-head"><h2>Log Entries <small>({data?.totalElements ?? 0} records)</small></h2>
                <button type="button" onClick={() => void download()}>Download Logs</button>
            </div>
            <p className="admin-muted">{summary ? `INFO ${summary.info} · WARN ${summary.warn} · ERROR ${summary.error} · DEBUG ${summary.debug} · TRACE ${summary.trace}` : 'Loading log summary…'}</p>
            <div className="admin-scroll">
                <ApplicationLogTable entries={data?.content ?? emptyEntries} sort={appliedSort}
                                      initialLoading={!data && busy} onSortChange={onSortChange}/>
            </div>
            <TablePagination
                page={data?.page ?? 0}
                totalPages={Math.max(1, Math.ceil((data?.totalElements ?? 0) / 10))}
                onPageChange={p => void load(p)}
                busy={busy || !data}
                totalRecords={data?.totalElements}
            />
            <p className="admin-muted">Source: {data?.source ?? 'Current rolling log file'}</p></section>
    </main>;
}
