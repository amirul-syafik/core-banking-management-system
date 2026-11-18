import {SortableTable} from '../components/SortableTable';
import {useCallback, useEffect, useState} from 'react';
import {createPortal} from 'react-dom';
import {api} from '../api/client';
import {CloseIcon, CustomerLookupButton, FieldLabel, TablePagination} from '../components/UiStandards';
import PopupView from '../components/PopupView';
import {explainError, type ApiPage} from './pageUtils';

type Choice = { id: number; reference: string; description: string };
type Response = { content: Choice[]; totalPages: number; totalElements: number };
type Category = 'CUSTOMER' | 'ACCOUNT' | 'VEHICLE' | 'COLLATERAL' | 'FINANCING_APPLICATION' | 'FINANCING_FACILITY';

function category(type: string): Category | null {
    const normalized = type.trim().toUpperCase().replace(/[^A-Z0-9]+/g, '_');
    const aliases: Record<string, Category> = {
        CUSTOMER: 'CUSTOMER', BANK_CUSTOMER: 'CUSTOMER',
        ACCOUNT: 'ACCOUNT', BANK_ACCOUNT: 'ACCOUNT',
        VEHICLE: 'VEHICLE', BANK_VEHICLE: 'VEHICLE',
        COLLATERAL: 'COLLATERAL', BANK_COLLATERAL: 'COLLATERAL',
        FINANCING_APPLICATION: 'FINANCING_APPLICATION', BANK_FINANCING_APPLICATION: 'FINANCING_APPLICATION',
        FINANCING_FACILITY: 'FINANCING_FACILITY', BANK_FINANCING_FACILITY: 'FINANCING_FACILITY'
    };
    return aliases[normalized] || null;
}

async function fetchChoices(type: Category, query: string, page: number): Promise<Response> {
    if (type === 'CUSTOMER') {
        const {data} = await api.get<ApiPage<{ custId: number; cifNo: string; custNm: string }>>('/customers/search',
            {params: {name: query || undefined, page, size: 10}});
        return {
            content: data.content.map(x => ({id: x.custId, reference: x.cifNo, description: x.custNm})),
            totalPages: data.totalPages, totalElements: data.totalElements
        };
    }
    if (type === 'ACCOUNT') {
        const {data} = await api.get<ApiPage<{ acctId: number; acctNo: string; acctTyp: string }>>('/accounts/search',
            {params: {accountNo: query || undefined, page, size: 10}});
        return {
            content: data.content.map(x => ({id: x.acctId, reference: x.acctNo, description: x.acctTyp})),
            totalPages: data.totalPages, totalElements: data.totalElements
        };
    }
    if (type === 'VEHICLE') {
        const {data} = await api.get<ApiPage<{
            vehicleId: number;
            registrationNo: string | null;
            chassisNo: string;
            vehicleMake: string;
            vehicleModel: string
        }>>('/vehicles',
            {params: {registrationNo: query || undefined, page, size: 10}});
        return {
            content: data.content.map(x => ({
                id: x.vehicleId,
                reference: x.registrationNo || x.chassisNo,
                description: `${x.vehicleMake} ${x.vehicleModel}`
            })), totalPages: data.totalPages, totalElements: data.totalElements
        };
    }
    if (type === 'COLLATERAL') {
        const {data} = await api.get<ApiPage<{
            collateralId: number;
            collateralRef: string;
            collateralDesc: string
        }>>('/collaterals',
            {params: {page, size: 10}});
        return {
            content: data.content.map(x => ({
                id: x.collateralId,
                reference: x.collateralRef,
                description: x.collateralDesc
            })), totalPages: data.totalPages, totalElements: data.totalElements
        };
    }
    if (type === 'FINANCING_APPLICATION') {
        const {data} = await api.get<Array<{
            applId: number;
            applRef: string;
            applSts: string
        }>>('/financing/applications');
        const filtered = data.filter(x => !query || x.applRef.toLowerCase().includes(query.toLowerCase()));
        return {
            content: filtered.slice(page * 10, page * 10 + 10).map(x => ({
                id: x.applId,
                reference: x.applRef,
                description: x.applSts
            })), totalPages: Math.ceil(filtered.length / 10), totalElements: filtered.length
        };
    }
    const {data} = await api.get<Array<{ facId: number; facRef: string; facSts: string }>>('/financing/facilities');
    const filtered = data.filter(x => !query || x.facRef.toLowerCase().includes(query.toLowerCase()));
    return {
        content: filtered.slice(page * 10, page * 10 + 10).map(x => ({
            id: x.facId,
            reference: x.facRef,
            description: x.facSts
        })), totalPages: Math.ceil(filtered.length / 10), totalElements: filtered.length
    };
}

