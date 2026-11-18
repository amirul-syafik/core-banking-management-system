import {SortableTable, type TableSortState} from '../components/SortableTable';
import {sortTableRecords} from '../utils/tableSorting';
import {
    TablePagination,
    ConfirmDialog,
    StatusBadge,
    CloseIcon,
    RefreshButton,
    FieldLabel,
    PageHeader,
    SubscreenTitle
} from '../components/UiStandards';
import {
    listAccountStatusRequests,
    proposeAccountStatus,
    decideAccountStatus,
    type AccountStatusRequest
} from '../api/accountStatusRequests';
import {useToast} from '../components/toastContext';
import {Link, useSearchParams} from 'react-router-dom';
import PopupView from '../components/PopupView';
import {useEffect, useRef, useState} from 'react';
import {AxiosError} from 'axios';
import {searchCustomers, getCustomer, type Customer} from '../api/customers';
import {useAuth} from '../auth/useAuth';
import {
    listApplications,
    listAccounts,
    getAccount,
    getApplication,
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
    const [searchParams, setSearchParams] = useSearchParams();
    const linkAccountId = searchParams.get('accountId');
    const linkApplicationId = searchParams.get('applicationId');
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
    const [linkedAccountsPage, setLinkedAccountsPage] = useState(0);
    const [linkedAccountsSort, setLinkedAccountsSort] = useState<TableSortState>(null);
    const [applications, setApplications] = useState<Application[]>([]);
    const [products, setProducts] = useState<Product[]>([]);
    const [schemes, setSchemes] = useState<Scheme[]>([]);
    const [productId, setProductId] = useState('');
    const [schemeId, setSchemeId] = useState('');
    const [remarks, setRemarks] = useState('');
    const [reviewRemarks, setReviewRemarks] = useState<Record<number, string>>({});
    const [applicationTab, setApplicationTab] = useState<'open' | 'closed'>('open');
    const [focusedApplicationId, setFocusedApplicationId] = useState<number | null>(null);
    const [validation, setValidation] = useState<Record<number, string[]>>({});
    const [busy, setBusy] = useState(false);
    const applicationOperationRunning = useRef(false);
    const [applicationConfirmation, setApplicationConfirmation] = useState<{
        app: Application; kind: 'submit' | 'approve' | 'reject' | 'complete'; remarks: string;
    } | null>(null);
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
    const [statusConfirmation, setStatusConfirmation] = useState<
        { kind: 'propose'; account: Account; from: string; to: string; reason: string } |
        { kind: 'review'; account: Account; request: AccountStatusRequest; approve: boolean } | null>(null);
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
        const accountId = linkAccountId && /^\d+$/.test(linkAccountId) ? Number(linkAccountId) : null;
        const applicationId = linkApplicationId && /^\d+$/.test(linkApplicationId) ? Number(linkApplicationId) : null;
        if (!accountId && !applicationId) return;
        let live = true;
        void (async () => {
            try {
                if (accountId) {
                    const account = await getAccount(accountId);
                    const owner = await getCustomer(account.custId);
                    if (!live) return;
                    setCustomer(owner);
                    setSelectedAccount(account);
                    setStatusRequests([]);
                    setStatusTarget('');
                    setStatusReason('');
                    setActiveModal('maintenance');
                } else if (applicationId) {
                    const application = await getApplication(applicationId);
                    const owner = await getCustomer(application.custId);
                    if (!live) return;
                    setCustomer(owner);
                    setFocusedApplicationId(application.applId);
                    setApplicationTab(['COMPLETED', 'REJECTED', 'CREATED'].includes(application.applSts) ? 'closed' : 'open');
                    setActiveModal('applications');
                }
                setSearchParams(params => {
                    const next = new URLSearchParams(params);
                    next.delete('accountId');
                    next.delete('applicationId');
                    return next;
                }, {replace: true});
            } catch (e) {
                if (live) notify('error', errorMessage(e));
            }
        })();
        return () => {
            live = false;
        };
    }, [linkAccountId, linkApplicationId, notify, setSearchParams]);

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

    useEffect(() => {
        if (activeModal !== 'applications' || focusedApplicationId == null || !applications.some(a => a.applId === focusedApplicationId)) return;
        const id = window.setTimeout(() => document.getElementById(`account-application-${focusedApplicationId}`)?.scrollIntoView({block: 'nearest'}), 0);
        return () => window.clearTimeout(id);
    }, [applications, activeModal, focusedApplicationId]);

    function closeAccountModal() {
        if (busy || statusBusy) return;
        setApplicationConfirmation(null);
        setStatusConfirmation(null);
        setActiveModal(null);
        setSelectedAccount(null);
        setStatusRequests([]);
        setStatusTarget('');
        setStatusReason('');
        setProductId('');
        setSchemeId('');
        setSchemes([]);
        setRemarks('');
        setReviewRemarks({});
        setValidation({});
        setFocusedApplicationId(null);
        setError('');
        setNotice('');
    }

    function selectAccount(account: Account) {
        setSelectedAccount(account);
        setStatusRequests([]);
        setStatusTarget('');
        setStatusReason('');
        setActiveModal('maintenance');
    }

    async function proposeAccountChange() {
        if (!selectedAccount || !canMake || !nextAccountStatuses.includes(statusTarget) || !statusReason.trim() || statusBusy) return;
        setStatusBusy(true);
        try {
            await proposeAccountStatus(selectedAccount.acctId, statusTarget, statusReason.trim());
            notify('success', 'Account status request submitted for approval.');
            setStatusConfirmation(null);
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
        if (!selectedAccount || !isAdmin || statusBusy) return;
        setStatusBusy(true);
        try {
            await decideAccountStatus(selectedAccount.acctId, requestId, approve);
            notify('success', approve ? 'Account status approved.' : 'Account status request rejected.');
            setStatusConfirmation(null);
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
        setLinkedAccountsPage(0);
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
            notify('warning', 'Enter a customer name, customer identification number or account number.');
            return;
        }
        setAccountSearchBusy(true);
        setError('');
        setMatches([]);
        setAccountSearchDone(false);
        try {
            if (searchMode === 'name') {
                const data = await searchCustomers({name: query.trim(), page, size: 10});
                setMatches(data.content);
                setAccountSearchPage(data.number);
                setAccountSearchTotalPages(data.totalPages);
                setAccountSearchTotal(data.totalElements);
            } else {
                const result = await searchAccounts({
                    accountNo: searchMode === 'account' ? query.trim() : undefined,
                    cifNo: searchMode === 'cif' ? query.trim() : undefined,
                    status: accountStatusFilter || undefined,
                    page, size: 10
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

    async function act(action: () => Promise<unknown>, success: string | ((result: unknown) => string)) {
        if (applicationOperationRunning.current) return;
        applicationOperationRunning.current = true;
        setBusy(true);
        setError('');
        setNotice('');
        try {
            const result = await action();
            const successMessage = typeof success === 'function' ? success(result) : success;
            setNotice(successMessage);
            notify('success', successMessage);
            setApplicationConfirmation(null);
            setRefresh(x => x + 1);
            setActiveModal(null);
        } catch (e) {
            setError(errorMessage(e));
            notify('error', errorMessage(e));
        } finally {
            applicationOperationRunning.current = false;
            setBusy(false);
        }
    }

    function requestApplicationAction(app: Application, kind: 'submit' | 'approve' | 'reject' | 'complete') {
        if (busy || applicationOperationRunning.current) return;
        const reviewText = (reviewRemarks[app.applId] ?? '').trim();
        if (kind === 'reject' && !reviewText) return;
        setApplicationConfirmation({app, kind, remarks: reviewText});
    }

    function confirmApplicationAction() {
        if (!applicationConfirmation || busy || applicationOperationRunning.current) return;
        const {app, kind, remarks: confirmationRemarks} = applicationConfirmation;
        const action = kind === 'submit' ? () => submitApplication(app.applId) :
            kind === 'approve' ? () => approveApplication(app.applId, confirmationRemarks) :
                kind === 'reject' ? () => rejectApplication(app.applId, confirmationRemarks) :
                    () => completeApplication(app.applId);
        const resultMessage = kind === 'submit' ? `Application ${app.applRef} submitted for approval.` :
            kind === 'approve' ? `Application ${app.applRef} approved.` :
                kind === 'reject' ? `Application ${app.applRef} rejected.` : `Account created for application ${app.applRef}.`;
        void act(action, resultMessage);
    }

    async function newApplication() {
        if (busy || !customer || !isCustomerActive || !selectedProduct || !selectedScheme || !canMake) return;
        await act(async () => {
            const created = await createApplication({
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
            return created;
        }, result => `Account application ${(result as Application).applRef} created as draft. Validate and submit it for approval.`);
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
        <PageHeader
            title="Accounts"
            description="Search for a customer, review linked accounts, and process account-opening applications."
            actions={
                <div className="cbms-toolbar-actions">
                    <RefreshButton loading={busy} onClick={() => setRefresh(x => x + 1)}/>
                </div>
            }
        />
        {error && <p role="alert" className="cbms-account-error">{error}</p>}
        {notice && <p role="status" className="cbms-account-notice">{notice}</p>}
        {!customer && <section className="cbms-account-card cbms-global-account-search">
            <h2>Find account or customer</h2>
            <div className="cbms-account-search-modes" role="group" aria-label="Search by">
                {([['name', 'Customer name'], ['cif', 'Customer Identification Number'], ['account', 'Account number']] as const).map(([mode, label]) =>
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
                        className="cbms-field-label">{searchMode === 'name' ? 'Customer name' : searchMode === 'cif' ? 'Customer Identification Number' : 'Account number'}
                        <span className="cbms-required-star" aria-hidden="true">*</span></span>
                        <input required value={query} maxLength={100} onChange={e => setQuery(e.target.value)}
                               placeholder={searchMode === 'name' ? 'Enter customer name' : searchMode === 'cif' ? 'Enter customer identification number' : 'Enter account number'}/></label>
                    <label><span className="cbms-field-label">Account status</span>
                        <span className="cbms-tooltip-wrapper"
                              data-tooltip="Available for customer identification number and account number searches">
                        <select value={accountStatusFilter} disabled={searchMode === 'name'}
                                onChange={e => setAccountStatusFilter(e.target.value)}>
                            <option value="">All statuses</option>
                            {['PENDING', 'ACTIVE', 'DORMANT', 'FROZEN', 'CLOSED'].map(st => <option key={st}
                                                                                                    value={st}>{formatBusinessValue(st)}</option>)}
                        </select>
                        </span>
                    </label>
                    <div className="cbms-global-account-buttons">
                        <button className="cbms-action-search" type="submit" disabled={accountSearchBusy}>Search
                        </button>
                        <button className="cbms-action-clear" type="button" disabled={accountSearchBusy}
                                onClick={() => {
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
                    <SortableTable>
                        <thead>
                        <tr>
                            <th>Account Number</th>
                            <th>Account Type</th>
                            <th>Currency</th>
                            <th>Status</th>
                            <th>Opening Date</th>
                            <th>Action</th>
                        </tr>
                        </thead>
                        <tbody>{accountSearchResults.map(a => <tr key={a.acctId}>
                            <td>{a.acctNo}</td>
                            <td>{formatBusinessValue(a.acctTyp)}</td>
                            <td>{a.ccyCd}</td>
                            <td><StatusBadge status={a.acctSts}/></td>
                            <td>{a.openDt || '—'}</td>
                            <td>
                                <button type="button" disabled={accountSearchBusy}
                                        onClick={() => void openAccountCustomer(a)}>Open dashboard
                                </button>
                            </td>
                        </tr>)}</tbody>
                    </SortableTable>
                </div>}
                <TablePagination page={accountSearchPage} totalPages={Math.max(1, accountSearchTotalPages)}
                                 totalElements={accountSearchTotal} onPageChange={p => void findAccounts(p)}
                                 busy={accountSearchBusy} className="cbms-account-pagination"/>
            </>}
        </section>}
        {customer && <>
            <section className="cbms-account-card">
                <div className="cbms-account-heading"><SubscreenTitle as="h2" title={customer.custNm}
                    reference={customer.cifNo}/><Link
                    to={`/customers/${customer.custId}`}>Customer profile</Link></div>
                <p>Customer status: {customer.sts}</p>
                <div className="cbms-account-dashboard-actions">
                    <button type="button" onClick={() => {
                        setApplicationTab('open');
                        setActiveModal('applications');
                    }}>Account applications
                        ({applications.length})
                    </button>
                    {canMake &&
                        <button type="button" className="cbms-action-create" disabled={!isCustomerActive}
                                onClick={() => setActiveModal('new')}>New
                            Account Application</button>}
                </div>
                {!isCustomerActive && canMake &&
                    <p className="cbms-account-warning" role="status">New account applications are available only for
                        ACTIVE customers.</p>}
                <h3>Linked accounts</h3>
                {accounts.length === 0 ? <p>No linked accounts.</p> : <div className="cbms-account-scroll">
                    <SortableTable sortState={linkedAccountsSort} onSortChange={sort => { setLinkedAccountsSort(sort); setLinkedAccountsPage(0); }}>
                        <thead>
                        <tr>
                            <th>Account Number</th>
                            <th>Account Type</th>
                            <th>Currency</th>
                            <th>Status</th>
                            <th>Opening Date</th>
                            <th>Closing Date</th>
                            <th>Action</th>
                        </tr>
                        </thead>
                        <tbody>{sortTableRecords(accounts, linkedAccountsSort, [a => a.acctNo, a => a.acctTyp, a => a.ccyCd, a => a.acctSts, a => a.openDt, a => a.closeDt]).slice(linkedAccountsPage * 10, (linkedAccountsPage + 1) * 10).map(a => <tr key={a.acctId}>
                            <td>{a.acctNo}</td>
                            <td>{formatBusinessValue(a.acctTyp)}</td>
                            <td>{a.ccyCd}</td>
                            <td><StatusBadge status={a.acctSts}/></td>
                            <td>{a.openDt || '—'}</td>
                            <td>{a.closeDt || '—'}</td>
                            <td>
                                <div className="cbms-account-table-actions">
                                    <button className="cbms-action-details" type="button"
                                            onClick={() => openAccountDetails(a)}>View Details
                                    </button>
                                    {canMake && <button type="button" onClick={() => selectAccount(a)}>Manage</button>}
                                </div>
                            </td>
                        </tr>)}</tbody>
                    </SortableTable>
                </div>}
                {accounts.length > 10 && <TablePagination
                    page={Math.min(linkedAccountsPage, Math.max(0, Math.ceil(accounts.length / 10) - 1))}
                    totalPages={Math.max(1, Math.ceil(accounts.length / 10))} totalElements={accounts.length}
                    onPageChange={setLinkedAccountsPage} className="cbms-account-pagination"/>}
                <div className="cbms-account-dashboard-footer">
                    <button type="button" disabled={busy || statusBusy} onClick={resetPage}>Cancel</button>
                </div>
            </section>
        </>}
        {selectedAccount && activeModal === 'details' &&
            <div className="cbms-account-modal-backdrop" onMouseDown={e => {
                if (e.target === e.currentTarget) closeAccountModal();
            }}>
                <section className="cbms-account-modal cbms-account-details-modal" role="dialog" aria-modal="true"
                         aria-labelledby="cbms-account-details-title">
                    <div className="cbms-account-heading"><SubscreenTitle as="h2" id="cbms-account-details-title"
                        title="Account details" reference={selectedAccount.acctNo}/>
                        <button type="button" className="cbms-account-modal-close" aria-label="Close account details"
                                title="Close" onClick={closeAccountModal}><CloseIcon/></button>
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
                                    <dd>{formatBusinessValue(selectedAccount.acctTyp)}</dd>
                                </div>
                                <div>
                                    <dt>Currency</dt>
                                    <dd>{selectedAccount.ccyCd}</dd>
                                </div>
                                <div>
                                    <dt>Status</dt>
                                    <dd>{formatBusinessValue(selectedAccount.acctSts)}</dd>
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
                                        <SortableTable>
                                            <thead>
                                            <tr>
                                                <th>Date</th>
                                                <th>Transaction Reference</th>
                                                <th>Account Type</th>
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
                                        </SortableTable>
                                    </div>}
                                <TablePagination page={transactionPage}
                                                 totalPages={Math.max(1, accountTransactions.totalPages)}
                                                 totalElements={accountTransactions.totalElements}
                                                 onPageChange={setTransactionPage} busy={accountDetailLoading}
                                                 className="cbms-account-pagination"/>
                            </>}
                        </>}
                    </div>
                </section>
            </div>}
        {selectedAccount && activeModal === 'maintenance' &&
            <div className="cbms-account-modal-backdrop" onMouseDown={e => {
                if (e.target === e.currentTarget) closeAccountModal();
            }}>
                <section className="cbms-account-modal" role="dialog" aria-modal="true"
                         aria-labelledby="cbms-maintenance-title">
                    <div className="cbms-account-heading"><SubscreenTitle as="h2" id="cbms-maintenance-title"
                        title="Account maintenance" reference={selectedAccount.acctNo}/>
                        <button type="button" className="cbms-account-modal-close"
                                aria-label="Close account maintenance" title="Close"
                                onClick={closeAccountModal}><CloseIcon/></button>
                    </div>
                    <p>Current
                        status: <strong>{accounts.find(a => a.acctId === selectedAccount.acctId)?.acctSts ?? selectedAccount.acctSts}</strong>
                    </p>
                    <p>Closure is not available until balance and pending-transaction checks are implemented.</p>
                    {canMake && !pendingAccountRequest && nextAccountStatuses.length > 0 &&
                        <form className="cbms-account-status-form" onSubmit={e => {
                            e.preventDefault();
                            if (selectedAccount && statusTarget && statusReason.trim() && !statusBusy) {
                                setStatusConfirmation({
                                    kind: 'propose',
                                    account: selectedAccount,
                                    from: currentAccountStatus,
                                    to: statusTarget,
                                    reason: statusReason.trim()
                                });
                            }
                        }}>
                            <label><FieldLabel className="cbms-field-label" required>New Status</FieldLabel>
                                <select required value={statusTarget} onChange={e => setStatusTarget(e.target.value)}>
                                    <option value="">Select status</option>
                                    {nextAccountStatuses.map(x => <option key={x} value={x}>{formatBusinessValue(x)}</option>)}
                                </select>
                            </label>
                            <label><FieldLabel className="cbms-field-label" required>Reason</FieldLabel>
                                <input required maxLength={500} value={statusReason}
                                       onChange={e => setStatusReason(e.target.value)}/>
                            </label>
                            <div className="cbms-account-status-actions">
                                <button type="submit"
                                        disabled={statusBusy}>Submit for
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
                                        onClick={() => setStatusConfirmation({
                                            kind: 'review',
                                            account: selectedAccount,
                                            request: r,
                                            approve: true
                                        })}>Approve
                                </button>
                                <button type="button" disabled={statusBusy}
                                        onClick={() => setStatusConfirmation({
                                            kind: 'review',
                                            account: selectedAccount,
                                            request: r,
                                            approve: false
                                        })}>Reject
                                </button>
                            </div>}
                        </div>)}
                    </div>}
                </section>
            </div>}
        {customer && <>{canMake && activeModal === 'new' &&
            <div className="cbms-account-modal-backdrop" onMouseDown={e => {
                if (e.target === e.currentTarget) closeAccountModal();
            }}>
                <section className="cbms-account-modal" role="dialog" aria-modal="true"
                         aria-labelledby="cbms-new-account-title">
                    <div className="cbms-account-heading"><h2 id="cbms-new-account-title">New account application</h2>
                        <button type="button" className="cbms-account-modal-close"
                                aria-label="Close new account application" title="Close"
                                onClick={closeAccountModal}><CloseIcon/></button>
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
                            <div className="cbms-form-actions">
                                <button type="button" className="cbms-btn cbms-btn-secondary" disabled={busy}
                                        onClick={closeAccountModal}>Cancel
                                </button>
                                <button disabled={!isCustomerActive || busy}
                                        type="submit"
                                        className="cbms-account-create-button cbms-btn cbms-btn-primary">Create
                                </button>
                            </div>
                        </fieldset>
                    </form>
                </section>
            </div>}
            {activeModal === 'applications' && <div className="cbms-account-modal-backdrop" onMouseDown={e => {
                if (e.target === e.currentTarget) closeAccountModal();
            }}>
                <section className="cbms-account-modal cbms-account-applications-modal" role="dialog" aria-modal="true"
                         aria-labelledby="cbms-applications-title">
                    <div className="cbms-account-heading"><h2 id="cbms-applications-title">Account applications</h2>
                        <div className="cbms-account-modal-heading-actions">
                            <RefreshButton loading={busy} onClick={() => setRefresh(x => x + 1)}/>
                            <button type="button" className="cbms-account-modal-close"
                                    aria-label="Close account applications" title="Close"
                                    onClick={closeAccountModal}><CloseIcon/></button>
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
                    <div className="cbms-account-application-scroll">
                        {applications.filter(app => applicationTab === 'closed'
                            ? ['COMPLETED', 'REJECTED', 'CREATED'].includes(app.applSts.toUpperCase())
                            : !['COMPLETED', 'REJECTED', 'CREATED'].includes(app.applSts.toUpperCase())).length === 0
                            ? <p>No applications in this category.</p>
                            : applications.filter(app => applicationTab === 'closed'
                                ? ['COMPLETED', 'REJECTED', 'CREATED'].includes(app.applSts.toUpperCase())
                                : !['COMPLETED', 'REJECTED', 'CREATED'].includes(app.applSts.toUpperCase())).map(app =>
                                <article
                                    className="cbms-account-application" key={app.applId}
                                    id={`account-application-${app.applId}`}
                                    style={focusedApplicationId === app.applId ? {
                                        outline: '2px solid #2563eb',
                                        outlineOffset: 2
                                    } : undefined}>
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
                                                    onClick={() => requestApplicationAction(app, 'submit')}>Submit</button>}
                                        {isAdmin && app.applSts === 'DRAFT' &&
                                            <button type="button" disabled={busy}
                                                    onClick={() => requestApplicationAction(app, 'approve')}>Approve</button>}
                                        {isAdmin && app.applSts === 'SUBMITTED' && <>
                                            <input aria-label={`Review remarks for ${app.applRef}`}
                                                   placeholder="Review remarks (required to reject)" maxLength={1000}
                                                   value={reviewRemarks[app.applId] ?? ''}
                                                   onChange={e => setReviewRemarks(r => ({
                                                       ...r,
                                                       [app.applId]: e.target.value
                                                   }))}/>
                                            <button type="button" disabled={busy}
                                                    onClick={() => requestApplicationAction(app, 'approve')}>Approve
                                            </button>
                                            <button type="button"
                                                    disabled={busy || !(reviewRemarks[app.applId] ?? '').trim()}
                                                    onClick={() => requestApplicationAction(app, 'reject')}>Reject
                                            </button>
                                        </>}
                                        {isAdmin && app.applSts === 'APPROVED' && <button type="button" disabled={busy}
                                                                                          onClick={() => requestApplicationAction(app, 'complete')}>Create
                                            account</button>}
                                    </div>
                                </article>)}
                    </div>
                </section>
            </div>}
        </>}
        {applicationConfirmation && <ConfirmDialog
            title={applicationConfirmation.kind === 'submit' ? 'Submit Account Application' :
                applicationConfirmation.kind === 'approve' ? 'Approve Account Application' :
                    applicationConfirmation.kind === 'reject' ? 'Reject Account Application' : 'Create Bank Account'}
            message={applicationConfirmation.kind === 'submit' ? 'Submit this application for approval?' :
                applicationConfirmation.kind === 'approve' ? 'Approve this account-opening application?' :
                    applicationConfirmation.kind === 'reject' ? 'Reject this account-opening application?' :
                        'Create the account from this approved application?'}
            confirmLabel={applicationConfirmation.kind === 'submit' ? 'Submit for Approval' :
                applicationConfirmation.kind === 'approve' ? 'Approve Application' :
                    applicationConfirmation.kind === 'reject' ? 'Reject Application' : 'Create'}
            danger={applicationConfirmation.kind === 'reject'}
            busy={busy}
            details={[
                {label: 'Application', value: applicationConfirmation.app.applRef},
                {
                    label: 'Product',
                    value: products.find(p => p.prodId === applicationConfirmation.app.prodId)?.prodNm ?? '—'
                },
                {label: 'Current Status', value: applicationConfirmation.app.applSts},
                ...(applicationConfirmation.app.reqAmt != null ? [{
                    label: 'Requested Amount',
                    value: String(applicationConfirmation.app.reqAmt)
                }] : []),
                ...(applicationConfirmation.remarks ? [{
                    label: 'Review Remarks',
                    value: applicationConfirmation.remarks
                }] : [])
            ]}
            notice={applicationConfirmation.kind === 'complete' ?
                'This action creates a real bank account record. Verify the application was independently approved.' :
                applicationConfirmation.kind === 'approve' ? 'This action advances the account-opening approval workflow.' : undefined}
            onCancel={() => {
                if (!busy) setApplicationConfirmation(null);
            }}
            onConfirm={confirmApplicationAction}/>}
        {statusConfirmation && <ConfirmDialog
            title={statusConfirmation.kind === 'propose' ? 'Submit Account Status Change' :
                statusConfirmation.approve ? 'Approve Account Status Change' : 'Reject Account Status Change'}
            message={statusConfirmation.kind === 'propose' ? 'Submit this change for independent approval? The account status will not change until approved.' :
                statusConfirmation.approve ? 'Apply the approved status change to the account?' : 'Reject this requested account status change?'}
            confirmLabel={statusConfirmation.kind === 'propose' ? 'Submit for Approval' : statusConfirmation.approve ? 'Approve' : 'Reject'}
            danger={statusConfirmation.kind === 'review' && !statusConfirmation.approve} busy={statusBusy}
            details={[
                {label: 'Account Number', value: statusConfirmation.account.acctNo},
                {
                    label: 'Current Status',
                    value: statusConfirmation.kind === 'propose' ? statusConfirmation.from : statusConfirmation.request.fromStatus
                },
                {
                    label: 'Requested Status',
                    value: statusConfirmation.kind === 'propose' ? statusConfirmation.to : statusConfirmation.request.toStatus
                },
                {
                    label: 'Request Reason',
                    value: statusConfirmation.kind === 'propose' ? statusConfirmation.reason : statusConfirmation.request.reason
                }
            ]}
            notice={statusConfirmation.kind === 'review' && statusConfirmation.approve ? 'Approval may immediately affect account transaction eligibility.' : undefined}
            onCancel={() => setStatusConfirmation(null)}
            onConfirm={() => {
                if (statusConfirmation.kind === 'propose') void proposeAccountChange();
                else void reviewAccountChange(statusConfirmation.request.requestId, statusConfirmation.approve);
            }}/>}
    </main>;
}

import {formatBusinessValue} from '../utils/display';
