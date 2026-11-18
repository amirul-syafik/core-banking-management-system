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
import {useCallback, useEffect, useRef, useState} from 'react';
import {api} from '../api/client';
import {useAuth} from '../auth/useAuth';
import {useToast} from '../components/toastContext';
import './maintenance.css';
import ReferenceCodeSelect from '../components/ReferenceCodeSelect';

const errorText = (e: unknown) => {
    if (typeof e === 'object' && e && 'response' in e) {
        const r = e as { response?: { data?: { message?: string; detail?: string } } };
        return r.response?.data?.message || r.response?.data?.detail || 'Request failed'
    }
    return e instanceof Error ? e.message : 'Request failed'
};
type Product = {
    prodId: number;
    prodCd: string;
    prodNm: string;
    prodCat: string;
    bankingTyp: string;
    ccyCd: string;
    prodDesc: string | null;
    prodSts: string
};
const categories = ['SAVINGS', 'CURRENT', 'FIXED_DEPOSIT', 'HIRE_PURCHASE', 'WHOLESALE_FINANCING', 'FLOOR_STOCKING', 'CREDIT_LINE'];
const blank = {prodCd: '', prodNm: '', prodCat: 'SAVINGS', bankingTyp: 'CONVENTIONAL', ccyCd: 'MYR', prodDesc: ''};
export default function ProductMaintenance() {
    const notify = useToast();
    const saveInFlight = useRef(false);
    const {session} = useAuth();
    const canEdit = session?.roles?.some(r => r === 'MAKER' || r === 'ADMIN') ?? false;
    const [rows, setRows] = useState<Product[]>([]), [query, setQuery] = useState(''), [status, setStatus] = useState('ALL'), [page, setPage] = useState(0), [tableSort, setTableSort] = useState<TableSortState>(null),
        [appliedQuery, setAppliedQuery] = useState(''), [appliedStatus, setAppliedStatus] = useState('ALL'), [loading, setLoading] = useState(false), [saving, setSaving] = useState(false), [selected, setSelected] = useState<Product | null>(null), [form, setForm] = useState(blank), [pendingToggle, setPendingToggle] = useState<Product | null>(null), [modal, setModal] = useState(false), [confirmSave, setConfirmSave] = useState(false), [audit, setAudit] = useState<unknown[] | null>(null);
    const load = useCallback(async () => {
        setLoading(true);
        try {
            setRows((await api.get<Product[]>('/products')).data)
        } catch (e) {
            notify('error', errorText(e))
        } finally {
            setLoading(false)
        }
    }, [notify]);
    useEffect(() => {
        void Promise.resolve().then(load)
    }, [load]);
    const edit = (row: Product | null) => {
        setConfirmSave(false);
        setSelected(row);
        setForm(row ? {
            prodCd: row.prodCd,
            prodNm: row.prodNm,
            prodCat: row.prodCat,
            bankingTyp: row.bankingTyp,
            ccyCd: row.ccyCd,
            prodDesc: row.prodDesc || ''
        } : blank);
        setModal(true)
    };
    const hasPendingChanges = !selected ||
        form.prodNm.trim() !== selected.prodNm.trim() ||
        form.prodDesc.trim() !== (selected.prodDesc ?? '').trim();
    const requestSave = () => {
        if (!canEdit || saving || saveInFlight.current) return;
        if (!hasPendingChanges) {
            notify('warning', 'No changes to save.');
            return;
        }
        setConfirmSave(true);
    };
    const closeModal = () => {
        if (saving) return;
        setConfirmSave(false);
        setModal(false);
        setSelected(null);
        setForm(blank);
    };
    const save = async () => {
        if (saving || saveInFlight.current || !canEdit || !confirmSave || !hasPendingChanges) return;
        saveInFlight.current = true;
        setSaving(true);
        try {
            if (selected) await api.put(`/products/${selected.prodId}`, {
                prodNm: form.prodNm,
                prodDesc: form.prodDesc
            }); else await api.post('/products', form);
            notify('success', 'Product saved.');
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
    const toggle = async (row: Product) => {
        try {
            await api.patch(`/products/${row.prodId}/status`, {status: row.prodSts === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'});
            notify('success', 'Product status updated.');
            await load()
        } catch (e) {
            notify('error', errorText(e))
        }
    };
    const history = async (row: Product) => {
        try {
            setAudit((await api.get<unknown[]>(`/products/${row.prodId}/audit`)).data)
        } catch (e) {
            notify('error', errorText(e))
        }
    };
    const visible = rows.filter(r => (appliedStatus === 'ALL' || r.prodSts === appliedStatus) && `${r.prodCd} ${r.prodNm} ${r.prodCat}`.toLowerCase().includes(appliedQuery.toLowerCase()));
    const sortedVisible = sortTableRecords(visible, tableSort, [r => r.prodCd, r => r.prodNm, r => r.prodCat, r => r.bankingTyp, r => r.ccyCd, r => r.prodSts]);
    const currentPage = Math.min(page, Math.max(0, Math.ceil(visible.length / 10) - 1));
    const displayed = sortedVisible.slice(currentPage * 10, (currentPage + 1) * 10);
    return <main className="maint-page">
        <PageHeader
            title="Product Maintenance"
            description="Manage banking products and their availability for new business."
            actions={
                <div className="cbms-toolbar-actions">
                    <RefreshButton onClick={() => void load()} loading={loading}/>
                    {canEdit && <CreateButton onClick={() => edit(null)}>New Product</CreateButton>}
                </div>
            }
        />
        <section className="maint-panel">
            <div className="maint-toolbar"><label>Search<input value={query} onChange={e => setQuery(e.target.value)}
                                                               placeholder="Code, name or category"/></label><label>Status<select
                value={status} onChange={e => setStatus(e.target.value)}>
                <option value="ALL">All</option>
                <option value="DRAFT">Draft</option>
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
            </select></label>
                <div className="cbms-search-actions">
                    <button className="cbms-action-search" onClick={() => {
                        setPage(0);
                        setAppliedQuery(query.trim());
                        setAppliedStatus(status);
                        void load();
                    }}>Search
                    </button>
                    <button className="cbms-action-clear" onClick={() => {
                        setPage(0);
                        setQuery('');
                        setStatus('ALL');
                    }}>Clear
                    </button>
                </div>

            </div>
        </section>
        <section className="maint-panel"><h2>Products <RecordCount count={visible.length}/></h2>
            <div className="maint-scroll">
                <SortableTable sortState={tableSort} onSortChange={sort => { setTableSort(sort); setPage(0); }}>
                    <thead>
                    <tr>
                        <th>Product Code</th>
                        <th>Product Name</th>
                        <th>Category</th>
                        <th>Banking Type</th>
                        <th>Currency</th>
                        <th>Status</th>
                        <th>Action</th>
                    </tr>
                    </thead>
            <tbody>{displayed.map(r => <tr key={r.prodId}>
                        <td>{r.prodCd}</td>
                        <td>{r.prodNm}</td>
                        <td>{formatBusinessValue(r.prodCat)}</td>
                        <td>{formatBusinessValue(r.bankingTyp)}</td>
                        <td>{r.ccyCd}</td>
                        <td><StatusBadge status={r.prodSts}/></td>
                        <td className="maint-actions">{canEdit &&
                            <button onClick={() => edit(r)}>Edit</button>}{canEdit && <button
                            onClick={() => setPendingToggle(r)}>{r.prodSts === 'ACTIVE' ? 'Deactivate' : 'Activate'}</button>}
                            <button onClick={() => void history(r)}>History</button>
                        </td>
                    </tr>)}{!visible.length && <tr>
                        <td colSpan={7}>{loading ? 'Loading…' : 'No products found.'}</td>
                    </tr>}</tbody>
                </SortableTable>
            </div>
            <TablePagination page={currentPage} totalPages={Math.max(1, Math.ceil(visible.length / 10))}
                             totalElements={visible.length} onPageChange={setPage} busy={loading}
                             className="maint-pagination"/>
        </section>
        {modal && <div className="maint-overlay">
            <section className="maint-dialog" role="dialog" aria-modal="true">
                <div className="maint-dialog-head"><SubscreenTitle as="h2" title={selected ? 'Edit Product' : 'New Product'}
                    reference={selected?.prodCd}/>
                    <button onClick={closeModal} aria-label="Close"><CloseIcon/></button>
                </div>
                <form onSubmit={e => {
                    e.preventDefault();
                    requestSave();
                }}>
                    <div className="maint-grid"><label><FieldLabel required>Product Code</FieldLabel><input required
                                                                                                            autoFocus
                                                                                                            maxLength={30}
                                                                                                            pattern="[A-Z0-9_-]+"
                                                                                                            disabled={!!selected}
                                                                                                            value={form.prodCd}
                                                                                                            onChange={e => setForm({
                                                                                                                ...form,
                                                                                                                prodCd: e.target.value.toUpperCase()
                                                                                                            })}/></label><label><FieldLabel
                        required>Product Name</FieldLabel><input required maxLength={150} value={form.prodNm}
                                                                 onChange={e => setForm({
                                                                     ...form,
                                                                     prodNm: e.target.value
                                                                 })}/></label><label><FieldLabel
                        required>Category</FieldLabel><select disabled={!!selected}
                                                              value={form.prodCat}
                                                              onChange={e => setForm({
                                                                  ...form,
                                                                  prodCat: e.target.value
                                                              })}>{categories.map(x =>
                        <option key={x} value={x}>{formatBusinessValue(x)}</option>)}</select></label><label><FieldLabel
                        required>Banking Type</FieldLabel><select disabled={!!selected} value={form.bankingTyp}
                                                                  onChange={e => setForm({
                                                                      ...form,
                                                                      bankingTyp: e.target.value
                                                                  })}>
                        <option value="CONVENTIONAL">Conventional</option>
                        <option value="ISLAMIC">Islamic</option>
                    </select></label><label><FieldLabel required>Currency</FieldLabel><ReferenceCodeSelect
                        kind="currencies" required disabled={!!selected} value={form.ccyCd}
                        onChange={value => setForm({...form, ccyCd: value})}/></label><label
                        className="maint-wide">Description<textarea maxLength={500} value={form.prodDesc}
                                                                    onChange={e => setForm({
                                                                        ...form,
                                                                        prodDesc: e.target.value
                                                                    })}/></label></div>
                    <div className="maint-footer">
                        <button type="button" onClick={closeModal}>Cancel</button>
                        <button className="maint-primary"
                                disabled={saving}>{saving ? 'Saving…' : selected ? 'Save Changes' : 'Create'}</button>
                    </div>
                </form>
            </section>
        </div>}
        {audit !== null && <div className="maint-overlay">
            <section className="maint-dialog" role="dialog" aria-modal="true">
                <div className="maint-dialog-head"><h2>Product History</h2>
                    <button type="button" aria-label="Close" onClick={() => setAudit(null)}><CloseIcon/></button>
                </div>
                <div className="maint-scroll">{audit.length ? audit.map((x, i) => <AuditHistoryRecord key={i}
                                                                                                      value={x}/>) : 'No history found.'}</div>
            </section>
        </div>}{confirmSave && modal && <ConfirmDialog
        title={selected ? 'Confirm Update' : 'Confirm Creation'}
        message={selected ? 'Are you sure you want to save these changes?' : 'Are you sure you want to create this product?'}
        confirmLabel={selected ? 'Save Changes' : 'Create'} busy={saving}
        details={[
            {label: 'Product Code', value: form.prodCd},
            {label: 'Product Name', value: form.prodNm},
            {label: 'Category', value: form.prodCat.replaceAll('_', ' ')},
            {label: 'Banking Type', value: form.bankingTyp},
            {label: 'Currency', value: form.ccyCd}
        ]}
        notice="Product definitions determine which banking services can be offered."
        onCancel={() => {
            if (!saving) setConfirmSave(false);
        }}
        onConfirm={() => void save()}/>}{pendingToggle && <ConfirmDialog title="Confirm Status Change"
                                                                         message={`Are you sure you want to ${pendingToggle.prodSts === 'ACTIVE' ? 'deactivate' : 'activate'} this product?`}
                                                                         confirmLabel={pendingToggle.prodSts === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                                                                         onCancel={() => setPendingToggle(null)}
                                                                         onConfirm={() => {
                                                                             const row = pendingToggle;
                                                                             setPendingToggle(null);
                                                                             void toggle(row);
                                                                         }}/>}</main>;
}
import {formatBusinessValue} from '../utils/display';
