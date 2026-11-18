import {useCallback, useEffect, useState, type FormEvent} from 'react';
import {AxiosError} from 'axios';
import {api} from '../api/client';
import {useAuth} from '../auth/useAuth';
import {useToast} from './toastContext';
import {RefreshButton} from './UiStandards';
import './IslamicDepositProfitTerms.css';

type ProfitTerms = {
    terms_id: number;
    fd_id: number;
    contract_typ: string;
    terms_sts: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED';
    principal_snapshot_amt: number | string;
    proposed_profit_amt: number | string | null;
    proposed_sale_price: number | string | null;
    expected_annual_rate_pct: number | string | null;
    customer_profit_share_pct: number | string | null;
    evidence_ref: string;
    basis_notes: string;
    crt_by: string;
    submitted_by: string | null;
    decided_by: string | null;
    review_reference: string | null;
    review_remarks: string | null;
};

const currency = (value: number | string | null) => value === null ? '—' :
    `MYR ${Number(value).toLocaleString('en-MY', {minimumFractionDigits: 2, maximumFractionDigits: 2})}`;
const pct = (value: number | string | null) => value === null ? '—' : `${Number(value)}%`;
const errorText = (error: unknown) => error instanceof AxiosError ?
    String(error.response?.data?.detail || error.response?.data?.message || error.message) :
    error instanceof Error ? error.message : 'Request failed';
const validDecimal = (value: string) => /^\d+(?:\.\d{1,5})?$/.test(value.trim());

