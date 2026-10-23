import {useCallback, useEffect, useRef, useState, type FormEvent} from 'react';
import {X, ArrowLeft} from 'lucide-react';
import {useNavigate, useParams} from 'react-router-dom';
import {AxiosError} from 'axios';
import {creditLines, type Monitoring, type Entry, type FloorStock} from '../api/creditLines';
import {getCustomer, searchCustomers, type Customer} from '../api/customers';
import {listProducts, listSchemes, type Product, type Scheme} from '../api/accounts';
import {useAuth} from '../auth/useAuth';
import {useToast} from '../components/toastContext';
import PopupView from '../components/PopupView';
import '../components/AccountLov.css';
import './CreditLines.css';

const money = (n?: number | null, c = 'MYR') => n == null ? '—' : `${c} ${Number(n).toLocaleString('en-MY', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
})}`;
const failure = (e: unknown) => e instanceof AxiosError ? String((e.response?.data as {
    detail?: string;
    message?: string
} | undefined)?.detail || (e.response?.data as {
    message?: string
} | undefined)?.message || `Request failed (${e.response?.status || 'network'})`) : e instanceof Error ? e.message : 'Request failed';
type Action = 'activate' | 'adjust' | 'drawdown' | 'repay' | 'suspend' | 'reactivate' | 'close' | 'accrue';
const captions: Record<Action, string> = {
    activate: 'Activate Facility',
    adjust: 'Adjust Limit',
    drawdown: 'Drawdown',
    repay: 'Repayment',
    suspend: 'Suspend Facility',
    reactivate: 'Reactivate Facility',
    close: 'Close Facility',
    accrue: 'Accrue Interest'
};
const today = () => new Date().toLocaleDateString('en-CA');
export default function CreditLines() {
    const navigate = useNavigate();
    const {facilityId} = useParams();
    const toast = useToast();
    const {session} = useAuth();
    const canMake = Boolean(session?.roles.some(r => r === 'MAKER' || r === 'ADMIN'));
    const isAdmin = Boolean(session?.roles.includes('ADMIN'));
    const [customerLabels, setCustomerLabels] = useState<Record<number, string>>({});
    const [rows, setRows] = useState<Monitoring[]>([]), [loading, setLoading] = useState(false), [refresh, setRefresh] = useState(0), [status, setStatus] = useState(''), [applied, setApplied] = useState(''), [page, setPage] = useState(0), [error, setError] = useState('');
    const [dialog, setDialog] = useState<'create' | 'detail' | 'action' | null>(null), [action, setAction] = useState<Action>('activate'), [busy, setBusy] = useState(false);
    const [history, setHistory] = useState<Record<string, Entry[]>>({}), [historyTab, setHistoryTab] = useState('drawdowns'), [summary, setSummary] = useState<Entry | null>(null), [tab, setTab] = useState('overview');
    const [lovSearch, setLovSearch] = useState(0);
    const queryRef = useRef('');
    const [customers, setCustomers] = useState<Customer[]>([]), [query, setQuery] = useState(''), [lov, setLov] = useState(false), [lovPage, setLovPage] = useState(0), [lovPages, setLovPages] = useState(0), [customer, setCustomer] = useState<Customer | null>(null);
    const [products, setProducts] = useState<Product[]>([]), [schemes, setSchemes] = useState<Scheme[]>([]), [productId, setProductId] = useState(''), [schemeId, setSchemeId] = useState(''), [type, setType] = useState('REVOLVING_CREDIT'), [rateType, setRateType] = useState('FIXED'), [limit, setLimit] = useState(''), [rate, setRate] = useState(''), [start, setStart] = useState(today()), [expiry, setExpiry] = useState(''), [remarks, setRemarks] = useState('');
    const [stocks, setStocks] = useState<FloorStock[]>([]);
    const [stocksLoading, setStocksLoading] = useState(false);
    const [stockDialog, setStockDialog] = useState<'finance' | 'settle' | null>(null);
    const [stockToSettle, setStockToSettle] = useState<FloorStock | null>(null);
    const [stockVin, setStockVin] = useState(''), [stockMake, setStockMake] = useState(''), [stockModel, setStockModel] = useState('');
    const [stockYear, setStockYear] = useState(''), [stockPurchase, setStockPurchase] = useState(''), [stockAmount, setStockAmount] = useState('');
    const [stockRemarks, setStockRemarks] = useState(''), [settlementRef, setSettlementRef] = useState(''), [settlementReason, setSettlementReason] = useState('');
    const [value, setValue] = useState(''), [reference, setReference] = useState(''), [reason, setReason] = useState(''), [date, setDate] = useState(today());
    const reload = useCallback(() => setRefresh(v => v + 1), []);
    useEffect(() => {
        let active = true;
        creditLines.monitoring().then(async data => {
            if (!active) return;
            setRows(data);
            setError('');
            const ids = [...new Set(data.map(r => r.customerId).filter(id => Number.isFinite(id)))];
            const results = await Promise.all(ids.map(async id => {
                try {
                    const c = await getCustomer(id);
                    return [id, `${c.cifNo} · ${c.custNm}`] as const
                } catch {
                    return [id, '—'] as const
                }
            }));
            if (active) setCustomerLabels(Object.fromEntries(results))
        }).catch(e => {
            if (active) setError(failure(e))
        }).finally(() => {
            if (active) setLoading(false)
        });
        return () => {
            active = false
        }
    }, [refresh]);
    useEffect(() => {
        listProducts().then(data => setProducts(data.filter(p => p.prodSts === 'ACTIVE' && ['CREDIT_LINE', 'FLOOR_STOCKING'].includes(p.prodCat)))).catch(e => setError(failure(e)))
    }, []);
    useEffect(() => {
        if (!productId) {
            return
        }
        listSchemes(Number(productId)).then(data => setSchemes(data.filter(s => s.schmSts === 'ACTIVE'))).catch(e => setError(failure(e)))
    }, [productId]);
    useEffect(() => {
        if (!lov) return;
        let active = true;
        searchCustomers({name: queryRef.current, status: 'ACTIVE', page: lovPage, size: 10}).then(data => {
            if (active) {
                setCustomers(data.content);
                setLovPages(data.totalPages)
            }
        }).catch(e => {
            if (active) setError(failure(e))
        });
        return () => {
            active = false
        }
    }, [lov, lovPage, lovSearch]);
    const selected = facilityId ? rows.find(r => String(r.facilityId) === facilityId) ?? null : null;
    const isRevolvingFacility = selected?.facilityType.toUpperCase() === 'REVOLVING_CREDIT';
    const availableHistoryTabs = isRevolvingFacility ? ([['drawdowns', 'Drawdowns'], ['repayments', 'Repayments'], ['limits', 'Limit Changes'], ['statuses', 'Status Changes']] as const) : ([['limits', 'Limit Changes'], ['statuses', 'Status Changes']] as const);
    const activeHistoryTab = availableHistoryTabs.some(([key]) => key === historyTab) ? historyTab : availableHistoryTabs[0][0];
    const filtered = rows.filter(r => !applied || r.facilityStatus === applied),
        visible = filtered.slice(page * 10, page * 10 + 10);
    const openDetail = async (r: Monitoring) => {
        navigate(`/credit-lines/${r.facilityId}`);
        setTab('overview');
        setHistory({});
        setHistoryTab('drawdowns');
        setSummary(null);
        setStocks([])
    };
    const loadTab = async (id: number, name: string) => {
        try {
            if (name === 'interest') {
                setSummary(await creditLines.interest(id));
                const accruals = await creditLines.accruals(id);
                setHistory(v => ({...v, interest: accruals}));
                return
            }
            const data = name === 'drawdowns' ? await creditLines.drawdowns(id) : name === 'repayments' ? await creditLines.repayments(id) : name === 'limits' ? await creditLines.limitHistory(id) : await creditLines.statusHistory(id);
            setHistory(v => ({...v, [name]: data}))
        } catch (e) {
            toast('error', failure(e))
        }
    };
    const loadStocks = async (id: number) => {
        setStocksLoading(true);
        try {
            setStocks(await creditLines.stocks(id))
        } catch (e) {
            toast('error', failure(e))
        } finally {
            setStocksLoading(false)
        }
    };
    const submitStock = async (e: FormEvent) => {
        e.preventDefault();
        if (!selected) return;
        setBusy(true);
        try {
            if (stockDialog === 'finance') {
                await creditLines.financeStock(selected.facilityId, {
                    vinNo: stockVin.trim(), vehicleMake: stockMake.trim(), vehicleModel: stockModel.trim(),
                    manufactureYear: Number(stockYear), purchasePrice: Number(stockPurchase),
                    financedAmount: Number(stockAmount), remarks: stockRemarks.trim()
                });
                toast('success', 'Stock financed successfully.');
            } else if (stockDialog === 'settle' && stockToSettle) {
                await creditLines.settleStock(stockToSettle.stockId, {
                    settlementReference: settlementRef.trim(), reason: settlementReason.trim()
                });
                toast('success', 'Stock settled successfully.');
            } else {
                return
            }
            setStockDialog(null);
            setStockToSettle(null);
            await loadStocks(selected.facilityId);
            reload();
        } catch (e) {
            toast('error', failure(e))
        } finally {
            setBusy(false)
        }
    };
    const openAction = (a: Action) => {
        setAction(a);
        setValue('');
        setReference('');
        setReason('');
        setDate(today());
        setDialog('action')
    };
    const close = () => {
        if (busy) return;
        setDialog(null);
        setLov(false)
    };

    const submitCreate = async (e: FormEvent) => {
        e.preventDefault();
        if (!customer || !productId || !schemeId) {
            toast('warning', 'Select an active customer, product and scheme.');
            return
        }
        if (expiry <= start) {
            toast('warning', 'Expiry date must be after effective date.');
            return
        }
        setBusy(true);
        try {
            await creditLines.create({
                custId: customer.custId,
                prodId: Number(productId),
                schmId: Number(schemeId),
                clFacTyp: type,
                apprLimit: Number(limit),
                effDt: start,
                expiryDt: expiry,
                rateTyp: rateType,
                effRate: Number(rate),
                remarks
            });
            setDialog(null);
            toast('success', 'Credit line facility created successfully.');
            reload()
        } catch (e) {
            toast('error', failure(e))
        } finally {
            setBusy(false)
        }
    };
    const submitAction = async (e: FormEvent) => {
        e.preventDefault();
        if (!selected) return;
        setBusy(true);
        try {
            const id = selected.facilityId;
            if (action === 'activate') await creditLines.activate(id, reason); else if (action === 'adjust') await creditLines.adjust(id, Number(value), reason); else if (action === 'drawdown') await creditLines.drawdown(id, Number(value), reference, reason); else if (action === 'repay') await creditLines.repay(id, Number(value), reference, reason); else if (action === 'accrue') await creditLines.accrue(id, date); else await creditLines.status(id, action, reason);
            setDialog(null);
            toast('success', `${captions[action]} completed successfully.`);
            reload()
        } catch (e) {
            toast('error', failure(e))
        } finally {
            setBusy(false)
        }
    };
    const renderHistory = (entries: Entry[]) => {
        const keys = Object.keys(entries[0] || {}).filter(k => !/(^.*Id$|createdBy|actionBy|createdDate|actionDate)/i.test(k));
        return <div className="cl-scroll cl-history-table">
            <table>
                <thead>
                <tr>{keys.map(k => <th key={k}>{k.replace(/([A-Z])/g, ' $1')}</th>)}</tr>
                </thead>
                <tbody>{entries.map((r, i) => <tr key={i}>{keys.map(k => <td
                    key={k}>{String(r[k] ?? '—')}</td>)}</tr>)}{!entries.length && <tr>
                    <td colSpan={Math.max(1, keys.length)} className="cl-empty">No records found.</td>
                </tr>}</tbody>
            </table>
        </div>
    };
    const label = (name: string, required = false) => <span className="cl-label">{name}{required &&
        <span className="cl-required"> *</span>}</span>;
    return <div className="cl-page">{!facilityId && <>
        <header className="cl-header">
            <div><h1>Credit Lines</h1><p>Facility limits, utilization, drawdowns and repayments</p></div>
        </header>
        <section className="cl-panel cl-search-panel"><h2>Search credit lines</h2>
            <div className="cl-search"><label>{label('Status')}<select value={status}
                                                                       onChange={e => setStatus(e.target.value)}>
                <option value="">All statuses</option>
                {Array.from(new Set(rows.map(r => r.facilityStatus))).map(s => <option key={s}>{s}</option>)}
            </select></label>
                <div className="cl-buttons">
                    <button onClick={() => {
                        setStatus('');
                        setApplied('');
                        setPage(0)
                    }}>Clear
                    </button>
                    <button onClick={() => {
                        setApplied(status);
                        setPage(0)
                    }}>Search
                    </button>
                </div>
                {canMake &&
                    <button type="button" className="cl-primary cl-new-inline" onClick={() => setDialog('create')}>New
                        Credit Line</button>}</div>
        </section>
        <section className="cl-panel"><h2>Credit Line Facilities <span
            className="cl-count">({filtered.length} records)</span></h2>{error &&
            <p className="cl-error">{error}</p>}{loading ? <p>Loading facilities…</p> : <div className="cl-scroll">
            <table>
                <thead>
                <tr>
                    <th>Customer</th>
                    <th>Reference</th>
                    <th>Facility Type</th>
                    <th>Status</th>
                    <th>Approved Limit</th>
                    <th>Expiry Date</th>
                    <th>Action</th>
                </tr>
                </thead>
                <tbody>{visible.map(r => <tr key={r.facilityId}>
                    <td>{customerLabels[r.customerId] ?? '—'}</td>
                    <td><PopupView value={r.facilityReference} maxLength={10}/></td>
                    <td>{r.facilityType.replaceAll('_', ' ')}</td>
                    <td>{r.facilityStatus}</td>
                    <td>{money(r.approvedLimit, r.currency)}</td>
                    <td>{r.expiryDate}</td>
                    <td>
                        <button onClick={() => void openDetail(r)}>Details</button>
                    </td>
                </tr>)}{!visible.length && <tr>
                    <td colSpan={7} className="cl-empty">No facilities found.</td>
                </tr>}</tbody>
            </table>
        </div>}
            <div className="cl-pager">
                <button data-cbms-scroll-top disabled={page === 0} onClick={() => setPage(v => v - 1)}>Previous</button>
                <span>Page {page + 1} of {Math.max(1, Math.ceil(filtered.length / 10))}</span>
                <button disabled={(page + 1) * 10 >= filtered.length} onClick={() => setPage(v => v + 1)}>Next</button>
            </div>
        </section>
    </>}
        {dialog === 'create' && <div className="cl-backdrop">
            <div className="cl-dialog">
                <div className="cl-modal-head"><h2>New Credit Line Facility</h2>
                    <button className="cl-close" onClick={close} aria-label="Close"><X size={20}/></button>
                </div>
                <form onSubmit={e => void submitCreate(e)} className="cl-form cl-create-form"><label
                    className="cl-wide">{label('Customer', true)}
                    <div className="cbms-lov-input-row"><input readOnly required
                                                               value={customer ? `${customer.cifNo} · ${customer.custNm}` : ''}
                                                               placeholder="Select an active customer" onClick={() => {
                        setQuery('');
                        setLovPage(0);
                        setLov(true)
                    }}/>
                        <button type="button" aria-label="Select customer" onClick={() => {
                            setQuery('');
                            setLovPage(0);
                            setLov(true)
                        }}>⌕
                        </button>
                    </div>
                </label><label>{label('Product', true)}<select required value={productId} onChange={e => {
                    setProductId(e.target.value);
                    setSchemeId('');
                    setSchemes([])
                }}>
                    <option value="">Select product</option>
                    {products.map(p => <option key={p.prodId} value={p.prodId}>{p.prodNm}</option>)}
                </select></label><label>{label('Scheme', true)}<select required value={schemeId}
                                                                       onChange={e => setSchemeId(e.target.value)}>
                    <option value="">Select scheme</option>
                    {schemes.map(s => <option key={s.schmId} value={s.schmId}>{s.schmNm}</option>)}
                </select></label><label>{label('Facility Type', true)}<select value={type}
                                                                              onChange={e => setType(e.target.value)}>
                    <option value="REVOLVING_CREDIT">Revolving Credit</option>
                    <option value="FLOOR_STOCKING">Floor Stocking</option>
                </select></label><label>{label('Approved Limit (RM)', true)}<input type="number" min="0.01" step="0.01"
                                                                                   required value={limit}
                                                                                   onChange={e => setLimit(e.target.value)}/></label><label>{label('Effective Rate (%)', true)}<input
                    type="number" min="0" step="0.000001" required value={rate}
                    onChange={e => setRate(e.target.value)}/></label><label>{label('Rate Type', true)}<select
                    value={rateType} onChange={e => setRateType(e.target.value)}>
                    <option value="FIXED">Fixed</option>
                    <option value="FLOATING">Floating</option>
                </select></label><label>{label('Effective Date', true)}<input type="date" required value={start}
                                                                              onChange={e => setStart(e.target.value)}/></label><label>{label('Expiry Date', true)}<input
                    type="date" required value={expiry} onChange={e => setExpiry(e.target.value)}/></label><label
                    className="cl-wide">{label('Remarks')}<textarea value={remarks}
                                                                    onChange={e => setRemarks(e.target.value)}/></label>
                    <div className="cl-footer">
                        <button type="button" onClick={close}>Cancel</button>
                        <button className="cl-primary" disabled={busy}>Create</button>
                    </div>
                </form>
            </div>
        </div>}
        {facilityId && selected && <div className="cl-detail-screen">
            <div className="cl-detail-top">
                <button type="button" className="cl-back" onClick={() => {
                    navigate('/credit-lines')
                }}><ArrowLeft size={18}/> Back to Credit Lines
                </button>
            </div>
            <section className="cl-panel cl-detail-panel">
                <div className="cl-modal-head">
                    <div><h2 className="cl-detail-heading">Credit Line - <span className="cl-detail-ref"
                                                                               title={selected.facilityReference}>{selected.facilityReference}</span><span
                        className="cl-heading-separator">–</span><span
                        className="cl-status">{selected.facilityStatus.replaceAll('_', ' ')}</span></h2></div>
                </div>
                <div className="cl-detail-body">
                    <div className="cl-tabs" role="tablist"
                         aria-label="Credit line details">{[['overview', 'Overview'], ['operations', 'Operations'], ...(isRevolvingFacility ? [] : [['stocks', 'Financed Stock']]), ['history', 'History'], ['interest', 'Profit / Interest']].map(([key, name]) =>
                        <button type="button" role="tab" aria-selected={tab === key}
                                className={tab === key ? 'active' : ''} key={key} onClick={() => {
                            setTab(key);
                            if (key === 'interest') void loadTab(selected.facilityId, 'interest');
                            if (key === 'stocks') void loadStocks(selected.facilityId);
                            if (key === 'history') void loadTab(selected.facilityId, activeHistoryTab)
                        }}>{name}</button>)}</div>
                    {tab === 'overview' && <><h3 className="cl-section-title">Facility Information</h3>
                        <div
                            className="cl-detail-grid">{[['Facility Type', selected.facilityType.replaceAll('_', ' ')], ['Approved Limit', money(selected.approvedLimit, selected.currency)], ['Utilized Amount', money(selected.utilizedAmount, selected.currency)], ['Available Limit', money(selected.availableLimit, selected.currency)], ['Effective Date', selected.effectiveDate], ['Expiry Date', selected.expiryDate]].map(([k, v]) =>
                            <div key={k}><small>{k}</small><strong>{v}</strong></div>)}</div>
                    </>}
                    {tab === 'operations' && <><h3 className="cl-section-title">Available Operations</h3>{(() => {
                        const state = selected.facilityStatus.toUpperCase();
                        const revolving = selected.facilityType.toUpperCase() === 'REVOLVING_CREDIT';
                        const actions: Action[] = state === 'DRAFT' ? ['activate'] : state === 'ACTIVE' ? ['adjust', ...(revolving ? (['drawdown', 'repay'] as Action[]) : []), 'suspend', 'close', 'accrue'] : state === 'SUSPENDED' ? ['reactivate', 'close'] : [];
                        const allowed = actions.filter(a => canMake && (isAdmin || ['drawdown', 'repay', 'accrue'].includes(a)));
                        return allowed.length ? <div className="cl-actions">
                            {allowed.map(a => <button key={a} type="button"
                                                      onClick={() => openAction(a)}>{captions[a]}</button>)}

                        </div> : <p className="cl-muted">No operations are available for this facility status and your
                            role.</p>
                    })()}</>}
                    {tab === 'stocks' && !isRevolvingFacility && <><h3 className="cl-section-title">Financed Stock</h3>
                        <div className="cl-stock-section">
                            <div className="cl-stock-heading">
                                <span>Vehicle stock financing and settlement records</span>
                                <div
                                    className="cl-stock-tools">{selected.facilityStatus === 'ACTIVE' && !isRevolvingFacility && canMake &&
                                    <button type="button" onClick={() => {
                                        setStockVin('');
                                        setStockMake('');
                                        setStockModel('');
                                        setStockYear('');
                                        setStockPurchase('');
                                        setStockAmount('');
                                        setStockRemarks('');
                                        setStockDialog('finance')
                                    }}>Finance Stock</button>}
                                    <button type="button" onClick={() => void loadStocks(selected.facilityId)}
                                            disabled={stocksLoading}>Refresh
                                    </button>
                                </div>
                            </div>
                            <div className="cl-scroll">
                                <table>
                                    <thead>
                                    <tr>
                                        <th>Stock Reference</th>
                                        <th>Vehicle</th>
                                        <th>VIN</th>
                                        <th>Financed Amount</th>
                                        <th>Outstanding</th>
                                        <th>Status</th>
                                        <th>Action</th>
                                    </tr>
                                    </thead>
                                    <tbody>{stocks.map(stock => <tr key={stock.stockId}>
                                        <td><PopupView value={stock.stockReference} maxLength={12}/></td>
                                        <td>{stock.vehicleMake} {stock.vehicleModel} ({stock.manufactureYear})</td>
                                        <td><PopupView value={stock.vinNo} maxLength={12}/></td>
                                        <td>{money(stock.financedAmount, selected.currency)}</td>
                                        <td>{money(stock.outstandingAmount, selected.currency)}</td>
                                        <td>{stock.status}</td>
                                        <td>{canMake && selected.facilityStatus === 'ACTIVE' && stock.status === 'FINANCED' ?
                                            <button type="button" onClick={() => {
                                                setStockToSettle(stock);
                                                setSettlementRef('');
                                                setSettlementReason('');
                                                setStockDialog('settle')
                                            }}>Settle Stock</button> : '—'}</td>
                                    </tr>)}
                                    {!stocks.length && <tr>
                                        <td colSpan={7}
                                            className="cl-empty">{stocksLoading ? 'Loading stock records…' : 'No financed stock records found.'}</td>
                                    </tr>}</tbody>
                                </table>
                            </div>
                        </div>
                    </>}
                    {tab === 'history' && <><h3 className="cl-section-title">Facility History</h3>
                        <div className="cl-subtabs">{availableHistoryTabs.map(([key, name]) => <button key={key}
                                                                                                       type="button"
                                                                                                       className={activeHistoryTab === key ? 'active' : ''}
                                                                                                       onClick={() => {
                                                                                                           setHistoryTab(key);
                                                                                                           void loadTab(selected.facilityId, key)
                                                                                                       }}>{name}</button>)}</div>
                        {renderHistory(history[activeHistoryTab] || [])}</>}
                    {tab === 'interest' && <><h3 className="cl-section-title">Profit / Interest Summary</h3>{summary &&
                        <div
                            className="cl-detail-grid">{Object.entries(summary).filter(([k]) => !/(id|created|updated|by$)/i.test(k)).map(([k, v]) =>
                            <div key={k}>
                                <small>{k.replace(/([A-Z])/g, ' $1')}</small><strong>{String(v ?? '—')}</strong>
                            </div>)}</div>}{renderHistory(history.interest || [])}</>}
                </div>
            </section>
        </div>}
        {stockDialog && selected && <div className="cl-backdrop">
            <div className="cl-dialog cl-stock-dialog">
                <div className="cl-modal-head"><h2>{stockDialog === 'finance' ? 'Finance Stock' : 'Settle Stock'}</h2>
                    <button type="button" className="cl-close" aria-label="Close" disabled={busy}
                            onClick={() => setStockDialog(null)}><X size={20}/></button>
                </div>
                <form className={`cl-form cl-stock-form ${stockDialog === 'settle' ? 'cl-stock-settle-form' : ''}`}
                      onSubmit={e => void submitStock(e)}>
                    {stockDialog === 'finance' ? <>
                        <label>{label('VIN', true)}<input required maxLength={50} value={stockVin}
                                                          onChange={e => setStockVin(e.target.value)}/></label>
                        <label>{label('Vehicle Make', true)}<input required maxLength={100} value={stockMake}
                                                                   onChange={e => setStockMake(e.target.value)}/></label>
                        <label>{label('Vehicle Model', true)}<input required maxLength={100} value={stockModel}
                                                                    onChange={e => setStockModel(e.target.value)}/></label>
                        <label>{label('Manufacture Year', true)}<input required type="number" min="1900" max="2100"
                                                                       value={stockYear}
                                                                       onChange={e => setStockYear(e.target.value)}/></label>
                        <label>{label('Purchase Price (RM)', true)}<input required type="number" min="0.01" step="0.01"
                                                                          value={stockPurchase}
                                                                          onChange={e => setStockPurchase(e.target.value)}/></label>
                        <label>{label('Financed Amount (RM)', true)}<input required type="number" min="0.01" step="0.01"
                                                                           value={stockAmount}
                                                                           onChange={e => setStockAmount(e.target.value)}/></label>
                        <label className="cl-wide">{label('Remarks')}<textarea maxLength={1000} value={stockRemarks}
                                                                               onChange={e => setStockRemarks(e.target.value)}/></label>
                    </> : <>
                        <div className="cl-stock-context">
                            <strong>{stockToSettle?.stockReference}</strong><span>{stockToSettle?.vehicleMake} {stockToSettle?.vehicleModel}</span>
                        </div>
                        <label>{label('Settlement Reference', true)}<input required maxLength={50} value={settlementRef}
                                                                           onChange={e => setSettlementRef(e.target.value)}/></label>
                        <label>{label('Reason', true)}<textarea required maxLength={1000} value={settlementReason}
                                                                onChange={e => setSettlementReason(e.target.value)}/></label>
                    </>}
                    <div className="cl-footer">
                        <button type="button" disabled={busy} onClick={() => setStockDialog(null)}>Cancel</button>
                        <button className="cl-primary"
                                disabled={busy}>{busy ? 'Processing…' : stockDialog === 'finance' ? 'Finance Stock' : 'Settle Stock'}</button>
                    </div>
                </form>
            </div>
        </div>}
        {dialog === 'action' && selected && <div className="cl-backdrop">
            <div className="cl-dialog cl-small">
                <div className="cl-modal-head"><h2>{captions[action]}</h2>
                    <button className="cl-close" onClick={close} aria-label="Close"><X size={20}/></button>
                </div>
                <form className="cl-form"
                      onSubmit={e => void submitAction(e)}>{['adjust', 'drawdown', 'repay'].includes(action) &&
                    <label>{label(action === 'adjust' ? 'New Limit (RM)' : action === 'drawdown' ? 'Drawdown Amount (RM)' : 'Repayment Amount (RM)', true)}<input
                        required type="number" min="0.01" step="0.01" value={value}
                        onChange={e => setValue(e.target.value)}/></label>}{['drawdown', 'repay'].includes(action) &&
                    <label>{label('Reference', true)}<input required maxLength={50} value={reference}
                                                            onChange={e => setReference(e.target.value)}/></label>}{action === 'accrue' ?
                    <label>{label('Accrual Date', true)}<input type="date" required value={date}
                                                               onChange={e => setDate(e.target.value)}/></label> :
                    <label>{label(['drawdown', 'repay'].includes(action) ? 'Remarks' : 'Reason', !['drawdown', 'repay'].includes(action))}<textarea
                        required={!['drawdown', 'repay'].includes(action)} maxLength={1000} value={reason}
                        onChange={e => setReason(e.target.value)}/></label>}
                    <div className="cl-footer">
                        <button type="button" onClick={() => setDialog(null)}>Cancel</button>
                        <button className="cl-primary" disabled={busy}>Confirm</button>
                    </div>
                </form>
            </div>
        </div>}
        {lov && <div className="cbms-lov-backdrop" onMouseDown={e => {
            if (e.target === e.currentTarget) setLov(false)
        }}>
            <section className="cbms-lov-dialog" role="dialog" aria-modal="true" aria-label="Active Customer LOV">
                <div className="cbms-lov-heading"><h2>Customer LOV</h2>
                    <button type="button" aria-label="Close" onClick={() => setLov(false)}>×</button>
                </div>
                <form className="cbms-lov-search" onSubmit={e => {
                    e.preventDefault();
                    setLovPage(0);
                    setLovSearch(v => v + 1)
                }}><input autoFocus placeholder="Search customer name" value={query} onChange={e => {
                    queryRef.current = e.target.value;
                    setQuery(e.target.value)
                }}/>
                    <button type="submit">Search</button>
                </form>
                <div className="cbms-lov-table">
                    <table>
                        <thead>
                        <tr>
                            <th>Customer Name</th>
                            <th>CIF</th>
                            <th>Status</th>
                        </tr>
                        </thead>
                        <tbody>{customers.map(c => <tr key={c.custId} className="cbms-lov-selectable-row" tabIndex={0}
                                                       role="button" aria-label={`Select customer ${c.custNm}`}
                                                       onClick={() => {
                                                           setCustomer(c);
                                                           setLov(false)
                                                       }} onKeyDown={e => {
                            if (e.key === 'Enter' || e.key === ' ') {
                                e.preventDefault();
                                setCustomer(c);
                                setLov(false)
                            }
                        }}>
                            <td>{c.custNm}</td>
                            <td>{c.cifNo}</td>
                            <td>ACTIVE</td>
                        </tr>)}{!customers.length && <tr>
                            <td colSpan={3}>No active customers found.</td>
                        </tr>}</tbody>
                    </table>
                </div>
                <div className="cl-pager">
                    <button type="button" disabled={lovPage === 0} onClick={() => setLovPage(v => v - 1)}>Previous
                    </button>
                    <span>Page {lovPage + 1} of {Math.max(1, lovPages)}</span>
                    <button type="button" disabled={lovPage + 1 >= lovPages}
                            onClick={() => setLovPage(v => v + 1)}>Next
                    </button>
                </div>
            </section>
        </div>}
    </div>
}
