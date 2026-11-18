import {SortableTable} from '../components/SortableTable';
import {PageHeader, TablePagination, EmptyState, RefreshButton, SubscreenTitle} from '../components/UiStandards';
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

const types = ['DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'REVERSAL', 'FD_PLACEMENT', 'FD_INTEREST_ACCRUAL', 'FD_INTEREST_PAYOUT', 'FD_RENEWAL', 'FD_PREMATURE_WITHDRAWAL', 'FD_MATURITY_SETTLEMENT'];

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
            setError('Select an account using the account lookup.');
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
        <PageHeader
            title="Transaction Inquiry"
            description="Search and review posted transactions. All information is read-only."
            actions={<RefreshButton loading={busy} onClick={() => void search(page?.number ?? 0)}/>}
        />
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
                    {types.map(t => <option key={t} value={t}>{formatBusinessValue(t)}</option>)}</select></label>
                <label>Status<select value={status} onChange={e => setStatus(e.target.value)}>
                    <option value="">All statuses</option>
                    {['POSTED', 'REVERSED'].map(s => <option key={s}
                                                                                                          value={s}>{formatBusinessValue(s)}</option>)}
                </select></label>
                <label>From Date<input type="date" value={from} onChange={e => setFrom(e.target.value)}/></label>
                <label>To Date<input type="date" value={to} onChange={e => setTo(e.target.value)}/></label>
                <div className="cbms-search-actions">
                    <button className="cbms-action-search" type="submit"
                            disabled={busy}>{busy ? 'Searching…' : 'Search'}</button>
                    <button className="cbms-action-clear" type="button" onClick={clear} disabled={busy}>Clear</button>
                </div>
            </form>
            {error && <p role="alert" className="cbms-inquiry-error">{error}</p>}
        </section>
        {page && <section className="cbms-inquiry-card"><h2>Results <small>({page.totalElements} records)</small></h2>
            <div className="cbms-inquiry-scroll">
                <SortableTable>
                    <thead>
                    <tr>
                        <th>Date</th>
                        <th>Transaction Reference</th>
                        <th>Type</th>
                        <th>Direction</th>
                        <th>Amount</th>
                        <th>Currency</th>
                        <th>Status</th>
                        <th>Action</th>
                    </tr>
                    </thead>
                    <tbody>
                    {page.content.map(t => <tr key={t.txnId}>
                        <td>{new Date(t.crtDt).toLocaleString('en-MY')}</td>
                        <td><PopupView value={t.txnRef} maxLength={10}/></td>
                        <td><PopupView value={t.txnTyp} maxLength={10}/></td>
                        <td>{formatBusinessValue(t.direction)}</td>
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
                    {!page.content.length && <EmptyState message="No matching transactions." columns={8}/>}
                    </tbody>
                </SortableTable>
            </div>
            <TablePagination
                page={page.number}
                totalPages={Math.max(1, page.totalPages)}
                onPageChange={p => void search(p)}
                busy={busy}
                totalRecords={page.totalElements}
                className="cbms-inquiry-pagination"
            />
        </section>}
        {detail && <div className="cbms-inquiry-backdrop" onMouseDown={e => {
            if (e.target === e.currentTarget) setDetail(null);
        }}>
            <section role="dialog" aria-modal="true" aria-label="Transaction details" className="cbms-inquiry-dialog">
                <div className="cbms-inquiry-dialog-head"><SubscreenTitle as="h2" title="Transaction Details" reference={detail.txnRef}/>
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
                    <SortableTable>
                        <thead>
                        <tr>
                            <th>General Ledger Code</th>
                            <th>General Ledger Name</th>
                            <th>Side</th>
                            <th>Amount</th>
                        </tr>
                        </thead>
                        <tbody>{detail.ledgerEntries.map(e => <tr key={e.entryId}>
                            <td>{e.glCd}</td>
                            <td>{e.glNm}</td>
                            <td>{formatBusinessValue(e.entrySide)}</td>
                            <td>{Number(e.entryAmt).toFixed(2)}</td>
                        </tr>)}</tbody>
                    </SortableTable>
                </div>
            </section>
        </div>}
    </main>;
}
import {formatBusinessValue} from '../utils/display';
