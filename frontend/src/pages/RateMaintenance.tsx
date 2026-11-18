import {SortableTable, type TableSortState} from '../components/SortableTable';
import {sortTableRecords} from '../utils/tableSorting';
import {StatusBadge, CloseIcon, RefreshButton, ConfirmDialog, FieldLabel, PageHeader, TablePagination, SubscreenTitle} from '../components/UiStandards';
import {useCallback, useEffect, useRef, useState} from 'react';
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
type BaseRateCode = { rateCd: string; rateNm: string; ccyCd: string; ratePct: number; effDt: string; rateSts: string };
type SchemeRate = {
    schmRateId: number;
    schmId: number;
    rateTyp: string;
    baseRateCd: string | null;
    fixedRatePct: number | null;
    minRatePct: number | null;
    maxRatePct: number | null;
    marginPct: number | null;
    effDt: string;
    rateSts: string
};
const today = () => new Date().toLocaleDateString('en-CA');
const nextVersionDate = (mostRecentDate?: string) => {
    if (!mostRecentDate || mostRecentDate < today()) return today();
    const date = new Date(`${mostRecentDate}T00:00:00Z`);
    date.setUTCDate(date.getUTCDate() + 1);
    return date.toISOString().slice(0, 10);
};
const numericInRange = (value: string) => value.trim() !== '' && Number.isFinite(Number(value)) && Number(value) >= 0 && Number(value) <= 100;

const compareRateNumber = (value: string) => value.trim() === '' ? null : Number(value);
const baseEditKey = (form: { rateCd: string; rateNm: string; ccyCd: string; ratePct: string; effDt: string }) =>
    JSON.stringify([form.rateCd.trim(), form.rateNm.trim(), form.ccyCd.trim(), compareRateNumber(form.ratePct), form.effDt]);
const schemeEditKey = (form: {
    rateTyp: string;
    baseRateCd: string;
    fixedRatePct: string;
    minRatePct: string;
    maxRatePct: string;
    marginPct: string;
    effDt: string
}) =>
    JSON.stringify(form.rateTyp === 'FLOATING'
        ? [form.rateTyp, form.baseRateCd.trim(), compareRateNumber(form.marginPct), form.effDt]
        : [form.rateTyp, compareRateNumber(form.fixedRatePct), compareRateNumber(form.minRatePct), compareRateNumber(form.maxRatePct), form.effDt]);

