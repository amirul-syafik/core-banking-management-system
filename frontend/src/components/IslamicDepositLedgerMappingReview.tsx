import {useCallback, useEffect, useState, type FormEvent} from 'react';
import {AxiosError} from 'axios';
import {api} from '../api/client';
import {businessReferenceFromResponse} from '../utils/businessReference';
import {useAuth} from '../auth/useAuth';
import {useToast} from './toastContext';
import {FieldLabel, ConfirmDialog, RequiredFieldError, RefreshButton} from './UiStandards';
import GlAccountLookup from './GlAccountLookup';
import './IslamicDepositLedgerMappingReview.css';

type GlKey = 'placementDebitGl' | 'placementCreditGl' | 'accrualDebitGl' | 'accrualCreditGl'
    | 'payoutDebitGl' | 'payoutCreditGl' | 'maturityDebitGl' | 'maturityCreditGl';
const glFields: ReadonlyArray<{ key: GlKey; caption: string; stored: string }> = [
    {key: 'placementDebitGl', caption: 'Placement — Debit GL', stored: 'placement_debit_gl'},
    {key: 'placementCreditGl', caption: 'Placement — Credit GL', stored: 'placement_credit_gl'},
    {key: 'accrualDebitGl', caption: 'Profit Accrual — Debit GL', stored: 'accrual_debit_gl'},
    {key: 'accrualCreditGl', caption: 'Profit Accrual — Credit GL', stored: 'accrual_credit_gl'},
    {key: 'payoutDebitGl', caption: 'Profit Payout — Debit GL', stored: 'payout_debit_gl'},
    {key: 'payoutCreditGl', caption: 'Profit Payout — Credit GL', stored: 'payout_credit_gl'},
    {key: 'maturityDebitGl', caption: 'Maturity Settlement — Debit GL', stored: 'maturity_debit_gl'},
    {key: 'maturityCreditGl', caption: 'Maturity Settlement — Credit GL', stored: 'maturity_credit_gl'}
];
type Proposal = Record<GlKey, string>;
type Mapping = Record<string, string | number | null> & {
    mapping_id: number;
    mapping_ref: string;
    mapping_sts: 'DRAFT' | 'SUBMITTED' | 'REVIEWED' | 'REJECTED';
    crt_by: string;
    decided_by: string | null;
};
const blank: Proposal = {
    placementDebitGl: '', placementCreditGl: '', accrualDebitGl: '', accrualCreditGl: '',
    payoutDebitGl: '', payoutCreditGl: '', maturityDebitGl: '', maturityCreditGl: ''
};
const errorText = (error: unknown) => error instanceof AxiosError
    ? String(error.response?.data?.detail || error.response?.data?.message || error.message)
    : error instanceof Error ? error.message : 'Request failed';

