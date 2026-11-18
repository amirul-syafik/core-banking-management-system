import {useCallback, useEffect, useState, type FormEvent} from 'react';
import {AxiosError} from 'axios';
import {api} from '../api/client';
import {businessReferenceFromResponse} from '../utils/businessReference';
import {useAuth} from '../auth/useAuth';
import {useToast} from './toastContext';
import {ConfirmDialog, FieldLabel, RecordCount, StatusBadge, RefreshButton} from './UiStandards';
import './IslamicDepositExecutionEvidence.css';

type Policy = {
    policy_id: number;
    policy_ref: string;
    contract_typ: string;
    event_codes: string;
    trusted_systems: string;
    effective_from: string;
    effective_until: string | null;
    policy_basis: string;
    external_signoff_ref: string;
    policy_sts: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED';
    crt_by: string;
    submitted_by: string | null;
    decided_by: string | null;
    review_ref: string | null;
    review_remarks: string | null;
    bank_signoff_verified: boolean;
};
type Confirmation = { type: 'submit' | 'approve' | 'reject'; id: number } | null;
const errorText = (error: unknown) => error instanceof AxiosError
    ? String(error.response?.data?.detail || error.response?.data?.message || error.message)
    : error instanceof Error ? error.message : 'Execution policy request failed';

/**
 * DOCUMENT-REVIEW ONLY: this screen cannot certify bank governance sign-off,
 * execute a commodity trade, activate a deposit or post a journal.
 */
export default function IslamicExecutionPolicy({contractType}: { contractType: string | null }) {
    // A different contract gets a fresh component instance and query state.
    return <IslamicExecutionPolicyContent key={contractType ?? '__no_contract__'} contractType={contractType}/>;
}

