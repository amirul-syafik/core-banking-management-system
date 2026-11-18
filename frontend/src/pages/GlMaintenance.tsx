import {SortableTable} from '../components/SortableTable';
import {useCallback, useEffect, useRef, useState, type FormEvent} from 'react';
import {api} from '../api/client';
import {useAuth} from '../auth/useAuth';
import {useToast} from '../components/toastContext';
import {
    CloseIcon,
    ConfirmDialog,
    FieldLabel,
    RecordCount,
    StatusBadge,
    PageHeader,
    CreateButton,
    RefreshButton,
    TablePagination,
    EmptyState,
    SubscreenTitle
} from '../components/UiStandards';
import './maintenance.css';
import './GlMaintenance.css';

type Account = { gl_cd: string; gl_nm: string; gl_typ: string; ccy_cd: string; act_flg: boolean };
type Action = 'CREATE' | 'RENAME' | 'ACTIVATE' | 'DEACTIVATE';
type Change = {
    request_id: number; request_ref: string; action_typ: Action; gl_cd: string; gl_nm: string;
    gl_typ: string; ccy_cd: string; request_sts: 'SUBMITTED' | 'APPROVED' | 'REJECTED';
    request_reason: string; requested_by: string; requested_at: string;
    decided_by: string | null; decided_at: string | null; decision_remarks: string | null;
};
type Page<T> = { content: T[]; page: number; totalPages: number; totalElements: number };
type Draft = {
    action: Action;
    glCode: string;
    glName: string;
    glType: string;
    currency: string;
    reason: string;
    previousName: string
};
const blank = (): Draft => ({
    action: 'CREATE',
    glCode: '',
    glName: '',
    glType: 'ASSET',
    currency: 'MYR',
    reason: '',
    previousName: ''
});
const glTypes = ['ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE'];
const actionLabels: Record<Action, string> = {
    CREATE: 'Create',
    RENAME: 'Save Changes',
    ACTIVATE: 'Activate',
    DEACTIVATE: 'Deactivate'
};

function errorText(err: unknown): string {
    if (err && typeof err === 'object' && 'response' in err) {
        const body = (err as { response?: { data?: { message?: string; detail?: string } } }).response?.data;
        return body?.message || body?.detail || 'Unable to complete the GL maintenance request.';
    }
    return err instanceof Error ? err.message : 'Unable to complete the GL maintenance request.';
}

