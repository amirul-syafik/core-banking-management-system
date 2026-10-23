import {useState} from 'react';
import {AxiosError} from 'axios';
import AccountLov from '../components/AccountLov';
import PopupView from '../components/PopupView';
import type {Account} from '../api/accounts';
import {
    getTransactionDetail,
    inquireTransactions,
    type TransactionDetail,
    type TransactionPage
} from '../api/transactionInquiry';
import './TransactionInquiry.css';

const types = ['DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'REVERSAL', 'FD_PLACEMENT', 'FD_INTEREST_ACCRUAL', 'FD_INTEREST_PAYOUT', 'FD_RENEWAL', 'FD_PREMATURE_WITHDRAWAL'];

function errorText(error: unknown) {
    if (error instanceof AxiosError) {
        const data: unknown = error.response?.data;
        if (data && typeof data === 'object' && 'detail' in data && typeof data.detail === 'string') return data.detail;
        return `Request failed (${error.response?.status ?? 'network'}).`;
    }
    return error instanceof Error ? error.message : 'Request failed.';
}

export default function TransactionInquiry() {
    const [account, setAccount] = useState<Account | null>(null);
    const [reference, setReference] = useState('');
    const [type, setType] = useState('');
    const [status, setStatus] = useState('');
    const [from, setFrom] = useState('');
    const [to, setTo] = useState('');
    const [page, setPage] = useState<TransactionPage | null>(null);
    const [detail, setDetail] = useState<TransactionDetail | null>(null);
    const [busy, setBusy] = useState(false);
    const [detailBusy, setDetailBusy] = useState(false);
    const [error, setError] = useState('');

    async function search(pageNumber = 0) {
        if (!account) {
            setError('Select an account using the LOV.');
            return;
        }
        if (from && to && from > to) {
            setError('From date must not exceed To date.');
            return;
        }
        setBusy(true);
        setError('');
        try {
            setPage(await inquireTransactions(account.acctId, {
                reference,
                type,
                status,
                from: from || undefined,
                to: to || undefined,
                page: pageNumber,
                size: 10
            }));
        } catch (e) {
            setPage(null);
            setError(errorText(e));
        } finally {
            setBusy(false);
        }
    }

    async function showDetail(txnId: number) {
        setDetailBusy(true);
        setError('');
        try {
            setDetail(await getTransactionDetail(txnId));
        } catch (e) {
            setError(errorText(e));
        } finally {
            setDetailBusy(false);
        }
    }

    function clear() {
        setAccount(null);
        setReference('');
        setType('');
        setStatus('');
        setFrom('');
        setTo('');
        setPage(null);
        setError('');
    }

    return <main className="cbms-inquiry">
        <header><h1>Transaction Inquiry</h1><p>Search and review posted transactions. All information is read-only.</p>
        </header>
        <section className="cbms-inquiry-card"><h2>Search transactions</h2>
            <form onSubmit={e => {
                e.preventDefault();
                void search();
            }} className="cbms-inquiry-filters">
                <AccountLov label="Account" value={account} onChange={value => {
                    setAccount(value);
                    setPage(null);
                    setError('');
                }} disabled={busy}/>
                <label>Transaction Reference<input value={reference} onChange={e => setReference(e.target.value)}
                                                   placeholder="Search reference"/></label>
                <label>Transaction Type<select value={type} onChange={e => setType(e.target.value)}>
                    <option value="">All types</option>
                    {types.map(t => <option key={t} value={t}>{t}</option>)}</select></label>
                <label>Status<select value={status} onChange={e => setStatus(e.target.value)}>
                    <option value="">All statuses</option>
                    {['POSTED', 'COMPLETED', 'SUCCESS', 'FAILED', 'REVERSED', 'PENDING'].map(s => <option key={s}
                                                                                                          value={s}>{s}</option>)}
                </select></label>
                <label>From Date<input type="date" value={from} onChange={e => setFrom(e.target.value)}/></label>
                <label>To Date<input type="date" value={to} onChange={e => setTo(e.target.value)}/></label>
                <div className="cbms-inquiry-actions">
                    <button type="button" onClick={clear} disabled={busy}>Clear</button>
                    <button type="submit" disabled={busy || !account}>{busy ? 'Searching…' : 'Search'}</button>
                </div>
            </form>
            {error && <p role="alert" className="cbms-inquiry-error">{error}</p>}
        </section>
        {page && <section className="cbms-inquiry-card"><h2>Results <small>({page.totalElements} records)</small></h2>
            <div className="cbms-inquiry-scroll">
                <table>
                    <thead>
                    <tr>
                        <th>Date</th>
                        <th>Reference</th>
                        <th>Type</th>
                        <th>Direction</th>
                        <th>Amount</th>
                        <th>Currency</th>
                        <th>Status</th>
                        <th>Details</th>
                    </tr>
                    </thead>
                    <tbody>
                    {page.content.map(t => <tr key={t.txnId}>
                        <td>{new Date(t.crtDt).toLocaleString('en-MY')}</td>
                        <td><PopupView value={t.txnRef} maxLength={10}/></td>
                        <td><PopupView value={t.txnTyp} maxLength={10}/></td>
                        <td>{t.direction}</td>
                        <td>{Number(t.txnAmt).toLocaleString('en-MY', {
                            minimumFractionDigits: 2,
                            maximumFractionDigits: 2
                        })}</td>
                        <td>{t.ccyCd}</td>
                        <td><PopupView value={t.txnSts} maxLength={10}/></td>
                        <td>
                            <button type="button" disabled={detailBusy} onClick={() => void showDetail(t.txnId)}>View
                            </button>
                        </td>
                    </tr>)}
                    {!page.content.length && <tr>
                        <td colSpan={8}>No matching transactions.</td>
                    </tr>}
                    </tbody>
                </table>
            </div>
            <div className="cbms-inquiry-pagination">
                <button type="button" disabled={busy || page.number === 0}
                        onClick={() => void search(page.number - 1)}>Previous
                </button>
                <span>Page {page.number + 1} of {Math.max(1, page.totalPages)}</span>
                <button type="button" disabled={busy || page.number + 1 >= page.totalPages}
                        onClick={() => void search(page.number + 1)}>Next
                </button>
            </div>
        </section>}
        {detail && <div className="cbms-inquiry-backdrop" onMouseDown={e => {
            if (e.target === e.currentTarget) setDetail(null);
        }}>
            <section role="dialog" aria-modal="true" aria-label="Transaction details" className="cbms-inquiry-dialog">
                <div className="cbms-inquiry-dialog-head"><h2>Transaction Details</h2>
                    <button type="button" aria-label="Close transaction details" onClick={() => setDetail(null)}>×
                    </button>
                </div>
                <dl>
                    {([['Reference', detail.txnRef], ['Type', detail.txnTyp], ['Status', detail.txnSts], ['Date', new Date(detail.crtDt).toLocaleString('en-MY')], ['Amount', `${detail.ccyCd} ${Number(detail.txnAmt).toFixed(2)}`], ['Posted by', detail.crtBy]] as const).map(([k, v]) =>
                        <div key={k}>
                            <dt>{k}</dt>
                            <dd>{v}</dd>
                        </div>)}
                </dl>
                <h3>Ledger entries</h3>
                <div className="cbms-inquiry-scroll">
                    <table>
                        <thead>
                        <tr>
                            <th>GL Code</th>
                            <th>GL Name</th>
                            <th>Side</th>
                            <th>Amount</th>
                        </tr>
                        </thead>
                        <tbody>{detail.ledgerEntries.map(e => <tr key={e.entryId}>
                            <td>{e.glCd}</td>
                            <td>{e.glNm}</td>
                            <td>{e.entrySide}</td>
                            <td>{Number(e.entryAmt).toFixed(2)}</td>
                        </tr>)}</tbody>
                    </table>
                </div>
            </section>
        </div>}
    </main>;
}
