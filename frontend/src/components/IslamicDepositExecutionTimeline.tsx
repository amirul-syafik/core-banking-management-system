import {useCallback, useEffect, useRef, useState, type FormEvent} from 'react';
import {AxiosError} from 'axios';
import {api} from '../api/client';
import {businessReferenceFromResponse} from '../utils/businessReference';
import {useAuth} from '../auth/useAuth';
import {useToast} from './toastContext';
import {ConfirmDialog, FieldLabel, RefreshButton} from './UiStandards';
import './IslamicDepositExecutionTimeline.css';

type Evidence = {
    execution_id: number;
    review_sts: string;
    contract_typ: string;
    primary_evidence_ref: string;
    secondary_evidence_ref: string;
    tertiary_evidence_ref: string | null;
};
type Timeline = {
    timeline_id: number;
    timeline_ref: string;
    contract_typ: string;
    review_sts: 'DRAFT' | 'SUBMITTED' | 'DOCUMENT_REVIEWED' | 'REJECTED';
    primary_recorded_at: string;
    secondary_recorded_at: string;
    tertiary_recorded_at: string | null;
    primary_evidence_ref: string;
    secondary_evidence_ref: string;
    tertiary_evidence_ref: string | null;
    control_notes: string;
    crt_by: string;
    decided_by: string | null;
    review_reference: string | null;
    review_remarks: string | null;
};
const errorText = (error: unknown) => error instanceof AxiosError
    ? String(error.response?.data?.detail || error.response?.data?.message || error.message)
    : error instanceof Error ? error.message : 'Request failed';
const localTime = (value: string | null) => value ? new Date(value).toLocaleString('en-MY') : '—';
const labels: Record<string, readonly [string, string, string | null]> = {
    TAWARRUQ: ['Commodity purchase', 'Bank-to-customer sale', 'Onward sale'],
    WAKALAH: ['Agency mandate', 'Investment instruction', null],
    MUDARABAH: ['Investment agreement', 'Investment mandate', null]
};

