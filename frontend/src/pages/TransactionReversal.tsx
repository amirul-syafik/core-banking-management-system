import {SortableTable} from '../components/SortableTable';
import {TablePagination, ConfirmDialog, PageHeader, RefreshButton} from '../components/UiStandards';
import {formatBusinessValue} from '../utils/display';
import {useEffect, useRef, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {AxiosError} from 'axios';
import AccountLov from '../components/AccountLov';
import {useToast} from '../components/toastContext';
import {useAuth} from '../auth/useAuth';
import type {Account} from '../api/accounts';
import {inquireTransactions, type TransactionHistory, type TransactionPage} from '../api/transactionInquiry';
import {
    getReversalEligibility, type ReversalEligibility,
    listReversals,
    getReversal,
    submitReversal,
    decideReversal,
    type ReversalPage,
    type ReversalRequest
} from '../api/reversals';
import './TransactionInquiry.css';
import './TransactionReversal.css';
import PopupView from "../components/PopupView.tsx";

function message(error: unknown) {
    if (error instanceof AxiosError) {
        const data: unknown = error.response?.data;
        if (data && typeof data === 'object' && 'detail' in data && typeof data.detail === 'string') return data.detail;
        return `Request failed (${error.response?.status ?? 'network'}).`;
    }
    return error instanceof Error ? error.message : 'Request failed.';
}

const eligible = (t: TransactionHistory) => t.txnSts === 'POSTED' && ['DEPOSIT', 'WITHDRAWAL', 'TRANSFER'].includes(t.txnTyp);
export default function TransactionReversal() {
    const {session} = useAuth();
    const notify = useToast();
    const [searchParams, setSearchParams] = useSearchParams();
    const requestFromUrl = searchParams.get('requestId');
    const [focusedRequest, setFocusedRequest] = useState<ReversalRequest | null>(null);
    const admin = !!session?.roles.includes('ADMIN');
    const canSubmit = admin || !!session?.roles.includes('MAKER');
    const [account, setAccount] = useState<Account | null>(null);
    const [reference, setReference] = useState('');
    const [transactions, setTransactions] = useState<TransactionPage | null>(null);
    const [eligibility, setEligibility] = useState<Record<string, ReversalEligibility>>({});
    const [eligibilityLoading, setEligibilityLoading] = useState(false);
    const [requests, setRequests] = useState<ReversalPage | null>(null);
    const [requestsLoading, setRequestsLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [selected, setSelected] = useState<TransactionHistory | null>(null);
    const [decision, setDecision] = useState<{ request: ReversalRequest; action: 'approve' | 'reject' } | null>(null);
    const [reason, setReason] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [searchAttempted, setSearchAttempted] = useState(false);
    const requestsInFlight = useRef(false);

    async function loadRequests(p = page) {
        if (requestsInFlight.current) return;
        requestsInFlight.current = true;
        setRequestsLoading(true);
        try {
            setRequests(await listReversals(p));
            setPage(p);
        } catch (e) {
            setError(message(e));
        } finally {
            requestsInFlight.current = false;
            setRequestsLoading(false);
        }
    }

    useEffect(() => {
        let active = true;
        requestsInFlight.current = true;
        listReversals(0).then(data => {
            if (active) {
                setRequests(data);
                setPage(0);
            }
        }).catch(() => {
            if (active) setError('Unable to load reversal requests.');
        }).finally(() => {
            if (active) {
                requestsInFlight.current = false;
                setRequestsLoading(false);
            }
        });
        return () => {
            active = false;
            requestsInFlight.current = false;
        };
    }, []);

    async function refreshData() {
        await Promise.all([
            loadRequests(page),
            transactions && account ? search(transactions.number) : Promise.resolve()
        ]);
    }

    useEffect(() => {
        if (!requestFromUrl || !/^\d+$/.test(requestFromUrl)) return;
        let live = true;
        void getReversal(Number(requestFromUrl)).then(request => {
            if (!live) return;
            setFocusedRequest(request);
            setSearchParams(previous => {
                const next = new URLSearchParams(previous);
                next.delete('requestId');
                return next;
            }, {replace: true});
        }).catch(e => {
            if (live) setError(message(e));
        });
        return () => {
            live = false;
        };
    }, [requestFromUrl, setSearchParams]);

    async function search(p = 0) {
        setSearchAttempted(true);
        if (!account) {
            setError('Please select an account.');
            return;
        }
        setBusy(true);
        setError('');
        try {
            setEligibility({});
            setEligibilityLoading(true);
            const result = await inquireTransactions(account.acctId, {reference, page: p, size: 10});
            setTransactions(result);
            const ids = result.content.map(t => t.txnId);
            if (ids.length) setEligibility(await getReversalEligibility(ids));
        } catch (e) {
            setError(message(e));
        } finally {
            setEligibilityLoading(false);
            setBusy(false);
        }
    }

    async function submit() {
        if (!selected || !reason.trim() || busy) return;
        setBusy(true);
        setError('');
        try {
            const request = await submitReversal(selected.txnId, reason.trim());
            notify('success', `Reversal request for ${request.originalTxnRef} submitted for approval.`);
            setSelected(null);
            setReason('');
            await loadRequests(0);
            if (account) await search(0);
        } catch (e) {
            setError(message(e));
            notify('error', message(e));
        } finally {
            setBusy(false);
        }
    }

    async function decide() {
        if (!decision || busy) return;
        if (decision.action === 'reject' && !reason.trim()) return;
        setBusy(true);
        setError('');
        try {
            await decideReversal(decision.request.id, decision.action, reason.trim());
            notify('success', decision.action === 'approve'
                ? `Reversal for ${decision.request.originalTxnRef} approved and posted.`
                : `Reversal request for ${decision.request.originalTxnRef} rejected.`);
            setDecision(null);
            setReason('');
            await loadRequests(page);
            if (account) await search(0);
        } catch (e) {
            setError(message(e));
            notify('error', message(e));
        } finally {
            setBusy(false);
        }
    }

    return <main className="cbms-inquiry cbms-reversal">
        <PageHeader
            title="Transaction Reversal"
            description="Request a reversal or review pending approvals. Original transactions are retained for audit purposes."
            actions={<RefreshButton loading={busy || requestsLoading} onClick={() => void refreshData()}/>}
        />
        {error && <p className="cbms-inquiry-error" role="alert">{error}</p>}
        <section className="cbms-inquiry-card"><h2>Find a transaction</h2>
            <form className="cbms-inquiry-filters" onSubmit={e => {
                e.preventDefault();
                void search(0);
            }}><AccountLov label="Account" value={account} invalid={searchAttempted && !account} onChange={value => {
                setAccount(value);
                setTransactions(null);
                setEligibility({});
            }} disabled={busy}/><label>Transaction Reference<input value={reference}
                                                                   onChange={e => setReference(e.target.value)}
                                                                   placeholder="Search reference"/></label>
                <div className="cbms-search-actions">
                    <button className="cbms-action-search" type="submit" disabled={busy}>Search</button>
                    <button className="cbms-action-clear" type="button" disabled={busy} onClick={() => {
                        setAccount(null);
                        setReference('');
                        setTransactions(null);
                        setEligibility({});
                        setEligibilityLoading(false);
                        setError('');
                    }}>Clear
                    </button>
                </div>
            </form>
            {transactions && <>
                <div className="cbms-inquiry-scroll">
                    <SortableTable>
                        <thead>
                        <tr>
                            <th>Date</th>
                            <th>Transaction Reference</th>
                            <th>Type</th>
                            <th>Amount</th>
                            <th>Status</th>
                            <th>Action</th>
                        </tr>
                        </thead>
                        <tbody>{transactions.content.map(t => <tr key={t.txnId}>
                            <td>{new Date(t.crtDt).toLocaleString('en-MY')}</td>
                            <td><PopupView value={t.txnRef} maxLength={10}/></td>
                            <td>{formatBusinessValue(t.txnTyp)}</td>
                            <td>{Number(t.txnAmt).toFixed(2)} {t.ccyCd}</td>
                            <td>{eligibility[t.txnId] === 'REVERSED' ? 'Reversed' :
                                eligibility[t.txnId] === 'PENDING' ? 'Pending Approval' :
                                    eligibility[t.txnId] === 'NOT_REVERSIBLE' ? 'Not Reversible' :
                                        eligibility[t.txnId] === 'REVERSIBLE' ? 'Reversible' : t.txnSts}</td>
                            <td>{canSubmit && !eligibilityLoading && eligibility[t.txnId] === 'REVERSIBLE' && eligible(t) ?
                                <button type="button" disabled={busy} onClick={() => {
                                    setSelected(t);
                                    setReason('');
                                    setError('');
                                }}>Reverse</button> : '—'}</td>
                        </tr>)}</tbody>
                    </SortableTable>
                </div>
                <TablePagination page={transactions.number} totalPages={Math.max(1, transactions.totalPages)}
                                 totalElements={transactions.totalElements} onPageChange={p => void search(p)}
                                 busy={busy} className="cbms-inquiry-pagination"/>
            </>}
        </section>
        {focusedRequest && <section className="cbms-inquiry-card" aria-label="Linked reversal request">
            <div className="cbms-inquiry-actions"><h2>Reversal request #{focusedRequest.id}</h2>
                <button type="button" onClick={() => setFocusedRequest(null)}>Close</button>
            </div>
            <p><strong>Transaction:</strong> {focusedRequest.originalTxnRef}</p>
            <p><strong>Status:</strong> {focusedRequest.status} · <strong>Requested
                By:</strong> {focusedRequest.requestedBy}</p>
            <p><strong>Reason:</strong> {focusedRequest.reason}</p>
            {admin && focusedRequest.status === 'PENDING' && focusedRequest.requestedBy.toLowerCase() !== session?.username.toLowerCase() &&
                <div className="cbms-inquiry-actions">
                    <button type="button" onClick={() => {
                        setDecision({request: focusedRequest, action: 'approve'});
                        setReason('');
                    }}>Approve
                    </button>
                    <button type="button" onClick={() => {
                        setDecision({request: focusedRequest, action: 'reject'});
                        setReason('');
                    }}>Reject
                    </button>
                </div>}
        </section>}
        <section className="cbms-inquiry-card"><h2>Reversal Requests</h2>{requests && <>
            <div className="cbms-inquiry-scroll">
                <SortableTable>
                    <thead>
                    <tr>
                        <th>Requested</th>
                        <th>Transaction</th>
                        <th>Reason</th>
                        <th>Requested By</th>
                        <th>Status</th>
                        <th>Decision</th>
                    </tr>
                    </thead>
                    <tbody>{requests.content.map(r => <tr key={r.id}>
                        <td>{new Date(r.requestedAt).toLocaleString('en-MY')}</td>
                        <td><PopupView value={r.originalTxnRef} maxLength={10}/></td>
                        <td>{r.reason}</td>
                        <td>{r.requestedBy}</td>
                        <td>{formatBusinessValue(r.status)}</td>
                        <td>{admin && r.status === 'PENDING' && r.requestedBy.toLowerCase() !== session?.username.toLowerCase() ? <>
                            <button type="button" onClick={() => {
                                setDecision({request: r, action: 'approve'});
                                setReason('');
                            }}>Approve
                            </button>
                            <button type="button" onClick={() => {
                                setDecision({request: r, action: 'reject'});
                                setReason('');
                            }}>Reject
                            </button>
                        </> : '—'}</td>
                    </tr>)}{!requests.content.length && <tr>
                        <td colSpan={6}>No reversal requests.</td>
                    </tr>}</tbody>
                </SortableTable>
            </div>
            <TablePagination page={page} totalPages={Math.max(1, requests.totalPages)}
                             totalElements={requests.totalElements} onPageChange={p => void loadRequests(p)}
                             busy={busy} className="cbms-inquiry-pagination"/>
        </>}</section>
        {(selected || decision) && <ConfirmDialog
            title={decision ? (decision.action === 'approve' ? 'Approve Transaction Reversal' : 'Reject Transaction Reversal') : 'Request Transaction Reversal'}
            message={decision ? (decision.action === 'approve' ? 'Are you sure you want to approve this reversal? Reversal entries will be posted.' : 'Reject this reversal request without posting any reversal?') : 'Submit a reversal request for independent approval? The original transaction remains on the audit trail.'}
            confirmLabel={decision ? (decision.action === 'approve' ? 'Approve Reversal' : 'Reject Reversal') : 'Submit Reversal Request'}
            busy={busy} danger={decision?.action === 'reject'}
            details={selected ? [
                {label: 'Transaction Reference', value: selected.txnRef},
                {label: 'Transaction Type', value: selected.txnTyp},
                {label: 'Amount', value: `${selected.ccyCd} ${Number(selected.txnAmt).toFixed(2)}`}
            ] : decision ? [
                {label: 'Transaction Reference', value: decision.request.originalTxnRef},
                {label: 'Requested By', value: decision.request.requestedBy},
                {label: 'Request Reason', value: decision.request.reason}
            ] : []}
            reasonLabel={decision ? (decision.action === 'reject' ? 'Rejection Reason' : 'Decision Remarks') : 'Reversal Reason'}
            reason={reason} onReasonChange={setReason}
            reasonRequired={!decision || decision.action === 'reject'} reasonMaxLength={250}
            notice={decision?.action === 'approve' ? 'Approval is a financial posting operation. Verify the transaction before confirming.' : undefined}
            onCancel={() => {
                setSelected(null);
                setDecision(null);
                setReason('');
            }}
            onConfirm={() => void (decision ? decide() : submit())}/>}
    </main>;
}
