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
type Scheme = { schmId: number; schmCd: string; schmNm: string; schmSts: string };
type BaseRate = {
    baseRateId: number;
    rateCd: string;
    rateNm: string;
    ccyCd: string;
    ratePct: number;
    effDt: string;
    rateSts: string;
    crtBy: string
};
type SchemeRate = {
    schmRateId: number;
    schmId: number;
    rateTyp: string;
    baseRateCd: string | null;
    fixedRatePct: number | null;
    marginPct: number | null;
    effDt: string;
    rateSts: string
};
const today = () => new Date().toLocaleDateString('en-CA');
export default function RateMaintenance() {
    const notify = useToast();
    const {session} = useAuth();
    const canEdit = session?.roles?.some(r => r === 'MAKER' || r === 'ADMIN') ?? false;
    const [schemes, setSchemes] = useState<Scheme[]>([]), [selectedScheme, setSelectedScheme] = useState(''), [baseCode, setBaseCode] = useState(''), [baseRows, setBaseRows] = useState<BaseRate[]>([]), [schemeRows, setSchemeRows] = useState<SchemeRate[]>([]), [effectiveDate, setEffectiveDate] = useState(today()), [effective, setEffective] = useState<number | null>(null), [busy, setBusy] = useState(false);
    const [dialog, setDialog] = useState<'base' | 'scheme' | null>(null), [baseForm, setBaseForm] = useState({
        rateCd: '',
        rateNm: '',
        ccyCd: 'MYR',
        ratePct: '',
        effDt: today()
    }), [schemeForm, setSchemeForm] = useState({
        rateTyp: 'FIXED',
        baseRateCd: '',
        fixedRatePct: '',
        marginPct: '',
        effDt: today()
    });
    const loadSchemes = useCallback(async () => {
        try {
            setSchemes((await api.get<Scheme[]>('/schemes')).data)
        } catch (e) {
            notify('error', errorText(e))
        }
    }, [notify]);
    useEffect(() => {
        void Promise.resolve().then(loadSchemes)
    }, [loadSchemes]);
    const loadBase = useCallback(async () => {
        if (!baseCode.trim()) {
            setBaseRows([]);
            return
        }
        try {
            setBaseRows((await api.get<BaseRate[]>(`/rates/base/${encodeURIComponent(baseCode.trim().toUpperCase())}/history`)).data)
        } catch (e) {
            notify('error', errorText(e))
        }
    }, [baseCode, notify]);
    const loadScheme = useCallback(async () => {
        if (!selectedScheme) {
            setSchemeRows([]);
            return
        }
        try {
            setSchemeRows((await api.get<SchemeRate[]>(`/rates/scheme/${selectedScheme}/history`)).data)
        } catch (e) {
            notify('error', errorText(e))
        }
    }, [selectedScheme, notify]);
    useEffect(() => {
        void Promise.resolve().then(loadBase)
    }, [loadBase]);
    useEffect(() => {
        void Promise.resolve().then(loadScheme)
    }, [loadScheme]);
    const save = async (e: FormEvent) => {
        e.preventDefault();
        setBusy(true);
        try {
            if (dialog === 'base') {
                await api.post('/rates/base', {
                    ...baseForm,
                    rateCd: baseForm.rateCd.trim().toUpperCase(),
                    ccyCd: baseForm.ccyCd.toUpperCase(),
                    ratePct: Number(baseForm.ratePct)
                });
                setBaseCode(baseForm.rateCd.trim().toUpperCase());
            } else {
                if (!selectedScheme) throw new Error('Select a scheme first.');
                await api.post('/rates/scheme', {
                    schmId: Number(selectedScheme),
                    rateTyp: schemeForm.rateTyp,
                    baseRateCd: schemeForm.rateTyp === 'FLOATING' ? schemeForm.baseRateCd.trim().toUpperCase() : null,
                    fixedRatePct: schemeForm.rateTyp === 'FIXED' ? Number(schemeForm.fixedRatePct) : null,
                    marginPct: schemeForm.rateTyp === 'FLOATING' ? Number(schemeForm.marginPct) : null,
                    effDt: schemeForm.effDt
                });
                await loadScheme()
            }
            notify('success', 'Rate version created.');
            setDialog(null)
        } catch (err) {
            notify('error', errorText(err))
        } finally {
            setBusy(false)
        }
    };
    const resolve = async () => {
        if (!selectedScheme) return;
        try {
            const r = await api.get<{
                effectiveRatePct: number
            }>(`/rates/scheme/${selectedScheme}/effective`, {params: {date: effectiveDate}});
            setEffective(r.data.effectiveRatePct)
        } catch (e) {
            setEffective(null);
            notify('error', errorText(e))
        }
    };
    return <main className="maint-page">
        <header><h1>Rate Maintenance</h1><p>Maintain effective-dated base and scheme rates without overwriting
            historical versions.</p></header>
        <section className="maint-panel"><h2>Base Rate History</h2>
            <div className="maint-toolbar"><label>Base Rate Code<input value={baseCode}
                                                                       onChange={e => setBaseCode(e.target.value.toUpperCase())}
                                                                       placeholder="e.g. BR"/></label>
                <button onClick={() => setBaseCode('')}>Clear</button>
                <button onClick={() => void loadBase()}>Search</button>
                {canEdit && <button className="maint-primary maint-new" onClick={() => setDialog('base')}>New Base
                    Rate</button>}</div>
            <div className="maint-scroll">
                <table>
                    <thead>
                    <tr>
                        <th>Code</th>
                        <th>Name</th>
                        <th>Currency</th>
                        <th>Rate (%)</th>
                        <th>Effective Date</th>
                        <th>Status</th>
                    </tr>
                    </thead>
                    <tbody>{baseRows.map(r => <tr key={r.baseRateId}>
                        <td>{r.rateCd}</td>
                        <td>{r.rateNm}</td>
                        <td>{r.ccyCd}</td>
                        <td>{r.ratePct}</td>
                        <td>{r.effDt}</td>
                        <td>{r.rateSts}</td>
                    </tr>)}{!baseRows.length && <tr>
                        <td colSpan={6}>Enter a base rate code to view its history.</td>
                    </tr>}</tbody>
                </table>
            </div>
        </section>
        <section className="maint-panel"><h2>Scheme Rate History</h2>
            <div className="maint-toolbar"><label>Scheme<select value={selectedScheme} onChange={e => {
                setSelectedScheme(e.target.value);
                setEffective(null)
            }}>
                <option value="">Select Scheme</option>
                {schemes.map(s => <option key={s.schmId} value={s.schmId}>{s.schmCd} — {s.schmNm}</option>)}
            </select></label>
                <button onClick={() => {
                    setSelectedScheme('');
                    setEffective(null)
                }}>Clear
                </button>
                <button onClick={() => void loadScheme()}>Search</button>
                {canEdit && <button className="maint-primary maint-new" disabled={!selectedScheme}
                                    onClick={() => setDialog('scheme')}>New Scheme Rate</button>}</div>
            <div className="maint-scroll">
                <table>
                    <thead>
                    <tr>
                        <th>Type</th>
                        <th>Base Rate</th>
                        <th>Fixed Rate (%)</th>
                        <th>Margin (%)</th>
                        <th>Effective Date</th>
                        <th>Status</th>
                    </tr>
                    </thead>
                    <tbody>{schemeRows.map(r => <tr key={r.schmRateId}>
                        <td>{r.rateTyp}</td>
                        <td>{r.baseRateCd || '—'}</td>
                        <td>{r.fixedRatePct ?? '—'}</td>
                        <td>{r.marginPct ?? '—'}</td>
                        <td>{r.effDt}</td>
                        <td>{r.rateSts}</td>
                    </tr>)}{!schemeRows.length && <tr>
                        <td colSpan={6}>Select a scheme to view its rate history.</td>
                    </tr>}</tbody>
                </table>
            </div>
            <div className="maint-toolbar maint-effective"><label>Rate as of<input type="date" value={effectiveDate}
                                                                                   onChange={e => {
                                                                                       setEffectiveDate(e.target.value);
                                                                                       setEffective(null)
                                                                                   }}/></label>
                <button disabled={!selectedScheme || !effectiveDate} onClick={() => void resolve()}>Resolve Effective
                    Rate
                </button>
                {effective !== null && <strong>Effective Rate: {effective}%</strong>}</div>
        </section>
        {dialog && <div className="maint-overlay">
            <section className="maint-dialog" role="dialog" aria-modal="true">
                <div className="maint-dialog-head">
                    <h2>{dialog === 'base' ? 'New Base Rate Version' : 'New Scheme Rate Version'}</h2>
                    <button onClick={() => setDialog(null)}>×</button>
                </div>
                <form onSubmit={e => void save(e)}>
                    <div className="maint-grid">{dialog === 'base' ? <><label>Rate Code *<input required maxLength={30}
                                                                                                pattern="[A-Z0-9_-]+"
                                                                                                value={baseForm.rateCd}
                                                                                                onChange={e => setBaseForm({
                                                                                                    ...baseForm,
                                                                                                    rateCd: e.target.value.toUpperCase()
                                                                                                })}/></label><label>Rate
                        Name *<input required maxLength={150} value={baseForm.rateNm}
                                     onChange={e => setBaseForm({...baseForm, rateNm: e.target.value})}/></label><label>Currency
                        *<input required maxLength={3} pattern="[A-Z]{3}" value={baseForm.ccyCd}
                                onChange={e => setBaseForm({
                                    ...baseForm,
                                    ccyCd: e.target.value.toUpperCase()
                                })}/></label><label>Rate (%) *<input required type="number" min="0" max="100" step="any"
                                                                     value={baseForm.ratePct}
                                                                     onChange={e => setBaseForm({
                                                                         ...baseForm,
                                                                         ratePct: e.target.value
                                                                     })}/></label><label>Effective Date *<input required
                                                                                                                type="date"
                                                                                                                value={baseForm.effDt}
                                                                                                                onChange={e => setBaseForm({
                                                                                                                    ...baseForm,
                                                                                                                    effDt: e.target.value
                                                                                                                })}/></label></> : <>
                        <label>Rate Type *<select value={schemeForm.rateTyp} onChange={e => setSchemeForm({
                            ...schemeForm,
                            rateTyp: e.target.value
                        })}>
                            <option>FIXED</option>
                            <option>FLOATING</option>
                        </select></label>{schemeForm.rateTyp === 'FIXED' ?
                        <label>Fixed Rate (%) *<input required type="number" step="any" value={schemeForm.fixedRatePct}
                                                      onChange={e => setSchemeForm({
                                                          ...schemeForm,
                                                          fixedRatePct: e.target.value
                                                      })}/></label> : <><label>Base Rate Code *<input required
                                                                                                      value={schemeForm.baseRateCd}
                                                                                                      onChange={e => setSchemeForm({
                                                                                                          ...schemeForm,
                                                                                                          baseRateCd: e.target.value.toUpperCase()
                                                                                                      })}/></label><label>Margin
                            (%) *<input required type="number" step="any" value={schemeForm.marginPct}
                                        onChange={e => setSchemeForm({
                                            ...schemeForm,
                                            marginPct: e.target.value
                                        })}/></label></>}<label>Effective Date *<input required type="date"
                                                                                       value={schemeForm.effDt}
                                                                                       onChange={e => setSchemeForm({
                                                                                           ...schemeForm,
                                                                                           effDt: e.target.value
                                                                                       })}/></label></>}</div>
                    <p className="maint-note">Saving creates a new effective-dated rate record. Existing rate history is
                        preserved.</p>
                    <div className="maint-footer">
                        <button type="button" onClick={() => setDialog(null)}>Cancel</button>
                        <button disabled={busy} className="maint-primary">{busy ? 'Saving…' : 'Save Rate'}</button>
                    </div>
                </form>
            </section>
        </div>}</main>;
}
