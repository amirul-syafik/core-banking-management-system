import {useEffect, useState} from 'react';
import {useToast} from '../components/toastContext';
import {AxiosError} from 'axios';
import {ChevronLeft, ChevronRight, Plus, RefreshCw, Search, Users, X} from 'lucide-react';
import {createCustomer, searchCustomers, type CustomerPage} from '../api/customers';
import {useAuth} from '../auth/useAuth';
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

export default function Customers() {
    const {session} = useAuth();
    const notify = useToast();
    const navigate = useNavigate();
    const [name, setName] = useState('');
    const [type, setType] = useState('');
    const [status, setStatus] = useState('');
    const [page, setPage] = useState(0);
    const [size, setSize] = useState(10);
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
    const [historyPage, setHistoryPage] = useState(0);
    const [history, setHistory] = useState<CustomerStatusHistoryPage | null>(null);
    const [historyError, setHistoryError] = useState('');
    const [historyLoading, setHistoryLoading] = useState(true);

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
                        name: name.trim(),
                        type,
                        status,
                        page,
                        size,
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
    }, [name, type, status, page, size, refresh]);

    useEffect(() => {
        const controller = new AbortController();
        getCustomerStatusHistory(historyPage, 10, controller.signal)
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
    }, [historyPage, refresh]);

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

    return (
        <section className="cbms-customers">
            <div className="cbms-customers-header">
                <div><h1>Customers</h1><p>Search and manage
                    customer records in your core banking system.</p></div>
                <div className="cbms-header-actions">
                    <button type="button" className="cbms-btn cbms-btn-secondary"
                            onClick={() => setRefresh(n => n + 1)}><RefreshCw size={16}/> Refresh
                    </button>
                    {canCreate && <button type="button" className="cbms-btn cbms-btn-primary" onClick={() => {
                        setCreateError('');
                        setShowCreate(true);
                    }}><Plus size={16}/> New customer</button>}</div>
            </div>
            {notice && <div className="cbms-notice" role="status">{notice}
                <button type="button" onClick={() => setNotice('')} aria-label="Dismiss">×</button>
            </div>}
            <div className="cbms-customer-panel">
                <div className="cbms-panel-title">
                    <div><Users size={20}/><strong>Customer directory</strong></div>
                    <span>{results ? `${results.totalElements.toLocaleString()} records` : 'Loading records'}</span>
                </div>
                <div className="cbms-filters">
                    <label className="cbms-search"><Search size={17}/><input value={name} onChange={e => {
                        setName(e.target.value);
                        setPage(0);
                    }} placeholder="Search customer name" aria-label="Search customer name"/></label>
                    <label>Type <input value={type} onChange={e => {
                        setType(e.target.value);
                        setPage(0);
                    }} placeholder="All types" aria-label="Customer type filter"/></label>
                    <label>Status <input value={status} onChange={e => {
                        setStatus(e.target.value);
                        setPage(0);
                    }} placeholder="All statuses" aria-label="Customer status filter"/></label>
                    <label>Rows <select value={size} onChange={e => {
                        setSize(Number(e.target.value));
                        setPage(0);
                    }}>
                        <option value={10}>10</option>
                        <option value={20}>20</option>
                        <option value={50}>50</option>
                    </select></label>
                </div>
                {error && <div className="cbms-error" role="alert">{error}</div>}
                <div className="cbms-table-scroll">
                    <table className="cbms-customer-table">
                        <thead>
                        <tr>
                            <th>CIF Number</th>
                            <th>Name</th>
                            <th>Type</th>
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
                            <td>{c.custTyp}</td>
                            <td><span className="cbms-status">{c.sts}</span></td>
                            <td>
                                <button type="button" className="cbms-link"
                                        onClick={() => navigate(`/customers/${c.custId}`)}>View profile
                                </button>
                            </td>
                        </tr>) : <tr>
                            <td colSpan={5} className="cbms-empty">No customers found. Try adjusting your filters.</td>
                        </tr>}
                        </tbody>
                    </table>
                </div>
                <div className="cbms-pagination">
                    <span>Page {(results?.number ?? page) + 1} of {Math.max(results?.totalPages ?? 1, 1)}</span>
                    <div>
                        <button type="button" disabled={loading || page === 0}
                                onClick={() => setPage(n => Math.max(0, n - 1))}><ChevronLeft size={16}/> Previous
                        </button>
                        <button type="button" disabled={loading || !results || page + 1 >= results.totalPages}
                                onClick={() => setPage(n => n + 1)}>Next <ChevronRight size={16}/></button>
                    </div>
                </div>
            </div>
            <div className="cbms-customer-panel cbms-history-panel">
                <div className="cbms-panel-title">
                    <div><Users size={20}/><strong>Customer Status History</strong></div>
                    <span>{history ? `${history.totalElements.toLocaleString()} records` : 'Loading records'}</span>
                </div>
                {historyError && <div className="cbms-error" role="alert">{historyError}</div>}
                <div className="cbms-table-scroll">
                    <table className="cbms-customer-table cbms-history-table">
                        <thead>
                        <tr>
                            <th>CIF Number</th>
                            <th>Name</th>
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
                                <td>{new Date(r.reviewedAt ?? r.requestedAt).toLocaleString()}</td>
                            </tr>) : <tr>
                                <td colSpan={4} className="cbms-empty">No customer status history available.</td>
                            </tr>}</tbody>
                    </table>
                </div>
                <div className="cbms-pagination">
                    <span>Page {(history?.number ?? historyPage) + 1} of {Math.max(history?.totalPages ?? 1, 1)}</span>
                    <div>
                        <button type="button" disabled={historyLoading || historyPage === 0}
                                onClick={() => {
                                    setHistoryLoading(true);
                                    setHistoryPage(n => Math.max(0, n - 1));
                                }}><ChevronLeft size={16}/> Previous
                        </button>
                        <button type="button"
                                disabled={historyLoading || !history || historyPage + 1 >= history.totalPages}
                                onClick={() => {
                                    setHistoryLoading(true);
                                    setHistoryPage(n => n + 1);
                                }}>Next <ChevronRight size={16}/></button>
                    </div>
                </div>
            </div>
            {showCreate && <div className="cbms-modal-backdrop" onMouseDown={e => {
                if (e.target === e.currentTarget && !saving) setShowCreate(false);
            }}>
                <div className="cbms-modal" role="dialog" aria-modal="true" aria-label="Create customer">
                    <div className="cbms-modal-head"><h2>New customer</h2>
                        <button type="button" aria-label="Close" disabled={saving} onClick={() => setShowCreate(false)}>
                            <X size={20}/></button>
                    </div>
                    <p className="cbms-muted">Creates a customer master record. Additional individual or corporate
                        details can be registered in a later phase.</p>
                    <form onSubmit={e => void submitCreate(e)} className="cbms-create-form"><label><span
                        className="cbms-field-label">Customer name <span className="cbms-required-star"
                                                                         aria-hidden="true">*</span></span> <input
                        required maxLength={200} value={newName} onChange={e => setNewName(e.target.value)}
                        placeholder="Enter customer name"/></label><label><span className="cbms-field-label">Customer type <span
                        className="cbms-required-star" aria-hidden="true">*</span></span> <input required
                                                                                                 value={newType}
                                                                                                 onChange={e => setNewType(e.target.value)}
                                                                                                 placeholder="Backend customer type code"/></label>{createError &&
                        <div className="cbms-error" role="alert">{createError}</div>}
                        <div className="cbms-form-actions">
                            <button type="button" className="cbms-btn cbms-btn-secondary" disabled={saving}
                                    onClick={() => setShowCreate(false)}>Cancel
                            </button>
                            <button type="submit" className="cbms-btn cbms-btn-primary"
                                    disabled={saving}>{saving ? 'Creating…' : 'Create customer'}</button>
                        </div>
                    </form>
                </div>
            </div>}
        </section>
    );
}
