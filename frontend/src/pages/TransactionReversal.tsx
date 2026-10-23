import {useEffect, useState} from 'react';
import {AxiosError} from 'axios';
import AccountLov from '../components/AccountLov';
import {useToast} from '../components/toastContext';
import {useAuth} from '../auth/useAuth';
import type {Account} from '../api/accounts';
import {inquireTransactions, type TransactionHistory, type TransactionPage} from '../api/transactionInquiry';
import {
    getReversalEligibility, type ReversalEligibility,
    listReversals,
    submitReversal,
    decideReversal,
    directReversal,
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
    const admin = !!session?.roles.includes('ADMIN');
    const canSubmit = admin || !!session?.roles.includes('MAKER');
    const [account, setAccount] = useState<Account | null>(null);
    const [reference, setReference] = useState('');
    const [transactions, setTransactions] = useState<TransactionPage | null>(null);
    const [eligibility, setEligibility] = useState<Record<string, ReversalEligibility>>({});
    const [eligibilityLoading, setEligibilityLoading] = useState(false);
    const [requests, setRequests] = useState<ReversalPage | null>(null);
    const [page, setPage] = useState(0);
    const [selected, setSelected] = useState<TransactionHistory | null>(null);
    const [decision, setDecision] = useState<{ request: ReversalRequest; action: 'approve' | 'reject' } | null>(null);
    const [reason, setReason] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');

    async function loadRequests(p = page) {
        try {
            setRequests(await listReversals(p));
            setPage(p);
        } catch (e) {
            setError(message(e));
        }
    }

    useEffect(() => {
        let active = true;
        listReversals(0).then(data => {
            if (active) setRequests(data);
        }).catch(() => {
            if (active) setError('Unable to load reversal requests.');
        });
        return () => {
            active = false;
        };
    }, []);

    async function search(p = 0) {
        if (!account) return;
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
        if (!selected || !reason.trim()) return;
        setBusy(true);
        setError('');
        try {
            if (admin) {
                await directReversal(selected.txnId, reason.trim());
                notify('success', 'Transaction reversed successfully.');
            } else {
                await submitReversal(selected.txnId, reason.trim());
                notify('success', 'Reversal request submitted for approval.');
            }
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
        if (!decision) return;
        if (decision.action === 'reject' && !reason.trim()) return;
        setBusy(true);
        setError('');
        try {
            await decideReversal(decision.request.id, decision.action, reason.trim());
            notify('success', decision.action === 'approve' ? 'Reversal approved and posted.' : 'Reversal request rejected.');
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
        <header><h1>Transaction Reversal</h1><p>Request a reversal or review pending approvals. Original transactions
            are retained for audit purposes.</p></header>
        {error && <p className="cbms-inquiry-error" role="alert">{error}</p>}
        <section className="cbms-inquiry-card"><h2>Find a transaction</h2>
            <form className="cbms-inquiry-filters" onSubmit={e => {
                e.preventDefault();
                void search(0);
            }}><AccountLov label="Account" value={account} onChange={value => {
                setAccount(value);
                setTransactions(null);
                setEligibility({});
            }} disabled={busy}/><label>Transaction Reference<input value={reference}
                                                                   onChange={e => setReference(e.target.value)}
                                                                   placeholder="Search reference"/></label>
                <div className="cbms-inquiry-actions">
                    <button type="button" disabled={busy} onClick={() => {
                        setAccount(null);
                        setReference('');
                        setTransactions(null);
                        setEligibility({});
                        setEligibilityLoading(false);
                        setError('');
                    }}>Clear
                    </button>
                    <button type="submit" disabled={!account || busy}>Search</button>
                </div>
            </form>
            {transactions && <>
                <div className="cbms-inquiry-scroll">
                    <table>
                        <thead>
                        <tr>
                            <th>Date</th>
                            <th>Reference</th>
                            <th>Type</th>
                            <th>Amount</th>
                            <th>Status</th>
                            <th>Action</th>
                        </tr>
                        </thead>
                        <tbody>{transactions.content.map(t => <tr key={t.txnId}>
                            <td>{new Date(t.crtDt).toLocaleString('en-MY')}</td>
                            <td><PopupView value={t.txnRef} maxLength={10}/></td>
                            <td>{t.txnTyp}</td>
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
                    </table>
                </div>
                <div className="cbms-inquiry-pagination">
                    <button type="button" disabled={busy || transactions.number === 0}
                            onClick={() => void search(transactions.number - 1)}>Previous
                    </button>
                    <span>Page {transactions.number + 1} of {Math.max(1, transactions.totalPages)}</span>
                    <button type="button" disabled={busy || transactions.number + 1 >= transactions.totalPages}
                            onClick={() => void search(transactions.number + 1)}>Next
                    </button>
                </div>
            </>}
        </section>
        <section className="cbms-inquiry-card"><h2>Reversal Requests</h2>{requests && <>
            <div className="cbms-inquiry-scroll">
                <table>
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
                        <td>{r.status}</td>
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
                </table>
            </div>
            <div className="cbms-inquiry-pagination">
                <button type="button" disabled={busy || page === 0}
                        onClick={() => void loadRequests(page - 1)}>Previous
                </button>
                <span>Page {page + 1} of {Math.max(1, requests.totalPages)}</span>
                <button type="button" disabled={busy || page + 1 >= requests.totalPages}
                        onClick={() => void loadRequests(page + 1)}>Next
                </button>
            </div>
        </>}</section>
        {(selected || decision) && <div className="cbms-inquiry-backdrop" onMouseDown={e => {
            if (e.target === e.currentTarget && !busy) {
                setSelected(null);
                setDecision(null);
            }
        }}>
            <section className="cbms-inquiry-dialog" role="dialog" aria-modal="true" aria-label="Confirm reversal">
                <div className="cbms-inquiry-dialog-head">
                    <h2>{decision ? `${decision.action === 'approve' ? 'Approve' : 'Reject'} Reversal` : admin ? 'Confirm Reversal' : 'Request Reversal'}</h2>
                    <button type="button" aria-label="Close" disabled={busy} onClick={() => {
                        setSelected(null);
                        setDecision(null);
                    }}>×
                    </button>
                </div>
                <p>{selected ? `Transaction ${selected.txnRef} — ${selected.ccyCd} ${Number(selected.txnAmt).toFixed(2)}` : `Transaction ${decision?.request.originalTxnRef}`}</p>
                <form className="cbms-reversal-form" onSubmit={e => {
                    e.preventDefault();
                    void (decision ? decide() : submit());
                }}>
                    <label><span
                        className="cbms-field-label">{decision ? (decision.action === 'reject' ? 'Rejection Reason' : 'Decision Remarks') : 'Reversal Reason'} {!decision || decision.action === 'reject' ?
                        <span className="cbms-required-star">*</span> : null}</span></label>
                    <textarea value={reason} maxLength={250}
                              required={!decision || decision.action === 'reject'}
                              onChange={e => setReason(e.target.value)}
                              rows={3}/>
                    <div className="cbms-reversal-actions">
                        <button type="button" className="cbms-reversal-cancel" disabled={busy} onClick={() => {
                            setSelected(null);
                            setDecision(null);
                        }}>Cancel
                        </button>
                        <button type="submit" className="cbms-reversal-confirm"
                                disabled={busy || ((!decision || decision.action === 'reject') && !reason.trim())}>{busy ? 'Processing…' : 'Confirm'}</button>
                    </div>
                </form>
            </section>
        </div>}
    </main>;
}
