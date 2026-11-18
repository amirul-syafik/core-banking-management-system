import {useCallback, useEffect, useState} from 'react';
import {api} from '../api/client';
import {useAuth} from '../auth/useAuth';
import {useToast} from './toastContext';
import {CloseIcon, StatusBadge, RequiredFieldError, RefreshButton} from './UiStandards';
import './islamic-contract.css';

type Profile = {
    profile_id: number;
    contract_typ: string;
    profit_model: string;
    terms_summary: string;
    profile_sts: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED';
    shariah_approval_ref: string | null;
    review_remarks: string | null;
    crt_by: string;
    submitted_by: string | null;
    decided_by: string | null;
};
type ContractTab = 'prepare' | 'approval' | 'history';
const depositContracts = ['TAWARRUQ', 'WAKALAH', 'MUDARABAH'];
const financingContracts = ['TAWARRUQ', 'MURABAHAH', 'IJARAH'];
const normalized = (value: string) => value.trim().toLowerCase();
const readable = (value: string) => value.replaceAll('_', ' ');
const describeError = (error: unknown): string => {
    if (typeof error === 'object' && error !== null && 'response' in error) {
        const result = error as { response?: { status?: number; data?: { message?: string; detail?: string } } };
        if (result.response?.status === 403) return 'Only a different authorized ADMIN reviewer may decide this contract.';
        return result.response?.data?.detail || result.response?.data?.message || 'Request failed';
    }
    return error instanceof Error ? error.message : 'Request failed';
};

