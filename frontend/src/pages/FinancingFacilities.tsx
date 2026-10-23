import {useCallback, useEffect, useState, type FormEvent} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {AxiosError} from 'axios';
import {ArrowLeft, X} from 'lucide-react';
import {useToast} from '../components/toastContext';
import {useAuth} from '../auth/useAuth';
import {getCustomer} from '../api/customers';
import {
    financingFacilities as api,
    listFinancing,
    type FinancingFacility,
    type FinancingOutstanding,
    type FinancingScheduleEntry,
    type FinancingRepayment,
    type FinancingDisbursement,
    type FinancingClosure
} from '../api/financing';
import PopupView from "../components/PopupView.tsx";
import './FinancingFacilities.css';


type Tab = 'overview' | 'operations' | 'schedule' | 'repayments' | 'outstanding';
type Action = 'disburse' | 'repay' | 'schedule' | 'close';
const errorText = (e: unknown) => e instanceof AxiosError ? ((e.response?.data as {
    message?: string;
    detail?: string
} | undefined)?.detail || (e.response?.data as {
    message?: string
} | undefined)?.message || `Request failed (${e.response?.status || 'network'})`) : e instanceof Error ? e.message : 'Request failed';
const amount = (v: number | null | undefined, ccy = 'MYR') => `${ccy} ${Number(v ?? 0).toLocaleString('en-MY', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
})}`;
const date = (v: string | null | undefined) => v ? String(v).slice(0, 10) : '—';
const fmt = (v: unknown) => v === null || v === undefined || v === '' ? '—' : String(v);
const tabNames: Record<Tab, string> = {
    overview: 'Overview',
    operations: 'Operations',
    schedule: 'Repayment Schedule',
    repayments: 'Repayment History',
    outstanding: 'Outstanding'
};
const actionNames: Record<Action, string> = {
    disburse: 'Disburse',
    repay: 'Record Repayment',
    schedule: 'Generate Schedule',
    close: 'Close Facility'
};
export default function FinancingFacilities() {
    const {facilityId} = useParams();
    const navigate = useNavigate();
    const notify = useToast();
    const {session} = useAuth();
    const canMake = Boolean(session?.roles.some(r => r === 'MAKER' || r === 'ADMIN'));
    const [rows, setRows] = useState<FinancingFacility[]>([]);
    const [selected, setSelected] = useState<FinancingFacility | null>(null);
    const [customerNames, setCustomerNames] = useState<Record<number, string>>({});
    const [applicationReference, setApplicationReference] = useState<string | null>(null);
    const [filter, setFilter] = useState('');
    const [applied, setApplied] = useState('');
    const [page, setPage] = useState(0);
    const [tab, setTab] = useState<Tab>('overview');
    const [loading, setLoading] = useState(false);
    const [detailLoading, setDetailLoading] = useState(false);
    const [outstanding, setOutstanding] = useState<FinancingOutstanding | null>(null);
    const [schedule, setSchedule] = useState<FinancingScheduleEntry[]>([]);
    const [repayments, setRepayments] = useState<FinancingRepayment[]>([]);
    const [disbursement, setDisbursement] = useState<FinancingDisbursement | null>(null);
    const [closure, setClosure] = useState<FinancingClosure | null>(null);
    const [action, setAction] = useState<Action | null>(null);
    const [value, setValue] = useState('');
    const [reference, setReference] = useState('');
    const [extra, setExtra] = useState('');
    const [remarks, setRemarks] = useState('');
    const [busy, setBusy] = useState(false);
    const [requestKey, setRequestKey] = useState('');
    const refreshList = useCallback(async () => {
        setLoading(true);
        try {
            setRows(await api.list())
        } catch (e) {
            notify('error', errorText(e))
        } finally {
            setLoading(false)
        }
    }, [notify]);
    const refreshDetails = useCallback(async (id: number) => {
        setDetailLoading(true);
        try {
            const f = await api.get(id);
            setSelected(f);
            const [o, s, r, d, c] = await Promise.allSettled([api.outstanding(id), api.schedule(id), api.repayments(id), api.disbursement(id), api.closure(id)]);
            setOutstanding(o.status === 'fulfilled' ? o.value : null);
            setSchedule(s.status === 'fulfilled' ? s.value : []);
            setRepayments(r.status === 'fulfilled' ? r.value : []);
            setDisbursement(d.status === 'fulfilled' ? d.value : null);
            setClosure(c.status === 'fulfilled' ? c.value : null);
        } catch (e) {
            notify('error', errorText(e))
        } finally {
            setDetailLoading(false)
        }
    }, [notify]);
    useEffect(() => {
        void Promise.resolve().then(refreshList)
    }, [refreshList]);
    useEffect(() => {
        if (facilityId && /^\d+$/.test(facilityId)) void Promise.resolve().then(() => refreshDetails(Number(facilityId)))
    }, [facilityId, refreshDetails]);
    useEffect(() => {
        if (!selected?.applId) return;
        let live = true;
        void listFinancing().then(items => {
            if (live) setApplicationReference(items.find(item => item.applId === selected.applId)?.applRef ?? null);
        }).catch(() => {
            if (live) setApplicationReference(null);
        });
        return () => {
            live = false;
        };
    }, [selected?.applId]);
    useEffect(() => {
        const ids = [...new Set(rows.map(r => r.custId))].filter(id => !customerNames[id]);
        if (!ids.length) return;
        let cancelled = false;
        void Promise.all(ids.map(async id => {
            try {
                const c = await getCustomer(id);
                return [id, `${c.cifNo} · ${c.custNm}`] as const
            } catch {
                return [id, `Customer #${id}`] as const
            }
        })).then(pairs => {
            if (!cancelled) setCustomerNames(old => ({...old, ...Object.fromEntries(pairs)}))
        });
        return () => {
            cancelled = true
        };
    }, [rows, customerNames]);
    useEffect(() => {
        if (!selected?.custId || customerNames[selected.custId]) return;
        let active = true;
        void getCustomer(selected.custId).then(c => {
            if (active) setCustomerNames(old => ({...old, [c.custId]: `${c.cifNo} · ${c.custNm}`}));
        }).catch(() => {
        });
        return () => {
            active = false;
        };
    }, [selected?.custId, customerNames]);
    const list = rows.filter(r => !applied || r.facSts === applied);
    const visible = list.slice(page * 10, page * 10 + 10);
    const openAction = (a: Action) => {
        setValue(a === 'disburse' ? String(selected?.apprAmt ?? '') : '');
        setReference(a === 'disburse' ? (customerNames[selected?.custId ?? -1]?.split(' · ').slice(1).join(' · ') || '') : '');
        setExtra('');
        setRemarks('');
        setRequestKey(crypto.randomUUID());
        setAction(a)
    };
    const submit = async (e: FormEvent) => {
        e.preventDefault();
        if (!selected || !action) return;
        const id = selected.facId;
        setBusy(true);
        try {
            if (action === 'disburse') await api.disburse(id, {
                amount: Number(value),
                idempotencyKey: requestKey,
                beneficiaryName: reference.trim(),
                beneficiaryRef: extra.trim(),
                remarks: remarks.trim()
            });
            if (action === 'repay') await api.repay(id, {
                amount: Number(value),
                idempotencyKey: requestKey,
                paymentRef: reference.trim(),
                paymentChannel: extra.trim(),
                remarks: remarks.trim()
            });
            if (action === 'schedule') await api.generateSchedule(id, value);
            if (action === 'close') await api.close(id, remarks.trim());
            notify('success', `${actionNames[action]} completed.`);
            setAction(null);
            await Promise.all([refreshDetails(id), refreshList()]);
        } catch (err) {
            notify('error', errorText(err))
        } finally {
            setBusy(false)
        }
    };
    const active = selected?.facSts === 'ACTIVE';
    const draft = selected?.facSts === 'DRAFT';
    const eligibleHp = selected?.facTyp === 'HIRE_PURCHASE' && selected?.ccyCd === 'MYR';
    const fixedRate = selected?.rateTyp === 'FIXED';
    const ccy = selected?.ccyCd || 'MYR';
    const actions: Action[] = selected ? [
        ...((draft && schedule.length === 0 && eligibleHp && fixedRate) ? ['schedule' as Action] : []),
        ...((draft && schedule.length > 0 && !disbursement && eligibleHp && fixedRate) ? ['disburse' as Action] : []),
        ...((active && Boolean(disbursement) && schedule.length > 0 && eligibleHp) ? ['repay' as Action] : []),
        ...((active && outstanding && Number(outstanding.totalUnpaid) <= 0 && Number(outstanding.principalOutstanding) <= 0 && Number(outstanding.remainingScheduledInterest) <= 0) ? ['close' as Action] : [])
    ] : [];
    return <div className="ff-page">
        {!facilityId ? <>
                <header className="ff-header"><h1>Financing Facilities</h1><p>Manage booked financing facilities,
                    repayments, schedules and outstanding balances.</p></header>
                <section className="ff-panel">
                    <div className="ff-search"><label>Status<select value={filter}
                                                                    onChange={e => setFilter(e.target.value)}>
                        <option value="">All Statuses</option>
                        {['DRAFT', 'ACTIVE', 'CLOSED', 'SUSPENDED'].map(s => <option key={s}>{s}</option>)}</select></label>
                        <button type="button" onClick={() => {
                            setApplied('');
                            setFilter('');
                            setPage(0)
                        }}>Clear
                        </button>
                        <button type="button" className="ff-primary" onClick={() => {
                            setApplied(filter);
                            setPage(0)
                        }}>Search
                        </button>
                    </div>
                </section>
                <section className="ff-panel"><h2>Financing Facility List <small>({list.length} records)</small></h2>
                    <div className="ff-scroll">
                        <table>
                            <thead>
                            <tr>
                                <th>Customer</th>
                                <th>Reference</th>
                                <th>Status</th>
                                <th>Facility Type</th>
                                <th>Approved Amount</th>
                                <th>Action</th>
                            </tr>
                            </thead>
                            <tbody>{visible.map(r => <tr key={r.facId}>
                                <td>{customerNames[r.custId] || `Customer #${r.custId}`}</td>
                                <td><PopupView value={r.facRef} maxLength={10}/></td>
                                <td><span className="ff-badge">{r.facSts}</span></td>
                                <td>{r.facTyp}</td>
                                <td>{amount(r.apprAmt, r.ccyCd)}</td>
                                <td>
                                    <button type="button" onClick={() => {
                                        setTab('overview');
                                        navigate(`/financing/facilities/${r.facId}`)
                                    }}>View Details
                                    </button>
                                </td>
                            </tr>)}{!visible.length && <tr>
                                <td colSpan={6}
                                    className="ff-empty">{loading ? 'Loading facilities…' : 'No facilities found.'}</td>
                            </tr>}</tbody>
                        </table>
                    </div>
                    <div className="ff-pagination">
                        <button disabled={page === 0} onClick={() => setPage(p => p - 1)}>Previous</button>
                        <span>Page {page + 1} of {Math.max(1, Math.ceil(list.length / 10))}</span>
                        <button disabled={(page + 1) * 10 >= list.length} onClick={() => setPage(p => p + 1)}>Next</button>
                    </div>
                </section>
            </> :
            <>
                <div className="ff-detail-top">
                    <button className="ff-back" type="button" onClick={() => {
                        navigate('/financing/facilities')
                    }}><ArrowLeft size={18}/> Back to
                        Financing Facilities
                    </button>
                </div>
                <section className="ff-panel ff-detail">
                    <header className="ff-detail-heading"><h1>Financing Facility
                        – {selected?.facRef || facilityId}</h1>{selected &&
                        <span className="ff-badge">{selected.facSts}</span>}</header>
                    <nav className="ff-tabs">{(Object.keys(tabNames) as Tab[]).map(t => <button type="button" key={t}
                                                                                                className={tab === t ? 'active' : ''}
                                                                                                onClick={() => setTab(t)}>{tabNames[t]}</button>)}</nav>
                    {detailLoading && <p className="ff-muted">Refreshing facility information…</p>}
                    {selected && <>
                        {tab === 'overview' && <><h2>Facility Overview</h2>
                            <div
                                className="ff-facts">{[['Facility Reference', selected.facRef], ['Customer', customerNames[selected.custId] || `Customer #${selected.custId}`], ['Facility Type', selected.facTyp], ['Approved Amount', amount(selected.apprAmt, ccy)], ['Interest Rate', `${selected.effRate}%`], ['Tenor', `${selected.tenorMth} months`], ['Booking Date', date(selected.bookDt)], ['Maturity Date', date(selected.maturityDt)], ['Status', selected.facSts], ...(selected.applId != null ? [['Financing Application Reference', applicationReference ?? '—']] : [])].map(([k, v]) =>
                                <div key={k}><small>{k}</small><strong>{v}</strong></div>)}</div>
                        </>}
                        {tab === 'operations' && <><h2>Facility Operations</h2>
                            <div className="ff-actions">{canMake && actions.map(a => <button key={a} type="button"
                                                                                             disabled={a === 'disburse' && !customerNames[selected.custId]?.includes(' · ')}
                                                                                             onClick={() => openAction(a)}>{actionNames[a]}</button>)}
                                <button type="button" onClick={() => void refreshDetails(selected.facId)}>Refresh
                                </button>
                                {canMake && actions.length === 0 &&
                                    <span className="ff-muted">No financial actions available for the current facility state.</span>}
                            </div>
                            <p className="ff-muted">Disbursement is available for eligible DRAFT hire-purchase
                                facilities. Generate the repayment schedule before disbursement, then record repayments
                                after activation. The backend enforces all financial validations.</p>{disbursement &&
                                <p>Disbursement: {disbursement.disbRef} · {amount(disbursement.disbAmt, ccy)} · {disbursement.disbSts}</p>}{closure &&
                                <p>Closure: {closure.closureRef} · {closure.facilityStatus}</p>}</>}
                        {tab === 'schedule' && <><h2>Repayment Schedule <small>({schedule.length} instalments)</small>
                        </h2>
                            <div className="ff-scroll">
                                <table>
                                    <thead>
                                    <tr>
                                        <th>Instalment</th>
                                        <th>Due Date</th>
                                        <th>Principal</th>
                                        <th>Interest</th>
                                        <th>Total</th>
                                        <th>Paid Principal</th>
                                        <th>Paid Interest</th>
                                        <th>Status</th>
                                    </tr>
                                    </thead>
                                    <tbody>{schedule.map(r => <tr key={r.schedId}>
                                        <td>{r.instNo}</td>
                                        <td>{date(r.dueDt)}</td>
                                        <td>{amount(r.principalAmt, ccy)}</td>
                                        <td>{amount(r.interestAmt, ccy)}</td>
                                        <td>{amount(r.instAmt, ccy)}</td>
                                        <td>{amount(r.paidPrincipal, ccy)}</td>
                                        <td>{amount(r.paidInterest, ccy)}</td>
                                        <td>{r.instSts}</td>
                                    </tr>)}{!schedule.length && <tr>
                                        <td colSpan={8} className="ff-empty">No repayment schedule available.</td>
                                    </tr>}</tbody>
                                </table>
                            </div>
                        </>}
                        {tab === 'repayments' && <><h2>Repayment History <small>({repayments.length} records)</small>
                        </h2>
                            <div className="ff-scroll">
                                <table>
                                    <thead>
                                    <tr>
                                        <th>Reference</th>
                                        <th>Amount</th>
                                        <th>Payment Reference</th>
                                        <th>Channel</th>
                                        <th>Status</th>
                                    </tr>
                                    </thead>
                                    <tbody>{repayments.map(r => <tr key={r.repayId}>
                                        <td>{r.repayRef}</td>
                                        <td>{amount(r.repayAmt, ccy)}</td>
                                        <td>{fmt(r.paymentRef)}</td>
                                        <td>{fmt(r.paymentChannel)}</td>
                                        <td>{r.repaySts}</td>
                                    </tr>)}{!repayments.length && <tr>
                                        <td colSpan={5} className="ff-empty">No repayments recorded.</td>
                                    </tr>}</tbody>
                                </table>
                            </div>
                        </>}
                        {tab === 'outstanding' && <><h2>Outstanding Balances</h2>{outstanding ? <div
                                className="ff-facts">{[['Approved Amount', amount(outstanding.approvedAmount, ccy)], ['Disbursed Principal', amount(outstanding.disbursedPrincipal, ccy)], ['Principal Paid', amount(outstanding.principalPaid, ccy)], ['Principal Outstanding', amount(outstanding.principalOutstanding, ccy)], ['Interest Paid', amount(outstanding.interestPaid, ccy)], ['Remaining Interest', amount(outstanding.remainingScheduledInterest, ccy)], ['Total Unpaid', amount(outstanding.totalUnpaid, ccy)], ['Overdue Amount', amount(outstanding.overdueAmount, ccy)], ['Next Due Date', date(outstanding.nextDueDate)], ['Next Due Amount', amount(outstanding.nextDueAmount, ccy)], ['Repayment Progress', `${outstanding.repaymentProgressPct}%`]].map(([k, v]) =>
                                <div key={k}><small>{k}</small><strong>{v}</strong></div>)}</div> :
                            <p className="ff-muted">Outstanding information is not available.</p>}</>}
                    </>}</section>
            </>}
        {action && selected && <div className="ff-overlay">
            <div className="ff-dialog">
                <div className="ff-dialog-head"><h2>{actionNames[action]}</h2>
                    <button type="button" disabled={busy} aria-label="Close" onClick={() => setAction(null)}><X
                        size={20}/></button>
                </div>
                <p className="ff-muted">{selected.facRef}</p>
                <form onSubmit={e => void submit(e)}>
                    {action === 'schedule' ? <label>First Due Date *<input required type="date" value={value}
                                                                           onChange={e => setValue(e.target.value)}/></label> : action !== 'close' && <>
                        <label>Amount ({ccy}) *<input required type="number" min="0.01" step="0.01" value={value}
                                                      onChange={e => setValue(e.target.value)}/></label><label>{action === 'disburse' ? 'Beneficiary Name' : 'Payment Reference'} *<input
                        required maxLength={action === 'disburse' ? 200 : 100} value={reference}
                        onChange={e => setReference(e.target.value)}/></label><label>{action === 'disburse' ? 'Beneficiary Reference' : 'Payment Channel'} *
                        {action === 'disburse' ?
                            <input required maxLength={100} value={extra} onChange={e => setExtra(e.target.value)}/> :
                            <select required value={extra} onChange={e => setExtra(e.target.value)}>
                                <option value="">Select Payment Channel</option>
                                <option value="BANK_TRANSFER">Bank Transfer</option>
                                <option value="CASH">Cash</option>
                                <option value="INTERNAL_TRANSFER">Internal Transfer</option>
                                <option value="OTHER">Other</option>
                            </select>}</label></>}
                    {action !== 'schedule' && <label className="ff-wide">Remarks<textarea value={remarks}
                                                                                          onChange={e => setRemarks(e.target.value)}/></label>}
                    {action === 'close' &&
                        <p className="ff-warning">Confirm closure only after all outstanding obligations have been
                            settled.</p>}
                    <div className="ff-footer">
                        <button type="button" disabled={busy} onClick={() => setAction(null)}>Cancel</button>
                        <button type="submit" className="ff-primary"
                                disabled={busy}>{busy ? 'Processing…' : actionNames[action]}</button>
                    </div>
                </form>
            </div>
        </div>}
    </div>;
}
