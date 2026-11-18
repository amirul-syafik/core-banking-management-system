import {SortableTable, type TableSortState} from '../components/SortableTable';
import {
    StatusBadge,
    CloseIcon,
    RefreshButton,
    FieldLabel,
    PageHeader,
    CreateButton,
    TablePagination,
    SearchActions
} from '../components/UiStandards';
import {useEffect, useState} from 'react';
import {useToast} from '../components/toastContext';
import {AxiosError} from 'axios';
import {createCustomer, searchCustomers, type CustomerPage} from '../api/customers';
import {useAuth} from '../auth/useAuth';
import {formatBusinessValue} from '../utils/display';
import {useNavigate} from 'react-router-dom';
import './Customers.css';
import {getCustomerStatusHistory, type CustomerStatusHistoryPage} from '../api/customerStatusHistory';

function message(error: unknown) {
    if (error instanceof AxiosError) {
        if (error.response?.status === 401) return 'Session expired. Please sign in again.';
        if (error.response?.status === 403) return 'You do not have permission for this operation.';
        const data: unknown = error.response?.data;
        if (typeof data === 'object' && data !== null && 'message' in data && typeof data.message === 'string') return data.message;
        return `Request failed (${error.response?.status ?? 'network error'}).`;
    }
    return error instanceof Error ? error.message : 'Unexpected error.';
}

type CustomerDirectoryState = {
    name: string;
    type: string;
    status: string;
    appliedFilters: {name: string; type: string; status: string};
    page: number;
    activeTab: 'directory' | 'history';
    historyPage: number;
};

const customerDirectoryStateKey = 'cbms.customer-directory-state.v2';

function readCustomerDirectoryState(): CustomerDirectoryState | null {
    try {
        const stored = sessionStorage.getItem(customerDirectoryStateKey);
        if (!stored) return null;
        sessionStorage.removeItem(customerDirectoryStateKey);
        const value: unknown = JSON.parse(stored);
        if (typeof value !== 'object' || value === null) return null;
        const state = value as Partial<CustomerDirectoryState>;
        if (typeof state.name !== 'string' || typeof state.type !== 'string' || typeof state.status !== 'string' ||
            typeof state.page !== 'number' || !Number.isInteger(state.page) || state.page < 0 ||
            !state.appliedFilters || typeof state.appliedFilters.name !== 'string' ||
            typeof state.appliedFilters.type !== 'string' || typeof state.appliedFilters.status !== 'string') return null;
        return {
            name: state.name,
            type: state.type,
            status: state.status,
            appliedFilters: state.appliedFilters,
            page: state.page,
            activeTab: state.activeTab === 'history' ? 'history' : 'directory',
            historyPage: typeof state.historyPage === 'number' && Number.isInteger(state.historyPage) && state.historyPage >= 0
                ? state.historyPage : 0
        };
    } catch {
        return null;
    }
}

