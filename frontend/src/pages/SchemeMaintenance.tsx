import {SortableTable, type TableSortState} from '../components/SortableTable';
import {sortTableRecords} from '../utils/tableSorting';
import {
    ConfirmDialog,
    RecordCount,
    CloseIcon,
    AuditHistoryRecord,
    StatusBadge,
    FieldLabel,
    PageHeader,
    CreateButton,
    RefreshButton,
    TablePagination,
    SubscreenTitle
} from '../components/UiStandards';
import IslamicContractDialog from '../components/IslamicContractDialog';
import {useCallback, useEffect, useRef, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import PopupView from '../components/PopupView';
import {api} from '../api/client';
import {useAuth} from '../auth/useAuth';
import {useToast} from '../components/toastContext';
import './maintenance.css';

const errorText = (e: unknown) => {
    if (typeof e === 'object' && e && 'response' in e) {
        const r = e as { response?: { data?: { message?: string; detail?: string } } };
        return r.response?.data?.message || r.response?.data?.detail || 'Request failed'
    }
    return e instanceof Error ? e.message : 'Request failed'
};
type Product = { prodId: number; prodCd: string; prodNm: string; prodSts: string; bankingTyp: string; prodCat: string };
type Scheme = {
    schmId: number;
    prodId: number;
    schmCd: string;
    schmNm: string;
    schmDesc: string | null;
    minAmt: number | null;
    maxAmt: number | null;
    minTenor: number | null;
    maxTenor: number | null;
    eligibilityDesc: string | null;
    schmSts: string
};
const blank = {
    prodId: '',
    schmCd: '',
    schmNm: '',
    schmDesc: '',
    minAmt: '',
    maxAmt: '',
    minTenor: '',
    maxTenor: '',
    eligibilityDesc: ''
};
export default function SchemeMaintenance() {
    const notify = useToast();
    const saveInFlight = useRef(false);
    const [searchParams, setSearchParams] = useSearchParams();
    const incomingSchemeId = Number(searchParams.get('schemeId'));
    const incomingProfileId = Number(searchParams.get('profileId'));
    const {session} = useAuth();
    const canEdit = session?.roles?.some(r => r === 'MAKER' || r === 'ADMIN') ?? false;
    const [islamicScheme, setIslamicScheme] = useState<Scheme | null>(null);
    const [rows, setRows] = useState<Scheme[]>([]), [products, setProducts] = useState<Product[]>([]), [query, setQuery] = useState(''), [filter, setFilter] = useState(''), [page, setPage] = useState(0), [tableSort, setTableSort] = useState<TableSortState>(null),
        [appliedQuery, setAppliedQuery] = useState(''), [appliedFilter, setAppliedFilter] = useState(''), [selected, setSelected] = useState<Scheme | null>(null), [form, setForm] = useState(blank), [pendingToggle, setPendingToggle] = useState<Scheme | null>(null), [modal, setModal] = useState(false), [confirmSave, setConfirmSave] = useState(false), [saving, setSaving] = useState(false), [loading, setLoading] = useState(false), [audit, setAudit] = useState<unknown[] | null>(null);
    const load = useCallback(async () => {
        setLoading(true);
        try {
            const [s, p] = await Promise.all([api.get<Scheme[]>('/schemes'), api.get<Product[]>('/products')]);
            setRows(s.data);
            setProducts(p.data)
        } catch (e) {
            notify('error', errorText(e))
        } finally {
            setLoading(false);
        }
    }, [notify]);
    useEffect(() => {
        void Promise.resolve().then(load)
    }, [load]);
    // Derive the notification deep link from loaded data; avoid state updates in an effect.
    const deepLinkedScheme = Number.isSafeInteger(incomingSchemeId) && incomingSchemeId > 0
        ? rows.find(row => row.schmId === incomingSchemeId) ?? null : null;
    const activeIslamicScheme = islamicScheme || (deepLinkedScheme &&
    products.find(product => product.prodId === deepLinkedScheme.prodId)?.bankingTyp === 'ISLAMIC'
        ? deepLinkedScheme : null);
    const closeIslamicContract = () => {
        setIslamicScheme(null);
        if (searchParams.has('schemeId') || searchParams.has('profileId')) {
            setSearchParams(previous => {
                const next = new URLSearchParams(previous);
                next.delete('schemeId');
                next.delete('profileId');
                return next;
            }, {replace: true});
        }
    };
    const edit = (r: Scheme | null) => {
        setConfirmSave(false);
        setSelected(r);
        setForm(r ? {
            prodId: String(r.prodId),
            schmCd: r.schmCd,
            schmNm: r.schmNm,
            schmDesc: r.schmDesc || '',
            minAmt: r.minAmt == null ? '' : String(r.minAmt),
            maxAmt: r.maxAmt == null ? '' : String(r.maxAmt),
            minTenor: r.minTenor == null ? '' : String(r.minTenor),
            maxTenor: r.maxTenor == null ? '' : String(r.maxTenor),
            eligibilityDesc: r.eligibilityDesc || ''
        } : blank);
        setModal(true)
    };
    const nullableNumber = (value: string | number | null) => value === '' || value == null ? null : Number(value);
    const hasPendingChanges = !selected ||
        form.schmNm.trim() !== selected.schmNm.trim() ||
        form.schmDesc.trim() !== (selected.schmDesc ?? '').trim() ||
        nullableNumber(form.minAmt) !== nullableNumber(selected.minAmt) ||
        nullableNumber(form.maxAmt) !== nullableNumber(selected.maxAmt) ||
        nullableNumber(form.minTenor) !== nullableNumber(selected.minTenor) ||
        nullableNumber(form.maxTenor) !== nullableNumber(selected.maxTenor) ||
        form.eligibilityDesc.trim() !== (selected.eligibilityDesc ?? '').trim();
    const closeModal = () => {
        if (saving) return;
        setConfirmSave(false);
        setModal(false);
        setSelected(null);
        setForm(blank);
    };
    const save = async () => {
        if (!canEdit || saving || saveInFlight.current || !confirmSave || !hasPendingChanges) return;
        if (form.minAmt && form.maxAmt && Number(form.minAmt) > Number(form.maxAmt)) {
            notify('warning', 'Minimum amount exceeds maximum.');
            return
        }
        if (form.minTenor && form.maxTenor && Number(form.minTenor) > Number(form.maxTenor)) {
            notify('warning', 'Minimum tenure exceeds maximum.');
            return
        }
        saveInFlight.current = true;
        setSaving(true);
        try {
            const payload = {
                schmNm: form.schmNm,
                schmDesc: form.schmDesc,
                minAmt: form.minAmt === '' ? null : Number(form.minAmt),
                maxAmt: form.maxAmt === '' ? null : Number(form.maxAmt),
                minTenor: form.minTenor === '' ? null : Number(form.minTenor),
                maxTenor: form.maxTenor === '' ? null : Number(form.maxTenor),
                eligibilityDesc: form.eligibilityDesc
            };
            if (selected) await api.put(`/schemes/${selected.schmId}`, payload); else await api.post('/schemes', {
                ...payload,
                prodId: Number(form.prodId),
                schmCd: form.schmCd
            });
            notify('success', 'Scheme saved.');
            setConfirmSave(false);
            setModal(false);
            await load()
        } catch (err) {
            notify('error', errorText(err))
        } finally {
            saveInFlight.current = false;
            setSaving(false)
        }
    };
    const toggle = async (r: Scheme) => {
        try {
            await api.patch(`/schemes/${r.schmId}/status`, {status: r.schmSts === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'});
            await load();
            notify('success', 'Scheme status updated.')
        } catch (e) {
            notify('error', errorText(e))
        }
    };
    const history = async (r: Scheme) => {
        try {
            setAudit((await api.get<unknown[]>(`/schemes/${r.schmId}/audit`)).data)
        } catch (e) {
            notify('error', errorText(e))
        }
    };
    const visible = rows.filter(r => (!appliedFilter || String(r.prodId) === appliedFilter) && `${r.schmCd} ${r.schmNm}`.toLowerCase().includes(appliedQuery.toLowerCase()));
    const sortedVisible = sortTableRecords(visible, tableSort, [r => r.schmCd, r => r.schmNm, r => products.find(x => x.prodId === r.prodId)?.prodCd || r.prodId, r => r.schmSts]);
    const currentPage = Math.min(page, Math.max(0, Math.ceil(visible.length / 10) - 1));
    const displayed = sortedVisible.slice(currentPage * 10, (currentPage + 1) * 10);
    return <main className="maint-page">
        <PageHeader
            title="Scheme Maintenance"
            description="Maintain product schemes, financing limits and eligibility conditions."
            actions={
                <div className="cbms-toolbar-actions">
                    <RefreshButton onClick={() => void load()} loading={loading}/>
                    {canEdit && <CreateButton onClick={() => edit(null)}>New Scheme</CreateButton>}
                </div>
            }
        />
        <section className="maint-panel">
            <div className="maint-toolbar"><label>Search<input value={query} onChange={e => setQuery(e.target.value)}
                                                               placeholder="Scheme code or name"/></label><label>Product<select
                value={filter} onChange={e => setFilter(e.target.value)}>
                <option value="">All Products</option>
                {products.map(x => <option key={x.prodId} value={x.prodId}>{x.prodCd} — {x.prodNm}</option>)}
            </select></label>
                <div className="cbms-search-actions">
                    <button className="cbms-action-search" onClick={() => {
                        setPage(0);
                        setAppliedQuery(query.trim());
                        setAppliedFilter(filter);
                        void load();
                    }}>Search
                    </button>
                    <button className="cbms-action-clear" onClick={() => {
                        setPage(0);
                        setQuery('');
                        setFilter('');
                        setAppliedQuery('');
                        setAppliedFilter('');
                    }}>Clear
                    </button>
                </div>

            </div>
        </section>
        <section className="maint-panel"><h2>Schemes <RecordCount count={visible.length}/></h2>
            <div className="maint-scroll">
                <SortableTable sortState={tableSort} onSortChange={sort => { setTableSort(sort); setPage(0); }}>
                    <thead>
                    <tr>
                        <th>Scheme Code</th>
                        <th>Scheme Name</th>
                        <th>Product</th>
                        <th>Status</th>
                        <th>Action</th>
                    </tr>
                    </thead>
                    <tbody>{displayed.map(r => <tr key={r.schmId}>
                        <td className="maint-scheme-identifier"><PopupView value={r.schmCd} maxLength={20}/></td>
                        <td className="maint-scheme-identifier"><PopupView value={r.schmNm} maxLength={32}/></td>
                        <td>{products.find(x => x.prodId === r.prodId)?.prodCd || r.prodId}</td>
                        <td><StatusBadge status={r.schmSts}/></td>
                        <td className="maint-actions">
                            <button onClick={() => edit(r)}>{canEdit ? 'Edit' : 'View'}</button>
                            {canEdit && <button
                                onClick={() => setPendingToggle(r)}>{r.schmSts === 'ACTIVE' ? 'Deactivate' : 'Activate'}</button>}
                            {products.find(product => product.prodId === r.prodId)?.bankingTyp === 'ISLAMIC' &&
                                <button type="button" onClick={() => setIslamicScheme(r)}>Islamic Contract</button>}
                            <button onClick={() => void history(r)}>History</button>
                        </td>
                    </tr>)}{!visible.length && <tr>
                        <td colSpan={5}>No schemes found.</td>
                    </tr>}</tbody>
                </SortableTable>
            </div>
            <TablePagination page={currentPage} totalPages={Math.max(1, Math.ceil(visible.length / 10))}
                             totalElements={visible.length} onPageChange={setPage} busy={loading}
                             className="maint-pagination"/>
        </section>
        {activeIslamicScheme &&
            <IslamicContractDialog key={activeIslamicScheme.schmId} schemeId={activeIslamicScheme.schmId}
                                   schemeName={`${activeIslamicScheme.schmCd} · ${activeIslamicScheme.schmNm}`}
                                   productCategory={products.find(product => product.prodId === activeIslamicScheme.prodId)?.prodCat || ''}
                                   focusProfileId={incomingSchemeId === activeIslamicScheme.schmId && Number.isSafeInteger(incomingProfileId) && incomingProfileId > 0 ? incomingProfileId : undefined}
                                   onClose={closeIslamicContract}/>}
        {modal && <div className="maint-overlay">
            <section className="maint-dialog" role="dialog" aria-modal="true">
                <div className="maint-dialog-head">
                    <SubscreenTitle as="h2"
                        title={selected ? (canEdit ? 'Edit Scheme' : 'Scheme Details') : 'New Scheme'}
                        reference={selected?.schmCd}/>
                    <button type="button" aria-label="Close" onClick={closeModal}><CloseIcon/></button>
                </div>
                <form onSubmit={e => {
                    e.preventDefault();
                    if (!canEdit || saving) return;
                    if (form.minAmt && form.maxAmt && Number(form.minAmt) > Number(form.maxAmt)) {
                        notify('warning', 'Minimum amount exceeds maximum.');
                        return;
                    }
                    if (form.minTenor && form.maxTenor && Number(form.minTenor) > Number(form.maxTenor)) {
                        notify('warning', 'Minimum tenure exceeds maximum.');
                        return;
                    }
                    if (!hasPendingChanges) {
                        notify('warning', 'No changes to save.');
                        return;
                    }
                    setConfirmSave(true);
                }}>
                    <fieldset className="maint-view-fields" disabled={!canEdit}>
                        <div className="maint-grid"><label><FieldLabel required>Product</FieldLabel><select required
                                                                                                            autoFocus
                                                                                                            disabled={!!selected}
                                                                                                            value={form.prodId}
                                                                                                            onChange={e => setForm({
                                                                                                                ...form,
                                                                                                                prodId: e.target.value
                                                                                                            })}>
                            <option value="">Select Product</option>
                            {products.filter(x => x.prodSts === 'ACTIVE' || x.prodId === selected?.prodId).map(x =>
                                <option
                                    key={x.prodId} value={x.prodId}>{x.prodCd} — {x.prodNm}</option>)}
                        </select></label><label><FieldLabel required>Scheme Code</FieldLabel><input required
                                                                                                    disabled={!!selected}
                                                                                                    maxLength={30}
                                                                                                    pattern="[A-Z0-9_-]+"
                                                                                                    value={form.schmCd}
                                                                                                    onChange={e => setForm({
                                                                                                        ...form,
                                                                                                        schmCd: e.target.value.toUpperCase()
                                                                                                    })}/></label><label><FieldLabel
                            required>Scheme Name</FieldLabel><input required maxLength={150} value={form.schmNm}
                                                                    onChange={e => setForm({
                                                                        ...form,
                                                                        schmNm: e.target.value
                                                                    })}/></label><label>Minimum Amount<input
                            type="number"
                            min="0"
                            step="0.01"
                            value={form.minAmt}
                            onChange={e => setForm({
                                ...form,
                                minAmt: e.target.value
                            })}/></label><label>Maximum
                            Amount<input type="number" min="0" step="0.01" value={form.maxAmt}
                                         onChange={e => setForm({...form, maxAmt: e.target.value})}/></label><label>Minimum
                            Tenure (months)<input type="number" min="1" step="1" value={form.minTenor}
                                                  onChange={e => setForm({
                                                      ...form,
                                                      minTenor: e.target.value
                                                  })}/></label><label>Maximum Tenure (months)<input type="number"
                                                                                                    min="1"
                                                                                                    step="1"
                                                                                                    value={form.maxTenor}
                                                                                                    onChange={e => setForm({
                                                                                                        ...form,
                                                                                                        maxTenor: e.target.value
                                                                                                    })}/></label><label
                            className="maint-wide">Description<textarea maxLength={500} value={form.schmDesc}
                                                                        onChange={e => setForm({
                                                                            ...form,
                                                                            schmDesc: e.target.value
                                                                        })}/></label><label className="maint-wide">Eligibility<textarea
                            maxLength={1000} value={form.eligibilityDesc}
                            onChange={e => setForm({...form, eligibilityDesc: e.target.value})}/></label></div>
                    </fieldset>
                    <div className="maint-footer">
                        <button type="button" onClick={closeModal}>{canEdit ? 'Cancel' : 'Close'}</button>
                        {canEdit && <button className="maint-primary"
                                            disabled={saving}>{saving ? 'Saving…' : selected ? 'Save Changes' : 'Create'}</button>}
                    </div>
                </form>
            </section>
        </div>}
        {audit !== null && <div className="maint-overlay">
            <section className="maint-dialog" role="dialog" aria-modal="true">
                <div className="maint-dialog-head"><h2>Scheme History</h2>
                    <button type="button" aria-label="Close" onClick={() => setAudit(null)}><CloseIcon/></button>
                </div>
                <div className="maint-scroll">{audit.length ? audit.map((x, i) => <AuditHistoryRecord key={i}
                                                                                                      value={x}/>) : 'No history found.'}</div>
            </section>
        </div>}{confirmSave && modal && <ConfirmDialog
        title={selected ? 'Confirm Update' : 'Confirm Creation'}
        message={selected ? 'Are you sure you want to save these changes?' : 'Are you sure you want to create this scheme?'}
        confirmLabel={selected ? 'Save Changes' : 'Create'} busy={saving}
        details={[
            {label: 'Scheme', value: `${form.schmCd} · ${form.schmNm}`},
            {label: 'Product', value: products.find(p => String(p.prodId) === form.prodId)?.prodNm ?? '—'},
            {label: 'Minimum Amount', value: form.minAmt || 'Not set'},
            {label: 'Maximum Amount', value: form.maxAmt || 'Not set'},
            {label: 'Minimum Tenor', value: form.minTenor ? `${form.minTenor} months` : 'Not set'},
            {label: 'Maximum Tenor', value: form.maxTenor ? `${form.maxTenor} months` : 'Not set'}
        ]}
        notice="Scheme eligibility and limits may affect new applications."
        onCancel={() => {
            if (!saving) setConfirmSave(false);
        }}
        onConfirm={() => void save()}/>}{pendingToggle && <ConfirmDialog title="Confirm Status Change"
                                                                         message={`Are you sure you want to ${pendingToggle.schmSts === 'ACTIVE' ? 'deactivate' : 'activate'} this scheme?`}
                                                                         confirmLabel={pendingToggle.schmSts === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                                                                         onCancel={() => setPendingToggle(null)}
                                                                         onConfirm={() => {
                                                                             const row = pendingToggle;
                                                                             setPendingToggle(null);
                                                                             void toggle(row);
                                                                         }}/>}</main>;
}
