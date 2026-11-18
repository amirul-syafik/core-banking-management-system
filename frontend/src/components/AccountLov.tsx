import {SortableTable} from './SortableTable';
import {CloseIcon, TablePagination} from '../components/UiStandards';
import {CustomerLookupButton, FieldLabel, RequiredFieldError} from './UiStandards';
import {useEffect, useState} from 'react';
import {searchAccounts, type Account} from '../api/accounts';
import {getCustomer} from '../api/customers';
import './AccountLov.css';
import {formatBusinessValue} from '../utils/display';

type Row = { account: Account; customerName: string };
type Props = {
    label: string;
    value: Account | null;
    onChange: (value: Account | null) => void;
    disabled?: boolean;
    required?: boolean;
    invalid?: boolean;
    excludeAccountId?: number;
    /** Allow non-active accounts in read-only inquiry and reconciliation lookups. */
    activeOnly?: boolean
};

export default function AccountLov({
                                       label,
                                       value,
                                       onChange,
                                       disabled,
                                       required = true,
                                       invalid = false,
                                       excludeAccountId,
                                       activeOnly = true
                                   }: Props) {
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState('');
    const [submittedQuery, setSubmittedQuery] = useState('');
    const [page, setPage] = useState(0);
    const [pages, setPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [rows, setRows] = useState<Row[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [requested, setRequested] = useState(0);
    useEffect(() => {
        if (!open) return;
        let active = true;
        Promise.resolve().then(async () => {
            // The account-search API requires at least one filter. Show active
            // accounts by default (as in Deposit, Financing and Credit Lines).
            // When a number is supplied, read-only inquiries may also find
            // dormant or closed accounts by searching without a status filter.
            setLoading(true);
            setError('');
            try {
                const result = await searchAccounts({
                    accountNo: submittedQuery.trim() || undefined,
                    status: activeOnly || !submittedQuery.trim() ? 'ACTIVE' : undefined,
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
                    setTotalElements(result.totalElements);
                }
            } catch (e) {
                if (active) setError(e instanceof Error ? 'Unable to search accounts. Check the account number and try again.' : 'Could not load accounts.');
            } finally {
                if (active) setLoading(false);
            }
        });
        return () => {
            active = false;
        };
    }, [activeOnly, open, page, requested, submittedQuery]);
    useEffect(() => {
        if (!open) return;

        function onEscape(e: KeyboardEvent) {
            if (e.key === 'Escape') setOpen(false);
        }

        window.addEventListener('keydown', onEscape);
        return () => window.removeEventListener('keydown', onEscape);
    }, [open]);

    function search() {
        setPage(0);
        setSubmittedQuery(query.trim());
        setRequested(x => x + 1);
    }

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
        <label><FieldLabel required={required}>{label}</FieldLabel>
            <span className="cbms-lov-input-row"><input readOnly required={required} value={value?.acctNo ?? ''}
                                                        placeholder={`Select ${label.toLowerCase()}`}
                                                        disabled={disabled}
                                                        onKeyDown={e => {
                                                            if (e.key === 'Enter') {
                                                                e.preventDefault();
                                                                launch();
                                                            }
                                                        }} onClick={launch}
                                                        aria-label={`Select ${label}`}/><CustomerLookupButton
                subject="account" onClick={launch} disabled={disabled} aria-label={`Search ${label}`}/></span>
        </label>
        <RequiredFieldError show={invalid && required && !value} message={`Please select ${label.toLowerCase()}.`}/>
        {open && <div className="cbms-lov-backdrop" onMouseDown={e => {
            if (e.target === e.currentTarget) setOpen(false);
        }}>
            <section className="cbms-lov-dialog" role="dialog" aria-modal="true" aria-label={`Search ${label}`}>
                <div className="cbms-lov-heading"><h2>Search {label}</h2>
                    <button type="button" onClick={() => setOpen(false)} aria-label="Close"><CloseIcon/></button>
                </div>
                {/* Use a search row, not a nested form: transaction pages already
                    contain a parent form, and nested submits trigger its Search. */}
                <div className="cbms-lov-search" role="search">
                    <input type="search" autoFocus placeholder="Search account number"
                           aria-label="Search account number"
                           value={query} onChange={e => setQuery(e.target.value)}
                           onKeyDown={e => {
                               if (e.key === 'Enter') {
                                   e.preventDefault();
                                   e.stopPropagation();
                                   search();
                               }
                           }}/>
                    <button className="cbms-action-search" type="button" disabled={loading} onClick={search}>
                        {loading ? 'Searching…' : 'Search'}
                    </button>
                </div>
                {error && <p role="alert" className="cbms-lov-error">{error}</p>}
                <div className="cbms-lov-table">
                    <SortableTable>
                        <thead>
                        <tr>
                            <th>Account Number</th>
                            <th>Customer Name</th>
                            <th>Account Type</th>
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
                            <td>{account.acctNo}</td>
                            <td>{customerName}</td>
                            <td>{formatBusinessValue(account.acctTyp)}</td>
                            <td>{formatBusinessValue(account.acctSts)}</td>
                        </tr>)}
                        </tbody>
                    </SortableTable>
                    {loading && <p>Loading accounts…</p>}{!loading && !error && rows.length === 0 &&
                    <p>No accounts found.</p>}</div>
                <TablePagination page={page} totalPages={Math.max(1, pages)} totalElements={totalElements}
                                 onPageChange={setPage} busy={loading} className="cbms-lov-pagination"/>
            </section>
        </div>}
    </div>;
}
