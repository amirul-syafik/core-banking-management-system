import {useCallback, useEffect, useState, type FormEvent} from 'react';
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
    const {session} = useAuth();
    const canEdit = session?.roles?.some(r => r === 'MAKER' || r === 'ADMIN') ?? false;
    const [rows, setRows] = useState<Product[]>([]), [query, setQuery] = useState(''), [status, setStatus] = useState('ALL'), [loading, setLoading] = useState(false), [saving, setSaving] = useState(false), [selected, setSelected] = useState<Product | null>(null), [form, setForm] = useState(blank), [modal, setModal] = useState(false), [audit, setAudit] = useState<unknown[] | null>(null);
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
    const save = async (e: FormEvent) => {
        e.preventDefault();
        setSaving(true);
        try {
            if (selected) await api.put(`/products/${selected.prodId}`, {
                prodNm: form.prodNm,
                prodDesc: form.prodDesc
            }); else await api.post('/products', form);
            notify('success', 'Product saved.');
            setModal(false);
            await load()
        } catch (err) {
            notify('error', errorText(err))
        } finally {
            setSaving(false)
        }
    };
    const toggle = async (row: Product) => {
        if (!window.confirm(`Set ${row.prodCd} to ${row.prodSts === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'}?`)) return;
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
    const visible = rows.filter(r => (status === 'ALL' || r.prodSts === status) && `${r.prodCd} ${r.prodNm} ${r.prodCat}`.toLowerCase().includes(query.toLowerCase()));
    return <main className="maint-page">
        <header><h1>Product Maintenance</h1><p>Manage banking products and their availability for new business.</p>
        </header>
        <section className="maint-panel">
            <div className="maint-toolbar"><label>Search<input value={query} onChange={e => setQuery(e.target.value)}
                                                               placeholder="Code, name or category"/></label><label>Status<select
                value={status} onChange={e => setStatus(e.target.value)}>
                <option>ALL</option>
                <option>ACTIVE</option>
                <option>INACTIVE</option>
            </select></label>
                <button onClick={() => {
                    setQuery('');
                    setStatus('ALL')
                }}>Clear
                </button>
                <button onClick={() => void load()}>Search</button>
                {canEdit && <button className="maint-primary maint-new" onClick={() => edit(null)}>New Product</button>}
            </div>
        </section>
        <section className="maint-panel"><h2>Products <small>({visible.length} records)</small></h2>
            <div className="maint-scroll">
                <table>
                    <thead>
                    <tr>
                        <th>Product Code</th>
                        <th>Product Name</th>
                        <th>Category</th>
                        <th>Banking Type</th>
                        <th>Currency</th>
                        <th>Status</th>
                        <th>Actions</th>
                    </tr>
                    </thead>
                    <tbody>{visible.map(r => <tr key={r.prodId}>
                        <td>{r.prodCd}</td>
                        <td>{r.prodNm}</td>
                        <td>{r.prodCat.replaceAll('_', ' ')}</td>
                        <td>{r.bankingTyp}</td>
                        <td>{r.ccyCd}</td>
                        <td>{r.prodSts}</td>
                        <td className="maint-actions">{canEdit &&
                            <button onClick={() => edit(r)}>Edit</button>}{canEdit && <button
                            onClick={() => void toggle(r)}>{r.prodSts === 'ACTIVE' ? 'Deactivate' : 'Activate'}</button>}
                            <button onClick={() => void history(r)}>History</button>
                        </td>
                    </tr>)}{!visible.length && <tr>
                        <td colSpan={7}>{loading ? 'Loading…' : 'No products found.'}</td>
                    </tr>}</tbody>
                </table>
            </div>
        </section>
        {modal && <div className="maint-overlay">
            <section className="maint-dialog" role="dialog" aria-modal="true">
                <div className="maint-dialog-head"><h2>{selected ? 'Edit Product' : 'New Product'}</h2>
                    <button onClick={() => setModal(false)} aria-label="Close">×</button>
                </div>
                <form onSubmit={e => void save(e)}>
                    <div className="maint-grid"><label>Product Code *<input required maxLength={30}
                                                                            pattern="[A-Z0-9_-]+" disabled={!!selected}
                                                                            value={form.prodCd} onChange={e => setForm({
                        ...form,
                        prodCd: e.target.value.toUpperCase()
                    })}/></label><label>Product Name *<input required maxLength={150} value={form.prodNm}
                                                             onChange={e => setForm({
                                                                 ...form,
                                                                 prodNm: e.target.value
                                                             })}/></label><label>Category *<select disabled={!!selected}
                                                                                                   value={form.prodCat}
                                                                                                   onChange={e => setForm({
                                                                                                       ...form,
                                                                                                       prodCat: e.target.value
                                                                                                   })}>{categories.map(x =>
                        <option key={x} value={x}>{x.replaceAll('_', ' ')}</option>)}</select></label><label>Banking
                        Type *<select disabled={!!selected} value={form.bankingTyp}
                                      onChange={e => setForm({...form, bankingTyp: e.target.value})}>
                            <option>CONVENTIONAL</option>
                            <option>ISLAMIC</option>
                        </select></label><label>Currency *<input required pattern="[A-Z]{3}" maxLength={3}
                                                                 disabled={!!selected} value={form.ccyCd}
                                                                 onChange={e => setForm({
                                                                     ...form,
                                                                     ccyCd: e.target.value.toUpperCase()
                                                                 })}/></label><label
                        className="maint-wide">Description<textarea maxLength={500} value={form.prodDesc}
                                                                    onChange={e => setForm({
                                                                        ...form,
                                                                        prodDesc: e.target.value
                                                                    })}/></label></div>
                    <div className="maint-footer">
                        <button type="button" onClick={() => setModal(false)}>Cancel</button>
                        <button className="maint-primary" disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
                    </div>
                </form>
            </section>
        </div>}
        {audit !== null && <div className="maint-overlay">
            <section className="maint-dialog" role="dialog" aria-modal="true">
                <div className="maint-dialog-head"><h2>Product History</h2>
                    <button onClick={() => setAudit(null)}>×</button>
                </div>
                <div className="maint-scroll">{audit.length ? audit.map((x, i) => <pre
                    key={i}>{JSON.stringify(x, null, 2)}</pre>) : 'No history found.'}</div>
            </section>
        </div>}</main>;
}
