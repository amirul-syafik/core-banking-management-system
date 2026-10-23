import {useCallback, useEffect, useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {AxiosError} from 'axios';
import {X} from 'lucide-react';
import {useToast} from '../components/toastContext';
import {useAuth} from '../auth/useAuth';
import {searchCustomers, getCustomer, type Customer} from '../api/customers';
import {listProducts, listSchemes, type Product, type Scheme} from '../api/accounts';
import {
    listFinancing,
    createFinancing,
    validateFinancing,
    submitFinancing,
    approveFinancing,
    rejectFinancing,
    bookFinancing,
    financingFacilities,
    type FinancingApplication
} from '../api/financing';
import './FinancingApplications.css';
import '../components/AccountLov.css';
import PopupView from "../components/PopupView.tsx";

const categories = new Set(['HIRE_PURCHASE', 'PERSONAL_FINANCING', 'TERM_LOAN', 'CREDIT_LINE', 'FLOOR_STOCKING']);

function message(e: unknown) {
    if (e instanceof AxiosError) {
        const data = e.response?.data as { detail?: string; message?: string } | undefined;
        return data?.detail || data?.message || `Request failed (${e.response?.status ?? 'network'})`;
    }
    return e instanceof Error ? e.message : 'Request failed';
}

export default function FinancingApplications() {
    const notify = useToast();
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const {session} = useAuth();
    const canMake = Boolean(session?.roles.some(r => r === 'MAKER' || r === 'ADMIN'));
    const isAdmin = Boolean(session?.roles.includes('ADMIN'));
    const [customerNames, setCustomerNames] = useState<Record<number, string>>({});
    const [rows, setRows] = useState<FinancingApplication[]>([]);
    const [loading, setLoading] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [refresh, setRefresh] = useState(0);
    const [lovOpen, setLovOpen] = useState(false);
    const [lovPage, setLovPage] = useState(0);
    const [lovTotal, setLovTotal] = useState(0);
    const pageSize = 10;
    const [status, setStatus] = useState('');
    const [appliedStatus, setAppliedStatus] = useState('');
    const [page, setPage] = useState(0);
    const [modal, setModal] = useState<'create' | 'details' | null>(null);
    const [selected, setSelected] = useState<FinancingApplication | null>(null);
    const [facilityReference, setFacilityReference] = useState<string | null>(null);
    const [products, setProducts] = useState<Product[]>([]);
    const [schemes, setSchemes] = useState<Scheme[]>([]);
    const [customerQuery, setCustomerQuery] = useState('');
    const [customerRows, setCustomerRows] = useState<Customer[]>([]);
    const [customer, setCustomer] = useState<Customer | null>(null);
    const [productId, setProductId] = useState('');
    const [schemeId, setSchemeId] = useState('');
    const [amount, setAmount] = useState('');
    const [tenor, setTenor] = useState('');
    const [purpose, setPurpose] = useState('');
    const [remarks, setRemarks] = useState('');
    const [reviewRemarks, setReviewRemarks] = useState('');
    const [approvedAmount, setApprovedAmount] = useState('');
    const [approvedTenor, setApprovedTenor] = useState('');
    const [approvedRate, setApprovedRate] = useState('');
    const [validation, setValidation] = useState<string[] | null>(null);
    const reload = useCallback(() => setRefresh(x => x + 1), []);
    useEffect(() => {
        if (modal !== 'details' || selected?.facId == null) return;
        let live = true;
        void financingFacilities.get(selected.facId).then(facility => {
            if (live) setFacilityReference(facility.facRef);
        }).catch(() => {
            if (live) setFacilityReference(null);
        });
        return () => {
            live = false;
        };
    }, [modal, selected?.facId]);
    useEffect(() => {
        let live = true;
        Promise.resolve().then(async () => {
            setLoading(true);
            try {
                const result = await listFinancing({status: appliedStatus || undefined});
                if (live) {
                    setRows(result);
                    setPage(0);
                    setError('');
                }
            } catch (e) {
                if (live) setError(message(e));
            } finally {
                if (live) setLoading(false);
            }
        });
        return () => {
            live = false;
        };
    }, [refresh, appliedStatus]);
    useEffect(() => {
        let live = true;
        listProducts().then(x => {
            if (live) setProducts(x.filter(p => categories.has(p.prodCat) && p.prodSts === 'ACTIVE'));
        }).catch(e => {
            if (live) setError(message(e));
        });
        return () => {
            live = false;
        };
    }, []);
    useEffect(() => {
        if (!productId) return;
        let live = true;
        listSchemes(Number(productId)).then(x => {
            if (live) setSchemes(x.filter(s => s.schmSts === 'ACTIVE'));
        }).catch(e => {
            if (live) setError(message(e));
        });
        return () => {
            live = false;
        };
    }, [productId]);
    useEffect(() => {
        let live = true;
        const ids = [...new Set(rows.map(r => r.custId))];
        Promise.all(ids.map(async id => {
            try {
                const c = await getCustomer(id);
                return [id, `${c.cifNo} · ${c.custNm}`] as const;
            } catch {
                return [id, 'Unavailable'] as const;
            }
        })).then(items => {
            if (live) setCustomerNames(Object.fromEntries(items));
        });
        return () => {
            live = false;
        };
    }, [rows]);
    const linkedApplicationId = searchParams.get('applicationId');
    useEffect(() => {
        if (!linkedApplicationId || loading) return;
        const linked = rows.find(r => String(r.applId) === linkedApplicationId);
        if (!linked) return;
        let active = true;
        void Promise.resolve().then(() => {
            if (!active) return;
            openDetails(linked);
            setSearchParams(previous => {
                const next = new URLSearchParams(previous);
                next.delete('applicationId');
                return next;
            }, {replace: true});
        });
        return () => {
            active = false;
        };
        // openDetails is an event-style initializer; the effect only runs for a deep link.
    }, [linkedApplicationId, rows, loading, setSearchParams]);
    const chosenScheme = schemes.find(x => String(x.schmId) === schemeId);
    const display = rows.slice(page * pageSize, (page + 1) * pageSize);
    const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));

    function openCreate() {
        setModal('create');
        setCustomer(null);
        setCustomerRows([]);
        setCustomerQuery('');
        setLovOpen(false);
        setProductId('');
        setSchemeId('');
        setAmount('');
        setTenor('');
        setPurpose('');
        setRemarks('');
        setError('');
    }

    function openDetails(item: FinancingApplication) {
        setFacilityReference(null);
        setSelected(item);
        setModal('details');
        setReviewRemarks('');
        setApprovedAmount(String(item.apprAmt ?? item.reqAmt));
        setApprovedTenor(String(item.apprTenor ?? item.reqTenor));
        setApprovedRate(String(item.apprRate ?? ''));
        setValidation(null);
        setError('');
    }

    async function lookupCustomer(nextPage = 0) {
        setBusy(true);
        try {
            const result = await searchCustomers({
                name: customerQuery.trim(),
                status: 'ACTIVE',
                page: nextPage,
                size: 10
            });
            setCustomerRows(result.content.filter(c => c.sts === 'ACTIVE'));
            setLovPage(nextPage);
            setLovTotal(result.totalPages);
            setError('');
        } catch (e) {
            setError(message(e));
            notify('error', message(e));
        } finally {
            setBusy(false);
        }
    }

    async function create() {
        if (!customer || !chosenScheme || !productId || !amount || !tenor) return;
        setBusy(true);
        try {
            await createFinancing({
                custId: customer.custId,
                prodId: Number(productId),
                schmId: Number(schemeId),
                reqAmt: Number(amount),
                reqTenor: Number(tenor),
                financingPurpose: purpose.trim(),
                remarks: remarks.trim()
            });
            setModal(null);
            notify('success', 'Financing application created successfully.');
            setError('');
            reload();
        } catch (e) {
            setError(message(e));
        } finally {
            setBusy(false);
        }
    }

    async function action(kind: 'validate' | 'submit' | 'approve' | 'reject' | 'book') {
        if (!selected) return;
        setBusy(true);
        try {
            if (kind === 'validate') {
                const r = await validateFinancing(selected.applId);
                setValidation(r.errors);
                setNotice(r.valid ? 'Validation passed.' : 'Validation returned errors.');
                return;
            }
            if (kind === 'submit') await submitFinancing(selected.applId);
            if (kind === 'approve') await approveFinancing(selected.applId, {
                apprAmt: Number(approvedAmount),
                apprTenor: Number(approvedTenor),
                apprRate: Number(approvedRate),
                remarks: reviewRemarks.trim()
            });
            if (kind === 'reject') await rejectFinancing(selected.applId, reviewRemarks.trim());
            if (kind === 'book') await bookFinancing(selected.applId);
            setModal(null);
            setSelected(null);
            notify('success', `Financing application ${kind} completed.`);
            setError('');
            reload();
        } catch (e) {
            setError(message(e));
            notify('error', message(e));
        } finally {
            setBusy(false);
        }
    }

    return <main className="fin-page">
        <header><h1>Financing Applications</h1><p>Create, review and book financing applications using live banking
            records.</p></header>
        {notice && <p className="fin-notice" role="status">{notice}</p>}{error &&
        <p className="fin-error" role="alert">{error}</p>}
        <section className="fin-panel"><h2>Search financing applications</h2>
            <div className="fin-toolbar"><label>Status<select value={status} onChange={e => setStatus(e.target.value)}>
                <option value="">All statuses</option>
                {['DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'BOOKED'].map(s => <option key={s}>{s}</option>)}
            </select></label>
                <button type="button" onClick={() => {
                    setStatus('');
                    setAppliedStatus('');
                    setPage(0);
                    reload();
                }}>Clear
                </button>
                <button type="button" onClick={() => {
                    setPage(0);
                    setAppliedStatus(status);
                    reload();
                }} disabled={loading}>Search
                </button>
                {canMake && <button type="button" className="fin-primary" onClick={openCreate}>New Application</button>}
            </div>
        </section>
        <section className="fin-panel"><h2>Applications <small>({rows.length} records)</small></h2>{loading ?
            <p>Loading…</p> : <div className="fin-scroll">
                <table>
                    <thead>
                    <tr>
                        <th>Reference</th>
                        <th>Customer</th>
                        <th>Requested Amount</th>
                        <th>Tenor</th>
                        <th>Status</th>
                        <th>Facility</th>
                        <th>Action</th>
                    </tr>
                    </thead>
                    <tbody>{display.map(r => <tr key={r.applId}>
                        <td><PopupView value={r.applRef} maxLength={10}/></td>
                        <td>{customerNames[r.custId] ?? 'Loading…'}</td>
                        <td>{Number(r.reqAmt).toLocaleString('en-MY', {minimumFractionDigits: 2})}</td>
                        <td>{r.reqTenor}</td>
                        <td>{r.applSts}</td>
                        <td>{r.facId != null
                            ? <button type="button" className="fin-related-link"
                                      onClick={() => navigate(`/financing/facilities/${r.facId}`)}>View
                                Facility</button>
                            : '—'}</td>
                        <td>
                            <button onClick={() => openDetails(r)}>Details</button>
                        </td>
                    </tr>)}{!display.length && <tr>
                        <td colSpan={7}>No financing applications found.</td>
                    </tr>}</tbody>
                </table>
            </div>}
            <div className="fin-pager">
                <button disabled={page === 0} onClick={() => setPage(p => p - 1)}>Previous</button>
                <span>Page {page + 1} of {totalPages}</span>
                <button disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)}>Next</button>
            </div>
        </section>
        {modal && <div className="fin-backdrop" onMouseDown={e => {
            if (e.target === e.currentTarget) setModal(null);
        }}>
            <section className="fin-dialog" role="dialog" aria-modal="true"
                     aria-label={modal === 'create' ? 'New financing application' : 'Financing application details'}>
                <div className="fin-dialog-head">
                    <h2>{modal === 'create' ? 'New Financing Application' : `Application ${selected?.applRef ?? ''}`}</h2>
                    <button type="button" onClick={() => setModal(null)} aria-label="Close"><X size={19}/></button>
                </div>
                {modal === 'create' ? <form onSubmit={e => {
                    e.preventDefault();
                    void create();
                }}>
                    <div className="fin-form"><label className="fin-wide"><span className="fin-label">Customer <span
                        className="fin-required">*</span></span>
                        <div className="cbms-lov-input-row"><input readOnly required
                                                                   value={customer ? `${customer.cifNo} · ${customer.custNm}` : ''}
                                                                   placeholder="Select an active customer"
                                                                   onClick={() => {
                                                                       setCustomerQuery('');
                                                                       setLovOpen(true);
                                                                       void lookupCustomer(0);
                                                                   }}/>
                            <button type="button" aria-label="Select customer" onClick={() => {
                                setCustomerQuery('');
                                setLovOpen(true);
                                void lookupCustomer(0);
                            }}>⌕
                            </button>
                        </div>
                    </label><label><span className="fin-label">Product <span
                        className="fin-required">*</span></span><select required value={productId} onChange={e => {
                        setProductId(e.target.value);
                        setSchemeId('');
                        setSchemes([]);
                    }}>
                        <option value="">Select product</option>
                        {products.map(p => <option key={p.prodId} value={p.prodId}>{p.prodCd} · {p.prodNm}</option>)}
                    </select></label><label><span className="fin-label">Scheme <span
                        className="fin-required">*</span></span><select required value={schemeId}
                                                                        onChange={e => setSchemeId(e.target.value)}>
                        <option value="">Select scheme</option>
                        {schemes.map(s => <option key={s.schmId} value={s.schmId}>{s.schmCd} · {s.schmNm}</option>)}
                    </select></label><label><span className="fin-label">Requested Amount <span
                        className="fin-required">*</span></span><input required type="number"
                                                                       min={chosenScheme?.minAmt ?? 0.01}
                                                                       max={chosenScheme?.maxAmt ?? undefined}
                                                                       step="0.01" value={amount}
                                                                       onChange={e => setAmount(e.target.value)}/></label><label><span
                        className="fin-label">Tenor <span className="fin-required">*</span></span><input required
                                                                                                         type="number"
                                                                                                         min={chosenScheme?.minTenor ?? 1}
                                                                                                         max={chosenScheme?.maxTenor ?? undefined}
                                                                                                         step="1"
                                                                                                         value={tenor}
                                                                                                         onChange={e => setTenor(e.target.value)}/></label><label
                        className="fin-wide">Financing Purpose<textarea maxLength={500} value={purpose}
                                                                        onChange={e => setPurpose(e.target.value)}/></label><label
                        className="fin-wide">Remarks<textarea maxLength={1000} value={remarks}
                                                              onChange={e => setRemarks(e.target.value)}/></label></div>
                    <div className="fin-actions">
                        <button type="button" onClick={() => setModal(null)}>Cancel</button>
                        <button className="fin-primary" type="submit"
                                disabled={busy || !customer || !chosenScheme}>Create
                        </button>
                    </div>
                </form> : selected && <div>
                    <div className="fin-details-grid">
                        <div className="fin-detail"><span
                            className="fin-detail-label">Status</span><strong>{selected.applSts}</strong></div>
                        <div className="fin-detail"><span
                            className="fin-detail-label">Facility</span><strong>{selected.facId ? 'Booked' : 'Not booked'}</strong>
                        </div>
                        {selected.facId != null && <div className="fin-detail">
                            <span className="fin-detail-label">Linked Financing Facility</span>
                            <strong>{facilityReference ?? '—'}</strong>
                        </div>}
                        <div className="fin-detail"><span
                            className="fin-detail-label">Requested Amount</span><strong>{Number(selected.reqAmt).toLocaleString('en-MY', {
                            style: 'currency',
                            currency: 'MYR'
                        })}</strong></div>
                        <div className="fin-detail"><span
                            className="fin-detail-label">Approved Amount</span><strong>{selected.apprAmt == null ? '—' : Number(selected.apprAmt).toLocaleString('en-MY', {
                            style: 'currency',
                            currency: 'MYR'
                        })}</strong></div>
                        <div className="fin-detail"><span
                            className="fin-detail-label">Requested Tenor</span><strong>{selected.reqTenor} months</strong>
                        </div>
                        <div className="fin-detail"><span
                            className="fin-detail-label">Approved Tenor</span><strong>{selected.apprTenor == null ? '—' : `${selected.apprTenor} months`}</strong>
                        </div>
                        <div className="fin-detail"><span
                            className="fin-detail-label">Approved Profit Rate</span><strong>{selected.apprRate == null ? '—' : `${selected.apprRate}%`}</strong>
                        </div>
                        <div className="fin-detail fin-detail-wide"><span
                            className="fin-detail-label">Financing Purpose</span><strong>{selected.financingPurpose || '—'}</strong>
                        </div>
                    </div>
                    {canMake && selected.applSts === 'DRAFT' && <div className="fin-actions">
                        <button type="button" disabled={busy} onClick={() => void action('validate')}>Validate</button>
                        <button className="fin-primary" type="button" disabled={busy}
                                onClick={() => void action('submit')}>Submit
                        </button>
                    </div>}{validation &&
                    <p role="status">{validation.length ? validation.join('; ') : 'No validation errors.'}</p>}{isAdmin && selected.applSts === 'SUBMITTED' && <>
                    <div className="fin-form"><label>Approved Amount<input type="number" min="0.01" step="0.01"
                                                                           value={approvedAmount}
                                                                           onChange={e => setApprovedAmount(e.target.value)}/></label><label>Approved
                        Tenor<input type="number" min="1" step="1" value={approvedTenor}
                                    onChange={e => setApprovedTenor(e.target.value)}/></label><label>Approved Rate
                        (%)<input type="number" min="0" max="100" step="0.000001" value={approvedRate}
                                  onChange={e => setApprovedRate(e.target.value)}/></label><label className="fin-wide">Review
                        Remarks<textarea maxLength={1000} value={reviewRemarks}
                                         onChange={e => setReviewRemarks(e.target.value)}/></label></div>
                    <div className="fin-actions">
                        <button type="button" disabled={busy || !reviewRemarks.trim()}
                                onClick={() => void action('reject')}>Reject
                        </button>
                        <button type="button" className="fin-primary"
                                disabled={busy || !approvedAmount || !approvedTenor || !approvedRate}
                                onClick={() => void action('approve')}>Approve
                        </button>
                    </div>
                </>}{isAdmin && selected.applSts === 'APPROVED' && <div className="fin-actions">
                    <button type="button" className="fin-primary" disabled={busy}
                            onClick={() => void action('book')}>Book Facility
                    </button>
                </div>}</div>}
            </section>
        </div>}{lovOpen && <div className="cbms-lov-backdrop" onMouseDown={e => {
        if (e.target === e.currentTarget) setLovOpen(false);
    }}>
        <section className="cbms-lov-dialog" role="dialog" aria-modal="true" aria-label="Active Customer LOV">
            <div className="cbms-lov-heading"><h2>Customer LOV</h2>
                <button type="button" aria-label="Close" onClick={() => setLovOpen(false)}>×</button>
            </div>
            <form className="cbms-lov-search" onSubmit={e => {
                e.preventDefault();
                void lookupCustomer(0);
            }}><input autoFocus placeholder="Search customer name" value={customerQuery}
                      onChange={e => setCustomerQuery(e.target.value)}/>
                <button type="submit" disabled={busy}>Search</button>
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
                    <tbody>{customerRows.map(c => <tr key={c.custId} className="cbms-lov-selectable-row" tabIndex={0}
                                                      role="button" aria-label={`Select customer ${c.custNm}`}
                                                      onClick={() => {
                                                          setCustomer(c);
                                                          setLovOpen(false);
                                                      }} onKeyDown={e => {
                        if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            setCustomer(c);
                            setLovOpen(false);
                        }
                    }}>
                        <td>{c.custNm}</td>
                        <td>{c.cifNo}</td>
                        <td>{c.sts}</td>
                    </tr>)}{!customerRows.length && <tr>
                        <td colSpan={3}>No active customers found.</td>
                    </tr>}</tbody>
                </table>
            </div>
            <div className="cbms-lov-pagination">
                <button type="button" disabled={busy || lovPage === 0}
                        onClick={() => void lookupCustomer(lovPage - 1)}>Previous
                </button>
                <span>Page {lovPage + 1} of {Math.max(1, lovTotal)}</span>
                <button type="button" disabled={busy || lovPage + 1 >= lovTotal}
                        onClick={() => void lookupCustomer(lovPage + 1)}>Next
                </button>
            </div>
        </section>
    </div>}</main>;
}
