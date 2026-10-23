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
    const load = useCallback(async (page = 0, filter = filters) => {
        setBusy(true);
        try {
            const [result, stats] = await Promise.all([api.get<Result>('/admin/logs', {
                params: {
                    ...filter,
                    page,
                    size: 20
                }
            }), api.get<Summary>('/admin/logs/summary')]);
            setData(result.data);
            setSummary(stats.data)
        } catch (e) {
            notify('error', msg(e))
        } finally {
            setBusy(false)
        }
    }, [filters, notify]);
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
        <header><h1>Application Logging</h1><p>Search server logs, inspect errors and export filtered entries.</p>
        </header>
        <section className="admin-panel">
            <form className="admin-toolbar" onSubmit={search}><label>Level<select value={level}
                                                                                  onChange={e => setLevel(e.target.value)}>
                <option value="">All Levels</option>
                {['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'].map(x => <option key={x}>{x}</option>)}
            </select></label><label>Logger<input value={logger} onChange={e => setLogger(e.target.value)}
                                                 placeholder="Logger name"/></label><label>Keyword<input value={keyword}
                                                                                                         onChange={e => setKeyword(e.target.value)}
                                                                                                         placeholder="Message contains"/></label>
                <button type="button" onClick={clear}>Clear</button>
                <button className="admin-primary" disabled={busy}>Search</button>
            </form>
        </section>
        <section className="admin-panel">
            <div className="admin-panel-head"><h2>Log Entries <small>({data?.totalElements ?? 0} records)</small></h2>
                <button type="button" onClick={() => void download()}>Download Logs</button>
            </div>
            <p className="admin-muted">{summary ? `INFO ${summary.info} · WARN ${summary.warn} · ERROR ${summary.error} · DEBUG ${summary.debug} · TRACE ${summary.trace}` : 'Loading log summary…'}</p>
            <div className="admin-scroll">
                <table>
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
                        <td>{entry.timestamp}</td>
                        <td>{entry.level}</td>
                        <td>{entry.thread}</td>
                        <td>{entry.logger}</td>
                        <td className="admin-log-message">{entry.message}</td>
                    </tr>)}{!data?.content.length && <tr>
                        <td colSpan={5}>{busy ? 'Loading…' : 'No matching log entries.'}</td>
                    </tr>}</tbody>
                </table>
            </div>
            <div className="admin-pagination">
                <button type="button" disabled={busy || !data || data.page === 0}
                        onClick={() => void load((data?.page ?? 0) - 1)}>Previous
                </button>
                <span>Page {(data?.page ?? 0) + 1} of {Math.max(1, Math.ceil((data?.totalElements ?? 0) / 20))}</span>
                <button type="button" disabled={busy || !data || (data.page + 1) * 20 >= data.totalElements}
                        onClick={() => void load((data?.page ?? 0) + 1)}>Next
                </button>
            </div>
            <p className="admin-muted">Source: {data?.source ?? 'Current rolling log file'}</p></section>
    </main>;
}