export default function Customers() {
    const {session} = useAuth();
    const notify = useToast();
    const navigate = useNavigate();
    const [restoredDirectoryState] = useState(readCustomerDirectoryState);
    const [name, setName] = useState(restoredDirectoryState?.name ?? '');
    const [type, setType] = useState(restoredDirectoryState?.type ?? '');
    const [status, setStatus] = useState(restoredDirectoryState?.status ?? '');
    const [appliedFilters, setAppliedFilters] = useState(restoredDirectoryState?.appliedFilters ?? {name: '', type: '', status: ''});
    const [page, setPage] = useState(restoredDirectoryState?.page ?? 0);
    const [results, setResults] = useState<CustomerPage | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [refresh, setRefresh] = useState(0);
    const [showCreate, setShowCreate] = useState(false);
    const [newName, setNewName] = useState('');
    const [newType, setNewType] = useState('INDIVIDUAL');
    const [saving, setSaving] = useState(false);
    const [createError, setCreateError] = useState('');
    const [notice, setNotice] = useState('');
    const [historyPage, setHistoryPage] = useState(restoredDirectoryState?.historyPage ?? 0);
    const [directorySort, setDirectorySort] = useState<TableSortState>(null);
    const [historySort, setHistorySort] = useState<TableSortState>(null);
    const [history, setHistory] = useState<CustomerStatusHistoryPage | null>(null);
    const [historyError, setHistoryError] = useState('');
    const [historyLoading, setHistoryLoading] = useState(true);
    const [activeTab, setActiveTab] = useState<'directory' | 'history'>(restoredDirectoryState?.activeTab ?? 'directory');

    // Backend permissions remain authoritative; this is only a UI affordance.
    // Roles are not inferred from the OpenAPI specification.
    const canCreate = Boolean(session?.roles.some(role => role === 'MAKER' || role === 'ADMIN'));

    useEffect(() => {
        const controller = new AbortController();

        async function loadCustomers() {
            try {
                setLoading(true);
                setError('');

                const data = await searchCustomers(
                    {
                        name: appliedFilters.name,
                        type: appliedFilters.type,
                        status: appliedFilters.status,
                        page,
                        size: 10,
                        sortBy: directorySort ? (['cifNo', 'custNm', 'custTyp', 'sts'] as const)[directorySort.column] : undefined,
                        sortDirection: directorySort?.direction,
                    },
                    controller.signal
                );

                if (!controller.signal.aborted) {
                    setResults(data);
                }
            } catch (err) {
                if (!controller.signal.aborted) {
                    setError(
                        err instanceof Error
                            ? err.message
                            : 'Failed to load customers.'
                    );
                }
            } finally {
                if (!controller.signal.aborted) {
                    setLoading(false);
                }
            }
        }

        void loadCustomers();

        return () => controller.abort();
    }, [appliedFilters, directorySort, page, refresh]);

    useEffect(() => {
        if (activeTab !== 'history') return;
        const controller = new AbortController();
        getCustomerStatusHistory(historyPage, 10, controller.signal, historySort?.direction)
            .then(data => {
                if (!controller.signal.aborted) {
                    setHistory(data);
                    setHistoryError('');
                    setHistoryLoading(false);
                }
            })
            .catch(err => {
                if (!controller.signal.aborted) {
                    setHistoryError(message(err));
                    setHistoryLoading(false);
                }
            });
        return () => controller.abort();
    }, [activeTab, historyPage, historySort, refresh]);

    async function submitCreate(event: React.SyntheticEvent<HTMLFormElement, SubmitEvent>) {
        event.preventDefault();
        if (!canCreate) {
            setCreateError('You do not have permission to create customers.');
            return;
        }
        if (!newName.trim()) {
            setCreateError('Customer name is required.');
            return;
        }
        setSaving(true);
        setCreateError('');
        try {
            const created = await createCustomer({custNm: newName.trim(), custTyp: newType.trim()});
            setShowCreate(false);
            setNewName('');
            setNotice(`Customer ${created.cifNo || 'record'} created successfully.`);
            notify('success', `Customer ${created.cifNo || 'record'} created successfully.`);
            setPage(0);
            setRefresh(n => n + 1);
        } catch (err) {
            setCreateError(message(err));
            notify('error', message(err));
        } finally {
            setSaving(false);
        }
    }

    const closeCreate = () => {
        if (saving) return;
        setShowCreate(false);
        setNewName('');
        setNewType('INDIVIDUAL');
        setCreateError('');
    };
    return (
        <section className="cbms-customers cbms-stack">
            <PageHeader
                title="Customers"
                description="Search and manage customer records in your core banking system."
                actions={<>
                    <RefreshButton loading={loading} onClick={() => setRefresh(n => n + 1)}/>
                    {canCreate && <CreateButton onClick={() => {
                        setNewName('');
                        setNewType('INDIVIDUAL');
                        setCreateError('');
                        setShowCreate(true);
                    }}>New Customer</CreateButton>}
                </>}
            />
            {notice && <div className="cbms-notice" role="status">{notice}
                <button type="button" onClick={() => setNotice('')} aria-label="Dismiss notification"><CloseIcon/>
                </button>
            </div>}
            <div className="cbms-customer-tabs" role="tablist" aria-label="Customer sections">
                <button type="button" role="tab" id="customer-directory-tab"
                        aria-selected={activeTab === 'directory'} aria-controls="customer-directory-panel"
                        onClick={() => setActiveTab('directory')}>Customer Directory
                </button>
                <button type="button" role="tab" id="customer-history-tab"
                        aria-selected={activeTab === 'history'} aria-controls="customer-history-panel"
                        onClick={() => {
                            setHistoryLoading(true);
                            setActiveTab('history');
                        }}>Customer Status History
                </button>
            </div>
            {activeTab === 'directory' && <div className="cbms-stack" id="customer-directory-panel"
                                               role="tabpanel" aria-labelledby="customer-directory-tab">
                <section className="cbms-card cbms-card-stack">
                    <div className="cbms-card-header"><h2>Search Customers</h2></div>
                <form className="cbms-filters cbms-customer-filters" onSubmit={event => {
                    event.preventDefault();
                    setPage(0);
                    setAppliedFilters({name: name.trim(), type: type.trim(), status: status.trim()});
                }}>
                    <label className="cbms-search">Customer Name <input value={name} onChange={e => {
                        setName(e.target.value);
                        setPage(0);
                    }} placeholder="Search customer name" aria-label="Search customer name"/></label>
                    <label>Type <select value={type} onChange={e => {
                        setType(e.target.value);
                        setPage(0);
                    }} aria-label="Customer type filter">
                        <option value="">All Types</option>
                        <option value="INDIVIDUAL">Individual</option>
                        <option value="CORPORATE">Corporate</option>
                    </select></label>
                    <label>Status <select value={status} onChange={e => {
                        setStatus(e.target.value);
                        setPage(0);
                    }} aria-label="Customer status filter">
                        <option value="">All Statuses</option>
                        <option value="ACTIVE">Active</option>
                        <option value="INACTIVE">Inactive</option>
                        <option value="PENDING">Pending</option>
                        <option value="BLOCKED">Blocked</option>
                    </select></label>
                    <SearchActions disabled={loading} onClear={() => {
                        setName('');
                        setType('');
                        setStatus('');
                        setPage(0);
                        setAppliedFilters({name: '', type: '', status: ''});
                    }}/>
                </form>
                </section>
                <section className="cbms-card cbms-card-stack">
                <div className="cbms-card-header"><div>
                    <h2>Customer List <small>{results ? `(${results.totalElements.toLocaleString()} ${results.totalElements === 1 ? 'record' : 'records'})` : '(Loading records)'}</small></h2>
                    <p className="cbms-card-subtitle">Search and manage customer records in your core banking system.</p>
                </div></div>
                {error && <div className="cbms-error" role="alert">{error}</div>}
                <div className="cbms-table-scroll">
                    <SortableTable className="cbms-customer-table" remoteSort={directorySort}
                                   remoteSortColumns={[0, 1, 2, 3]}
                                   onRemoteSortChange={sort => { setDirectorySort(sort); setPage(0); }}>
                        <thead>
                        <tr>
                            <th>Customer Identification Number</th>
                            <th>Customer Name</th>
                            <th>Customer Type</th>
                            <th>Status</th>
                            <th>Action</th>
                        </tr>
                        </thead>
                        <tbody>
                        {loading ? <tr>
                            <td colSpan={5} className="cbms-empty">Loading customer records…</td>
                        </tr> : results?.content.length ? results.content.map(c => <tr key={c.custId}>
                            <td className="cbms-id">{c.cifNo || '—'}</td>
                            <td><strong>{c.custNm}</strong></td>
                            <td>{formatBusinessValue(c.custTyp)}</td>
                            <td><StatusBadge status={c.sts}/></td>
                            <td>
                                <button type="button" className="cbms-link cbms-action-details"
                                        onClick={() => {
                                            try {
                                                sessionStorage.setItem(customerDirectoryStateKey, JSON.stringify({
                                                    name, type, status, appliedFilters, page, activeTab, historyPage
                                                } satisfies CustomerDirectoryState));
                                            } catch {
                                                // Navigation still works if session storage is unavailable.
                                            }
                                            navigate(`/customers/${c.custId}`);
                                        }}>View Details
                                </button>
                            </td>
                        </tr>) : <tr>
                            <td colSpan={5} className="cbms-empty">No customers found. Try adjusting your filters.</td>
                        </tr>}
                        </tbody>
                    </SortableTable>
                </div>
                <TablePagination
                    page={results?.number ?? page}
                    totalPages={results?.totalPages ?? 1}
                    totalElements={results?.totalElements}
                    onPageChange={setPage}
                    busy={loading}
                />
                </section>
            </div>}
            {activeTab === 'history' &&
                <div className="cbms-card cbms-card-stack" id="customer-history-panel"
                     role="tabpanel" aria-labelledby="customer-history-tab">
                    <div className="cbms-card-header"><div>
                        <h2>Customer Status History <small>{history ? `(${history.totalElements.toLocaleString()} ${history.totalElements === 1 ? 'record' : 'records'})` : '(Loading records)'}</small></h2>
                        <p className="cbms-card-subtitle">Historical customer status changes and review details.</p>
                    </div></div>
                    {historyError && <div className="cbms-error" role="alert">{historyError}</div>}
                    <div className="cbms-table-scroll">
                        <SortableTable className="cbms-customer-table cbms-history-table"
                                       remoteSort={historySort} remoteSortColumns={[3]}
                                       onRemoteSortChange={sort => {
                                           setHistorySort(sort?.column === 3 ? sort : null);
                                           setHistoryPage(0);
                                           setHistoryLoading(true);
                                       }}>
                            <thead>
                            <tr>
                                <th>Customer Identification Number</th>
                                <th>Customer Name</th>
                                <th>Description</th>
                                <th>Date / Time</th>
                            </tr>
                            </thead>
                            <tbody>{historyLoading ? <tr>
                                    <td colSpan={4} className="cbms-empty">Loading history…</td>
                                </tr>
                                : history?.content.length ? history.content.map((r, index) => <tr
                                    key={`${r.cifNo}-${r.requestedAt}-${index}`}>
                                    <td className="cbms-id">{r.cifNo}</td>
                                    <td>{r.customerName}</td>
                                    <td>Changing status
                                        to {r.toStatus} ({r.requestStatus}){r.reason ? ` — ${r.reason}` : ''}</td>
                                    <td><time dateTime={r.reviewedAt ?? r.requestedAt}>
                                        {new Date(r.reviewedAt ?? r.requestedAt).toLocaleString()}
                                    </time></td>
                                </tr>) : <tr>
                                    <td colSpan={4} className="cbms-empty">No customer status history available.</td>
                                </tr>}</tbody>
                        </SortableTable>
                    </div>
                    <TablePagination
                        page={history?.number ?? historyPage}
                        totalPages={history?.totalPages ?? 1}
                        totalElements={history?.totalElements}
                        onPageChange={n => {
                            setHistoryLoading(true);
                            setHistoryPage(n);
                        }}
                        busy={historyLoading}
                    />
                </div>}
            {showCreate && <div className="cbms-modal-backdrop" onMouseDown={e => {
                if (e.target === e.currentTarget) closeCreate();
            }}>
                <div className="cbms-modal" role="dialog" aria-modal="true" aria-label="Create customer">
                    <div className="cbms-modal-head"><h2>New Customer</h2>
                        <button type="button" aria-label="Close" disabled={saving} onClick={closeCreate}>
                            <CloseIcon/></button>
                    </div>
                    <p className="cbms-muted">Create a customer record. Additional profile details can be added from the
                        customer details screen.</p>
                    <form onSubmit={e => void submitCreate(e)} className="cbms-create-form"><label><FieldLabel
                        className="cbms-field-label" required>Customer Name</FieldLabel> <input
                        required autoFocus maxLength={200} value={newName} onChange={e => setNewName(e.target.value)}
                        placeholder="Enter customer name"/></label><label><FieldLabel className="cbms-field-label"
                                                                                      required>Customer
                        Type</FieldLabel>
                        <select required value={newType} onChange={e => setNewType(e.target.value)}>
                            <option value="INDIVIDUAL">Individual</option>
                            <option value="CORPORATE">Corporate</option>
                        </select></label>{createError &&
                        <div className="cbms-error" role="alert">{createError}</div>}
                        <div className="cbms-form-actions">
                            <button type="button" className="cbms-btn cbms-btn-secondary" disabled={saving}
                                    onClick={closeCreate}>Cancel
                            </button>
                            <button type="submit" className="cbms-btn cbms-btn-primary"
                                    disabled={saving}>{saving ? 'Creating…' : 'Create'}</button>
                        </div>
                    </form>
                </div>
            </div>}
        </section>
    );
}