export default function IslamicDepositProfitTerms({fdId, depositStatus, contractType}: {
    fdId: number;
    depositStatus: string;
    contractType: string | null;
}) {
    const {session} = useAuth();
    const toast = useToast();
    const canMake = Boolean(session?.roles.some(r => r === 'MAKER' || r === 'ADMIN'));
    const isAdmin = Boolean(session?.roles.includes('ADMIN'));
    const [history, setHistory] = useState<ProfitTerms[]>([]);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [profit, setProfit] = useState('');
    const [expectedRate, setExpectedRate] = useState('');
    const [sharePct, setSharePct] = useState('');
    const [evidence, setEvidence] = useState('');
    const [notes, setNotes] = useState('');
    const [reviewRef, setReviewRef] = useState('');
    const [remarks, setRemarks] = useState('');

    const refresh = useCallback(async () => {
        const {data} = await api.get<ProfitTerms[]>(`/islamic/deposits/${fdId}/profit-terms`);
        setHistory(data);
        setError('');
    }, [fdId]);

    useEffect(() => {
        let active = true;
        void api.get<ProfitTerms[]>(`/islamic/deposits/${fdId}/profit-terms`)
            .then(({data}) => {
                if (active) {
                    setHistory(data);
                    setLoading(false);
                }
            })
            .catch((e: unknown) => {
                if (active) {
                    setError(errorText(e));
                    setLoading(false);
                }
            });
        return () => {
            active = false;
        };
    }, [fdId]);

    const current = history[0];
    const isDraft = depositStatus === 'DRAFT';
    const canCreate = canMake && isDraft && !loading && !error &&
        ['TAWARRUQ', 'WAKALAH', 'MUDARABAH'].includes(contractType || '') &&
        !history.some(row => row.terms_sts !== 'REJECTED');
    const canSubmit = canMake && isDraft && current?.terms_sts === 'DRAFT' &&
        current.crt_by.toLowerCase() === session?.username.toLowerCase();
    const canReview = isAdmin && isDraft && current?.terms_sts === 'SUBMITTED' &&
        current.crt_by.toLowerCase() !== session?.username.toLowerCase();

    const perform = async (work: () => Promise<unknown>, confirmation: string) => {
        if (busy) return;
        setBusy(true);
        setError('');
        try {
            await work();
            await refresh();
            toast('success', confirmation);
        } catch (e: unknown) {
            const text = errorText(e);
            setError(text);
            toast('error', text);
        } finally {
            setBusy(false);
        }
    };

    const propose = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!canCreate || !contractType || busy) return;
        if (evidence.trim().length < 4 || notes.trim().length < 20 ||
            (contractType === 'TAWARRUQ' ? !validDecimal(profit) || Number(profit) <= 0 || !/^\d+(?:\.\d{1,2})?$/.test(profit) :
                contractType === 'WAKALAH' ? !validDecimal(expectedRate) || Number(expectedRate) > 100 :
                    contractType === 'MUDARABAH' ? !validDecimal(sharePct) || Number(sharePct) <= 0 || Number(sharePct) > 100 : true)) {
            setError('Please correct the required profit terms fields.');
            return;
        }
        const numberFor = (value: string) => value.trim() === '' ? null : Number(value);
        void perform(() => api.post(`/islamic/deposits/${fdId}/profit-terms`, {
            proposedProfitAmount: contractType === 'TAWARRUQ' ? numberFor(profit) : null,
            expectedAnnualRatePct: contractType === 'WAKALAH' ? numberFor(expectedRate) : null,
            customerProfitSharePct: contractType === 'MUDARABAH' ? numberFor(sharePct) : null,
            evidenceReference: evidence.trim(),
            basisNotes: notes.trim()
        }), 'Profit terms proposal created. No financial posting has occurred.');
    };

    return <section className="fd-account-section fd-islamic-profit-terms">
        <div className="fd-profit-heading">
            <div><h2>Islamic Profit Terms</h2><p className="fd-note">Pre-execution review only · No placement or GL
                posting</p></div>
            <RefreshButton loading={busy || loading} onClick={() => {
                setLoading(true);
                void refresh().catch((e: unknown) => setError(errorText(e))).finally(() => setLoading(false));
            }}/>
        </div>
        {error && <p role="alert" className="fd-error">{error}</p>}
        {loading && <p role="status" className="fd-note">Loading profit terms…</p>}
        {!loading && current && <>
            <div className="fd-profit-summary-grid">
                <div><small>Status</small><strong>{current.terms_sts}</strong></div>
                <div><small>Contract</small><strong>{current.contract_typ.replaceAll('_', ' ')}</strong></div>
                <div><small>Principal (MYR)</small><strong>{currency(current.principal_snapshot_amt)}</strong></div>
                {current.contract_typ === 'TAWARRUQ' && <>
                    <div><small>Proposed Contract Profit</small><strong>{currency(current.proposed_profit_amt)}</strong>
                    </div>
                    <div><small>Proposed Deferred Sale
                        Price</small><strong>{currency(current.proposed_sale_price)}</strong></div>
                </>}
                {current.contract_typ === 'WAKALAH' && <div><small>Expected Annual Rate (Not
                    Guaranteed)</small><strong>{pct(current.expected_annual_rate_pct)}</strong></div>}
                {current.contract_typ === 'MUDARABAH' &&
                    <div><small>Customer Profit Share</small><strong>{pct(current.customer_profit_share_pct)}</strong>
                    </div>}
                <div><small>Maker</small><strong>{current.crt_by}</strong></div>
                <div><small>Supporting Document</small><strong>{current.evidence_ref}</strong></div>
                <div><small>Independent Reviewer</small><strong>{current.decided_by || 'Pending'}</strong></div>
                <div><small>Review Reference</small><strong>{current.review_reference || '—'}</strong></div>
            </div>
            <details className="fd-profit-details">
                <summary>Pricing basis and review remarks</summary>
                <p>{current.basis_notes}</p>
                {current.review_remarks && <p><strong>Review:</strong> {current.review_remarks}</p>}
            </details>
            {canSubmit && <div className="fd-profit-actions">
                <button type="button" className="fd-primary" disabled={busy}
                        onClick={() => void perform(() => api.post(`/islamic/deposits/profit-terms/${current.terms_id}/submit`), 'Profit terms submitted for independent review.')}>Submit
                    for Review
                </button>
            </div>}
            {canReview && <form className="fd-profit-form" onSubmit={e => {
                e.preventDefault();
                if (reviewRef.trim().length < 4) {
                    setError('Enter an external pricing approval reference (at least 4 characters).');
                    return;
                }
                void perform(() => api.post(`/islamic/deposits/profit-terms/${current.terms_id}/decision`, {
                    decision: 'APPROVE', reviewReference: reviewRef.trim(), remarks: remarks.trim()
                }), 'Profit terms reviewed. Deposit processing remains blocked.');
            }}>
                <label>External Pricing Approval Reference *
                    <input value={reviewRef} onChange={e => setReviewRef(e.target.value)} maxLength={160}
                           placeholder="Document reference"/></label>
                <label>Review Remarks
                    <textarea rows={2} value={remarks} onChange={e => setRemarks(e.target.value)}
                              maxLength={1000}/></label>
                <div className="fd-profit-actions">
                    <button type="submit" className="fd-primary" disabled={busy}>Approve Terms</button>
                    <button type="button" disabled={busy} onClick={() => {
                        if (remarks.trim().length < 5) {
                            setError('Enter a rejection reason (at least 5 characters).');
                            return;
                        }
                        void perform(() => api.post(`/islamic/deposits/profit-terms/${current.terms_id}/decision`, {
                            decision: 'REJECT', reviewReference: null, remarks: remarks.trim()
                        }), 'Profit terms rejected.');
                    }}>Reject Terms
                    </button>
                </div>
            </form>}
            {current.terms_sts === 'SUBMITTED' && !canReview &&
                <p className="fd-note">Awaiting review by a different authorized ADMIN user.</p>}
        </>}
        {!loading && canCreate && <form className="fd-profit-form" onSubmit={propose}>
            <h3>New {contractType?.replaceAll('_', ' ')} Terms Proposal</h3>
            {contractType === 'TAWARRUQ' && <label>Proposed Deferred-Sale Profit (MYR) *
                <input type="number" min="0.01" step="0.01" required value={profit}
                       onChange={e => setProfit(e.target.value)}/></label>}
            {contractType === 'WAKALAH' && <label>Expected Annual Profit Rate (%) *
                <input type="number" min="0" max="100" step="0.00001" required value={expectedRate}
                       onChange={e => setExpectedRate(e.target.value)}/></label>}
            {contractType === 'MUDARABAH' && <label>Customer Profit-Sharing Ratio (%) *
                <input type="number" min="0.00001" max="100" step="0.00001" required value={sharePct}
                       onChange={e => setSharePct(e.target.value)}/></label>}
            <label>Supporting Document Reference *
                <input required minLength={4} maxLength={160} value={evidence}
                       onChange={e => setEvidence(e.target.value)}/></label>
            <label className="fd-profit-wide">Pricing Basis and Assumptions *
                <textarea required minLength={20} maxLength={1000} rows={3} value={notes}
                          onChange={e => setNotes(e.target.value)}/></label>
            <div className="fd-profit-actions">
                <button className="fd-primary" type="submit" disabled={busy}>
                    Create Proposal
                </button>
            </div>
        </form>}
        {!loading && !current && !canCreate &&
            <p className="fd-note">No proposal is available for this deposit. An eligible maker can create one while it
                is DRAFT.</p>}
        {history.length > 1 && <details className="fd-profit-details">
            <summary>Previous rejected proposals ({history.length - 1})</summary>
            {history.slice(1).map(item => <p
                key={item.terms_id}>#{item.terms_id} — {item.terms_sts} by {item.crt_by}</p>)}
        </details>}
    </section>;
}
