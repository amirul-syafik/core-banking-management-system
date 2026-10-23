import {
    listAccountStatusRequests,
    proposeAccountStatus,
    decideAccountStatus,
    type AccountStatusRequest
} from '../api/accountStatusRequests';
import {useToast} from '../components/toastContext';
import {Link} from 'react-router-dom';
import PopupView from '../components/PopupView';
import {useEffect, useState} from 'react';
import {AxiosError} from 'axios';
import {searchCustomers, getCustomer, type Customer} from '../api/customers';
import {useAuth} from '../auth/useAuth';
import {
    listApplications,
    listAccounts,
    searchAccounts,
    getAccountBalance,
    getAccountTransactions,
    type AccountBalance,
    type AccountTransactionPage,
    listProducts,
    listSchemes,
    createApplication,
    validateApplication,
    submitApplication,
    approveApplication,
    rejectApplication,
    completeApplication,
    type Account,
    type Application,
    type Product,
    type Scheme
} from '../api/accounts';
import './Accounts.css';

function errorMessage(error: unknown): string {
    if (error instanceof AxiosError) {
        const body: unknown = error.response?.data;
        if (typeof body === 'object' && body !== null) {
            if ('detail' in body && typeof body.detail === 'string') return body.detail;
            if ('message' in body && typeof body.message === 'string') return body.message;
        }
        return `Request failed (${error.response?.status ?? 'network'}).`;
    }
    return error instanceof Error ? error.message : 'Request failed.';
}

