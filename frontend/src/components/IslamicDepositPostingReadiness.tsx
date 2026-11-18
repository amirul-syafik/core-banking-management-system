import {useCallback, useEffect, useState} from 'react';
import {AxiosError} from 'axios';
import {api} from '../api/client';
import {RefreshButton} from './UiStandards';
import './IslamicDepositExecutionEvidence.css';

type PostingReadinessCheck = {
    code: string;
    label: string;
    status: 'PASSED' | 'PENDING' | 'BLOCKED';
    detail: string;
};
type PostingReadiness = {
    fdId: number;
    depositStatus: string;
    contractType: string | null;
    postingEnabled: boolean;
    checks: PostingReadinessCheck[];
    message: string;
};
type ExternalVerification = {
    fdId: number;
    status: 'BLOCKED' | 'VERIFIED_FOR_INDEPENDENT_REVIEW';
    postingEnabled: boolean;
    depositActivationEnabled: boolean;
    blockers: { code: string; label: string; detail: string }[];
    message: string;
};
const errorText = (error: unknown) => error instanceof AxiosError
    ? String(error.response?.data?.detail || error.response?.data?.message || error.message)
    : error instanceof Error ? error.message : 'Unable to load posting readiness';

/** Read-only reporting. Neither response can authorize contract execution or posting. */
export default function IslamicDepositPostingReadiness({fdId}: { fdId: number }) {
    // Remount on a deposit change so the initial loading state is reset without
    // a synchronous state update inside useEffect.
    return <IslamicDepositPostingReadinessContent key={fdId} fdId={fdId}/>;
}

function IslamicDepositPostingReadinessContent({fdId}: { fdId: number }) {
    const [readiness, setReadiness] = useState<PostingReadiness | null>(null);
    const [external, setExternal] = useState<ExternalVerification | null>(null);
    const [loading, setLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        let active = true;
        void Promise.all([
            api.get<PostingReadiness>(`/islamic/deposits/${fdId}/posting-readiness`),
            api.get<ExternalVerification>(`/islamic/deposits/${fdId}/external-verification`)
        ]).then(([posting, verification]) => {
            if (active) {
                setReadiness(posting.data);
                setExternal(verification.data);
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

    const refresh = useCallback(async () => {
        if (refreshing) return;
        setRefreshing(true);
        // A failed refresh must never leave an old readiness result visible.
        setReadiness(null);
        setExternal(null);
        setError('');
        try {
            const [posting, verification] = await Promise.all([
                api.get<PostingReadiness>(`/islamic/deposits/${fdId}/posting-readiness`),
                api.get<ExternalVerification>(`/islamic/deposits/${fdId}/external-verification`)
            ]);
            setReadiness(posting.data);
            setExternal(verification.data);
            setError('');
        } catch (e: unknown) {
            setError(errorText(e));
        } finally {
            setRefreshing(false);
        }
    }, [fdId, refreshing]);

    const externalLabel = external?.status === 'VERIFIED_FOR_INDEPENDENT_REVIEW'
        ? 'Requires independent review' : 'Not verified';

    return <section className="fd-account-section fd-execution-review" aria-label="Islamic financial posting readiness">
        <div className="fd-execution-heading">
            <div>
                <h2>Financial Posting Readiness</h2>
                <p className="fd-note">Review the prerequisites for Islamic financial processing. This screen does not
                    authorize financial transactions.</p>
            </div>
            <RefreshButton loading={loading || refreshing} onClick={() => void refresh()}/>
        </div>
        {loading && <p className="fd-note" role="status">Checking financial posting readiness…</p>}
        {refreshing && <p className="fd-note" role="status">Refreshing all financial readiness checks…</p>}
        {error &&
            <p className="fd-error" role="alert">Readiness information is unavailable. No posting authorization can be
                inferred. {error}</p>}
        {!loading && readiness && <div className="fd-posting-readiness">
            <div className="fd-posting-readiness-head">
                <h3>Preflight Checks</h3>
                <span className="fd-posting-readiness-status">
                    {readiness.postingEnabled ? 'Enabled' : 'Not enabled'}
                </span>
            </div>
            <p className="fd-note">{readiness.message}</p>
            <div className="fd-posting-checks">
                {readiness.checks.map(check => <div key={check.code} className="fd-posting-check">
                    <span className={`fd-posting-check-status fd-check-${check.status.toLowerCase()}`}>
                        {check.status.replaceAll('_', ' ')}
                    </span>
                    <div><strong>{check.label}</strong><small>{check.detail}</small></div>
                </div>)}
            </div>
        </div>}
        {!loading && external && <div className="fd-posting-readiness" aria-label="External execution verification">
            <div className="fd-posting-readiness-head">
                <h3>External Execution Verification</h3>
                <span className="fd-posting-readiness-status">{externalLabel}</span>
            </div>
            <p className="fd-note">{external.message}</p>
            {external.blockers.length > 0 && <div className="fd-posting-checks">
                {external.blockers.map(blocker => <div key={blocker.code} className="fd-posting-check">
                    <span className="fd-posting-check-status fd-check-blocked">Blocked</span>
                    <div><strong>{blocker.label}</strong><small>{blocker.detail}</small></div>
                </div>)}
            </div>}
            {external.status === 'VERIFIED_FOR_INDEPENDENT_REVIEW' && <p className="fd-note">
                External evidence is available for independent review. This does not authorize contract execution,
                settlement or financial posting.
            </p>}
        </div>}
    </section>;
}
