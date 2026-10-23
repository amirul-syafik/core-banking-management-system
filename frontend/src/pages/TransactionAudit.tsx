import {useRef, useState} from 'react';
import {AxiosError} from 'axios';
import AccountLov from '../components/AccountLov';
import PopupView from '../components/PopupView';
import type {Account} from '../api/accounts';
import {searchTransactionAudit, type TransactionAuditEvent, type TransactionAuditPage} from '../api/transactionAudit';
import './TransactionInquiry.css';

function errorText(error: unknown): string {
    if (error instanceof AxiosError) {
        const data: unknown = error.response?.data;
        if (data && typeof data === 'object' && 'detail' in data && typeof data.detail === 'string') return data.detail;
        return `Request failed (${error.response?.status ?? 'network'}).`;
    }
    return error instanceof Error ? error.message : 'Request failed.';
}

export default function TransactionAudit() {
    const [account, setAccount] = useState<Account | null>(null);
    const [reference, setReference] = useState('');
    const [action, setAction] = useState('');
    const [actor, setActor] = useState('');
    const [from, setFrom] = useState('');
    const [to, setTo] = useState('');
    const [result, setResult] = useState<TransactionAuditPage | null>(null);
    const [detail, setDetail] = useState<TransactionAuditEvent | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const requestSequence = useRef(0);

    async function search(page = 0) {
        const sequence = ++requestSequence.current;
        if (from && to && from > to) {
            setResult(null);
            setError('From date must not exceed To date.');
            return;
        }
        setBusy(true);
        setError('');
        try {
            const response = await searchTransactionAudit({
                accountId: account?.acctId,
                reference: reference.trim() || undefined,
                action: action || undefined,
                actor: actor.trim() || undefined,
                from: from || undefined,
                to: to || undefined,
                page, size: 10
            });
            if (sequence === requestSequence.current) setResult(response);
        } catch (e) {
            if (sequence === requestSequence.current) {
                setResult(null);
                setError(errorText(e));
            }
        } finally {
            if (sequence === requestSequence.current) setBusy(false);
        }
    }

    function clear() {
        ++requestSequence.current;
        setBusy(false);
        setAccount(null);
        setReference('');
        setAction('');
        setActor('');
        setFrom('');
        setTo('');
        setResult(null);
        setDetail(null);
        setError('');
    }

    return <main className="cbms-inquiry">
        <header><h1>Transaction Audit</h1><p>Review transaction posting and reversal events from recorded banking
            activity. Read-only.</p></header>
        <section className="cbms-inquiry-card"><h2>Search audit history</h2>
            <form className="cbms-inquiry-filters" onSubmit={e => {
                e.preventDefault();
                void search();
            }}>
                <AccountLov label="Account" value={account} onChange={setAccount} disabled={busy}/>
                <label>Transaction Reference<input value={reference} onChange={e => setReference(e.target.value)}
                                                   placeholder="Search reference"/></label>
                <label>Action<select value={action} onChange={e => setAction(e.target.value)}>
                    <option value="">All actions</option>
                    {['POSTED', 'REQUESTED', 'APPROVED', 'REJECTED', 'REVERSED'].map(value => <option key={value}
                                                                                                      value={value}>{value}</option>)}
                </select></label>
                <label>User<input value={actor} onChange={e => setActor(e.target.value)} placeholder="Search username"/></label>
                <label>From Date<input type="date" value={from} onChange={e => setFrom(e.target.value)}/></label>
                <label>To Date<input type="date" value={to} onChange={e => setTo(e.target.value)}/></label>
                <div className="cbms-inquiry-actions">
                    <button type="button" onClick={clear}>Clear</button>
                    <button type="submit" disabled={busy}>{busy ? 'Searching…' : 'Search'}</button>
                </div>
            </form>
            {error && <p role="alert" className="cbms-inquiry-error">{error}</p>}
        </section>
        {result &&
            <section className="cbms-inquiry-card"><h2>Audit History <small>({result.totalElements} records)</small>
            </h2>
                <div className="cbms-inquiry-scroll">
                    <table>
                        <thead>
                        <tr>
                            <th>Date / Time</th>
                            <th>Transaction Reference</th>
                            <th>Action</th>
                            <th>User</th>
                            <th>Description</th>
                            <th>Details</th>
                        </tr>
                        </thead>
                        <tbody>
                        {result.content.map(event => <tr key={event.eventKey}>
                            <td>{new Date(event.eventAt).toLocaleString('en-MY')}</td>
                            <td><PopupView value={event.reference} maxLength={18}/></td>
                            <td>{event.action}</td>
                            <td><PopupView value={event.actor} maxLength={18}/></td>
                            <td><PopupView value={event.description} maxLength={42}/></td>
                            <td>
                                <button type="button" onClick={() => setDetail(event)}>View</button>
                            </td>
                        </tr>)}
                        {!result.content.length && <tr>
                            <td colSpan={6}>No matching audit records.</td>
                        </tr>}
                        </tbody>
                    </table>
                </div>
                <div className="cbms-inquiry-pagination">
                    <button type="button" disabled={busy || result.number === 0}
                            onClick={() => void search(result.number - 1)}>Previous
                    </button>
                    <span>Page {result.number + 1} of {Math.max(1, result.totalPages)}</span>
                    <button type="button" disabled={busy || result.number + 1 >= result.totalPages}
                            onClick={() => void search(result.number + 1)}>Next
                    </button>
                </div>
            </section>}
        {detail && <div className="cbms-inquiry-backdrop" onMouseDown={e => {
            if (e.target === e.currentTarget) setDetail(null);
        }}>
            <section role="dialog" aria-modal="true" aria-label="Audit event details" className="cbms-inquiry-dialog">
                <div className="cbms-inquiry-dialog-head"><h2>Audit Event Details</h2>
                    <button type="button" aria-label="Close audit event details" onClick={() => setDetail(null)}>×
                    </button>
                </div>
                <dl>{([
                    ['Date / Time', new Date(detail.eventAt).toLocaleString('en-MY')],
                    ['Transaction Reference', detail.reference],
                    ['Action', detail.action],
                    ['User', detail.actor],
                    ['Description', detail.description],
                    ['Related Reversal Reference', detail.relatedReference ?? '—']
                ] as const).map(([label, value]) => <div key={label}>
                    <dt>{label}</dt>
                    <dd>{value}</dd>
                </div>)}</dl>
            </section>
        </div>}
    </main>;
}