export default function Accounts() {
    const {session} = useAuth();
    const notify = useToast();
    const canMake = Boolean(session?.roles.some(r => r === 'MAKER' || r === 'ADMIN'));
    const isAdmin = Boolean(session?.roles.includes('ADMIN'));
    const [query, setQuery] = useState('');
    const [searchMode, setSearchMode] = useState<'name' | 'cif' | 'account'>('name');
    const [accountStatusFilter, setAccountStatusFilter] = useState('');
    const [accountSearchResults, setAccountSearchResults] = useState<Account[]>([]);
    const [accountSearchPage, setAccountSearchPage] = useState(0);
    const [accountSearchTotalPages, setAccountSearchTotalPages] = useState(0);
    const [accountSearchTotal, setAccountSearchTotal] = useState(0);
    const [accountSearchBusy, setAccountSearchBusy] = useState(false);
    const [accountSearchDone, setAccountSearchDone] = useState(false);
    const [activeModal, setActiveModal] = useState<'maintenance' | 'details' | 'new' | 'applications' | null>(null);
    const [matches, setMatches] = useState<Customer[]>([]);
    const [customer, setCustomer] = useState<Customer | null>(null);
    const [accounts, setAccounts] = useState<Account[]>([]);
    const [applications, setApplications] = useState<Application[]>([]);
    const [products, setProducts] = useState<Product[]>([]);
    const [schemes, setSchemes] = useState<Scheme[]>([]);
    const [productId, setProductId] = useState('');
    const [schemeId, setSchemeId] = useState('');
    const [remarks, setRemarks] = useState('');
    const [reviewRemarks, setReviewRemarks] = useState<Record<number, string>>({});
    const [applicationTab, setApplicationTab] = useState<'open' | 'closed'>('open');
    const [validation, setValidation] = useState<Record<number, string[]>>({});
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [refresh, setRefresh] = useState(0);
    const [selectedAccount, setSelectedAccount] = useState<Account | null>(null);
    const [accountBalance, setAccountBalance] = useState<AccountBalance | null>(null);
    const [accountTransactions, setAccountTransactions] = useState<AccountTransactionPage | null>(null);
    const [accountDetailLoading, setAccountDetailLoading] = useState(false);
    const [accountBalanceError, setAccountBalanceError] = useState('');
    const [accountTransactionsError, setAccountTransactionsError] = useState('');
    const [transactionPage, setTransactionPage] = useState(0);
    const [accountDetailsTab, setAccountDetailsTab] = useState<'overview' | 'balances' | 'transactions'>('overview');


    const [statusRequests, setStatusRequests] = useState<AccountStatusRequest[]>([]);
    const [statusTarget, setStatusTarget] = useState('');
    const [statusReason, setStatusReason] = useState('');
    const [statusRefresh, setStatusRefresh] = useState(0);
    const [statusBusy, setStatusBusy] = useState(false);
    const allowedAccountTransitions: Record<string, string[]> = {
        PENDING: ['ACTIVE'], ACTIVE: ['DORMANT', 'FROZEN'],
        DORMANT: ['ACTIVE', 'FROZEN'], FROZEN: ['ACTIVE'], CLOSED: []
    };
    const currentAccountStatus = accounts.find(a => a.acctId === selectedAccount?.acctId)?.acctSts ?? selectedAccount?.acctSts ?? '';
    const nextAccountStatuses = allowedAccountTransitions[currentAccountStatus] ?? [];
    const pendingAccountRequest = statusRequests.some(r => r.requestStatus === 'PENDING');

    function openAccountDetails(account: Account) {
        setSelectedAccount(account);
        setAccountBalance(null);
        setAccountTransactions(null);
        setAccountBalanceError('');
        setAccountTransactionsError('');
        setTransactionPage(0);
        setAccountDetailsTab('overview');
        setActiveModal('details');
    }

    useEffect(() => {
        if (activeModal !== 'details' || !selectedAccount) return;
        let live = true;
        const accountId = selectedAccount.acctId;
        Promise.resolve().then(async () => {
            if (live) setAccountDetailLoading(true);
            const [balanceResult, transactionResult] = await Promise.allSettled([
                getAccountBalance(accountId),
                getAccountTransactions(accountId, transactionPage)
            ]);
            if (!live) return;
            if (balanceResult.status === 'fulfilled') {
                setAccountBalance(balanceResult.value);
                setAccountBalanceError('');
            } else {
                setAccountBalance(null);
                setAccountBalanceError(errorMessage(balanceResult.reason));
            }
            if (transactionResult.status === 'fulfilled') {
                setAccountTransactions(transactionResult.value);
                setAccountTransactionsError('');
            } else {
                setAccountTransactions(null);
                setAccountTransactionsError(errorMessage(transactionResult.reason));
            }
            setAccountDetailLoading(false);
        });
        return () => {
            live = false;
        };
    }, [activeModal, selectedAccount, transactionPage]);

    const formatAccountAmount = (value: number | null | undefined, currency: string) =>
        value == null ? '—' : `${currency} ${Number(value).toLocaleString('en-MY', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        })}`;

    function selectAccount(account: Account) {
        setSelectedAccount(account);
        setStatusRequests([]);
        setStatusTarget('');
        setStatusReason('');
        setActiveModal('maintenance');
    }

    async function proposeAccountChange() {
        if (!selectedAccount || !canMake || !nextAccountStatuses.includes(statusTarget) || !statusReason.trim()) return;
        setStatusBusy(true);
        try {
            await proposeAccountStatus(selectedAccount.acctId, statusTarget, statusReason.trim());
            notify('success', 'Account status request submitted for approval.');
            setStatusReason('');
            setStatusTarget('');
            setStatusRefresh(x => x + 1);
            setActiveModal(null);
        } catch (e) {
            notify('error', errorMessage(e));
        } finally {
            setStatusBusy(false);
        }
    }

    async function reviewAccountChange(requestId: number, approve: boolean) {
        if (!selectedAccount || !isAdmin) return;
        setStatusBusy(true);
        try {
            await decideAccountStatus(selectedAccount.acctId, requestId, approve);
            notify('success', approve ? 'Account status approved.' : 'Account status request rejected.');
            setStatusRefresh(x => x + 1);
            setRefresh(x => x + 1);
            setActiveModal(null);
        } catch (e) {
            notify('error', errorMessage(e));
        } finally {
            setStatusBusy(false);
        }
    }

    const isCustomerActive = customer?.sts?.toUpperCase() === 'ACTIVE';
    const selectedProduct = products.find(p => String(p.prodId) === productId);
    const selectedScheme = schemes.find(s => String(s.schmId) === schemeId);

    useEffect(() => {
        let active = true;
        listProducts().then(items => {
            if (active) setProducts(items);
        }).catch(e => {
            if (active) setError(errorMessage(e));
        });
        return () => {
            active = false;
        };
    }, []);
    useEffect(() => {
        if (!productId) return;

        let active = true;

        listSchemes(Number(productId))
            .then(items => {
                if (active) setSchemes(items);
            })
            .catch(e => {
                if (active) setError(errorMessage(e));
            });

        return () => {
            active = false;
        };
    }, [productId]);
    useEffect(() => {
        if (!customer) return;

        let active = true;

        Promise.all([
            listAccounts(customer.custId),
            listApplications(customer.custId)
        ])
            .then(([a, b]) => {
                if (active) {
                    setAccounts(a);
                    setApplications(b);
                }
            })
            .catch(e => {
                if (active) setError(errorMessage(e));
            });

        return () => {
            active = false;
        };
    }, [customer, refresh]);

    useEffect(() => {
        if (!selectedAccount) return;
        let active = true;
        listAccountStatusRequests(selectedAccount.acctId).then(items => {
            if (active) setStatusRequests(items);
        }).catch(e => {
            if (active) notify('error', errorMessage(e));
        });
        return () => {
            active = false;
        };
    }, [selectedAccount, statusRefresh, notify]);

    function chooseCustomer(found: Customer) {
        setCustomer(found);
        setSelectedAccount(null);
        setStatusRequests([]);
        setAccounts([]);
        setApplications([]);
        setMatches([]);
        setAccountSearchResults([]);
        setAccountSearchDone(false);
        setActiveModal(null);
        setError('');
        setNotice('');
    }

    async function findAccounts(page = 0) {
        if (!query.trim()) {
            notify('warning', 'Enter a customer name, CIF or account number.');
            return;
        }
        setAccountSearchBusy(true);
        setError('');
        setMatches([]);
        setAccountSearchDone(false);
        try {
            if (searchMode === 'name') {
                const data = await searchCustomers({name: query.trim(), page, size: 20});
                setMatches(data.content);
                setAccountSearchPage(data.number);
                setAccountSearchTotalPages(data.totalPages);
                setAccountSearchTotal(data.totalElements);
            } else {
                const result = await searchAccounts({
                    accountNo: searchMode === 'account' ? query.trim() : undefined,
                    cifNo: searchMode === 'cif' ? query.trim() : undefined,
                    status: accountStatusFilter || undefined,
                    page, size: 20
                });
                setAccountSearchResults(result.content);
                setAccountSearchPage(result.number);
                setAccountSearchTotalPages(result.totalPages);
                setAccountSearchTotal(result.totalElements);
            }
            setAccountSearchDone(true);
        } catch (e) {
            notify('error', errorMessage(e));
        } finally {
            setAccountSearchBusy(false);
        }
    }

    async function openAccountCustomer(account: Account) {
        setAccountSearchBusy(true);
        try {
            const found = await getCustomer(account.custId);
            chooseCustomer(found);
        } catch (e) {
            notify('error', errorMessage(e));
        } finally {
            setAccountSearchBusy(false);
        }
    }

    function resetPage() {
        setSearchMode('name');
        setAccountStatusFilter('');
        setAccountSearchResults([]);
        setAccountSearchDone(false);
        setAccountSearchPage(0);
        setAccountSearchTotalPages(0);
        setAccountSearchTotal(0);
        setActiveModal(null);
        setSelectedAccount(null);
        setStatusRequests([]);
        setStatusTarget('');
        setStatusReason('');
        setQuery('');
        setMatches([]);
        setCustomer(null);
        setSelectedAccount(null);
        setStatusRequests([]);
        setAccounts([]);
        setApplications([]);
        setProductId('');
        setSchemeId('');
        setSchemes([]);
        setRemarks('');
        setReviewRemarks({});
        setValidation({});
        setError('');
        setNotice('');
    }

    async function act(action: () => Promise<unknown>, success: string) {
        setBusy(true);
        setError('');
        setNotice('');
        try {
            await action();
            setNotice(success);
            notify('success', success);
            setRefresh(x => x + 1);
            setActiveModal(null);
        } catch (e) {
            setError(errorMessage(e));
            notify('error', errorMessage(e));
        } finally {
            setBusy(false);
        }
    }

    async function newApplication() {
        if (busy || !customer || !isCustomerActive || !selectedProduct || !selectedScheme || !canMake) return;
        await act(async () => {
            await createApplication({
                custId: customer.custId,
                prodId: selectedProduct.prodId,
                schmId: selectedScheme.schmId,
                reqAmt: null,
                reqTenor: null,
                remarks: remarks.trim()
            });
            setProductId('');
            setSchemeId('');
            setSchemes([]);
            setRemarks('');
        }, 'Application created as draft. Validate and submit it for approval.');
    }

    async function check(id: number) {
        setBusy(true);
        setError('');
        try {
            const result = await validateApplication(id);
            setValidation(v => ({...v, [id]: result.errors}));
            setNotice(result.valid ? 'Application validation passed.' : 'Application has validation issues.');
            notify(result.valid ? 'success' : 'warning', result.valid ? 'Application validation passed.' : 'Application has validation issues.');
        } catch (e) {
            setError(errorMessage(e));
            notify('error', errorMessage(e));
        } finally {
            setBusy(false);
        }
    }

    return <main className="cbms-accounts">
        <header><h1>Accounts</h1><p>Search for a customer, review linked accounts, and process account-opening
            applications.</p></header>
        {error && <p role="alert" className="cbms-account-error">{error}</p>}
        {notice && <p role="status" className="cbms-account-notice">{notice}</p>}
        {!customer && <section className="cbms-account-card cbms-global-account-search">
            <h2>Find account or customer</h2>
            <div className="cbms-account-search-modes" role="group" aria-label="Search by">
                {([['name', 'Customer name'], ['cif', 'Customer CIF'], ['account', 'Account number']] as const).map(([mode, label]) =>
                    <button key={mode} type="button" aria-pressed={searchMode === mode}
                            className={searchMode === mode ? 'cbms-account-search-mode-active' : ''}
                            onClick={() => {
                                setSearchMode(mode);
                                setQuery('');
                                setMatches([]);
                                setAccountSearchResults([]);
                                setAccountSearchDone(false);
                                setAccountSearchPage(0);
                                setAccountSearchTotalPages(0);
                                setAccountSearchTotal(0);
                            }}>{label}</button>)}
            </div>
            <form onSubmit={e => {
                e.preventDefault();
                void findAccounts(0);
            }}>
                <div className="cbms-unified-account-filters">
                    <label><span
                        className="cbms-field-label">{searchMode === 'name' ? 'Customer name' : searchMode === 'cif' ? 'Customer CIF' : 'Account number'}
                        <span className="cbms-required-star" aria-hidden="true">*</span></span>
                        <input required value={query} maxLength={100} onChange={e => setQuery(e.target.value)}
                               placeholder={searchMode === 'name' ? 'Enter customer name' : searchMode === 'cif' ? 'Enter customer CIF' : 'Enter account number'}/></label>
                    <label><span className="cbms-field-label">Account status</span>
                        <select value={accountStatusFilter} disabled={searchMode === 'name'}
                                onChange={e => setAccountStatusFilter(e.target.value)}>
                            <option value="">All statuses</option>
                            {['PENDING', 'ACTIVE', 'DORMANT', 'FROZEN', 'CLOSED'].map(st => <option key={st}
                                                                                                    value={st}>{st}</option>)}
                        </select>
                        {searchMode === 'name' && <small>Available for CIF and account-number searches</small>}
                    </label>
                    <div className="cbms-global-account-buttons">
                        <button type="submit" disabled={accountSearchBusy}>Search</button>
                        <button type="button" disabled={accountSearchBusy} onClick={() => {
                            setQuery('');
                            setAccountStatusFilter('');
                            setMatches([]);
                            setAccountSearchResults([]);
                            setAccountSearchDone(false);
                            setAccountSearchPage(0);
                            setAccountSearchTotalPages(0);
                            setAccountSearchTotal(0);
                        }}>Clear
                        </button>
                    </div>
                </div>
            </form>
            {accountSearchDone && <>
                <p role="status">{accountSearchTotal} {searchMode === 'name' ? 'customer(s)' : 'account(s)'} found.</p>
                {searchMode === 'name' && matches.length > 0 && <div className="cbms-account-results">
                    {matches.map(c => <button type="button" key={c.custId} onClick={() => chooseCustomer(c)}>
                        {c.cifNo} · {c.custNm}
                    </button>)}
                </div>}
                {searchMode !== 'name' && accountSearchResults.length > 0 && <div className="cbms-account-scroll">
                    <table>
                        <thead>
                        <tr>
                            <th>Account number</th>
                            <th>Type</th>
                            <th>Currency</th>
                            <th>Status</th>
                            <th>Opened</th>
                            <th>Action</th>
                        </tr>
                        </thead>
                        <tbody>{accountSearchResults.map(a => <tr key={a.acctId}>
                            <td>{a.acctNo}</td>
                            <td>{a.acctTyp}</td>
                            <td>{a.ccyCd}</td>
                            <td>{a.acctSts}</td>
                            <td>{a.openDt || '—'}</td>
                            <td>
                                <button type="button" disabled={accountSearchBusy}
                                        onClick={() => void openAccountCustomer(a)}>Open dashboard
                                </button>
                            </td>
                        </tr>)}</tbody>
                    </table>
                </div>}
                {accountSearchTotalPages > 1 && <div className="cbms-account-pagination">
                    <button type="button" disabled={accountSearchBusy || accountSearchPage === 0}
                            onClick={() => void findAccounts(accountSearchPage - 1)}>Previous
                    </button>
                    <span>Page {accountSearchPage + 1} of {accountSearchTotalPages}</span>
                    <button type="button"
                            disabled={accountSearchBusy || accountSearchPage + 1 >= accountSearchTotalPages}
                            onClick={() => void findAccounts(accountSearchPage + 1)}>Next
                    </button>
                </div>}
            </>}
        </section>}
        {customer && <>
            <section className="cbms-account-card">
                <div className="cbms-account-heading"><h2>{customer.custNm} <small>({customer.cifNo})</small></h2><Link
                    to={`/customers/${customer.custId}`}>Customer profile</Link></div>
                <p>Customer status: {customer.sts}</p>
                <div className="cbms-account-dashboard-actions">
                    {canMake &&
                        <button type="button" disabled={!isCustomerActive} onClick={() => setActiveModal('new')}>New
                            account application</button>}
                    <button type="button" onClick={() => {
                        setApplicationTab('open');
                        setActiveModal('applications');
                    }}>Account applications
                        ({applications.length})
                    </button>
                </div>
                {!isCustomerActive && canMake &&
                    <p className="cbms-account-warning" role="status">New account applications are available only for
                        ACTIVE customers.</p>}
                <h3>Linked accounts</h3>
                {accounts.length === 0 ? <p>No linked accounts.</p> : <div className="cbms-account-scroll">
                    <table>
                        <thead>
                        <tr>
                            <th>Account number</th>
                            <th>Type</th>
                            <th>Currency</th>
                            <th>Status</th>
                            <th>Opened</th>
                            <th>Closed</th>
                            <th>Actions</th>
                        </tr>
                        </thead>
                        <tbody>{accounts.map(a => <tr key={a.acctId}>
                            <td>{a.acctNo}</td>
                            <td>{a.acctTyp}</td>
                            <td>{a.ccyCd}</td>
                            <td>{a.acctSts}</td>
                            <td>{a.openDt || '—'}</td>
                            <td>{a.closeDt || '—'}</td>
                            <td>
                                <div className="cbms-account-table-actions">
                                    <button type="button" onClick={() => openAccountDetails(a)}>Details</button>
                                    {canMake && <button type="button" onClick={() => selectAccount(a)}>Manage</button>}
                                </div>
                            </td>
                        </tr>)}</tbody>
                    </table>
                </div>}
                <div className="cbms-account-dashboard-footer">
                    <button type="button" disabled={busy || statusBusy} onClick={resetPage}>Cancel</button>
                </div>
            </section>
        </>}
        {selectedAccount && activeModal === 'details' &&
            <div className="cbms-account-modal-backdrop" onMouseDown={e => {
                if (e.target === e.currentTarget) setActiveModal(null);
            }}>
                <section className="cbms-account-modal cbms-account-details-modal" role="dialog" aria-modal="true"
                         aria-labelledby="cbms-account-details-title">
                    <div className="cbms-account-heading"><h2 id="cbms-account-details-title">Account details
                        — {selectedAccount.acctNo}</h2>
                        <button type="button" className="cbms-account-modal-close" aria-label="Close account details"
                                title="Close" onClick={() => setActiveModal(null)}>×
                        </button>
                    </div>
                    <div className="cbms-account-details-tabs" role="tablist" aria-label="Account detail sections">
                        {(['overview', 'balances', 'transactions'] as const).map(tab => <button
                            key={tab} type="button" role="tab" aria-selected={accountDetailsTab === tab}
                            className={accountDetailsTab === tab ? 'cbms-account-details-tab active' : 'cbms-account-details-tab'}
                            onClick={() => setAccountDetailsTab(tab)}>{tab === 'overview' ? 'Overview' : tab === 'balances' ? 'Balances' : 'Transactions'}</button>)}
                    </div>
                    <div role="tabpanel" className="cbms-account-details-panel">
                        {accountDetailsTab === 'overview' && <>
                            <dl className="cbms-account-detail-grid">
                                <div>
                                    <dt>Account number</dt>
                                    <dd>{selectedAccount.acctNo}</dd>
                                </div>
                                <div>
                                    <dt>Account type</dt>
                                    <dd>{selectedAccount.acctTyp}</dd>
                                </div>
                                <div>
                                    <dt>Currency</dt>
                                    <dd>{selectedAccount.ccyCd}</dd>
                                </div>
                                <div>
                                    <dt>Status</dt>
                                    <dd>{selectedAccount.acctSts}</dd>
                                </div>
                                <div>
                                    <dt>Opened</dt>
                                    <dd>{selectedAccount.openDt || '—'}</dd>
                                </div>
                                <div>
                                    <dt>Closed</dt>
                                    <dd>{selectedAccount.closeDt || '—'}</dd>
                                </div>
                            </dl>
                        </>}
                        {accountDetailsTab === 'balances' && <>
                            {accountDetailLoading && !accountBalance && <p role="status">Loading balances…</p>}
                            {accountBalanceError && <p role="alert" className="cbms-account-warning">Balance
                                unavailable: {accountBalanceError}</p>}
                            {accountBalance && <dl className="cbms-account-detail-grid">
                                <div>
                                    <dt>Ledger balance</dt>
                                    <dd>{formatAccountAmount(accountBalance.ledgerBal, accountBalance.ccyCd)}</dd>
                                </div>
                                <div>
                                    <dt>Available balance</dt>
                                    <dd>{formatAccountAmount(accountBalance.availBal, accountBalance.ccyCd)}</dd>
                                </div>
                            </dl>}
                            <p className="cbms-account-detail-note">Balances are read-only and retrieved from the bank's
                                persisted balance records. Held amount is not available from the current balance
                                API.</p>
                        </>}
                        {accountDetailsTab === 'transactions' && <>
                            {accountDetailLoading && <p role="status">Loading transactions…</p>}
                            {accountTransactionsError && <p role="alert" className="cbms-account-warning">Transactions
                                unavailable: {accountTransactionsError}</p>}
                            {accountTransactions && <>
                                {accountTransactions.content.length === 0 ?
                                    <p>No transactions found for this account.</p> :
                                    <div className="cbms-account-scroll">
                                        <table>
                                            <thead>
                                            <tr>
                                                <th>Date</th>
                                                <th>Reference</th>
                                                <th>Type</th>
                                                <th>Direction</th>
                                                <th>Status</th>
                                                <th>Amount</th>
                                            </tr>
                                            </thead>
                                            <tbody>{accountTransactions.content.map(t => <tr key={t.txnRef}>
                                                <td data-label="Date">{t.crtDt ? new Date(t.crtDt).toLocaleString('en-MY') : '—'}</td>
                                                <td data-label="Reference"><PopupView value={t.txnRef}/></td>
                                                <td data-label="Type"><PopupView value={t.txnTyp}/></td>
                                                <td data-label="Direction"><PopupView value={t.direction}/></td>
                                                <td data-label="Status"><PopupView value={t.txnSts}/></td>
                                                <td data-label="Amount"><PopupView
                                                    value={t.direction === 'N/A' ? `${formatAccountAmount(t.txnAmt, t.ccyCd)} (no account movement)` : formatAccountAmount(t.signedAmount, t.ccyCd)}/>
                                                </td>
                                            </tr>)}</tbody>
                                        </table>
                                    </div>}
                                {accountTransactions.totalPages > 1 && <div className="cbms-account-pagination">
                                    <button type="button" disabled={accountDetailLoading || transactionPage === 0}
                                            onClick={() => setTransactionPage(p => p - 1)}>Previous
                                    </button>
                                    <span>Page {transactionPage + 1} of {accountTransactions.totalPages}</span>
                                    <button type="button"
                                            disabled={accountDetailLoading || transactionPage + 1 >= accountTransactions.totalPages}
                                            onClick={() => setTransactionPage(p => p + 1)}>Next
                                    </button>
                                </div>}
                            </>}
                        </>}
                    </div>
                </section>
            </div>}
        {selectedAccount && activeModal === 'maintenance' &&
            <div className="cbms-account-modal-backdrop" onMouseDown={e => {
                if (e.target === e.currentTarget) setActiveModal(null);
            }}>
                <section className="cbms-account-modal" role="dialog" aria-modal="true"
                         aria-labelledby="cbms-maintenance-title">
                    <div className="cbms-account-heading"><h2 id="cbms-maintenance-title">Account maintenance
                        — {selectedAccount.acctNo}</h2>
                        <button type="button" className="cbms-account-modal-close"
                                aria-label="Close account maintenance" title="Close"
                                onClick={() => setActiveModal(null)}>×
                        </button>
                    </div>
                    <p>Current
                        status: <strong>{accounts.find(a => a.acctId === selectedAccount.acctId)?.acctSts ?? selectedAccount.acctSts}</strong>
                    </p>
                    <p>Closure is not available until balance and pending-transaction checks are implemented.</p>
                    {canMake && !pendingAccountRequest && nextAccountStatuses.length > 0 &&
                        <form className="cbms-account-status-form" onSubmit={e => {
                            e.preventDefault();
                            void proposeAccountChange();
                        }}>
                            <label><span className="cbms-field-label">New status <span className="cbms-required-star"
                                                                                       aria-hidden="true">*</span></span>
                                <select required value={statusTarget} onChange={e => setStatusTarget(e.target.value)}>
                                    <option value="">Select status</option>
                                    {nextAccountStatuses.map(x => <option key={x} value={x}>{x}</option>)}
                                </select>
                            </label>
                            <label><span className="cbms-field-label">Reason <span className="cbms-required-star"
                                                                                   aria-hidden="true">*</span></span>
                                <input required maxLength={500} value={statusReason}
                                       onChange={e => setStatusReason(e.target.value)}/>
                            </label>
                            <div className="cbms-account-status-actions">
                                <button type="submit"
                                        disabled={statusBusy || !statusTarget || !statusReason.trim()}>Submit for
                                    approval
                                </button>
                            </div>
                        </form>}
                    {pendingAccountRequest && <p role="status">A status change request is awaiting approval.</p>}
                    {statusRequests.length > 0 && <div className="cbms-account-status-history">
                        <h3>Status requests</h3>
                        {statusRequests.map(r => <div key={r.requestId} className="cbms-account-status-item">
                            <span><strong>{r.fromStatus} → {r.toStatus}</strong> · {r.requestStatus} — {r.reason}</span>
                            {r.canReview && <div className="cbms-account-status-actions">
                                <button type="button" disabled={statusBusy}
                                        onClick={() => void reviewAccountChange(r.requestId, true)}>Approve
                                </button>
                                <button type="button" disabled={statusBusy}
                                        onClick={() => void reviewAccountChange(r.requestId, false)}>Reject
                                </button>
                            </div>}
                        </div>)}
                    </div>}
                </section>
            </div>}
        {customer && <>{canMake && activeModal === 'new' &&
            <div className="cbms-account-modal-backdrop" onMouseDown={e => {
                if (e.target === e.currentTarget) setActiveModal(null);
            }}>
                <section className="cbms-account-modal" role="dialog" aria-modal="true"
                         aria-labelledby="cbms-new-account-title">
                    <div className="cbms-account-heading"><h2 id="cbms-new-account-title">New account application</h2>
                        <button type="button" className="cbms-account-modal-close"
                                aria-label="Close new account application" title="Close"
                                onClick={() => setActiveModal(null)}>×
                        </button>
                    </div>
                    <p>Opening an account requires
                        application validation and approval.</p>

                    {!isCustomerActive &&
                        <p role="status" className="cbms-account-notice">New account applications are available only for
                            ACTIVE customers.</p>}
                    <form className="cbms-account-form" onSubmit={e => {
                        e.preventDefault();
                        void newApplication();
                    }}>
                        <fieldset disabled={!isCustomerActive || busy} className="cbms-account-form-fields">
                            <label><span className="cbms-field-label">Product <span className="cbms-required-star"
                                                                                    aria-hidden="true">*</span></span>
                                <select required value={productId} onChange={e => {
                                    setProductId(e.target.value);
                                    setSchemeId('');
                                    setSchemes([]);
                                }}>
                                    <option value="">Select product</option>
                                    {products.filter(p => p.prodSts === 'ACTIVE' && ['SAVINGS', 'CURRENT', 'SAVINGS_ACCOUNT', 'CURRENT_ACCOUNT'].includes(p.prodCat.toUpperCase())).map(p =>
                                        <option key={p.prodId}
                                                value={p.prodId}>{p.prodCd} · {p.prodNm}</option>)}
                                </select></label>
                            <label><span className="cbms-field-label">Scheme <span className="cbms-required-star"
                                                                                   aria-hidden="true">*</span></span>
                                <select required value={schemeId} onChange={e => setSchemeId(e.target.value)}>
                                    <option value="">Select scheme</option>
                                    {schemes.filter(s => s.schmSts === 'ACTIVE').map(s => <option key={s.schmId}
                                                                                                  value={s.schmId}>{s.schmCd} · {s.schmNm}</option>)}
                                </select></label>
                            <label className="wide">Remarks <textarea maxLength={1000} value={remarks}
                                                                      onChange={e => setRemarks(e.target.value)}/></label>
                            <button disabled={!isCustomerActive || busy || !selectedScheme || !selectedProduct}
                                    type="submit">Create draft
                                application
                            </button>
                        </fieldset>
                    </form>
                </section>
            </div>}
            {activeModal === 'applications' && <div className="cbms-account-modal-backdrop" onMouseDown={e => {
                if (e.target === e.currentTarget) setActiveModal(null);
            }}>
                <section className="cbms-account-modal" role="dialog" aria-modal="true"
                         aria-labelledby="cbms-applications-title">
                    <div className="cbms-account-heading"><h2 id="cbms-applications-title">Account applications</h2>
                        <div className="cbms-account-modal-heading-actions">
                            <button type="button" disabled={busy} onClick={() => setRefresh(x => x + 1)}>Refresh
                            </button>
                            <button type="button" className="cbms-account-modal-close"
                                    aria-label="Close account applications" title="Close"
                                    onClick={() => setActiveModal(null)}>×
                            </button>
                        </div>
                    </div>
                    <div className="cbms-application-tabs" role="tablist" aria-label="Application status">
                        <button type="button" role="tab" aria-selected={applicationTab === 'open'}
                                className={applicationTab === 'open' ? 'active' : ''}
                                onClick={() => setApplicationTab('open')}>Pending / In Progress
                        </button>
                        <button type="button" role="tab" aria-selected={applicationTab === 'closed'}
                                className={applicationTab === 'closed' ? 'active' : ''}
                                onClick={() => setApplicationTab('closed')}>Completed / Rejected
                        </button>
                    </div>
                    {applications.filter(app => applicationTab === 'closed'
                        ? ['COMPLETED', 'REJECTED', 'CREATED'].includes(app.applSts.toUpperCase())
                        : !['COMPLETED', 'REJECTED', 'CREATED'].includes(app.applSts.toUpperCase())).length === 0
                        ? <p>No applications in this category.</p>
                        : applications.filter(app => applicationTab === 'closed'
                            ? ['COMPLETED', 'REJECTED', 'CREATED'].includes(app.applSts.toUpperCase())
                            : !['COMPLETED', 'REJECTED', 'CREATED'].includes(app.applSts.toUpperCase())).map(app =>
                            <article
                                className="cbms-account-application" key={app.applId}>
                                <div><strong>{app.applRef}</strong> <span
                                    className="cbms-account-status">{app.applSts}</span>
                                </div>
                                <p>Product: {products.find(p => p.prodId === app.prodId)?.prodNm ?? '—'} ·
                                    Amount: {app.reqAmt ?? '—'} · Tenor: {app.reqTenor ?? '—'}</p>
                                {app.remarks && <p>Remarks: {app.remarks}</p>}
                                {validation[app.applId] &&
                                    <p>{validation[app.applId].length ? validation[app.applId].join('; ') : 'Validation passed'}</p>}
                                <div className="cbms-account-actions">
                                    {app.applSts === 'DRAFT' || app.applSts === 'SUBMITTED' ?
                                        <button type="button" disabled={busy}
                                                onClick={() => void check(app.applId)}>Validate</button> : null}
                                    {canMake && !isAdmin && app.applSts === 'DRAFT' &&
                                        <button type="button" disabled={busy}
                                                onClick={() => void act(() => submitApplication(app.applId), 'Application submitted for approval.')}>Submit</button>}
                                    {isAdmin && app.applSts === 'DRAFT' &&
                                        <button type="button" disabled={busy}
                                                onClick={() => void act(() => approveApplication(app.applId, ''), 'Application approved.')}>Approve</button>}
                                    {isAdmin && app.applSts === 'SUBMITTED' && <>
                                        <input aria-label={`Review remarks for ${app.applRef}`}
                                               placeholder="Review remarks (required to reject)" maxLength={1000}
                                               value={reviewRemarks[app.applId] ?? ''}
                                               onChange={e => setReviewRemarks(r => ({
                                                   ...r,
                                                   [app.applId]: e.target.value
                                               }))}/>
                                        <button type="button" disabled={busy}
                                                onClick={() => void act(() => approveApplication(app.applId, reviewRemarks[app.applId] ?? ''), 'Application approved.')}>Approve
                                        </button>
                                        <button type="button"
                                                disabled={busy || !(reviewRemarks[app.applId] ?? '').trim()}
                                                onClick={() => void act(() => rejectApplication(app.applId, reviewRemarks[app.applId] ?? ''), 'Application rejected.')}>Reject
                                        </button>
                                    </>}
                                    {isAdmin && app.applSts === 'APPROVED' && <button type="button" disabled={busy}
                                                                                      onClick={() => void act(() => completeApplication(app.applId), 'Account created successfully.')}>Create
                                        account</button>}
                                </div>
                            </article>)}
                </section>
            </div>}
        </>}
    </main>;
}