export default function RateMaintenance() {
    const notify = useToast();
    const saveInFlight = useRef(false);
    const [revisionBaseline, setRevisionBaseline] = useState<string | null>(null);
    const {session} = useAuth();
    const canEdit = session?.roles?.some(r => r === 'MAKER' || r === 'ADMIN') ?? false;
    const [catalog, setCatalog] = useState<BaseRateCode[]>([]), [activeCatalog, setActiveCatalog] = useState<BaseRateCode[]>([]), [catalogError, setCatalogError] = useState(''), [schemes, setSchemes] = useState<Scheme[]>([]), [selectedScheme, setSelectedScheme] = useState(''), [baseCode, setBaseCode] = useState(''), [baseRows, setBaseRows] = useState<BaseRate[]>([]), [schemeRows, setSchemeRows] = useState<SchemeRate[]>([]), [appliedBaseCode, setAppliedBaseCode] = useState(''), [appliedScheme, setAppliedScheme] = useState(''), [basePage, setBasePage] = useState(0), [schemePage, setSchemePage] = useState(0), [effectiveDate, setEffectiveDate] = useState(today()), [effective, setEffective] = useState<number | null>(null), [busy, setBusy] = useState(false), [refreshing, setRefreshing] = useState(false);
    const [baseSort, setBaseSort] = useState<TableSortState>(null), [schemeSort, setSchemeSort] = useState<TableSortState>(null);
    const [dialog, setDialog] = useState<'base' | 'scheme' | null>(null), [confirmSave, setConfirmSave] = useState(false), [revising, setRevising] = useState(false), [baseForm, setBaseForm] = useState({
        rateCd: '',
        rateNm: '',
        ccyCd: 'MYR',
        ratePct: '',
        effDt: today()
    }), [schemeForm, setSchemeForm] = useState({
        rateTyp: 'FIXED',
        baseRateCd: '',
        fixedRatePct: '',
        minRatePct: '',
        maxRatePct: '',
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
    const loadCatalog = useCallback(async () => {
        try {
            const {data} = await api.get<BaseRateCode[]>('/rates/base/codes', {params: {activeOnly: false}});
            setCatalog(data);
            setCatalogError('');
        } catch (error) {
            setCatalogError(errorText(error));
        }
    }, []);
    useEffect(() => {
        void Promise.resolve().then(loadCatalog);
    }, [loadCatalog]);
    useEffect(() => {
        if (dialog !== 'scheme' || schemeForm.rateTyp !== 'FLOATING' || !/^\d{4}-\d{2}-\d{2}$/.test(schemeForm.effDt)) return;
        let active = true;
        void api.get<BaseRateCode[]>('/rates/base/codes', {params: {activeOnly: true, asOf: schemeForm.effDt}})
            .then(({data}) => {
                if (active) setActiveCatalog(data);
            })
            .catch(error => {
                if (active) {
                    setActiveCatalog([]);
                    notify('error', errorText(error));
                }
            });
        return () => {
            active = false;
        };
    }, [dialog, schemeForm.rateTyp, schemeForm.effDt, notify]);
    const loadBase = useCallback(async () => {
        if (!appliedBaseCode.trim()) {
            setBaseRows([]);
            return
        }
        try {
            const data = (await api.get<BaseRate[]>(`/rates/base/${encodeURIComponent(appliedBaseCode.trim().toUpperCase())}/history`)).data;
            setBaseRows(data)
        } catch (e) {
            notify('error', errorText(e))
        }
    }, [appliedBaseCode, notify]);
    const loadScheme = useCallback(async () => {
        if (!appliedScheme) {
            setSchemeRows([]);
            return
        }
        try {
            setSchemeRows((await api.get<SchemeRate[]>(`/rates/scheme/${appliedScheme}/history`)).data)
        } catch (e) {
            notify('error', errorText(e))
        }
    }, [appliedScheme, notify]);
    const refreshScreen = async () => {
        if (refreshing) return;
        setRefreshing(true);
        try {
            await Promise.all([loadCatalog(), loadSchemes(), loadBase(), loadScheme()]);
        } finally {
            setRefreshing(false);
        }
    };
    useEffect(() => {
        void Promise.resolve().then(loadBase)
    }, [loadBase]);
    useEffect(() => {
        void Promise.resolve().then(loadScheme)
    }, [loadScheme]);
    const currentBasePage = Math.min(basePage, Math.max(0, Math.ceil(baseRows.length / 10) - 1));
    const currentSchemePage = Math.min(schemePage, Math.max(0, Math.ceil(schemeRows.length / 10) - 1));
    const sortedBaseRows = sortTableRecords(baseRows, baseSort, [r => r.rateCd, r => r.rateNm, r => r.ccyCd, r => r.ratePct, r => r.effDt, r => r.rateSts]);
    const sortedSchemeRows = sortTableRecords(schemeRows, schemeSort, [r => r.rateTyp, r => r.baseRateCd, r => r.fixedRatePct, r => r.minRatePct ?? r.fixedRatePct, r => r.maxRatePct ?? r.fixedRatePct, r => r.marginPct, r => r.effDt, r => r.rateSts]);
    const closeDialog = () => {
        if (busy) return;
        setConfirmSave(false);
        setDialog(null);
        setRevising(false);
        setRevisionBaseline(null);
        setBaseForm({rateCd: '', rateNm: '', ccyCd: 'MYR', ratePct: '', effDt: today()});
        setSchemeForm({
            rateTyp: 'FIXED',
            baseRateCd: '',
            fixedRatePct: '',
            minRatePct: '',
            maxRatePct: '',
            marginPct: '',
            effDt: today()
        });
    };
    // Editing a published rate creates a replacement effective-dated version.
    // Historical rates are immutable because already-booked contracts refer to them.
    const reviseBase = (row: BaseRate) => {
        setRevising(true);
        setBaseCode(row.rateCd);
        const nextForm = {
            rateCd: row.rateCd, rateNm: row.rateNm, ccyCd: row.ccyCd,
            ratePct: String(row.ratePct), effDt: nextVersionDate(baseRows[0]?.effDt)
        };
        setRevisionBaseline(baseEditKey(nextForm));
        setBaseForm(nextForm);
        setDialog('base');
    };
    const reviseScheme = (row: SchemeRate) => {
        setRevising(true);
        setSelectedScheme(String(row.schmId));
        const nextForm = {
            rateTyp: row.rateTyp, baseRateCd: row.baseRateCd ?? '',
            fixedRatePct: row.fixedRatePct == null ? '' : String(row.fixedRatePct),
            minRatePct: row.minRatePct == null ? '' : String(row.minRatePct),
            maxRatePct: row.maxRatePct == null ? '' : String(row.maxRatePct),
            marginPct: row.marginPct == null ? '' : String(row.marginPct),
            effDt: nextVersionDate(schemeRows[0]?.effDt)
        };
        setRevisionBaseline(schemeEditKey(nextForm));
        setSchemeForm(nextForm);
        setDialog('scheme');
    };
    const validBase = /^[A-Z0-9_-]{1,30}$/.test(baseForm.rateCd) &&
        baseForm.rateNm.trim().length > 0 && baseForm.rateNm.length <= 150 &&
        /^[A-Z]{3}$/.test(baseForm.ccyCd) && numericInRange(baseForm.ratePct) && Boolean(baseForm.effDt) &&
        (!revising || baseForm.effDt >= nextVersionDate(baseRows[0]?.effDt));
    const referenceRate = activeCatalog.find(rate => rate.rateCd === schemeForm.baseRateCd);
    const floatingEffectiveRate = schemeForm.rateTyp === 'FLOATING' && referenceRate && schemeForm.marginPct.trim() !== ''
        ? Number(referenceRate.ratePct) + Number(schemeForm.marginPct) : null;
    const validScheme = Boolean(selectedScheme && schemeForm.effDt) &&
        (!revising || schemeForm.effDt >= nextVersionDate(schemeRows[0]?.effDt)) &&
        (schemeForm.rateTyp === 'FLOATING'
            ? Boolean(schemeForm.baseRateCd && schemeForm.marginPct.trim() && referenceRate &&
                floatingEffectiveRate != null && Number.isFinite(floatingEffectiveRate) &&
                floatingEffectiveRate >= 0 && floatingEffectiveRate <= 100)
            : numericInRange(schemeForm.fixedRatePct) &&
            ((schemeForm.minRatePct === '' && schemeForm.maxRatePct === '') ||
                (numericInRange(schemeForm.minRatePct) && numericInRange(schemeForm.maxRatePct) &&
                    Number(schemeForm.minRatePct) <= Number(schemeForm.fixedRatePct) &&
                    Number(schemeForm.maxRatePct) >= Number(schemeForm.fixedRatePct))));
    const rateRangeStarted = schemeForm.minRatePct.trim() !== '' || schemeForm.maxRatePct.trim() !== '';
    const hasPendingChanges = !revising || revisionBaseline !==
        (dialog === 'base' ? baseEditKey(baseForm) : schemeEditKey(schemeForm));
    const canSave = (dialog === 'base' ? validBase : validScheme) && !busy;
    const save = async () => {
        if (!canSave || !confirmSave || !canEdit || saveInFlight.current || !hasPendingChanges) return;
        if (dialog === 'scheme' && schemeForm.rateTyp === 'FIXED' && ((schemeForm.minRatePct === '') !== (schemeForm.maxRatePct === '')
            || (schemeForm.minRatePct !== '' && (Number(schemeForm.minRatePct) > Number(schemeForm.fixedRatePct)
                || Number(schemeForm.maxRatePct) < Number(schemeForm.fixedRatePct))))) {
            notify('warning', 'Provide a valid minimum and maximum rate containing the fixed scheme rate.');
            return;
        }
        saveInFlight.current = true;
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
                setAppliedBaseCode(baseForm.rateCd.trim().toUpperCase());
                await loadCatalog();
                const code = baseForm.rateCd.trim().toUpperCase();
                setBaseRows((await api.get<BaseRate[]>(`/rates/base/${encodeURIComponent(code)}/history`)).data);
            } else {
                if (!selectedScheme) throw new Error('Select a scheme first.');
                await api.post('/rates/scheme', {
                    schmId: Number(selectedScheme),
                    rateTyp: schemeForm.rateTyp,
                    baseRateCd: schemeForm.rateTyp === 'FLOATING' ? schemeForm.baseRateCd.trim().toUpperCase() : null,
                    fixedRatePct: schemeForm.rateTyp === 'FIXED' ? Number(schemeForm.fixedRatePct) : null,
                    minRatePct: schemeForm.rateTyp === 'FIXED' && schemeForm.minRatePct !== '' ? Number(schemeForm.minRatePct) : null,
                    maxRatePct: schemeForm.rateTyp === 'FIXED' && schemeForm.maxRatePct !== '' ? Number(schemeForm.maxRatePct) : null,
                    marginPct: schemeForm.rateTyp === 'FLOATING' ? Number(schemeForm.marginPct) : null,
                    effDt: schemeForm.effDt
                });
                setAppliedScheme(selectedScheme);
                setSchemeRows((await api.get<SchemeRate[]>(`/rates/scheme/${selectedScheme}/history`)).data)
            }
            notify('success', revising ? 'Rate revision saved as a new effective-dated version.' : 'Rate version created.');
            setConfirmSave(false);
            setDialog(null);
            setRevising(false);
            setRevisionBaseline(null);
            setBaseForm({rateCd: '', rateNm: '', ccyCd: 'MYR', ratePct: '', effDt: today()});
            setSchemeForm({
                rateTyp: 'FIXED',
                baseRateCd: '',
                fixedRatePct: '',
                minRatePct: '',
                maxRatePct: '',
                marginPct: '',
                effDt: today()
            });
        } catch (err) {
            notify('error', errorText(err))
        } finally {
            saveInFlight.current = false;
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
        <PageHeader
            title="Rate Maintenance"
            description="Maintain effective-dated base and scheme rates without overwriting historical versions."
            actions={<RefreshButton loading={refreshing} onClick={() => void refreshScreen()}/>}
        />
        <section className="maint-panel">
            <div className="maint-section-heading"><h2>Base Rate History</h2>
                {canEdit && <button className="maint-primary cbms-action-create" type="button" onClick={() => {
                    closeDialog();
                    setDialog('base');
                }}>New Base Rate</button>}
            </div>
            <div className="maint-toolbar"><label>Base Rate Code
                <select value={baseCode} onChange={event => setBaseCode(event.target.value)}
                        aria-label="Select base rate code">
                    <option value="">Select base rate code</option>
                    {catalog.map(option => <option key={option.rateCd} value={option.rateCd}>
                        {option.rateCd} — {option.rateNm} ({option.ccyCd})
                    </option>)}
                </select></label>
                <div className="cbms-search-actions">
                    <button type="button" className="cbms-action-search"
                            onClick={() => {
                                if (!baseCode) {
                                    notify('warning', 'Please select a base rate code.');
                                    return;
                                }
                                setBasePage(0);
                                setAppliedBaseCode(baseCode);
                                if (appliedBaseCode === baseCode) void loadBase();
                            }}>Search
                    </button>
                    <button type="button" className="cbms-action-clear" onClick={() => {
                        setBasePage(0);
                        setBaseCode('');
                        setAppliedBaseCode('');
                        setBaseRows([]);
                    }}>Clear
                    </button>
                </div>
            </div>
            {catalogError && <p className="maint-note" role="alert">Unable to load base rate codes: {catalogError}</p>}
            <div className="maint-scroll">
                <SortableTable sortState={baseSort} onSortChange={sort => { setBaseSort(sort); setBasePage(0); }}>
                    <thead>
                    <tr>
                        <th>Base Rate Code</th>
                        <th>Base Rate Name</th>
                        <th>Currency</th>
                        <th>Rate (%)</th>
                        <th>Effective Date</th>
                        <th>Status</th>
                        {canEdit && <th>Action</th>}
                    </tr>
                    </thead>
                    <tbody>{sortedBaseRows.slice(currentBasePage * 10, (currentBasePage + 1) * 10).map(r => <tr key={r.baseRateId}>
                        <td>{r.rateCd}</td>
                        <td>{r.rateNm}</td>
                        <td>{r.ccyCd}</td>
                        <td>{r.ratePct}</td>
                        <td>{r.effDt}</td>
                        <td><StatusBadge status={r.rateSts}/></td>
                        {canEdit && <td>
                            <button type="button" className="cbms-action-details" onClick={() => reviseBase(r)}>Edit
                            </button>
                        </td>}
                    </tr>)}{!baseRows.length && <tr>
                        <td colSpan={canEdit ? 7 : 6}>Select a base rate code to view its history.</td>
                    </tr>}</tbody>
                </SortableTable>
            </div>
            <TablePagination page={currentBasePage} totalPages={Math.max(1, Math.ceil(baseRows.length / 10))}
                             totalElements={baseRows.length} onPageChange={setBasePage} busy={busy || refreshing}
                             className="maint-pagination"/>
        </section>
        <section className="maint-panel">
            <div className="maint-section-heading"><h2>Scheme Rate History</h2>
                {canEdit && <button className="maint-primary cbms-action-create" type="button"
                                    onClick={() => {
                                        if (!selectedScheme) {
                                            notify('warning', 'Please select a scheme first.');
                                            return;
                                        }
                                        closeDialog();
                                        setDialog('scheme');
                                    }}>New Scheme Rate</button>}
            </div>
            <div className="maint-toolbar"><label>Scheme<select value={selectedScheme} onChange={e => {
                setSelectedScheme(e.target.value);
                setEffective(null)
            }}>
                <option value="">Select Scheme</option>
                {schemes.map(s => <option key={s.schmId} value={s.schmId}>{s.schmCd} — {s.schmNm}</option>)}
            </select></label>
                <div className="cbms-search-actions">
                    <button className="cbms-action-search" onClick={() => {
                        setSchemePage(0);
                        setAppliedScheme(selectedScheme);
                        if (appliedScheme === selectedScheme) void loadScheme();
                    }}>Search
                    </button>
                    <button className="cbms-action-clear" onClick={() => {
                        setSchemePage(0);
                        setSelectedScheme('');
                        setAppliedScheme('');
                        setSchemeRows([]);
                        setEffective(null);
                    }}>Clear
                    </button>
                </div>
            </div>
            <div className="maint-scroll">
                <SortableTable sortState={schemeSort} onSortChange={sort => { setSchemeSort(sort); setSchemePage(0); }}>
                    <thead>
                    <tr>
                        <th>Type</th>
                        <th>Base Rate</th>
                        <th>Fixed Rate (%)</th>
                        <th>Minimum Rate (%)</th>
                        <th>Maximum Rate (%)</th>
                        <th>Margin (%)</th>
                        <th>Effective Date</th>
                        <th>Status</th>
                        {canEdit && <th>Action</th>}
                    </tr>
                    </thead>
                    <tbody>{sortedSchemeRows.slice(currentSchemePage * 10, (currentSchemePage + 1) * 10).map(r => <tr key={r.schmRateId}>
                        <td>{formatBusinessValue(r.rateTyp)}</td>
                        <td>{r.baseRateCd || '—'}</td>
                        <td>{r.fixedRatePct ?? '—'}</td>
                        <td>{r.minRatePct ?? r.fixedRatePct ?? '—'}</td>
                        <td>{r.maxRatePct ?? r.fixedRatePct ?? '—'}</td>
                        <td>{r.marginPct ?? '—'}</td>
                        <td>{r.effDt}</td>
                        <td><StatusBadge status={r.rateSts}/></td>
                        {canEdit && <td>
                            <button type="button" className="cbms-action-details" onClick={() => reviseScheme(r)}>Edit
                            </button>
                        </td>}
                    </tr>)}{!schemeRows.length && <tr>
                        <td colSpan={canEdit ? 9 : 8}>Select a scheme to view its rate history.</td>
                    </tr>}</tbody>
                </SortableTable>
            </div>
            <TablePagination page={currentSchemePage} totalPages={Math.max(1, Math.ceil(schemeRows.length / 10))}
                             totalElements={schemeRows.length} onPageChange={setSchemePage} busy={busy || refreshing}
                             className="maint-pagination"/>
            <div className="maint-toolbar maint-effective"><label>Rate as of<input type="date" value={effectiveDate}
                                                                                   onChange={e => {
                                                                                       setEffectiveDate(e.target.value);
                                                                                       setEffective(null)
                                                                                   }}/></label>
                <button onClick={() => {
                    if (!selectedScheme || !effectiveDate) {
                        notify('warning', 'Please select a scheme and an effective date.');
                        return;
                    }
                    void resolve();
                }}>Resolve Effective
                    Rate
                </button>
                {effective !== null && <strong>Effective Rate: {effective}%</strong>}</div>
        </section>
        {dialog && <div className="maint-overlay">
            <section className="maint-dialog" role="dialog" aria-modal="true">
                <div className="maint-dialog-head">
                    <SubscreenTitle as="h2"
                        title={revising ? dialog === 'base' ? 'Edit Base Rate' : 'Edit Scheme Rate' : dialog === 'base' ? 'New Base Rate Version' : 'New Scheme Rate Version'}
                        reference={revising ? dialog === 'base' ? baseForm.rateCd : schemes.find(s => s.schmId === Number(selectedScheme))?.schmCd : undefined}/>
                    <button type="button" aria-label="Close" onClick={closeDialog}><CloseIcon/></button>
                </div>
                <form onSubmit={e => {
                    e.preventDefault();
                    if (busy || !canEdit) return;
                    if (!canSave) {
                        notify('warning', 'Please correct the required rate fields and ensure the rate is valid.');
                        return;
                    }
                    if (dialog === 'scheme' && schemeForm.rateTyp === 'FIXED' && (
                        (schemeForm.minRatePct === '') !== (schemeForm.maxRatePct === '') ||
                        (schemeForm.minRatePct !== '' && (Number(schemeForm.minRatePct) > Number(schemeForm.fixedRatePct) ||
                            Number(schemeForm.maxRatePct) < Number(schemeForm.fixedRatePct))))) {
                        notify('warning', 'Provide valid minimum and maximum rates containing the fixed scheme rate.');
                        return;
                    }
                    if (!hasPendingChanges) {
                        notify('warning', 'No changes to save.');
                        return;
                    }
                    setConfirmSave(true);
                }}>
                    <div className="maint-grid">{dialog === 'base' ? <><label><FieldLabel required>Rate
                        Code</FieldLabel><input autoFocus required readOnly={revising} maxLength={30}
                                                pattern="[A-Z0-9_-]+"
                                                value={baseForm.rateCd}
                                                onChange={e => setBaseForm({
                                                    ...baseForm,
                                                    rateCd: e.target.value.toUpperCase()
                                                })}/></label><label><FieldLabel required>Rate Name</FieldLabel><input
                        required maxLength={150} value={baseForm.rateNm}
                        onChange={e => setBaseForm({...baseForm, rateNm: e.target.value})}/></label><label><FieldLabel
                        required>Currency</FieldLabel><input required maxLength={3} pattern="[A-Z]{3}"
                                                             value={baseForm.ccyCd}
                                                             onChange={e => setBaseForm({
                                                                 ...baseForm,
                                                                 ccyCd: e.target.value.toUpperCase()
                                                             })}/></label><label><FieldLabel required>Rate
                        (%)</FieldLabel><input required type="number" min="0" max="100" step="any"
                                               value={baseForm.ratePct}
                                               onChange={e => setBaseForm({
                                                   ...baseForm,
                                                   ratePct: e.target.value
                                               })}/></label><label><FieldLabel required>Effective
                        Date</FieldLabel><input required
                                                type="date"
                                                min={revising ? nextVersionDate(baseRows[0]?.effDt) : undefined}
                                                value={baseForm.effDt}
                                                onChange={e => setBaseForm({
                                                    ...baseForm,
                                                    effDt: e.target.value
                                                })}/></label></> : <>
                        <label><FieldLabel required>Rate Type</FieldLabel><select value={schemeForm.rateTyp}
                                                                                  onChange={e => setSchemeForm({
                                                                                      ...schemeForm,
                                                                                      rateTyp: e.target.value,
                                                                                      baseRateCd: '',
                                                                                      fixedRatePct: '',
                                                                                      minRatePct: '',
                                                                                      maxRatePct: '',
                                                                                      marginPct: ''
                                                                                  })}>
                            <option value="FIXED">{formatBusinessValue('FIXED')}</option>
                            <option value="FLOATING">{formatBusinessValue('FLOATING')}</option>
                        </select></label>{schemeForm.rateTyp === 'FIXED' ?
                        <><label><FieldLabel required>Fixed Rate (%)</FieldLabel><input required type="number" min="0"
                                                                                        max="100" step="any"
                                                                                        value={schemeForm.fixedRatePct}
                                                                                        onChange={e => setSchemeForm({
                                                                                            ...schemeForm,
                                                                                            fixedRatePct: e.target.value
                                                                                        })}/></label><label><FieldLabel
                            required={rateRangeStarted}>Minimum Effective Rate (%)</FieldLabel><input
                            required={rateRangeStarted} type="number" min="0" max="100" step="0.01"
                            aria-required={rateRangeStarted} value={schemeForm.minRatePct}
                            onChange={e => setSchemeForm({...schemeForm, minRatePct: e.target.value})}
                            placeholder={rateRangeStarted ? 'Enter minimum rate' : 'Optional – same as fixed rate'}/></label>
                            <label><FieldLabel required={rateRangeStarted}>Maximum Effective Rate (%)</FieldLabel><input
                                required={rateRangeStarted} type="number" min="0" max="100" step="0.01"
                                aria-required={rateRangeStarted} value={schemeForm.maxRatePct}
                                onChange={e => setSchemeForm({...schemeForm, maxRatePct: e.target.value})}
                                placeholder={rateRangeStarted ? 'Enter maximum rate' : 'Optional – same as fixed rate'}/></label></> : <>
                            <label><FieldLabel required>Base Rate Code</FieldLabel><select required
                                                                                           value={schemeForm.baseRateCd}
                                                                                           onChange={e => setSchemeForm({
                                                                                               ...schemeForm,
                                                                                               baseRateCd: e.target.value
                                                                                           })}>
                                <option value="">Select an active base rate code</option>
                                {activeCatalog.map(rate => <option key={rate.rateCd} value={rate.rateCd}>
                                    {rate.rateCd} — {rate.rateNm} ({rate.ccyCd})
                                </option>)}
                            </select>{!activeCatalog.length &&
                                <small className="maint-note">No active base rates are available for the selected
                                    effective date.</small>}</label><label><FieldLabel required>Margin
                            (%)</FieldLabel><input required type="number" step="any" value={schemeForm.marginPct}
                                                   onChange={e => setSchemeForm({
                                                       ...schemeForm,
                                                       marginPct: e.target.value
                                                   })}/></label>
                            <label><FieldLabel>Current Effective Rate (%)</FieldLabel><input readOnly
                                                                                             value={floatingEffectiveRate == null || !Number.isFinite(floatingEffectiveRate)
                                                                                                 ? '—' : `${floatingEffectiveRate.toFixed(2)}%`}
                                                                                             title="Current base rate plus scheme margin"/>
                            </label></>}<label><FieldLabel required>Effective Date</FieldLabel><input required
                                                                                                      type="date"
                                                                                                      min={revising ? nextVersionDate(schemeRows[0]?.effDt) : undefined}
                                                                                                      value={schemeForm.effDt}
                                                                                                      onChange={e => setSchemeForm({
                                                                                                          ...schemeForm,
                                                                                                          effDt: e.target.value,
                                                                                                          baseRateCd: ''
                                                                                                      })}/></label></>}</div>
                    <p className="maint-note">{revising
                        ? 'This edit publishes a new effective-dated rate version. Historical rate records and booked contracts are not overwritten.'
                        : 'Saving creates a new effective-dated rate record. Existing rate history is preserved.'}</p>
                    <div className="maint-footer">
                        <button type="button" onClick={closeDialog}>Cancel</button>
                        <button type="submit" disabled={busy}
                                className="maint-primary">{busy ? 'Saving…' : revising ? 'Save Changes' : 'Create'}</button>
                    </div>
                </form>
            </section>
        </div>}{confirmSave && dialog && <ConfirmDialog
        title={revising ? 'Confirm Update' : 'Confirm Creation'}
        message={revising ? 'Are you sure you want to save these changes as a new rate version?' : 'Are you sure you want to create this rate?'}
        confirmLabel={revising ? 'Save Changes' : 'Create'} busy={busy}
        details={dialog === 'base' ? [
            {label: 'Rate Code', value: baseForm.rateCd},
            {label: 'Rate Name', value: baseForm.rateNm},
            {label: 'Currency', value: baseForm.ccyCd},
            {label: 'Rate (%)', value: baseForm.ratePct},
            {label: 'Effective Date', value: baseForm.effDt}
        ] : [
            {label: 'Scheme', value: schemes.find(s => String(s.schmId) === selectedScheme)?.schmNm ?? '—'},
            {label: 'Rate Type', value: schemeForm.rateTyp},
            {
                label: schemeForm.rateTyp === 'FLOATING' ? 'Base Rate' : 'Fixed Rate (%)',
                value: schemeForm.rateTyp === 'FLOATING' ? schemeForm.baseRateCd : schemeForm.fixedRatePct
            },
            {
                label: schemeForm.rateTyp === 'FLOATING' ? 'Margin (%)' : 'Rate Range (%)',
                value: schemeForm.rateTyp === 'FLOATING' ? schemeForm.marginPct :
                    (schemeForm.minRatePct || schemeForm.maxRatePct ? `${schemeForm.minRatePct} – ${schemeForm.maxRatePct}` : 'Not set')
            },
            {label: 'Effective Date', value: schemeForm.effDt}
        ]}
        notice="Publishing a new rate version may affect future product pricing and subsequent floating-rate calculations."
        onCancel={() => {
            if (!busy) setConfirmSave(false);
        }}
        onConfirm={() => void save()}/>}</main>;
}
import {formatBusinessValue} from '../utils/display';
