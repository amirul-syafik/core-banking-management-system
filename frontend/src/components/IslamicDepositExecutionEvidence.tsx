import {useCallback, useEffect, useState, type FormEvent} from 'react';
import {AxiosError} from 'axios';
import {api} from '../api/client';
import {businessReferenceFromResponse} from '../utils/businessReference';
import {useAuth} from '../auth/useAuth';
import {useToast} from './toastContext';
import {FieldLabel, RefreshButton} from './UiStandards';
import './IslamicDepositExecutionEvidence.css';

type Evidence = {
    execution_id: number;
    execution_ref: string;
    fd_id: number;
    contract_typ: string;
    review_sts: 'DRAFT' | 'SUBMITTED' | 'REVIEWED' | 'REJECTED';
    primary_evidence_ref: string;
    secondary_evidence_ref: string;
    tertiary_evidence_ref: string | null;
    accounting_policy_ref: string;
    ledger_mapping_ref: string;
    notes: string;
    crt_by: string;
    submitted_by: string | null;
    decided_by: string | null;
    review_reference: string | null;
    review_remarks: string | null;
};
type ProfitTerms = { terms_sts: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' };

const inputLabels: Record<string, readonly [string, string, string?]> = {
    TAWARRUQ: ['Commodity Purchase Evidence Reference', 'Bank-to-Customer Sale Evidence Reference', 'Onward Sale / Liquidation Evidence Reference'],
    WAKALAH: ['Signed Agency / Wakalah Mandate Reference', 'Investment Instruction Evidence Reference'],
    MUDARABAH: ['Signed Mudarabah Agreement Reference', 'Investment Mandate Evidence Reference']
};
const getError = (error: unknown) => error instanceof AxiosError
    ? String(error.response?.data?.detail || error.response?.data?.message || error.message)
    : error instanceof Error ? error.message : 'Request failed';

export default function IslamicDepositExecutionEvidence({fdId, depositStatus, contractType}: {
    fdId: number;
    depositStatus: string;
    contractType: string | null;
}) {
    const {session} = useAuth();
    const notify = useToast();
    const canMake = Boolean(session?.roles.some(r => r === 'MAKER' || r === 'ADMIN'));
    const isAdmin = Boolean(session?.roles.includes('ADMIN'));
    const [history, setHistory] = useState<Evidence[]>([]);
    const [termsApproved, setTermsApproved] = useState(false);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [primary, setPrimary] = useState('');
    const [secondary, setSecondary] = useState('');
    const [third, setThird] = useState('');
    const [policy, setPolicy] = useState('');
    const [mapping, setMapping] = useState('');
    const [notes, setNotes] = useState('');
    const [reviewRef, setReviewRef] = useState('');
    const [remarks, setRemarks] = useState('');

    const refresh = useCallback(async () => {
        const [evidence, terms] = await Promise.all([
            api.get<Evidence[]>(`/islamic/deposits/${fdId}/execution-evidence`),
            api.get<ProfitTerms[]>(`/islamic/deposits/${fdId}/profit-terms`)
        ]);
        setHistory(evidence.data);
        setTermsApproved(terms.data.some(t => t.terms_sts === 'APPROVED'));
        setError('');
    }, [fdId]);
    useEffect(() => {
        let active = true;
        void Promise.all([
            api.get<Evidence[]>(`/islamic/deposits/${fdId}/execution-evidence`),
            api.get<ProfitTerms[]>(`/islamic/deposits/${fdId}/profit-terms`)
        ]).then(([evidence, terms]) => {
            if (active) {
                setHistory(evidence.data);
                setTermsApproved(terms.data.some(t => t.terms_sts === 'APPROVED'));
                setError('');
                setLoading(false);
            }
        }).catch((e: unknown) => {
            if (active) {
                setError(getError(e));
                setLoading(false);
            }
        });
        return () => {
            active = false;
        };
    }, [fdId]);

    const current = history[0];
    const isDraft = depositStatus === 'DRAFT';
    const applicable = inputLabels[contractType || ''];
    const canCreate = canMake && isDraft && termsApproved && !loading && !error && Boolean(applicable) &&
        !history.some(e => e.review_sts !== 'REJECTED');
    const canSubmit = canMake && isDraft && current?.review_sts === 'DRAFT' &&
        current.crt_by.toLowerCase() === session?.username.toLowerCase();
    const canReview = isAdmin && isDraft && current?.review_sts === 'SUBMITTED' &&
        current.crt_by.toLowerCase() !== session?.username.toLowerCase();
    const validInput = primary.trim().length >= 4 && secondary.trim().length >= 4 &&
        (contractType !== 'TAWARRUQ' || third.trim().length >= 4) &&
        policy.trim().length >= 4 && mapping.trim().length >= 4 && notes.trim().length >= 20;

    const perform = async (work: () => Promise<unknown>, success: string) => {
        if (busy) return;
        setBusy(true);
        setError('');
        try {
            const result = await work();
            await refresh();
            const reference = businessReferenceFromResponse(result);
            notify('success', `${success}${reference ? ` Reference: ${reference}` : ''}`);
        } catch (e: unknown) {
            setError(getError(e));
            notify('error', getError(e));
        } finally {
            setBusy(false);
        }
    };
    const create = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!canCreate || busy) return;
        if (!validInput) {
            setError('Please complete all required evidence fields before creating a draft.');
            return;
        }
        void perform(() => api.post(`/islamic/deposits/${fdId}/execution-evidence`, {
            primaryEvidenceReference: primary.trim(),
            secondaryEvidenceReference: secondary.trim(),
            tertiaryEvidenceReference: contractType === 'TAWARRUQ' ? third.trim() : null,
            accountingPolicyReference: policy.trim(),
            ledgerMappingReference: mapping.trim(),
            notes: notes.trim()
        }), 'Execution evidence draft saved; no financial transaction was executed.');
    };

    return <section className="fd-account-section fd-execution-review">
        <div className="fd-execution-heading">
            <div>
                <h2>Execution Evidence</h2>
                <p className="fd-note">Document review only · Deposit remains DRAFT · No funds or GL postings</p>
            </div>
            <RefreshButton loading={busy || loading} onClick={() => {
                setLoading(true);
                void refresh().catch((e: unknown) => setError(getError(e))).finally(() => setLoading(false));
            }}/>
        </div>
        {error && <p className="fd-error" role="alert">{error}</p>}
        {loading && <p className="fd-note" role="status">Loading execution evidence…</p>}
        {!loading && !termsApproved &&
            <p className="fd-note">Approved Profit Terms are required before execution evidence can be prepared.</p>}
        {!loading && current && <>
            <div className="fd-execution-grid">
                <div><small>Evidence Reference</small><strong>{current.execution_ref}</strong></div>
                <div><small>Review Status</small><strong>{current.review_sts.replaceAll('_', ' ')}</strong></div>
                <div><small>Contract</small><strong>{current.contract_typ}</strong></div>
                <div>
                    <small>{inputLabels[current.contract_typ]?.[0] || 'Primary Evidence'}</small><strong>{current.primary_evidence_ref}</strong>
                </div>
                <div>
                    <small>{inputLabels[current.contract_typ]?.[1] || 'Secondary Evidence'}</small><strong>{current.secondary_evidence_ref}</strong>
                </div>
                {current.tertiary_evidence_ref && <div>
                    <small>{inputLabels[current.contract_typ]?.[2] || 'Third Evidence'}</small><strong>{current.tertiary_evidence_ref}</strong>
                </div>}
                <div><small>Accounting Policy Reference</small><strong>{current.accounting_policy_ref}</strong></div>
                <div><small>Islamic Ledger Mapping Reference</small><strong>{current.ledger_mapping_ref}</strong></div>
                <div><small>Maker</small><strong>{current.crt_by}</strong></div>
                <div><small>Reviewed By</small><strong>{current.decided_by || 'Pending'}</strong></div>
                <div><small>Independent Review Reference</small><strong>{current.review_reference || '—'}</strong></div>
            </div>
            <details className="fd-execution-details">
                <summary>Evidence assumptions and review remarks</summary>
                <p>{current.notes}</p>
                {current.review_remarks && <p><strong>Review:</strong> {current.review_remarks}</p>}
            </details>
            {canSubmit && <div className="fd-execution-actions">
                <button className="fd-primary" type="button" disabled={busy}
                        onClick={() => void perform(() => api.post(`/islamic/deposits/execution-evidence/${current.execution_id}/submit`),
                            'Evidence submitted to an independent reviewer. No financial posting has occurred.')}>Submit
                    for Review
                </button>
            </div>}
            {canReview && <form className="fd-execution-form" onSubmit={event => {
                event.preventDefault();
                if (reviewRef.trim().length < 4) {
                    setError('Enter an independent document-review reference of at least 4 characters.');
                    return;
                }
                void perform(() => api.post(`/islamic/deposits/execution-evidence/${current.execution_id}/decision`, {
                    decision: 'ACCEPT', independentReviewReference: reviewRef.trim(), remarks: remarks.trim()
                }), 'Evidence review recorded. Placement and GL posting remain blocked.');
            }}>
                <label><FieldLabel required>Independent Evidence Review Reference</FieldLabel>
                    <input required minLength={4} maxLength={160} value={reviewRef}
                           onChange={e => setReviewRef(e.target.value)}/></label>
                <label>Review Remarks<textarea maxLength={1000} rows={2} value={remarks}
                                               onChange={e => setRemarks(e.target.value)}/></label>
                <div className="fd-execution-actions">
                    <button className="fd-primary" type="submit" disabled={busy}>Mark Evidence Reviewed</button>
                    <button type="button" disabled={busy} onClick={() => {
                        if (remarks.trim().length < 5) {
                            setError('Enter a rejection reason of at least 5 characters.');
                            return;
                        }
                        void perform(() => api.post(`/islamic/deposits/execution-evidence/${current.execution_id}/decision`, {
                            decision: 'REJECT', independentReviewReference: null, remarks: remarks.trim()
                        }), 'Evidence rejected; maker may submit replacement evidence.');
                    }}>Reject Evidence
                    </button>
                </div>
            </form>}
            {current.review_sts === 'SUBMITTED' && !canReview &&
                <p className="fd-note">Awaiting review by another authorized administrator.</p>}
        </>}
        {!loading && canCreate && <form className="fd-execution-form" onSubmit={create}>
            <h3>New {contractType?.replaceAll('_', ' ')} Evidence Record</h3>
            <label><FieldLabel required>{applicable?.[0] || 'Primary Evidence'}</FieldLabel>
                <input required minLength={4} maxLength={160} value={primary}
                       onChange={e => setPrimary(e.target.value)}/></label>
            <label><FieldLabel required>{applicable?.[1] || 'Secondary Evidence'}</FieldLabel>
                <input required minLength={4} maxLength={160} value={secondary}
                       onChange={e => setSecondary(e.target.value)}/></label>
            {contractType === 'TAWARRUQ' &&
                <label><FieldLabel required>{applicable?.[2] || 'Third Evidence'}</FieldLabel>
                    <input required minLength={4} maxLength={160} value={third}
                           onChange={e => setThird(e.target.value)}/></label>}
            <label><FieldLabel required>Approved Accounting Policy Reference</FieldLabel>
                <input required minLength={4} maxLength={160} value={policy} onChange={e => setPolicy(e.target.value)}/></label>
            <label><FieldLabel required>Islamic Ledger Mapping Reference</FieldLabel>
                <input required minLength={4} maxLength={160} value={mapping}
                       onChange={e => setMapping(e.target.value)}/></label>
            <label className="fd-execution-wide"><FieldLabel required>Execution Evidence and Accounting
                Assumptions</FieldLabel>
                <textarea required minLength={20} maxLength={1000} rows={3} value={notes}
                          onChange={e => setNotes(e.target.value)}/></label>
            <div className="fd-execution-actions">
                <button type="submit" className="fd-primary" disabled={busy}>Create Evidence Draft</button>
            </div>
        </form>}
        {history.length > 1 && <details className="fd-execution-details">
            <summary>Previous rejected evidence ({history.length - 1})</summary>
            {history.slice(1).map(item => <p key={item.execution_id}>{item.execution_ref} — {item.review_sts}</p>)}
        </details>}
        <p className="fd-note">Reviewed references are not document uploads or proof of actual settlement. Contract
            execution, profit recognition and GL transactions require further approved bank-specific processing
            rules.</p>
    </section>;
}