export default function IslamicContractDialog({schemeId, schemeName, productCategory, focusProfileId, onClose}: {
    schemeId: number;
    schemeName: string;
    productCategory: string;
    focusProfileId?: number;
    onClose: () => void;
}) {
    const {session} = useAuth();
    const toast = useToast();
    const maker = session?.roles?.some(role => role === 'MAKER' || role === 'ADMIN') ?? false;
    const checker = session?.roles?.includes('ADMIN') ?? false;
    const username = normalized(session?.username || '');
    const types = ['TERM_DEPOSIT', 'FIXED_DEPOSIT'].includes(productCategory) ? depositContracts : financingContracts;
    const [tab, setTab] = useState<ContractTab>(focusProfileId ? 'approval' : 'prepare');
    const [rows, setRows] = useState<Profile[]>([]);
    const [type, setType] = useState(types[0]);
    const [terms, setTerms] = useState('');
    const [reference, setReference] = useState('');
    const [approvalValidation, setApprovalValidation] = useState('');
    const [termsValidation, setTermsValidation] = useState('');
    const [reviewValidation, setReviewValidation] = useState('');
    const [review, setReview] = useState('');
    const [reviewingId, setReviewingId] = useState<number | null>(focusProfileId ?? null);
    const [busy, setBusy] = useState(false);
    const [loading, setLoading] = useState(false);
    const load = useCallback(async () => {
        setLoading(true);
        try {
            const result = await api.get<Profile[]>('/islamic/contracts', {params: {schemeId}});
            setRows(result.data);
        } catch (error) {
            toast('error', describeError(error));
        } finally {
            setLoading(false);
        }
    }, [schemeId, toast]);
    useEffect(() => {
        void Promise.resolve().then(load);
    }, [load]);

    const perform = async (work: () => Promise<unknown>, success: string, nextTab: ContractTab = tab) => {
        setBusy(true);
        try {
            await work();
            toast('success', success);
            setTerms('');
            setReference('');
            setApprovalValidation('');
            setReview('');
            setTab(nextTab);
            window.dispatchEvent(new Event('cbms:notifications-changed'));
            await load();
        } catch (error) {
            toast('error', describeError(error));
        } finally {
            setBusy(false);
        }
    };
    const drafts = rows.filter(row => row.profile_sts === 'DRAFT');
    const pending = rows.filter(row => row.profile_sts === 'SUBMITTED');
    const completed = rows.filter(row => row.profile_sts === 'APPROVED' || row.profile_sts === 'REJECTED');
    const canDecide = (row: Profile) => checker && normalized(row.crt_by) !== username;

    return <div className="maint-overlay" onMouseDown={event => {
        if (event.target === event.currentTarget && !busy) onClose();
    }}>
        <section className="maint-dialog islamic-contract-dialog" role="dialog" aria-modal="true"
                 aria-label="Islamic contract profile">
            <div className="maint-dialog-head">
                <div><h2>Islamic Contract Profile</h2><p>{schemeName}</p></div>
                <button type="button" aria-label="Close" disabled={busy} onClick={onClose}><CloseIcon/></button>
            </div>
            <p className="islamic-contract-warning" role="note">
                Contract approval records documentation only. Islamic financial processing remains disabled.
            </p>
            <div className="islamic-contract-tabs" role="tablist" aria-label="Islamic contract workflow">
                <button type="button" role="tab" aria-selected={tab === 'prepare'}
                        className={tab === 'prepare' ? 'active' : ''}
                        onClick={() => setTab('prepare')}>Drafts ({drafts.length})
                </button>
                <button type="button" role="tab" aria-selected={tab === 'approval'}
                        className={tab === 'approval' ? 'active' : ''}
                        onClick={() => setTab('approval')}>Approvals ({pending.length})
                </button>
                <button type="button" role="tab" aria-selected={tab === 'history'}
                        className={tab === 'history' ? 'active' : ''}
                        onClick={() => setTab('history')}>History ({completed.length})
                </button>
                <RefreshButton loading={busy || loading} onClick={() => void load()}/>
            </div>
            <div className="islamic-contract-tab-content" role="tabpanel">
                {loading && <p className="islamic-contract-empty">Loading contract profiles…</p>}
                {tab === 'prepare' && <>
                    {maker ? <div className="islamic-contract-create">
                        <h3>New Contract Draft</h3>
                        <div className="islamic-contract-form">
                            <label>Contract Type
                                <select value={type} disabled={busy} onChange={event => setType(event.target.value)}>
                                    {types.map(value => <option key={value} value={value}>{readable(value)}</option>)}
                                </select>
                            </label>
                            <label className="islamic-contract-wide">Proposed Contract Terms Summary
                                <textarea rows={3} maxLength={2000} disabled={busy} value={terms}
                                          onChange={event => setTerms(event.target.value)}
                                          placeholder="Summarize the contract, profit method, responsibilities and supporting approvals"/>
                            </label>
                            <RequiredFieldError show={Boolean(termsValidation) && terms.trim().length < 20}
                                                message={termsValidation}/>
                        </div>
                        <button type="button" className="maint-primary" disabled={busy}
                                onClick={() => {
                                    if (terms.trim().length < 20) {
                                        setTermsValidation('Please enter at least 20 characters of contract terms.');
                                        return;
                                    }
                                    setTermsValidation('');
                                    void perform(() => api.post('/islamic/contracts',
                                        {
                                            schemeId,
                                            contractType: type,
                                            termsSummary: terms
                                        }), 'Contract draft created.', 'prepare');
                                }}>Create Draft
                        </button>
                    </div> : <p className="islamic-contract-hint">Draft creation requires a MAKER or ADMIN account.</p>}
                    <h3>Draft Profiles</h3>
                    {!loading && drafts.length === 0 &&
                        <p className="islamic-contract-empty">No drafts for this scheme.</p>}
                    {drafts.map(item => <article className="islamic-contract-entry" key={item.profile_id}>
                        <div className="islamic-contract-entry-head">
                            <strong>#{item.profile_id} · {readable(item.contract_typ)}</strong><StatusBadge
                            status={item.profile_sts}/></div>
                        <p className="islamic-contract-meta">Created
                            by {item.crt_by} · {readable(item.profit_model)}</p>
                        <details>
                            <summary>View proposed terms</summary>
                            <p>{item.terms_summary}</p></details>
                        {maker && normalized(item.crt_by) === username && <div className="islamic-contract-actions">
                            <button type="button" className="maint-primary" disabled={busy}
                                    onClick={() => void perform(() => api.post(`/islamic/contracts/${item.profile_id}/submit`),
                                        'Submitted to independent ADMIN reviewers.', 'approval')}>Submit for Review
                            </button>
                        </div>}
                    </article>)}
                </>}
                {tab === 'approval' && <>
                    <h3>Awaiting Independent Review</h3>
                    {!checker &&
                        <p className="islamic-contract-hint">Approval requires an ADMIN account different from the
                            maker.</p>}
                    {!loading && pending.length === 0 &&
                        <p className="islamic-contract-empty">No pending approvals.</p>}
                    {pending.map(item => <article
                        className={`islamic-contract-entry${focusProfileId === item.profile_id ? ' islamic-contract-focused' : ''}`}
                        key={item.profile_id}>
                        <div className="islamic-contract-entry-head">
                            <strong>#{item.profile_id} · {readable(item.contract_typ)}</strong><StatusBadge
                            status={item.profile_sts}/></div>
                        <p className="islamic-contract-meta">Maker: {item.crt_by} · {readable(item.profit_model)}</p>
                        <details>
                            <summary>Review proposed contract terms</summary>
                            <p>{item.terms_summary}</p></details>
                        {canDecide(item) ? <>
                            <button type="button" className="islamic-contract-review-toggle"
                                    disabled={busy} onClick={() => {
                                setReviewingId(reviewingId === item.profile_id ? null : item.profile_id);
                                setReference('');
                                setApprovalValidation('');
                                setReview('');
                            }}>{reviewingId === item.profile_id ? 'Cancel Review' : 'Review Profile'}</button>
                            {reviewingId === item.profile_id && <div className="islamic-contract-review">
                                <label>External Shariah Approval Reference
                                    <input maxLength={160} minLength={4} required disabled={busy} value={reference}
                                           aria-invalid={Boolean(approvalValidation)}
                                           onChange={event => {
                                               setReference(event.target.value);
                                               if (approvalValidation) setApprovalValidation('');
                                           }} placeholder="Enter the documented approval reference"/>
                                    {approvalValidation
                                        ? <span className="islamic-contract-validation"
                                                role="alert">{approvalValidation}</span>
                                        :
                                        <span className="islamic-contract-helper">At least 4 characters. Required before approval can be recorded.</span>}
                                </label>
                                <label>Review Remarks
                                    <input maxLength={1000} disabled={busy} value={review}
                                           onChange={event => setReview(event.target.value)}
                                           placeholder="Required for rejection"/>
                                </label>
                                <RequiredFieldError show={Boolean(reviewValidation) && !review.trim()}
                                                    message={reviewValidation}/>
                                <div className="islamic-contract-actions">
                                    <button type="button" className="maint-primary" disabled={busy}
                                            onClick={() => {
                                                if (reference.trim().length < 4) {
                                                    setApprovalValidation('Enter an external Shariah approval reference of at least 4 characters.');
                                                    return;
                                                }
                                                void perform(() => api.post(`/islamic/contracts/${item.profile_id}/decision`,
                                                        {
                                                            decision: 'APPROVE',
                                                            shariahApprovalReference: reference.trim(),
                                                            remarks: review
                                                        }),
                                                    'Contract profile approved.', 'history');
                                            }}>Approve Profile
                                    </button>
                                    <button type="button" disabled={busy}
                                            onClick={() => {
                                                if (!review.trim()) {
                                                    setReviewValidation('Please enter review remarks before rejecting.');
                                                    return;
                                                }
                                                setReviewValidation('');
                                                void perform(() => api.post(`/islamic/contracts/${item.profile_id}/decision`,
                                                    {
                                                        decision: 'REJECT',
                                                        remarks: review
                                                    }), 'Contract profile rejected.', 'history');
                                            }}>Reject
                                    </button>
                                </div>
                            </div>}
                        </> : <p className="islamic-contract-hint">{normalized(item.crt_by) === username
                            ? 'You submitted this profile. Another ADMIN must review it.'
                            : `Approval requires a different ADMIN account. Current account: ${session?.username || 'unknown'} (${session?.roles?.join(', ') || 'no role'}).`}</p>}
                    </article>)}
                </>}
                {tab === 'history' && <>
                    <h3>Reviewed Profiles</h3>
                    {!loading && completed.length === 0 &&
                        <p className="islamic-contract-empty">No decisions recorded yet.</p>}
                    {completed.map(item => <article className="islamic-contract-entry" key={item.profile_id}>
                        <div className="islamic-contract-entry-head">
                            <strong>#{item.profile_id} · {readable(item.contract_typ)}</strong><StatusBadge
                            status={item.profile_sts}/></div>
                        <p className="islamic-contract-meta">Maker: {item.crt_by} ·
                            Checker: {item.decided_by || '—'}</p>
                        {item.shariah_approval_ref &&
                            <p className="islamic-contract-meta">Approval reference: {item.shariah_approval_ref}</p>}
                        {item.review_remarks && <p className="islamic-contract-meta">Remarks: {item.review_remarks}</p>}
                        <details>
                            <summary>View contract terms</summary>
                            <p>{item.terms_summary}</p></details>
                    </article>)}
                </>}
            </div>
            <div className="maint-footer">
                <button type="button" disabled={busy} onClick={onClose}>Close</button>
            </div>
        </section>
    </div>;
}