/** External document chronology review only; NEVER a trade execution or financial posting control. */
export default function IslamicDepositExecutionTimeline({fdId, depositStatus}: {
    fdId: number; depositStatus: string;
}) {
    const {session} = useAuth();
    const toast = useToast();
    const canMake = Boolean(session?.roles.some(role => role === 'MAKER' || role === 'ADMIN'));
    const isAdmin = Boolean(session?.roles.includes('ADMIN'));
    const [evidence, setEvidence] = useState<Evidence[]>([]);
    const [history, setHistory] = useState<Timeline[]>([]);
    const [first, setFirst] = useState('');
    const [second, setSecond] = useState('');
    const [third, setThird] = useState('');
    const [notes, setNotes] = useState('');
    const [reviewReference, setReviewReference] = useState('');
    const [reviewRemarks, setReviewRemarks] = useState('');
    const [pending, setPending] = useState<'submit' | 'accept' | 'reject' | null>(null);
    const [busy, setBusy] = useState(false);
    const inFlight = useRef(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    const refresh = useCallback(async () => {
        const [timelines, reviewed] = await Promise.all([
            api.get<Timeline[]>(`/islamic/deposits/${fdId}/execution-timeline`),
            api.get<Evidence[]>(`/islamic/deposits/${fdId}/execution-evidence`)
        ]);
        setHistory(timelines.data);
        setEvidence(reviewed.data);
        setError('');
    }, [fdId]);
    useEffect(() => {
        let active = true;
        void Promise.all([
            api.get<Timeline[]>(`/islamic/deposits/${fdId}/execution-timeline`),
            api.get<Evidence[]>(`/islamic/deposits/${fdId}/execution-evidence`)
        ]).then(([timelines, reviewed]) => {
            if (!active) return;
            setHistory(timelines.data);
            setEvidence(reviewed.data);
            setLoading(false);
            setError('');
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
    const approvedEvidence = evidence.find(row => row.review_sts === 'REVIEWED');
    const type = approvedEvidence?.contract_typ ?? current?.contract_typ ?? '';
    const steps = labels[type] ?? ['First documentary event', 'Second documentary event', null];
    const isDraft = depositStatus === 'DRAFT';
    const canCreate = canMake && isDraft && Boolean(approvedEvidence) && !loading &&
        !history.some(row => row.review_sts !== 'REJECTED');
    const canSubmit = canMake && isDraft && current?.review_sts === 'DRAFT' &&
        current.crt_by.toLowerCase() === session?.username.toLowerCase();
    const canReview = isAdmin && isDraft && current?.review_sts === 'SUBMITTED' &&
        current.crt_by.toLowerCase() !== session?.username.toLowerCase();
    const run = async (action: () => Promise<unknown>, successMessage: string) => {
        if (inFlight.current) return;
        inFlight.current = true;
        setBusy(true);
        setError('');
        try {
            const result = await action();
            await refresh();
            setPending(null);
            const reference = businessReferenceFromResponse(result);
            toast('success', `${successMessage}${reference ? ` Reference: ${reference}` : ''}`);
        } catch (e: unknown) {
            setError(errorText(e));
            toast('error', errorText(e));
        } finally {
            inFlight.current = false;
            setBusy(false);
        }
    };
    const create = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!canCreate) return;
        const t1 = new Date(first).getTime();
        const t2 = new Date(second).getTime();
        const t3 = new Date(third).getTime();
        if (!Number.isFinite(t1) || !Number.isFinite(t2) || !(t1 < t2) ||
            (type === 'TAWARRUQ' && (!Number.isFinite(t3) || !(t2 < t3)))) {
            setError('Documentary event times must be chronological. Each later event must occur after the preceding event.');
            return;
        }
        if (notes.trim().length < 20) {
            setError('Please enter at least 20 characters of documentary sequencing notes.');
            return;
        }
        void run(() => api.post(`/islamic/deposits/${fdId}/execution-timeline`, {
            primaryRecordedAt: new Date(first).toISOString(),
            secondaryRecordedAt: new Date(second).toISOString(),
            tertiaryRecordedAt: type === 'TAWARRUQ' ? new Date(third).toISOString() : null,
            controlNotes: notes.trim()
        }), 'Documentary execution timeline created. No trade or posting was performed.');
    };

    return <section className="fd-execution-timeline fd-account-section">
        <div className="fd-timeline-heading"><h2>Execution Timeline Review</h2>
            <RefreshButton loading={busy || loading}
                    onClick={() => {
                        setLoading(true);
                        void refresh().catch((e: unknown) => setError(errorText(e))).finally(() => setLoading(false));
                    }}/>
        </div>
        <p className="fd-note">Record the timestamps shown on external documents. This review does not verify external
            transactions, execute contracts, move funds or post journals.</p>
        {loading && <p className="fd-note" role="status">Loading documentary chronology…</p>}
        {error && <p className="fd-error" role="alert">{error}</p>}
        {!loading && !approvedEvidence &&
            <p className="fd-note">Complete independent Execution Evidence review before creating a timeline.</p>}
        {!loading && current && <>
            <div className="fd-timeline-summary">
                <div><small>Timeline Reference</small><strong>{current.timeline_ref}</strong></div>
                <div><small>Review Status</small><strong>{current.review_sts.replaceAll('_', ' ')}</strong></div>
                <div><small>Created By</small><strong>{current.crt_by}</strong></div>
                <div><small>Reviewed By</small><strong>{current.decided_by || 'Pending'}</strong></div>
            </div>
            <div className="fd-timeline-steps">
                {([0, 1, 2] as const).filter(i => i < 2 || current.tertiary_recorded_at).map(i => {
                    const label = labels[current.contract_typ]?.[i] || `Event ${i + 1}`;
                    const reference = [current.primary_evidence_ref, current.secondary_evidence_ref, current.tertiary_evidence_ref][i];
                    const timestamp = [current.primary_recorded_at, current.secondary_recorded_at, current.tertiary_recorded_at][i];
                    return <div key={i}>
                        <small>{label}</small><strong>{localTime(timestamp)}</strong><span>Evidence: {reference || '—'}</span>
                    </div>;
                })}
            </div>
            <div className="fd-timeline-notes"><strong>Documentary review notes</strong><p>{current.control_notes}</p>
                {current.review_reference && <p>Independent review: {current.review_reference}</p>}
                {current.review_remarks && <p>Remarks: {current.review_remarks}</p>}
            </div>
            {canSubmit && <button className="fd-primary" type="button" disabled={busy}
                                  onClick={() => setPending('submit')}>Submit for Approval</button>}
            {canReview && <form className="fd-timeline-form" onSubmit={event => {
                event.preventDefault();
                if (reviewReference.trim().length < 4) {
                    setError('Enter an independent document-review reference.');
                    return;
                }
                setPending('accept');
            }}>
                <label><FieldLabel required>Independent Review Reference</FieldLabel>
                    <input required minLength={4} maxLength={160} value={reviewReference}
                           onChange={event => setReviewReference(event.target.value)}/></label>
                <label>Review Remarks<textarea maxLength={1000} rows={2} value={reviewRemarks}
                                               onChange={event => setReviewRemarks(event.target.value)}/></label>
                <div className="fd-timeline-actions">
                    <button type="submit" className="fd-primary" disabled={busy}>Mark Documents Reviewed</button>
                    <button type="button" disabled={busy} onClick={() => {
                        if (reviewRemarks.trim().length < 5) {
                            setError('Enter a rejection reason of at least 5 characters.');
                            return;
                        }
                        setPending('reject');
                    }}>Reject
                    </button>
                </div>
            </form>}
            {current.review_sts === 'SUBMITTED' && !canReview &&
                <p className="fd-note">Awaiting independent ADMIN review.</p>}
        </>}
        {!loading && canCreate && <form className="fd-timeline-form fd-timeline-create-form" onSubmit={create}>
            <h3>Record Documentary Event Times</h3>
            <p className="fd-note">References come from reviewed Execution Evidence; do not retype their
                identifiers.</p>
            <div className="fd-timeline-event-grid">
                {([0, 1, 2] as const).filter(i => i < 2 || type === 'TAWARRUQ').map(i => <label key={i}>
                    <FieldLabel required>{steps[i]} — Local Date &amp; Time</FieldLabel>
                    <small>External
                        reference: {[approvedEvidence?.primary_evidence_ref, approvedEvidence?.secondary_evidence_ref, approvedEvidence?.tertiary_evidence_ref][i]}</small>
                    <input type="datetime-local" required value={[first, second, third][i]}
                           onChange={event => [setFirst, setSecond, setThird][i](event.target.value)}/>
                </label>)}
            </div>
            <label><FieldLabel required>Documentary Sequencing Notes</FieldLabel>
                <textarea required minLength={20} maxLength={1000} rows={3} value={notes}
                          onChange={event => setNotes(event.target.value)}
                          placeholder="Explain which external records contain these timestamps and who supplied them."/></label>
            <button type="submit" className="fd-primary" disabled={busy}>Create</button>
        </form>}
        {!loading && history.length > 1 && <p className="fd-note">Previous rejected
            submissions: {history.filter(row => row.review_sts === 'REJECTED').length}.</p>}
        {pending && current && <ConfirmDialog
            title={pending === 'submit' ? 'Submit Execution Timeline?' : pending === 'accept' ? 'Review Documentary Timeline?' : 'Reject Execution Timeline?'}
            message={pending === 'submit' ? 'Are you sure you want to submit this timeline for review?' :
                pending === 'accept' ? 'Are you sure you want to mark the timeline documents as reviewed?' :
                    'Are you sure you want to reject this timeline?'}
            confirmLabel={pending === 'submit' ? 'Submit for Approval' : pending === 'accept' ? 'Mark Reviewed' : 'Reject'}
            danger={pending === 'reject'} busy={busy} onCancel={() => setPending(null)}
            onConfirm={() => {
                if (pending === 'submit') {
                    void run(() => api.post(`/islamic/deposits/execution-timeline/${current.timeline_id}/submit`),
                        'Timeline submitted for independent document review.');
                } else {
                    void run(() => api.post(`/islamic/deposits/execution-timeline/${current.timeline_id}/decision`, {
                        decision: pending === 'accept' ? 'ACCEPT' : 'REJECT',
                        reviewReference: pending === 'accept' ? reviewReference.trim() : null,
                        remarks: reviewRemarks.trim()
                    }), pending === 'accept' ? 'Documentary timeline reviewed; no execution was authorized.' : 'Timeline rejected.');
                }
            }}/>}
    </section>;
}
