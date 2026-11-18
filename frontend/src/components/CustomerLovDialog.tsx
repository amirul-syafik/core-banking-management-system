import {SortableTable} from './SortableTable';
import {useEffect} from 'react';
import type {Customer} from '../api/customers';
import {CloseIcon, StatusBadge, TablePagination} from './UiStandards';
import './AccountLov.css';

type Props = {
    query: string;
    onQueryChange: (query: string) => void;
    onSearch: () => void;
    customers: Customer[];
    page: number;
    totalPages: number;
    totalElements?: number;
    loading?: boolean;
    error?: string;
    onPageChange: (page: number) => void;
    onSelect: (customer: Customer) => void;
    onClose: () => void;
};

/** Shared active-customer selection UI. The parent retains its search and selection logic. */
export default function CustomerLovDialog({
                                              query, onQueryChange, onSearch, customers, page, totalPages, totalElements = 0,
                                              loading = false, error, onPageChange, onSelect, onClose
                                          }: Props) {
    useEffect(() => {
        function closeOnEscape(event: KeyboardEvent) {
            if (event.key === 'Escape') onClose();
        }

        window.addEventListener('keydown', closeOnEscape);
        return () => window.removeEventListener('keydown', closeOnEscape);
    }, [onClose]);
    return <div className="cbms-lov-backdrop" onMouseDown={event => {
        if (event.target === event.currentTarget) onClose();
    }}>
        <section className="cbms-lov-dialog cbms-customer-lov-dialog" role="dialog" aria-modal="true"
                 aria-label="Search Customers">
            <div className="cbms-lov-heading"><h2>Search Customers</h2>
                <button type="button" aria-label="Close customer search" onClick={onClose}><CloseIcon/></button>
            </div>
            <form className="cbms-lov-search" onSubmit={event => {
                event.preventDefault();
                onSearch();
            }}>
                <input autoFocus type="search" placeholder="Search customer name" aria-label="Search customer name"
                       value={query} onChange={event => onQueryChange(event.target.value)}/>
                <button type="submit" className="cbms-action-search"
                        disabled={loading}>{loading ? 'Searching…' : 'Search'}</button>
            </form>
            {error && <p className="cbms-lov-error" role="alert">{error}</p>}
            <div className="cbms-lov-table">
                <SortableTable>
                    <thead>
                    <tr>
                        <th>Customer Name</th>
                        <th>Customer Identification Number</th>
                        <th>Status</th>
                    </tr>
                    </thead>
                    <tbody>
                    {customers.map(customer => <tr key={customer.custId} tabIndex={loading ? -1 : 0}
                                                   className="cbms-lov-selectable-row" role="button"
                                                   aria-label={`Select customer ${customer.custNm}`}
                                                   onClick={() => {
                                                       if (!loading) onSelect(customer);
                                                   }}
                                                   onKeyDown={event => {
                                                       if (!loading && (event.key === 'Enter' || event.key === ' ')) {
                                                           event.preventDefault();
                                                           onSelect(customer);
                                                       }
                                                   }}>
                        <td>{customer.custNm}</td>
                        <td>{customer.cifNo}</td>
                        <td><StatusBadge status={customer.sts}/></td>
                    </tr>)}
                    {!customers.length && <tr>
                        <td colSpan={3}>{loading ? 'Loading customers…' : 'No active customers found.'}</td>
                    </tr>}
                    </tbody>
                </SortableTable>
            </div>
            <TablePagination page={page} totalPages={Math.max(1, totalPages)} totalElements={totalElements}
                             onPageChange={onPageChange} busy={loading} className="cbms-lov-pagination"/>
        </section>
    </div>;
}