/** Shows business references, storing the selected internal key only inside form state. */
export function WorkflowEntityField({entityType, value, entityRef, onChange}: {
    entityType: string; value: string; entityRef: string;
    onChange: (id: string, reference: string) => void
}) {
    const kind = category(entityType);
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState('');
    const [applied, setApplied] = useState('');
    const [page, setPage] = useState(0);
    const [refresh, setRefresh] = useState(0);
    const [data, setData] = useState<Response | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const load = useCallback(async () => {
        if (!kind) return;
        setLoading(true);
        try {
            const response = await fetchChoices(kind, applied, page);
            setData(response);
            setError('');
        } catch (e) {
            setData(null);
            setError(explainError(e));
        } finally {
            setLoading(false);
        }
    }, [kind, applied, page]);
    useEffect(() => {
        if (open) void Promise.resolve().then(load);
    }, [open, load, refresh]);
    useEffect(() => {
        if (!open) return;
        const escape = (event: KeyboardEvent) => {
            if (event.key === 'Escape') setOpen(false);
        };
        window.addEventListener('keydown', escape);
        return () => window.removeEventListener('keydown', escape);
    }, [open]);
    return <div className="gap-customer-field">
        <label><FieldLabel required>Business Reference</FieldLabel></label>
        <div className="gap-inline cbms-lov-input-row"><input readOnly required aria-label="Business Reference"
                                                              placeholder={kind ? 'Select business reference' : 'No lookup configured for this workflow type'}
                                                              value={value ? entityRef : ''} onClick={() => {
            if (kind) {
                setQuery('');
                setApplied('');
                setPage(0);
                setError('');
                setOpen(true);
                setRefresh(x => x + 1);
            }
        }}/>
            <CustomerLookupButton subject="reference" aria-label="Search business references" disabled={!kind}
                                  onClick={() => {
                                      setQuery('');
                                      setApplied('');
                                      setPage(0);
                                      setError('');
                                      setOpen(true);
                                      setRefresh(x => x + 1);
                                  }}/>
        </div>
        {!kind && <span className="gap-muted">This workflow type does not have an approved business-reference lookup. Submission is unavailable.</span>}
        {open && kind && createPortal(<div className="cbms-lov-backdrop" onMouseDown={e => {
            if (e.target === e.currentTarget) setOpen(false);
        }}>
            <section className="cbms-lov-dialog cbms-customer-lov-dialog" role="dialog" aria-modal="true"
                     aria-label="Search Business References">
                <div className="cbms-lov-heading"><h2>Search Business References</h2>
                    <button aria-label="Close reference search" type="button" onClick={() => setOpen(false)}>
                        <CloseIcon/></button>
                </div>
                {kind !== 'COLLATERAL' && <form className="cbms-lov-search" onSubmit={event => {
                    event.preventDefault();
                    setPage(0);
                    setApplied(query.trim());
                    setRefresh(x => x + 1);
                }}><input autoFocus type="search" placeholder="Search reference" value={query}
                          onChange={event => setQuery(event.target.value)}/>
                    <button type="submit" className="cbms-action-search" disabled={loading}>Search</button>
                </form>}
                {error && <p role="alert" className="cbms-lov-error">{error}</p>}
                <div className="cbms-lov-table">
                    <SortableTable>
                        <thead>
                        <tr>
                            <th>Reference</th>
                            <th>Description</th>
                        </tr>
                        </thead>
                        <tbody>
                        {data?.content.map(item => <tr key={item.id} tabIndex={loading ? -1 : 0} role="button"
                                                       className="cbms-lov-selectable-row"
                                                       onClick={() => {
                                                           if (!loading) {
                                                               onChange(String(item.id), item.reference);
                                                               setOpen(false);
                                                           }
                                                       }}
                                                       onKeyDown={e => {
                                                           if (!loading && (e.key === 'Enter' || e.key === ' ')) {
                                                               e.preventDefault();
                                                               onChange(String(item.id), item.reference);
                                                               setOpen(false);
                                                           }
                                                       }}>
                            <td><PopupView value={item.reference} maxLength={24}/></td>
                            <td><PopupView value={item.description} maxLength={32}/></td>
                        </tr>)}
                        {!data?.content.length && <tr>
                            <td colSpan={2}>{loading ? 'Loading references…' : 'No matching references.'}</td>
                        </tr>}
                        </tbody>
                    </SortableTable>
                </div>
                <TablePagination page={page} totalPages={Math.max(1, data?.totalPages ?? 1)}
                                 totalElements={data?.totalElements ?? 0} onPageChange={setPage}
                                 busy={loading} className="cbms-lov-pagination"/>
            </section>
        </div>, document.body)}
    </div>;
}