function IslamicExecutionPolicyContent({contractType}: { contractType: string | null }) {
    const {session} = useAuth();
    const notify = useToast();
    const canMake = Boolean(session?.roles.some(role => role === 'MAKER' || role === 'ADMIN'));
    const isAdmin = Boolean(session?.roles.includes('ADMIN'));
    const user = session?.username?.toLowerCase() || '';
    const [rows, setRows] = useState<Policy[]>([]);
    const [loading, setLoading] = useState(Boolean(contractType));
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [events, setEvents] = useState('');
    const [systems, setSystems] = useState('');
    const [effectiveFrom, setEffectiveFrom] = useState('');
    const [effectiveUntil, setEffectiveUntil] = useState('');
    const [basis, setBasis] = useState('');
    const [signoff, setSignoff] = useState('');
    const [reviewRefs, setReviewRefs] = useState<Record<number, string>>({});
    const [reviewRemarksById, setReviewRemarksById] = useState<Record<number, string>>({});
    const [confirmation, setConfirmation] = useState<Confirmation>(null);

    const refresh = useCallback(async () => {
        if (!contractType) {
            setRows([]);
            return;
        }
        const result = await api.get<Policy[]>('/islamic/execution-policies', {params: {contractType}});
        setRows(result.data);
        setError('');
    }, [contractType]);
    useEffect(() => {
        if (!contractType) return;
        let active = true;
        void api.get<Policy[]>('/islamic/execution-policies', {params: {contractType}})
            .then(result => {
                if (active) {
                    setRows(result.data);
                    setError('');
                }
            })
            .catch((e: unknown) => {
                if (active) setError(errorText(e));
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, [contractType]);
    const perform = async (operation: () => Promise<unknown>, message: string,
                           onSaved?: () => void) => {
        if (busy) return;
        setBusy(true);
        setError('');
        let result: unknown;
        try {
            result = await operation();
        } catch (e: unknown) {
            const text = errorText(e);
            setError(text);
            notify('error', text);
            setBusy(false);
            return;
        }
        // The mutation succeeded even if the subsequent GET fails. Do not describe a
        // saved policy as a failed operation (or encourage accidental resubmission).
        onSaved?.();
        const reference = businessReferenceFromResponse(result);
        notify('success', `${message}${reference ? ` Reference: ${reference}` : ''}`);
        try {
            await refresh();
        } catch (e: unknown) {
            setRows([]);
            setError(`Saved successfully, but the policy list could not be refreshed: ${errorText(e)}. Use Refresh to verify.`);
        } finally {
            setBusy(false);
        }
    };
    const create = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!contractType || !canMake || busy) return;
        void perform(() => api.post('/islamic/execution-policies', {
            contractType,
            orderedEventCodes: events,
            trustedExternalSystems: systems,
            effectiveFrom: new Date(effectiveFrom).toISOString(),
            effectiveUntil: effectiveUntil ? new Date(effectiveUntil).toISOString() : null,
            policyBasis: basis.trim(),
            externalSignoffReference: signoff.trim()
        }), 'Execution policy draft created. Bank attestation and posting remain disabled.', () => {
            setEvents('');
            setSystems('');
            setEffectiveFrom('');
            setEffectiveUntil('');
            setBasis('');
            setSignoff('');
        });
    };
    const decide = () => {
        if (!confirmation || busy) return;
        const item = confirmation;
        setConfirmation(null);
        const reference = (reviewRefs[item.id] || '').trim();
        const remarks = (reviewRemarksById[item.id] || '').trim();
        if (item.type === 'submit') {
            void perform(() => api.post(`/islamic/execution-policies/${item.id}/submit`), 'Policy submitted for review.');
        } else {
            if (item.type === 'approve' && reference.length < 4) {
                setError('Please enter an independent policy-review reference.');
                return;
            }
            if (item.type === 'reject' && remarks.length < 5) {
                setError('Please enter a rejection reason of at least five characters.');
                return;
            }
            void perform(() => api.post(`/islamic/execution-policies/${item.id}/decision`, {
                decision: item.type === 'approve' ? 'APPROVE' : 'REJECT',
                reviewReference: reference, remarks
            }), item.type === 'approve' ? 'Policy documents approved; independent bank attestation still required.' : 'Policy rejected.');
        }
    };
    return <section className="fd-account-section fd-execution-review"
                    aria-label="Islamic execution policy documentation">
        <div className="fd-execution-heading">
            <div><h2>Execution Policy</h2><p className="fd-note">
                Bank policy documentation and independent review only. Approval here does not certify an external
                sign-off or allow financial processing.
            </p></div>
            <RefreshButton loading={loading || busy} disabled={!contractType}
                    onClick={() => void refresh().catch((e: unknown) => {
                        setRows([]);
                        setError(errorText(e));
                    })}/>
        </div>
        {error && <p className="fd-error" role="alert">{error}</p>}
        {!contractType && <p className="fd-note">An approved Islamic contract snapshot must be available.</p>}
        {contractType && <>
            <div className="fd-posting-readiness">
                <div className="fd-posting-readiness-head"><h3>{contractType.replaceAll('_', ' ')} Policies</h3>
                    <RecordCount count={rows.length}/></div>
                {loading && <p className="fd-note">Loading policies…</p>}
                {!loading && !error && rows.length === 0 &&
                    <p className="fd-note">No policy records. External execution remains blocked.</p>}
                {rows.map(row => <article className="fd-execution-details" key={row.policy_id}>
                    <div className="fd-posting-readiness-head"><strong>{row.policy_ref}</strong><StatusBadge
                        status={row.policy_sts}/></div>
                    <p className="fd-note">Events: {row.event_codes.replaceAll(',', ' → ')} · Trusted
                        systems: {row.trusted_systems.replaceAll(',', ', ')}</p>
                    <p className="fd-note">Effective: {new Date(row.effective_from).toLocaleString('en-MY')}
                        {row.effective_until ? ` – ${new Date(row.effective_until).toLocaleString('en-MY')}` : ' (no end date)'}</p>
                    <p className="fd-note">Bank sign-off independently
                        attested: <strong>{row.bank_signoff_verified ? 'Yes' : 'No'}</strong></p>
                    <details>
                        <summary>Policy documentation and review history</summary>
                        <p><strong>Bank authority reference:</strong> {row.external_signoff_ref}</p>
                        <p><strong>Basis:</strong> {row.policy_basis}</p>
                        <p><strong>Maker:</strong> {row.crt_by} · <strong>Checker:</strong> {row.decided_by || '—'}</p>
                        <p><strong>Review:</strong> {row.review_ref || '—'}</p>
                        <p>{row.review_remarks || ''}</p>
                    </details>
                    {canMake && row.policy_sts === 'DRAFT' && row.crt_by.toLowerCase() === user &&
                        <button type="button" disabled={busy}
                                onClick={() => setConfirmation({type: 'submit', id: row.policy_id})}>Submit for
                            Review</button>}
                    {isAdmin && row.policy_sts === 'SUBMITTED' && row.crt_by.toLowerCase() !== user &&
                        <div className="fd-execution-form">
                            <label><FieldLabel required>Independent Review Reference</FieldLabel>
                                <input value={reviewRefs[row.policy_id] || ''}
                                       onChange={event => setReviewRefs(previous => ({
                                           ...previous,
                                           [row.policy_id]: event.target.value
                                       }))} maxLength={160}/></label>
                            <div className="fd-execution-actions">
                                <button type="button" disabled={busy}
                                        onClick={() => setConfirmation({type: 'approve', id: row.policy_id})}>Approve
                                    Documents
                                </button>
                                <button type="button" disabled={busy}
                                        onClick={() => setConfirmation({type: 'reject', id: row.policy_id})}>Reject
                                </button>
                            </div>
                        </div>}
                </article>)}
            </div>
            {canMake && <form className="fd-execution-form" onSubmit={create}>
                <h3>Create Execution Policy Draft</h3>
                <label><FieldLabel required>Ordered External Event Codes</FieldLabel>
                    <input required value={events} maxLength={700} onChange={event => setEvents(event.target.value)}
                           placeholder="Enter approved event codes, separated by commas"/>
                    <small>Enter the event codes from your bank-approved policy, in sequence.</small></label>
                <label><FieldLabel required>Trusted External System Codes</FieldLabel>
                    <input required value={systems} maxLength={500} onChange={event => setSystems(event.target.value)}
                           placeholder="Enter approved external system codes"/></label>
                <label><FieldLabel required>Effective From (Local Date &amp; Time)</FieldLabel>
                    <input type="datetime-local" required value={effectiveFrom}
                           onChange={event => setEffectiveFrom(event.target.value)}/></label>
                <label><FieldLabel>Effective Until (Local Date &amp; Time)</FieldLabel>
                    <input type="datetime-local" value={effectiveUntil}
                           onChange={event => setEffectiveUntil(event.target.value)}/></label>
                <label><FieldLabel required>External Bank Sign-off Reference</FieldLabel>
                    <input required minLength={4} maxLength={160} value={signoff}
                           onChange={event => setSignoff(event.target.value)}/></label>
                <label className="fd-execution-wide"><FieldLabel required>Policy Basis</FieldLabel>
                    <textarea required minLength={20} maxLength={2000} rows={3} value={basis}
                              onChange={event => setBasis(event.target.value)}/></label>
                <div className="fd-execution-actions">
                    <button type="submit" disabled={busy}>Create</button>
                </div>
            </form>}
        </>}
        {confirmation && <ConfirmDialog
            title={confirmation.type === 'submit' ? 'Submit Execution Policy' : 'Review Execution Policy'}
            message={confirmation.type === 'submit' ? 'Submit this policy for review?' :
                confirmation.type === 'approve' ? 'Approve this policy documentation?' : 'Reject this policy?'}
            confirmLabel={confirmation.type === 'submit' ? 'Submit' : confirmation.type === 'approve' ? 'Approve' : 'Reject'}
            busy={busy} danger={confirmation.type === 'reject'}
            reasonLabel={confirmation.type === 'reject' ? 'Rejection Reason' : undefined}
            reason={confirmation.type === 'reject' ? (reviewRemarksById[confirmation.id] || '') : undefined}
            onReasonChange={confirmation.type === 'reject' ? (value => setReviewRemarksById(previous => ({
                ...previous,
                [confirmation.id]: value
            }))) : undefined}
            reasonRequired={confirmation.type === 'reject'}
            onCancel={() => setConfirmation(null)} onConfirm={decide}/>}
    </section>;
}
