import {useRef, useState} from 'react';
import {AxiosError} from 'axios';
import {
    getReconciliationReport,
    getReconciliationInvestigation,
    downloadReconciliationCsv,
    type ReportPage,
    type ReportRow,
    type InvestigationPage,
    getTransactionExceptions,
    downloadTransactionExceptions,
    type ExceptionPage
} from '../api/reconciliation';
import './TransactionInquiry.css';

function errorText(error: unknown): string {
    if (error instanceof AxiosError) return `Request failed (${error.response?.status ?? 'network'}).`;
    return error instanceof Error ? error.message : 'Request failed.';
}

function money(value: number | null, currency: string) {
    return value == null ? '—' : `${currency} ${Number(value).toLocaleString('en-MY', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    })}`;
}

export default function TransactionReconciliation() {
    const [accountNo, setAccountNo] = useState('');
    const [status, setStatus] = useState('ALL');
    const [result, setResult] = useState<ReportPage | null>(null);
    const [busy, setBusy] = useState(false);
    const [exporting, setExporting] = useState(false);
    const [error, setError] = useState('');
    const [selected, setSelected] = useState<ReportRow | null>(null);
    const [investigation, setInvestigation] = useState<InvestigationPage | null>(null);
    const [detailBusy, setDetailBusy] = useState(false);
    const [detailError, setDetailError] = useState('');
    const [exceptionType, setExceptionType] = useState('ALL');
    const [exceptionResult, setExceptionResult] = useState<ExceptionPage | null>(null);
    const [exceptionBusy, setExceptionBusy] = useState(false);
    const [exceptionExporting, setExceptionExporting] = useState(false);
    const [exceptionError, setExceptionError] = useState('');
    const exceptionSeq = useRef(0);
    const searchSeq = useRef(0);
    const detailSeq = useRef(0);

    async function search(page = 0) {
        const seq = ++searchSeq.current;
        setBusy(true);
        setError('');
        try {
            const data = await getReconciliationReport(accountNo, status, page);
            if (seq === searchSeq.current) setResult(data);
        } catch (e) {
            if (seq === searchSeq.current) {
                setResult(null);
                setError(errorText(e));
            }
        } finally {
            if (seq === searchSeq.current) setBusy(false);
        }
    }

    function clear() {
        ++searchSeq.current;
        ++detailSeq.current;
        setAccountNo('');
        setStatus('ALL');
        setResult(null);
        setBusy(false);
        setError('');
        setSelected(null);
        setInvestigation(null);
        setDetailBusy(false);
        setDetailError('');
    }

    async function inspect(row: ReportRow, page = 0) {
        const seq = ++detailSeq.current;
        setSelected(row);
        setInvestigation(null);
        setDetailBusy(true);
        setDetailError('');
        try {
            const data = await getReconciliationInvestigation(row.acctId, page);
            if (seq === detailSeq.current) setInvestigation(data);
        } catch (e) {
            if (seq === detailSeq.current) setDetailError(errorText(e));
        } finally {
            if (seq === detailSeq.current) setDetailBusy(false);
        }
    }

    function closeDetails() {
        ++detailSeq.current;
        setSelected(null);
        setInvestigation(null);
        setDetailError('');
        setDetailBusy(false);
    }

    async function exportCsv() {
        setExporting(true);
        setError('');
        try {
            const blob = await downloadReconciliationCsv(accountNo, status);
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = 'transaction-reconciliation.csv';
            document.body.appendChild(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(url);
        } catch (e) {
            setError(errorText(e));
        } finally {
            setExporting(false);
        }
    }

    async function searchExceptions(page = 0, type = exceptionType) {
        const seq = ++exceptionSeq.current;
        setExceptionBusy(true);
        setExceptionError('');
        try {
            const data = await getTransactionExceptions(type, page);
            if (seq === exceptionSeq.current) setExceptionResult(data);
        } catch (e) {
            if (seq === exceptionSeq.current) {
                setExceptionResult(null);
                setExceptionError(errorText(e));
            }
        } finally {
            if (seq === exceptionSeq.current) setExceptionBusy(false);
        }
    }

    function clearExceptions() {
        ++exceptionSeq.current;
        setExceptionType('ALL');
        setExceptionResult(null);
        setExceptionBusy(false);
        setExceptionError('');
    }

    async function exportExceptions() {
        setExceptionExporting(true);
        setExceptionError('');
        try {
            const blob = await downloadTransactionExceptions(exceptionType);
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = 'transaction-exceptions.csv';
            document.body.appendChild(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(url);
        } catch (e) {
            setExceptionError(errorText(e));
        } finally {
            setExceptionExporting(false);
        }
    }

    return <main className="cbms-inquiry">
        <header><h1>Transaction Reconciliation</h1><p>ADMIN-only · Read-only balance verification and investigation.</p>
        </header>
        <section className="cbms-inquiry-card"><h2>Search accounts</h2>
            <form className="cbms-inquiry-filters" onSubmit={e => {
                e.preventDefault();
                void search(0);
            }}>
                <label>Account Number<input value={accountNo} maxLength={30}
                                            onChange={e => setAccountNo(e.target.value)}
                                            placeholder="All accounts or part of account number"/></label>
                <label>Reconciliation Status<select value={status} onChange={e => setStatus(e.target.value)}>
                    <option value="ALL">All</option>
                    <option value="MATCHED">Matched</option>
                    <option value="DISCREPANCY">Discrepancy</option>
                    <option value="UNAVAILABLE">Unavailable balance</option>
                </select></label>
                <div className="cbms-inquiry-actions">
                    <button type="button" onClick={clear}>Clear</button>
                    <button type="button" disabled={busy || exporting}
                            onClick={() => void exportCsv()}>{exporting ? 'Exporting…' : 'Export CSV'}</button>
                    <button type="submit" disabled={busy}>{busy ? 'Checking…' : 'Reconcile'}</button>
                </div>
            </form>
            {error && <p role="alert" className="cbms-inquiry-error">{error}</p>}
        </section>
        {result && <section className="cbms-inquiry-card">
            <h2>Reconciliation Results <small>({result.totalElements} matching accounts)</small></h2>
            <p>All matching accounts: {result.summary.total} checked · {result.summary.matched} matched
                · {result.summary.discrepancies} discrepancies · {result.summary.unavailable} unavailable.</p>
            <p>Live calculation, not a stored reconciliation run. Opening balances and unsupported posting types must be
                verified before interpreting differences as errors.</p>
            <div className="cbms-inquiry-scroll">
                <table>
                    <thead>
                    <tr>
                        <th>Account Number</th>
                        <th>Currency</th>
                        <th>Stored Balance</th>
                        <th>Ledger Balance</th>
                        <th>Difference</th>
                        <th>Status</th>
                        <th>Action</th>
                    </tr>
                    </thead>
                    <tbody>
                    {result.content.map(row => <tr key={row.acctId}>
                        <td>{row.acctNo}</td>
                        <td>{row.ccyCd}</td>
                        <td>{money(row.storedBalance, row.ccyCd)}</td>
                        <td>{money(row.calculatedBalance, row.ccyCd)}</td>
                        <td>{money(row.difference, row.ccyCd)}</td>
                        <td>{row.status}</td>
                        <td>
                            <button type="button" className="cbms-popup-view-trigger"
                                    onClick={() => void inspect(row)}>Investigate
                            </button>
                        </td>
                    </tr>)}
                    {result.content.length === 0 && <tr>
                        <td colSpan={7}>No matching accounts.</td>
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
        <section className="cbms-inquiry-card" style={{marginTop: 20}}>
            <h2>Transaction Exception Monitoring</h2>
            <p>Read-only indicators for investigation. A finding is not proof of an incorrect financial balance.</p>
            <form className="cbms-inquiry-filters" onSubmit={e => {
                e.preventDefault();
                void searchExceptions(0);
            }}>
                <label>Exception Type<select value={exceptionType} onChange={e => {
                    setExceptionType(e.target.value);
                    ++exceptionSeq.current;
                    setExceptionResult(null);
                    setExceptionError('');
                }}>
                    <option value="ALL">All</option>
                    <option value="DUPLICATE_IDEMPOTENCY_KEY">Duplicate Idempotency Key</option>
                    <option value="ORPHAN_REVERSAL">Orphan Reversal</option>
                    <option value="INVALID_REVERSAL_LINK">Missing Reversal Reference</option>
                    <option value="MULTIPLE_POSTED_REVERSALS">Multiple Posted Reversals</option>
                    <option value="NONPOSITIVE_LEDGER_AMOUNT">Nonpositive Ledger Amount</option>
                </select></label>
                <div className="cbms-inquiry-actions">
                    <button type="button" onClick={clearExceptions}>Clear</button>
                    <button type="button" disabled={exceptionBusy || exceptionExporting}
                            onClick={() => void exportExceptions()}>{exceptionExporting ? 'Exporting…' : 'Export CSV'}</button>
                    <button type="submit"
                            disabled={exceptionBusy}>{exceptionBusy ? 'Checking…' : 'Check Exceptions'}</button>
                </div>
            </form>
            {exceptionError && <p role="alert" className="cbms-inquiry-error">{exceptionError}</p>}
            {exceptionResult && <>
                <h3>Exception Results ({exceptionResult.totalElements})</h3>
                <div className="cbms-inquiry-scroll">
                    <table>
                        <thead>
                        <tr>
                            <th>Type</th>
                            <th>Severity</th>
                            <th>Transaction ID</th>
                            <th>Account ID</th>
                            <th>Description</th>
                            <th>Detail</th>
                        </tr>
                        </thead>
                        <tbody>
                        {exceptionResult.content.map((entry, i) => <tr key={`${entry.type}-${entry.txnId}-${i}`}>
                            <td>{entry.type.replaceAll('_', ' ')}</td>
                            <td>{entry.severity}</td>
                            <td>{entry.txnId}</td>
                            <td>{entry.acctId ?? '—'}</td>
                            <td>{entry.description}</td>
                            <td>{entry.detail}</td>
                        </tr>)}
                        {exceptionResult.content.length === 0 && <tr>
                            <td colSpan={6}>No exceptions found for this filter.</td>
                        </tr>}
                        </tbody>
                    </table>
                </div>
                <div className="cbms-inquiry-pagination">
                    <button type="button" disabled={exceptionBusy || exceptionResult.number === 0}
                            onClick={() => void searchExceptions(exceptionResult.number - 1)}>Previous
                    </button>
                    <span>Page {exceptionResult.number + 1} of {Math.max(1, exceptionResult.totalPages)}</span>
                    <button type="button"
                            disabled={exceptionBusy || exceptionResult.number + 1 >= exceptionResult.totalPages}
                            onClick={() => void searchExceptions(exceptionResult.number + 1)}>Next
                    </button>
                </div>
            </>}
        </section>
        {selected && <div className="cbms-inquiry-backdrop" onMouseDown={e => {
            if (e.target === e.currentTarget) closeDetails();
        }}>
            <section className="cbms-inquiry-dialog" role="dialog" aria-modal="true"
                     aria-label="Reconciliation investigation">
                <div className="cbms-inquiry-dialog-head"><h2>Account Investigation — {selected.acctNo}</h2>
                    <button type="button" aria-label="Close" onClick={closeDetails}>×</button>
                </div>
                <p>Stored: {money(selected.storedBalance, selected.ccyCd)} ·
                    Calculated: {money(selected.calculatedBalance, selected.ccyCd)} ·
                    Difference: {money(selected.difference, selected.ccyCd)}</p>
                <p>Posted ledger entries on account GL 200100. Reversals are identified by their original transaction
                    ID.</p>
                {detailBusy && <p>Loading ledger entries…</p>}
                {detailError && <p role="alert" className="cbms-inquiry-error">{detailError}</p>}
                {investigation && <>
                    <div className="cbms-inquiry-scroll">
                        <table>
                            <thead>
                            <tr>
                                <th>Date/Time</th>
                                <th>Reference</th>
                                <th>Type</th>
                                <th>Side</th>
                                <th>Amount</th>
                                <th>Impact</th>
                                <th>Reversal Of</th>
                            </tr>
                            </thead>
                            <tbody>
                            {investigation.entries.map(entry => <tr key={entry.entryId}>
                                <td>{new Date(entry.dateTime).toLocaleString('en-MY')}</td>
                                <td>{entry.reference}</td>
                                <td>{entry.type}</td>
                                <td>{entry.side}</td>
                                <td>{money(entry.amount, selected.ccyCd)}</td>
                                <td>{money(entry.signedAmount, selected.ccyCd)}</td>
                                <td>{entry.reversalOfTxnId ?? '—'}</td>
                            </tr>)}
                            {investigation.entries.length === 0 && <tr>
                                <td colSpan={7}>No posted ledger entries.</td>
                            </tr>}
                            </tbody>
                        </table>
                    </div>
                    <div className="cbms-inquiry-pagination">
                        <button type="button" disabled={detailBusy || investigation.number === 0}
                                onClick={() => void inspect(selected, investigation.number - 1)}>Previous
                        </button>
                        <span>Page {investigation.number + 1} of {Math.max(1, investigation.totalPages)}</span>
                        <button type="button"
                                disabled={detailBusy || investigation.number + 1 >= investigation.totalPages}
                                onClick={() => void inspect(selected, investigation.number + 1)}>Next
                        </button>
                    </div>
                </>}
            </section>
        </div>}
    </main>;
}
