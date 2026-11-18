import {SortableTable} from './SortableTable';
import {useEffect, useState, type FormEvent} from 'react';
import {AxiosError} from 'axios';
import {Search} from 'lucide-react';
import {api} from '../api/client';
import {CloseIcon, TablePagination} from './UiStandards';
import './GlAccountLookup.css';
import {formatBusinessValue} from '../utils/display';

export type GlAccountOption = { glCode: string; glName: string; glType: string; currency: string };
type GlPage = { content: GlAccountOption[]; totalElements: number; page: number; totalPages: number };
const errorText = (error: unknown) => error instanceof AxiosError
    ? String(error.response?.data?.detail || error.response?.data?.message || error.message)
    : error instanceof Error ? error.message : 'Unable to load GL accounts';

/** Select only server-filtered active MYR GL accounts; preserve business codes instead of exposing PKs. */
export default function GlAccountLookup({value, onChange, disabled = false, label = 'GL account'}: {
    value: string; onChange: (code: string) => void; disabled?: boolean; label?: string;
}) {
    const [open, setOpen] = useState(false);
    const [selected, setSelected] = useState<GlAccountOption | null>(null);
    const display = selected?.glCode === value ? `${selected.glCode} · ${selected.glName}` : value;
    return <div className="cbms-gl-lookup">
        <div className="cbms-lov-input-row">
            <input readOnly required disabled={disabled} value={display} placeholder="Select a GL account"
                   onClick={() => {
                       if (!disabled) setOpen(true);
                   }}
                   aria-label={label} title={display || 'Select a GL account'}/>
            <button type="button" className="cbms-lookup-trigger" disabled={disabled}
                    onClick={() => setOpen(true)} aria-label={`Search ${label}`} title={`Search ${label}`}>
                <Search size={16} aria-hidden="true"/>
            </button>
        </div>
        {open && <GlAccountLovDialog onClose={() => setOpen(false)} onSelect={item => {
            setSelected(item);
            onChange(item.glCode);
            setOpen(false);
        }}/>}
    </div>;
}

function GlAccountLovDialog({onSelect, onClose}: {
    onSelect: (account: GlAccountOption) => void; onClose: () => void;
}) {
    const [query, setQuery] = useState('');
    const [applied, setApplied] = useState('');
    const [page, setPage] = useState(0);
    const [requestVersion, setRequestVersion] = useState(0);
    const [result, setResult] = useState<GlPage | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    useEffect(() => {
        let active = true;
        void api.get<GlPage>('/ledger-accounts/eligible', {params: {query: applied, page}})
            .then(({data}) => {
                if (active) {
                    setResult(data);
                    setError('');
                    setLoading(false);
                }
            })
            .catch((e: unknown) => {
                if (active) {
                    setResult(null);
                    setError(errorText(e));
                    setLoading(false);
                }
            });
        return () => {
            active = false;
        };
    }, [applied, page, requestVersion]);
    useEffect(() => {
        const handler = (event: KeyboardEvent) => {
            if (event.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', handler);
        return () => window.removeEventListener('keydown', handler);
    }, [onClose]);
    const search = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setLoading(true);
        setPage(0);
        setApplied(query.trim());
        setRequestVersion(i => i + 1);
    };
    const changePage = (next: number) => {
        setLoading(true);
        setPage(next);
    };
    return <div className="cbms-lov-backdrop" onMouseDown={event => {
        if (event.target === event.currentTarget) onClose();
    }}>
        <section className="cbms-lov-dialog cbms-gl-lov-dialog" role="dialog" aria-modal="true"
                 aria-label="Select GL account">
            <div className="cbms-lov-heading"><h2>Select GL Account</h2>
                <button type="button" onClick={onClose} aria-label="Close GL lookup"><CloseIcon/></button>
            </div>
            <p className="cbms-gl-help">Showing configured, active MYR GL accounts only.</p>
            <form className="cbms-lov-search" onSubmit={search}>
                <input autoFocus type="search" aria-label="Search GL code or name" placeholder="Search GL code or name"
                       maxLength={80} value={query} onChange={event => setQuery(event.target.value)}/>
                <button className="cbms-action-search" disabled={loading} type="submit">Search</button>
            </form>
            {error && <p className="cbms-lov-error" role="alert">{error}</p>}
            <div className="cbms-lov-table">
                <SortableTable>
                    <thead>
                    <tr>
                        <th>GL Code</th>
                        <th>GL Account Name</th>
                        <th>Type</th>
                        <th>Currency</th>
                        <th>Action</th>
                    </tr>
                    </thead>
                    <tbody>{result?.content.map(item => <tr key={item.glCode}>
                        <td>{item.glCode}</td>
                        <td>{item.glName}</td>
                        <td>{formatBusinessValue(item.glType)}</td>
                        <td>{item.currency}</td>
                        <td>
                            <button type="button" className="cbms-gl-select" onClick={() => onSelect(item)}>Select
                            </button>
                        </td>
                    </tr>)}
                    {!loading && !result?.content.length && <tr>
                        <td colSpan={5}>No eligible GL accounts match the search.</td>
                    </tr>}
                    {loading && <tr>
                        <td colSpan={5}>Loading GL accounts…</td>
                    </tr>}
                    </tbody>
                </SortableTable>
            </div>
            <TablePagination page={page} totalPages={Math.max(1, result?.totalPages ?? 1)}
                             totalElements={result?.totalElements ?? 0} totalRecords={result?.totalElements}
                             onPageChange={changePage} busy={loading} className="cbms-lov-pagination"/>
        </section>
    </div>;
}
