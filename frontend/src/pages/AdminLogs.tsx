import {SortableTable, type TableSortState} from '../components/SortableTable';
import {PageHeader, TablePagination, EmptyState, RefreshButton} from '../components/UiStandards';
import {useCallback, useEffect, useState, type FormEvent} from 'react';
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
export default function AdminLogs() {
    const notify = useToast();
    const [level, setLevel] = useState(''), [logger, setLogger] = useState(''), [keyword, setKeyword] = useState('');
    const [filters, setFilters] = useState({level: '', logger: '', keyword: ''});
    const [data, setData] = useState<Result | null>(null), [summary, setSummary] = useState<Summary | null>(null), [busy, setBusy] = useState(false);
    const [sort, setSort] = useState<TableSortState>(null);
    const load = useCallback(async (page = 0, filter = filters, currentSort = sort) => {
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
            setData(result.data);
            setSummary(stats.data)
        } catch (e) {
            notify('error', msg(e))
        } finally {
            setBusy(false)
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
                <SortableTable remoteSort={sort} remoteSortColumns={[0, 1, 2, 3, 4]}
                               onRemoteSortChange={setSort}>
                    <thead>
                    <tr>
                        <th>Timestamp</th>
                        <th>Level</th>
                        <th>Thread</th>
                        <th>Logger</th>
                        <th>Message</th>
                    </tr>
                    </thead>
                    <tbody>{data?.content.map((entry, i) => <tr key={`${entry.timestamp}-${i}`}>
                        <td><time dateTime={entry.timestamp.replace(' ', 'T')}>{entry.timestamp}</time></td>
                        <td>{entry.level}</td>
                        <td>{entry.thread}</td>
                        <td>{entry.logger}</td>
                        <td className="admin-log-message">{entry.message}</td>
                    </tr>)}{!data?.content.length &&
                        <EmptyState message={busy ? 'Loading…' : 'No matching log entries.'} columns={5}/>}</tbody>
                </SortableTable>
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