/** Proposal only. Does not activate a deposit, calculate a profit, or post journals. */
export default function IslamicDepositLedgerMappingReview({fdId, depositStatus}: {
    fdId: number;
    depositStatus: string;
}) {
    const {session} = useAuth();
    const toast = useToast();
    const canMake = Boolean(session?.roles.some(role => role === 'MAKER' || role === 'ADMIN'));
    const isAdmin = Boolean(session?.roles.includes('ADMIN'));
    const [history, setHistory] = useState<Mapping[]>([]);
    const [evidenceReviewed, setEvidenceReviewed] = useState(false);
    const [values, setValues] = useState<Proposal>(blank);
    const [reviewReference, setReviewReference] = useState('');
    const [remarks, setRemarks] = useState('');
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [attempted, setAttempted] = useState(false);
    const [error, setError] = useState('');
    const [pendingDecision, setPendingDecision] = useState<'submit' | 'accept' | 'reject' | null>(null);
    const refresh = useCallback(async () => {
        const [mappings, evidence] = await Promise.all([
            api.get<Mapping[]>(`/islamic/deposits/${fdId}/ledger-mapping`),
            api.get<Array<{ review_sts: string }>>(`/islamic/deposits/${fdId}/execution-evidence`)
        ]);
        setHistory(mappings.data);
        setEvidenceReviewed(evidence.data.some(item => item.review_sts === 'REVIEWED'));
        setError('');
    }, [fdId]);
    useEffect(() => {
        let active = true;
        void Promise.all([
            api.get<Mapping[]>(`/islamic/deposits/${fdId}/ledger-mapping`),
            api.get<Array<{ review_sts: string }>>(`/islamic/deposits/${fdId}/execution-evidence`)
        ]).then(([mappings, evidence]) => {
            if (active) {
                setHistory(mappings.data);
                setEvidenceReviewed(evidence.data.some(item => item.review_sts === 'REVIEWED'));
                setError('');
                setLoading(false);
            }
        }).catch((e: unknown) => {
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
    const canCreate = canMake && evidenceReviewed && isDraft && !loading && !busy &&
        !history.some(m => m.mapping_sts !== 'REJECTED');
    const valid = glFields.every(({key}) => /^[A-Za-z0-9_-]{1,30}$/.test(values[key].trim())) &&
        [0, 2, 4, 6].every(i => values[glFields[i].key].trim().toLowerCase() !== values[glFields[i + 1].key].trim().toLowerCase());
    const canSubmit = canMake && isDraft && current?.mapping_sts === 'DRAFT' &&
        current.crt_by.toLowerCase() === session?.username.toLowerCase();
    const canReview = isAdmin && isDraft && current?.mapping_sts === 'SUBMITTED' &&
        current.crt_by.toLowerCase() !== session?.username.toLowerCase();

    const run = async (work: () => Promise<unknown>, message: string) => {
        if (busy) return;
        setBusy(true);
        setError('');
        try {
            const result = await work();
            await refresh();
            setPendingDecision(null);
            const reference = businessReferenceFromResponse(result);
            toast('success', `${message}${reference ? ` Reference: ${reference}` : ''}`);
        } catch (e: unknown) {
            setError(errorText(e));
            toast('error', errorText(e));
        } finally {
            setBusy(false);
        }
    };
    const create = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setAttempted(true);
        if (!canCreate || !valid) {
            const missing = glFields.find(({key}) => !values[key].trim());
            setError(missing ? `Please select ${missing.caption}.` :
                'Select valid GL accounts. Debit and credit must differ for each event.');
            return;
        }
        void run(() => api.post(`/islamic/deposits/${fdId}/ledger-mapping`,
                Object.fromEntries(glFields.map(({key}) => [key, values[key].trim()]))),
            'GL mapping proposal created. No financial transaction was posted.');
    };

    return <section className="fd-ledger-review fd-ledger-review-page">
        <div className="fd-ledger-heading">
            <h2>Ledger Mapping Review</h2>
            <RefreshButton loading={busy || loading} onClick={() => {
                setLoading(true);
                void refresh().catch((e: unknown) => setError(errorText(e))).finally(() => setLoading(false));
            }}/>
        </div>
        <p className="fd-note">This is a proposed mapping of accounting events to existing MYR GL accounts. Review does
            not authorize journal posting.</p>
        {loading && <p className="fd-note" role="status">Loading GL mapping proposals…</p>}
        {error && <p className="fd-error" role="alert">{error}</p>}
        {!loading && current && <>
            <div className="fd-ledger-summary">
                <div><small>Mapping Reference</small><strong>{current.mapping_ref}</strong></div>
                <div><small>Review Status</small><strong>{current.mapping_sts}</strong></div>
                <div><small>Created By</small><strong>{current.crt_by}</strong></div>
                <div><small>Reviewed By</small><strong>{current.decided_by || 'Pending'}</strong></div>
            </div>
            <div className="fd-ledger-summary">
                {glFields.map(field => <div key={field.key}><small>{field.caption}</small>
                    <strong>{String(current[field.stored] ?? '—')}</strong></div>)}
            </div>
            {canSubmit && <button className="fd-primary" type="button" disabled={busy}
                                  onClick={() => setPendingDecision('submit')}>Submit Mapping for Review</button>}
            {canReview && <form className="fd-ledger-form" onSubmit={event => {
                event.preventDefault();
                if (reviewReference.trim().length < 4) {
                    setError('Enter an independent review reference.');
                    return;
                }
                setPendingDecision('accept');
            }}>
                <label><FieldLabel required>Independent Accounting Review Reference</FieldLabel>
                    <input required minLength={4} maxLength={160} value={reviewReference}
                           onChange={event => setReviewReference(event.target.value)}/></label>
                <label>Review Remarks
                    <textarea maxLength={1000} rows={2} value={remarks}
                              onChange={event => setRemarks(event.target.value)}/></label>
                <div className="fd-ledger-actions">
                    <button className="fd-primary" disabled={busy} type="submit">Mark Mapping Reviewed</button>
                    <button type="button" disabled={busy} onClick={() => {
                        if (remarks.trim().length < 5) {
                            setError('Rejection remarks must be at least 5 characters.');
                            return;
                        }
                        setPendingDecision('reject');
                    }}>Reject Mapping
                    </button>
                </div>
            </form>}
            {current.mapping_sts === 'SUBMITTED' && !canReview &&
                <p className="fd-note">Awaiting independent ADMIN review.</p>}
        </>}
        {!loading && !evidenceReviewed &&
            <p className="fd-note">Execution Evidence must be independently reviewed before GL mapping can be
                proposed.</p>}
        {!loading && canCreate && <form className="fd-ledger-form" onSubmit={create}>
            <h3>Proposed Accounting Event Mapping</h3>
            <p className="fd-note">Select eligible GL accounts from the maintained catalog. The server verifies every
                selected code again when saving.</p>
            <div className="fd-ledger-fields">
                {glFields.map(({key, caption}) => <div key={key} className="fd-ledger-field">
                    <FieldLabel required>{caption}</FieldLabel>
                    <GlAccountLookup label={caption} value={values[key]} onChange={code =>
                        setValues(previous => ({...previous, [key]: code}))}/>
                    <RequiredFieldError show={attempted && !values[key].trim()} message={`Please select ${caption}.`}/>
                </div>)}
            </div>
            <button className="fd-primary" type="submit" disabled={busy}>Create</button>
        </form>}
        {pendingDecision && current && <ConfirmDialog
            title={pendingDecision === 'submit' ? 'Submit Ledger Mapping for Review?' : pendingDecision === 'accept' ? 'Mark Ledger Mapping Reviewed?' : 'Reject Ledger Mapping?'}
            message={pendingDecision === 'submit' ? 'Are you sure you want to submit this mapping for review?' :
                pendingDecision === 'accept' ? 'Are you sure you want to mark this mapping as reviewed?' :
                    'Are you sure you want to reject this mapping?'}
            details={[{label: 'Mapping Reference', value: current.mapping_ref},
                {label: 'Current Status', value: current.mapping_sts}]}
            notice="This is documentary accounting review only. It will not post GL entries or activate the deposit."
            confirmLabel={pendingDecision === 'submit' ? 'Submit for Review' : pendingDecision === 'accept' ? 'Mark Reviewed' : 'Reject Mapping'}
            danger={pendingDecision === 'reject'} busy={busy}
            onCancel={() => setPendingDecision(null)}
            onConfirm={() => {
                if (pendingDecision === 'submit') {
                    void run(() => api.post(`/islamic/deposits/ledger-mapping/${current.mapping_id}/submit`),
                        'GL mapping submitted for independent review.');
                } else {
                    void run(() => api.post(`/islamic/deposits/ledger-mapping/${current.mapping_id}/decision`, {
                        decision: pendingDecision === 'accept' ? 'ACCEPT' : 'REJECT',
                        reviewReference: pendingDecision === 'accept' ? reviewReference.trim() : null,
                        remarks: remarks.trim()
                    }), pendingDecision === 'accept' ? 'GL mapping review recorded. Financial posting remains disabled.' :
                        'GL mapping rejected; maker may create a replacement proposal.');
                }
            }}/>}
        {history.length > 1 && <p className="fd-note">Previous rejected
            mappings: {history.filter(m => m.mapping_sts === 'REJECTED').length}</p>}
    </section>;
}
