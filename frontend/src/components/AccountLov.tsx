import {useEffect, useState} from 'react';
import {searchAccounts, type Account} from '../api/accounts';
import {getCustomer} from '../api/customers';
import './AccountLov.css';

type Row = { account: Account; customerName: string };
type Props = {
    label: string;
    value: Account | null;
    onChange: (value: Account | null) => void;
    disabled?: boolean;
    excludeAccountId?: number
};

export default function AccountLov({label, value, onChange, disabled, excludeAccountId}: Props) {
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState('');
    const [submittedQuery, setSubmittedQuery] = useState('');
    const [page, setPage] = useState(0);
    const [pages, setPages] = useState(0);
    const [rows, setRows] = useState<Row[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [requested, setRequested] = useState(0);
    useEffect(() => {
        if (!open) return;
        let active = true;
        Promise.resolve().then(async () => {
            setLoading(true);
            setError('');
            try {
                const result = await searchAccounts({
                    accountNo: submittedQuery || undefined,
                    status: 'ACTIVE',
                    page,
                    size: 10
                });
                const customers = new Map<number, string>();
                const ids = [...new Set(result.content.map(a => a.custId))];
                await Promise.all(ids.map(async id => {
                    try {
                        const c = await getCustomer(id);
                        customers.set(id, c.custNm);
                    } catch {
                        customers.set(id, 'Unavailable');
                    }
                }));
                if (active) {
                    setRows(result.content.map(account => ({
                        account,
                        customerName: customers.get(account.custId) ?? 'Unavailable'
                    })));
                    setPages(result.totalPages);
                }
            } catch (e) {
                if (active) setError(e instanceof Error ? e.message : 'Could not load accounts.');
            } finally {
                if (active) setLoading(false);
            }
        });
        return () => {
            active = false;
        };
    }, [open, page, requested, submittedQuery]);
    useEffect(() => {
        if (!open) return;

        function onEscape(e: KeyboardEvent) {
            if (e.key === 'Escape') setOpen(false);
        }

        window.addEventListener('keydown', onEscape);
        return () => window.removeEventListener('keydown', onEscape);
    }, [open]);

    function launch() {
        if (!disabled) {
            setQuery('');
            setSubmittedQuery('');
            setPage(0);
            setRequested(x => x + 1);
            setOpen(true);
        }
    }

    return <div className="cbms-lov-field">
        <label><span className="cbms-field-label">{label} <span className="cbms-required-star"
                                                                aria-hidden="true">*</span></span>
            <span className="cbms-lov-input-row"><input readOnly required value={value?.acctNo ?? ''}
                                                        placeholder="Press Enter for LOV" disabled={disabled}
                                                        onKeyDown={e => {
                                                            if (e.key === 'Enter') {
                                                                e.preventDefault();
                                                                launch();
                                                            }
                                                        }} onClick={launch}
                                                        aria-label={`${label}. Press Enter for LOV`}/><button
                type="button" onClick={launch} disabled={disabled} aria-label={`Select ${label}`}>⌕</button></span>
        </label>
        {open && <div className="cbms-lov-backdrop" onMouseDown={e => {
            if (e.target === e.currentTarget) setOpen(false);
        }}>
            <section className="cbms-lov-dialog" role="dialog" aria-modal="true" aria-label={`${label} LOV`}>
                <div className="cbms-lov-heading"><h2>{label} LOV</h2>
                    <button type="button" onClick={() => setOpen(false)} aria-label="Close">×</button>
                </div>
                <form className="cbms-lov-search" onSubmit={e => {
                    e.preventDefault();
                    setPage(0);
                    setSubmittedQuery(query.trim());
                    setRequested(x => x + 1);
                }}><input placeholder="Search account number" value={query} onChange={e => setQuery(e.target.value)}/>
                    <button type="submit">Search</button>
                </form>
                {error && <p role="alert" className="cbms-lov-error">{error}</p>}
                <div className="cbms-lov-table">
                    <table>
                        <thead>
                        <tr>
                            <th>Customer Name</th>
                            <th>Account No.</th>
                            <th>Type</th>
                            <th>Status</th>
                        </tr>
                        </thead>
                        <tbody>
                        {rows.filter(r => r.account.acctId !== excludeAccountId).map(({account, customerName}) => <tr
                            key={account.acctId} className="cbms-lov-selectable-row" tabIndex={loading ? -1 : 0}
                            role="button" aria-label={`Select account ${account.acctNo} for ${customerName}`}
                            onClick={() => {
                                if (!loading) {
                                    onChange(account);
                                    setOpen(false);
                                }
                            }} onKeyDown={e => {
                            if (!loading && (e.key === "Enter" || e.key === " ")) {
                                e.preventDefault();
                                onChange(account);
                                setOpen(false);
                            }
                        }}>
                            <td>{customerName}</td>
                            <td>{account.acctNo}</td>
                            <td>{account.acctTyp}</td>
                            <td>{account.acctSts}</td>
                        </tr>)}
                        </tbody>
                    </table>
                    {loading && <p>Loading accounts…</p>}{!loading && !error && rows.length === 0 &&
                    <p>No accounts found.</p>}</div>
                <div className="cbms-lov-pagination">
                    <button type="button" disabled={loading || page === 0}
                            onClick={() => setPage(p => p - 1)}>Previous
                    </button>
                    <span>Page {page + 1} of {Math.max(1, pages)}</span>
                    <button type="button" disabled={loading || page + 1 >= pages}
                            onClick={() => setPage(p => p + 1)}>Next
                    </button>
                </div>
            </section>
        </div>}
    </div>;
}
