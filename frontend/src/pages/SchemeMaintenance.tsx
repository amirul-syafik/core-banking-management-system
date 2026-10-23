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
const display = (v: unknown) => v === null || v === undefined || v === '' ? '—' : String(v);
type Product = { prodId: number; prodCd: string; prodNm: string; prodSts: string };
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
    const {session} = useAuth();
    const canEdit = session?.roles?.some(r => r === 'MAKER' || r === 'ADMIN') ?? false;
    const [rows, setRows] = useState<Scheme[]>([]), [products, setProducts] = useState<Product[]>([]), [query, setQuery] = useState(''), [filter, setFilter] = useState(''), [selected, setSelected] = useState<Scheme | null>(null), [form, setForm] = useState(blank), [modal, setModal] = useState(false), [saving, setSaving] = useState(false), [audit, setAudit] = useState<unknown[] | null>(null);
    const load = useCallback(async () => {
        try {
            const [s, p] = await Promise.all([api.get<Scheme[]>('/schemes'), api.get<Product[]>('/products')]);
            setRows(s.data);
            setProducts(p.data)
        } catch (e) {
            notify('error', errorText(e))
        }
    }, [notify]);
    useEffect(() => {
        void Promise.resolve().then(load)
    }, [load]);
    const edit = (r: Scheme | null) => {
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
    const save = async (e: FormEvent) => {
        e.preventDefault();
        if (form.minAmt && form.maxAmt && Number(form.minAmt) > Number(form.maxAmt)) {
            notify('warning', 'Minimum amount exceeds maximum.');
            return
        }
        if (form.minTenor && form.maxTenor && Number(form.minTenor) > Number(form.maxTenor)) {
            notify('warning', 'Minimum tenure exceeds maximum.');
            return
        }
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
            setModal(false);
            await load()
        } catch (err) {
            notify('error', errorText(err))
        } finally {
            setSaving(false)
        }
    };
    const toggle = async (r: Scheme) => {
        if (!window.confirm(`Change status of ${r.schmCd}?`)) return;
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
    const visible = rows.filter(r => (!filter || String(r.prodId) === filter) && `${r.schmCd} ${r.schmNm}`.toLowerCase().includes(query.toLowerCase()));
    return <main className="maint-page">
        <header><h1>Scheme Maintenance</h1><p>Maintain product schemes, financing limits and eligibility conditions.</p>
        </header>
        <section className="maint-panel">
            <div className="maint-toolbar"><label>Search<input value={query} onChange={e => setQuery(e.target.value)}
                                                               placeholder="Scheme code or name"/></label><label>Product<select
                value={filter} onChange={e => setFilter(e.target.value)}>
                <option value="">All Products</option>
                {products.map(x => <option key={x.prodId} value={x.prodId}>{x.prodCd} — {x.prodNm}</option>)}
            </select></label>
                <button onClick={() => {
                    setQuery('');
                    setFilter('')
                }}>Clear
                </button>
                <button onClick={() => void load()}>Search</button>
                {canEdit && <button className="maint-primary maint-new" onClick={() => edit(null)}>New Scheme</button>}
            </div>
        </section>
        <section className="maint-panel"><h2>Schemes <small>({visible.length} records)</small></h2>
            <div className="maint-scroll">
                <table>
                    <thead>
                    <tr>
                        <th>Scheme Code</th>
                        <th>Scheme Name</th>
                        <th>Product</th>
                        <th>Min Amount</th>
                        <th>Max Amount</th>
                        <th>Tenure (months)</th>
                        <th>Status</th>
                        <th>Actions</th>
                    </tr>
                    </thead>
                    <tbody>{visible.map(r => <tr key={r.schmId}>
                        <td>{r.schmCd}</td>
                        <td>{r.schmNm}</td>
                        <td>{products.find(x => x.prodId === r.prodId)?.prodCd || r.prodId}</td>
                        <td>{display(r.minAmt)}</td>
                        <td>{display(r.maxAmt)}</td>
                        <td>{display(r.minTenor)} – {display(r.maxTenor)}</td>
                        <td>{r.schmSts}</td>
                        <td className="maint-actions">{canEdit &&
                            <button onClick={() => edit(r)}>Edit</button>}{canEdit && <button
                            onClick={() => void toggle(r)}>{r.schmSts === 'ACTIVE' ? 'Deactivate' : 'Activate'}</button>}
                            <button onClick={() => void history(r)}>History</button>
                        </td>
                    </tr>)}{!visible.length && <tr>
                        <td colSpan={8}>No schemes found.</td>
                    </tr>}</tbody>
                </table>
            </div>
        </section>
        {modal && <div className="maint-overlay">
            <section className="maint-dialog" role="dialog" aria-modal="true">
                <div className="maint-dialog-head"><h2>{selected ? 'Edit Scheme' : 'New Scheme'}</h2>
                    <button onClick={() => setModal(false)}>×</button>
                </div>
                <form onSubmit={e => void save(e)}>
                    <div className="maint-grid"><label>Product *<select required disabled={!!selected}
                                                                        value={form.prodId} onChange={e => setForm({
                        ...form,
                        prodId: e.target.value
                    })}>
                        <option value="">Select Product</option>
                        {products.filter(x => x.prodSts === 'ACTIVE' || x.prodId === selected?.prodId).map(x => <option
                            key={x.prodId} value={x.prodId}>{x.prodCd} — {x.prodNm}</option>)}</select></label><label>Scheme
                        Code *<input required disabled={!!selected} maxLength={30} pattern="[A-Z0-9_-]+"
                                     value={form.schmCd} onChange={e => setForm({
                            ...form,
                            schmCd: e.target.value.toUpperCase()
                        })}/></label><label>Scheme Name *<input required maxLength={150} value={form.schmNm}
                                                                onChange={e => setForm({
                                                                    ...form,
                                                                    schmNm: e.target.value
                                                                })}/></label><label>Minimum Amount<input type="number"
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
                                              })}/></label><label>Maximum Tenure (months)<input type="number" min="1"
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
                    <div className="maint-footer">
                        <button type="button" onClick={() => setModal(false)}>Cancel</button>
                        <button className="maint-primary" disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
                    </div>
                </form>
            </section>
        </div>}
        {audit !== null && <div className="maint-overlay">
            <section className="maint-dialog" role="dialog" aria-modal="true">
                <div className="maint-dialog-head"><h2>Scheme History</h2>
                    <button onClick={() => setAudit(null)}>×</button>
                </div>
                <div className="maint-scroll">{audit.length ? audit.map((x, i) => <pre
                    key={i}>{JSON.stringify(x, null, 2)}</pre>) : 'No history found.'}</div>
            </section>
        </div>}</main>;
}
