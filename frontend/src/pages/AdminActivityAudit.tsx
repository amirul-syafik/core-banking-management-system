import {SortableTable, type TableSortState} from '../components/SortableTable';
import {useCallback, useEffect, useState, type FormEvent} from 'react';
import {api} from '../api/client';
import {useToast} from '../components/toastContext';
import {RefreshButton} from '../components/UiStandards';
import {Badge, Empty, ModuleHeader, Paging, Panel} from './PageComponents';
import {dateOnly, explainError, type ApiPage} from './pageUtils';
import PopupView from '../components/PopupView';

type Audit = {
    auditId: number;
    actor: string;
    httpMethod: string;
    requestPath: string;
    httpStatus: number;
    outcome: string;
    createdAt: string
};
export default function AdminActivityAudit() {
    const notify = useToast();
    const [actor, setActor] = useState('');
    const [method, setMethod] = useState('');
    const [status, setStatus] = useState('');
    const [filter, setFilter] = useState({actor: '', method: '', status: ''});
    const [page, setPage] = useState(0);
    const [sort, setSort] = useState<TableSortState>(null);
    const [audit, setAudit] = useState<ApiPage<Audit> | null>(null);
    const [busy, setBusy] = useState(false);
    const load = useCallback(async () => {
        setBusy(true);
        try {
            const {data} = await api.get<ApiPage<Audit>>('/admin/activity-audit', {
                params: {
                    actor: filter.actor || undefined,
                    method: filter.method || undefined, status: filter.status || undefined, page, size: 10,
                    sortBy: sort ? (['createdAt', 'actor', 'httpMethod', 'requestPath', 'httpStatus', 'outcome'] as const)[sort.column] : undefined,
                    sortDirection: sort?.direction
                }
            });
            setAudit(data);
        } catch (error) {
            setAudit(null);
            notify('error', explainError(error));
        } finally {
            setBusy(false);
        }
    }, [filter, page, sort, notify]);
    useEffect(() => {
        void Promise.resolve().then(load);
    }, [load]);
    const search = (event: FormEvent) => {
        event.preventDefault();
        setPage(0);
        setFilter({actor: actor.trim(), method, status});
    };
    const clear = () => {
        setActor('');
        setMethod('');
        setStatus('');
        setPage(0);
        setFilter({actor: '', method: '', status: ''});
    };
    return <main className="gap-page">
        <ModuleHeader title="Application Activity Audit"
                      description="Review authenticated application activity and request outcomes."/>
        <Panel title="Activity Audit" count={audit?.totalElements}
               actions={<RefreshButton onClick={() => void load()} loading={busy}/> }>
            <form className="gap-toolbar" onSubmit={search}>
                <label>Actor<input maxLength={100} value={actor} onChange={e => setActor(e.target.value)}
                                   placeholder="Username"/></label>
                <label>HTTP Method<select value={method} onChange={e => setMethod(e.target.value)}>
                    <option value="">All Methods</option>
                    {['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map(v => <option key={v}>{v}</option>)}</select></label>
                <label>HTTP Status<input type="number" min="100" max="599" value={status}
                                         onChange={e => setStatus(e.target.value)}/></label>
                <div className="cbms-search-actions">
                    <button type="submit" className="cbms-action-search" disabled={busy}>Search</button>
                    <button type="button" className="cbms-action-clear" onClick={clear}>Clear</button>
                </div>
            </form>
            <div className="gap-table-wrap">
                <SortableTable remoteSort={sort} remoteSortColumns={[0, 1, 2, 3, 4, 5]}
                               onRemoteSortChange={next => { setSort(next); setPage(0); }}>
                    <thead>
                    <tr>
                        <th>Date</th>
                        <th>Actor</th>
                        <th>Method</th>
                        <th>Request Path</th>
                        <th>HTTP Status</th>
                        <th>Outcome</th>
                    </tr>
                    </thead>
                    <tbody>
                    {audit?.content.map(row => <tr key={row.auditId}>
                        <td>{dateOnly(row.createdAt)}</td>
                        <td><PopupView value={row.actor}/></td>
                        <td>{row.httpMethod}</td>
                        <td><PopupView value={row.requestPath} maxLength={38}/></td>
                        <td>{row.httpStatus}</td>
                        <td><Badge status={row.outcome}/></td>
                    </tr>)}
                    {!audit?.content.length &&
                        <Empty columns={6} message={busy ? 'Loading…' : 'No activity records.'}/>}</tbody>
                </SortableTable>
            </div>
            <Paging page={page} totalPages={audit?.totalPages ?? 0} totalElements={audit?.totalElements ?? 0} onPage={setPage} busy={busy}/>
        </Panel>
    </main>;
}
