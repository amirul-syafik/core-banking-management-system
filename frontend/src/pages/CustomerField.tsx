import {useCallback, useEffect, useState} from 'react';
import {createPortal} from 'react-dom';
import {CustomerLookupButton, FieldLabel} from '../components/UiStandards';
import CustomerLovDialog from '../components/CustomerLovDialog';
import {getCustomer, searchCustomers, type Customer} from '../api/customers';
import {explainError} from './pageUtils';

export function CustomerField({value, onChange, label = 'Customer', required = true}: {
    value: string; onChange: (value: string) => void; label?: string; required?: boolean
}) {
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState('');
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [rows, setRows] = useState<Customer[]>([]);
    const [selected, setSelected] = useState<Customer | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [unavailableId, setUnavailableId] = useState<number | null>(null);
    useEffect(() => {
        if (!value) return;
        if (selected?.custId === Number(value)) return;
        let active = true;
        void getCustomer(Number(value)).then(customer => {
            if (active) {
                setSelected(customer);
                setUnavailableId(null);
            }
        }).catch(() => {
            if (active) {
                setSelected(null);
                setUnavailableId(Number(value));
            }
        });
        return () => {
            active = false;
        };
    }, [value, selected?.custId]);
    const lookup = useCallback(async (nextPage: number, searchName = query) => {
        setBusy(true);
        try {
            const data = await searchCustomers({name: searchName.trim(), status: 'ACTIVE', page: nextPage, size: 10});
            setRows(data.content);
            setPage(nextPage);
            setTotalPages(data.totalPages);
            setTotalElements(data.totalElements);
            setError('');
        } catch (err) {
            setError(explainError(err));
            setRows([]);
        } finally {
            setBusy(false);
        }
    }, [query]);
    const display = value ? (selected?.custId === Number(value)
        ? `${selected.cifNo} · ${selected.custNm}` : unavailableId === Number(value) ? 'Customer unavailable' : 'Loading customer…') : '';
    return <>
        <div className="gap-customer-field"><label><FieldLabel required={required}>{label}</FieldLabel></label>
            <div className="gap-inline cbms-lov-input-row">
                <input type="text" required={required} readOnly value={display}
                       placeholder="Select an active customer" aria-label={label} title={display} onClick={() => {
                    setOpen(true);
                    setQuery('');
                    setPage(0);
                    setTotalPages(0);
                    setRows([]);
                    setError('');
                    void lookup(0, '');
                }}/>
                <CustomerLookupButton aria-label={`Search ${label.toLowerCase()}`} onClick={() => {
                    setOpen(true);
                    setQuery('');
                    setRows([]);
                    setPage(0);
                    setTotalPages(0);
                    setError('');
                    void lookup(0, '');
                }}/>
            </div>
        </div>
        {open && createPortal(<CustomerLovDialog query={query} onQueryChange={setQuery} onSearch={() => void lookup(0)}
                                                 customers={rows} page={page} totalPages={totalPages} totalElements={totalElements} loading={busy}
                                                 error={error}
                                                 onPageChange={next => void lookup(next)} onSelect={customer => {
            setSelected(customer);
            setUnavailableId(null);
            onChange(String(customer.custId));
            setOpen(false);
        }} onClose={() => setOpen(false)}/>, document.body)}
    </>;
}