export default function GlMaintenance() {
    const {session} = useAuth();
    const notify = useToast();
    const canMake = Boolean(session?.roles.some(role => role === 'MAKER' || role === 'ADMIN'));
    const isAdmin = Boolean(session?.roles.includes('ADMIN'));
    const [tab, setTab] = useState<'accounts' | 'requests'>('accounts');
    const [query, setQuery] = useState('');
    const [appliedQuery, setAppliedQuery] = useState('');
    const [page, setPage] = useState(0);
    const [accountData, setAccountData] = useState<Page<Account> | null>(null);
    const [status, setStatus] = useState('SUBMITTED');
    const [requestPage, setRequestPage] = useState(0);
    const [requestData, setRequestData] = useState<Page<Change> | null>(null);
    const [loading, setLoading] = useState(false);
    const [refreshCount, setRefreshCount] = useState(0);
    const [draft, setDraft] = useState<Draft | null>(null);
    const [confirmDraft, setConfirmDraft] = useState(false);
    const [decision, setDecision] = useState<{ request: Change; approve: boolean } | null>(null);
    const [remarks, setRemarks] = useState('');
    const [busy, setBusy] = useState(false);
    const inFlight = useRef(false);

    const reload = useCallback(async () => {
        setLoading(true);
        try {
            const [accounts, requests] = await Promise.all([
                api.get<Page<Account>>('/gl-maintenance/accounts', {params: {query: appliedQuery, page}}),
                api.get<Page<Change>>('/gl-maintenance/requests', {params: {status, page: requestPage}})
            ]);
            setAccountData(accounts.data);
            setRequestData(requests.data);
        } catch (err) {
            notify('error', errorText(err));
        } finally {
            setLoading(false);
        }
    }, [appliedQuery, page, requestPage, status, notify]);
    useEffect(() => {
        void Promise.resolve().then(reload);
    }, [reload, refreshCount]);

    const start = (action: Action, account?: Account) => {
        if (!canMake || busy) return;
        setConfirmDraft(false);
        setDraft(account ? {
            action, glCode: account.gl_cd, glName: account.gl_nm, glType: account.gl_typ,
            currency: account.ccy_cd, previousName: account.gl_nm, reason: ''
        } : blank());
    };
    const requestSubmit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!draft || busy) return;
        if (draft.action === 'RENAME' && draft.glName.trim() === draft.previousName.trim()) {
            notify('warning', 'No changes to save.');
            return;
        }
        setConfirmDraft(true);
    };
    const submit = async () => {
        if (!draft || busy || inFlight.current || !confirmDraft) return;
        inFlight.current = true;
        setBusy(true);
        try {
            const {data} = await api.post<{ reference: string }>('/gl-maintenance/requests', {
                action: draft.action, glCode: draft.glCode.trim().toUpperCase(),
                glName: draft.glName.trim(), glType: draft.glType,
                currency: draft.currency.trim().toUpperCase(), reason: draft.reason.trim()
            });
            notify('success', `GL maintenance request ${data.reference} submitted for independent review.`);
            setConfirmDraft(false);
            setDraft(null);
            setStatus('SUBMITTED');
            setRequestPage(0);
            setTab('requests');
            setRefreshCount(n => n + 1);
        } catch (err) {
            notify('error', errorText(err));
        } finally {
            inFlight.current = false;
            setBusy(false);
        }
    };
    const confirmDecision = async () => {
        if (!decision || busy || inFlight.current) return;
        if (!decision.approve && remarks.trim().length < 5) return;
        inFlight.current = true;
        setBusy(true);
        try {
            await api.post(`/gl-maintenance/requests/${decision.request.request_id}/decision`, {
                approve: decision.approve, remarks: remarks.trim()
            });
            notify('success', `GL change request ${decision.approve ? 'approved' : 'rejected'}.`);
            setDecision(null);
            setRemarks('');
            setRefreshCount(n => n + 1);
        } catch (err) {
            notify('error', errorText(err));
        } finally {
            inFlight.current = false;
            setBusy(false);
        }
    };
    const openDecision = (request: Change, approve: boolean) => {
        if (!isAdmin || request.request_sts !== 'SUBMITTED' ||
            request.requested_by.toLowerCase() === session?.username.toLowerCase()) return;
        setRemarks('');
        setDecision({request, approve});
    };

    return <main className="maint-page gl-maint-page">
        <PageHeader
            title="GL Account Maintenance"
            description="Maintain the chart of accounts through independent maker-checker approval. This screen does not post journal entries."
            actions={
                <div className="cbms-toolbar-actions">
                    <RefreshButton onClick={() => void reload()} loading={loading}/>
                    {canMake && <CreateButton onClick={() => start('CREATE')}>New GL Account</CreateButton>}
                </div>
            }
        />
        <section className="maint-panel">
            <div className="gl-maint-tabs" role="tablist" aria-label="GL Maintenance sections">
                <button type="button" role="tab" aria-selected={tab === 'accounts'}
                        className={tab === 'accounts' ? 'active' : ''}
                        onClick={() => setTab('accounts')}>GL Accounts
                </button>
                <button type="button" role="tab" aria-selected={tab === 'requests'}
                        className={tab === 'requests' ? 'active' : ''}
                        onClick={() => setTab('requests')}>Approval Requests
                </button>
            </div>
            {tab === 'accounts' ? <>
                <form className="maint-toolbar gl-maint-toolbar" onSubmit={event => {
                    event.preventDefault();
                    setPage(0);
                    setAppliedQuery(query.trim());
                }}>
                    <label>Search GL Accounts<input type="search" maxLength={80} value={query}
                                                    placeholder="GL code or name"
                                                    onChange={event => setQuery(event.target.value)}/></label>
                    <div className="cbms-search-actions">
                        <button type="submit" className="cbms-action-search">Search</button>
                        <button type="button" className="cbms-action-clear" onClick={() => {
                            setQuery('');
                            setAppliedQuery('');
                            setPage(0);
                        }}>Clear
                        </button>
                    </div>
                </form>
                <h2>GL Accounts <RecordCount count={accountData?.totalElements ?? 0}/></h2>
                <div className="maint-scroll">
                    <SortableTable>
                        <thead>
                        <tr>
                            <th>GL Code</th>
                            <th>GL Name</th>
                            <th>GL Type</th>
                            <th>Currency</th>
                            <th>Status</th>
                            <th>Action</th>
                        </tr>
                        </thead>
                        <tbody>
                        {accountData?.content.map(account => <tr key={account.gl_cd}>
                            <td>{account.gl_cd}</td>
                            <td>{account.gl_nm}</td>
                            <td>{account.gl_typ}</td>
                            <td>{account.ccy_cd}</td>
                            <td><StatusBadge status={account.act_flg ? 'ACTIVE' : 'INACTIVE'}/></td>
                            <td className="maint-actions">{canMake && <>
                                <button type="button" onClick={() => start('RENAME', account)}>Edit</button>
                                <button type="button"
                                        onClick={() => start(account.act_flg ? 'DEACTIVATE' : 'ACTIVATE', account)}>
                                    {account.act_flg ? 'Deactivate' : 'Activate'}</button>
                            </>}</td>
                        </tr>)}
                        {!accountData?.content.length &&
                            <EmptyState message={loading ? 'Loading…' : 'No GL accounts found.'} columns={6}/>}
                        </tbody>
                    </SortableTable>
                </div>
                <TablePagination
                    page={page}
                    totalPages={accountData?.totalPages ?? 1}
                    onPageChange={setPage}
                    busy={loading}
                    totalElements={accountData?.totalElements ?? 0}
                    className="gl-maint-pagination"
                />
            </> : <>
                <div className="maint-toolbar gl-maint-toolbar">
                    <label>Status<select value={status} onChange={event => {
                        setStatus(event.target.value);
                        setRequestPage(0);
                    }}>
                        {['SUBMITTED', 'APPROVED', 'REJECTED', 'ALL'].map(s => <option key={s} value={s}>{formatBusinessValue(s)}</option>)}
                    </select></label>
                    <RefreshButton type="button" loading={loading} onClick={() => setRefreshCount(n => n + 1)}/>
                </div>
                <h2>GL Change Requests <RecordCount count={requestData?.totalElements ?? 0}/></h2>
                <div className="maint-scroll">
                    <SortableTable>
                        <thead>
                        <tr>
                            <th>Request Reference</th>
                            <th>Action</th>
                            <th>GL Code</th>
                            <th>GL Name</th>
                            <th>Submitted By</th>
                            <th>Status</th>
                            <th>Action</th>
                        </tr>
                        </thead>
                        <tbody>
                        {requestData?.content.map(request => <tr key={request.request_id}>
                            <td>{request.request_ref}</td>
                            <td>{request.action_typ}</td>
                            <td>{request.gl_cd}</td>
                            <td>{request.gl_nm}</td>
                            <td>{request.requested_by}</td>
                            <td><StatusBadge status={request.request_sts}/></td>
                            <td className="maint-actions">{isAdmin && request.request_sts === 'SUBMITTED' &&
                            request.requested_by.toLowerCase() !== session?.username.toLowerCase() ? <>
                                <button type="button" onClick={() => openDecision(request, true)}>Approve</button>
                                <button type="button" onClick={() => openDecision(request, false)}>Reject</button>
                            </> : request.request_sts === 'SUBMITTED' ? 'Awaiting independent review' : request.decided_by || '—'}</td>
                        </tr>)}
                        {!requestData?.content.length &&
                            <EmptyState message={loading ? 'Loading…' : 'No GL change requests found.'} columns={7}/>}
                        </tbody>
                    </SortableTable>
                </div>
                <TablePagination
                    page={requestPage}
                    totalPages={requestData?.totalPages ?? 1}
                    onPageChange={setRequestPage}
                    busy={loading}
                    totalRecords={requestData?.totalElements}
                    className="gl-maint-pagination"
                />
            </>}
        </section>
        {draft && <div className="maint-overlay" onMouseDown={event => {
            if (event.target === event.currentTarget && !busy) setDraft(null);
        }}>
            <section className="maint-dialog" role="dialog" aria-modal="true" aria-label="GL account changes">
                <div className="maint-dialog-head">
                    <SubscreenTitle as="h2"
                        title={draft.action === 'CREATE' ? 'New GL Account' : draft.action === 'RENAME' ? 'Edit GL Account' : `${actionLabels[draft.action]} GL Account`}
                        reference={draft.action === 'CREATE' ? undefined : draft.glCode}/>
                    <button type="button" disabled={busy} aria-label="Close" onClick={() => setDraft(null)}><CloseIcon/>
                    </button>
                </div>
                <form onSubmit={requestSubmit}>
                    <div className="maint-grid">
                        <label><FieldLabel required>GL Code</FieldLabel><input required autoFocus maxLength={30}
                                                                               pattern="[A-Z0-9_-]+"
                                                                               readOnly={draft.action !== 'CREATE'}
                                                                               value={draft.glCode}
                                                                               onChange={event => setDraft({
                                                                                   ...draft,
                                                                                   glCode: event.target.value.toUpperCase()
                                                                               })}/></label>
                        <label><FieldLabel required>GL Name</FieldLabel><input required maxLength={150} minLength={2}
                                                                               readOnly={!['CREATE', 'RENAME'].includes(draft.action)}
                                                                               value={draft.glName}
                                                                               onChange={event => setDraft({
                                                                                   ...draft,
                                                                                   glName: event.target.value
                                                                               })}/></label>
                        <label><FieldLabel required>GL Type</FieldLabel><select required
                                                                                disabled={draft.action !== 'CREATE'}
                                                                                value={draft.glType}
                                                                                onChange={event => setDraft({
                                                                                    ...draft,
                                                                                    glType: event.target.value
                                                                                })}>
                            {glTypes.map(value => <option key={value} value={value}>{formatBusinessValue(value)}</option>)}
                        </select></label>
                        <label><FieldLabel required>Currency</FieldLabel><input required maxLength={3} minLength={3}
                                                                                pattern="[A-Z]{3}"
                                                                                readOnly={draft.action !== 'CREATE'}
                                                                                value={draft.currency}
                                                                                onChange={event => setDraft({
                                                                                    ...draft,
                                                                                    currency: event.target.value.toUpperCase()
                                                                                })}/></label>
                        <label className="maint-wide"><FieldLabel required>Reason</FieldLabel><textarea required
                                                                                                        minLength={5}
                                                                                                        maxLength={500}
                                                                                                        value={draft.reason}
                                                                                                        onChange={event => setDraft({
                                                                                                            ...draft,
                                                                                                            reason: event.target.value
                                                                                                        })}/></label>
                    </div>
                    <p className="maint-note">All changes require approval by a different administrator. New GL accounts
                        remain inactive until a separate activation is approved.</p>
                    <div className="maint-footer">
                        <button type="button" disabled={busy} onClick={() => setDraft(null)}>Cancel</button>
                        <button type="submit" className="maint-primary"
                                disabled={busy}>{busy ? 'Processing…' : draft.action === 'RENAME' ? 'Save Changes' : draft.action === 'CREATE' ? 'Create' : actionLabels[draft.action]}</button>
                    </div>
                </form>
            </section>
        </div>}
        {draft && confirmDraft && <ConfirmDialog title="Confirm Submission"
                                                 message="Are you sure you want to submit this GL account change for approval?"
                                                 confirmLabel="Submit for Approval" busy={busy}
                                                 onCancel={() => setConfirmDraft(false)}
                                                 onConfirm={() => void submit()}/>}
        {decision && <ConfirmDialog title={decision.approve ? 'Confirm Approval' : 'Confirm Rejection'}
                                    message={`Are you sure you want to ${decision.approve ? 'approve' : 'reject'} this GL account change?`}
                                    confirmLabel={decision.approve ? 'Approve' : 'Reject'} busy={busy}
                                    danger={!decision.approve}
                                    reasonLabel="Review Remarks" reason={remarks} onReasonChange={setRemarks}
                                    reasonRequired={!decision.approve} reasonMaxLength={500}
                                    onCancel={() => {
                                        setDecision(null);
                                        setRemarks('');
                                    }} onConfirm={() => void confirmDecision()}/>}
    </main>;
}
import {formatBusinessValue} from '../utils/display';
