import {useEffect, useState} from 'react';
import {AxiosError} from 'axios';
import {api} from '../api/client';
import {RefreshButton} from './UiStandards';

type Gate = { code: string; label: string; status: 'PASSED' | 'BLOCKED' | 'REVIEW_REQUIRED'; detail: string };
type Report = {
    fdId: number; releaseReady: boolean; postingEnabled: boolean;
    contractExecutionEnabled: boolean; gates: Gate[]; message: string
};
const displayError = (error: unknown) => error instanceof AxiosError
    ? String(error.response?.data?.detail || error.response?.data?.message || error.message)
    : error instanceof Error ? error.message : 'Phase 10 release controls unavailable';

export default function IslamicExecutionGates({fdId}: { fdId: number }) {
    return <IslamicExecutionGatesContent key={fdId} fdId={fdId}/>;
}

function IslamicExecutionGatesContent({fdId}: { fdId: number }) {
    const [report, setReport] = useState<Report | null>(null);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);
    const [reload, setReload] = useState(0);
    useEffect(() => {
        let active = true;
        void api.get<Report>(`/islamic/deposits/${fdId}/phase10-gates`)
            .then(result => {
                if (active) {
                    setReport(result.data);
                    setError('');
                }
            })
            .catch((e: unknown) => {
                if (active) {
                    setReport(null);
                    setError(displayError(e));
                }
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, [fdId, reload]);
    const refresh = () => {
        // Event-driven reset is permitted; the effect only synchronizes with the API.
        setLoading(true);
        setReport(null);
        setError('');
        setReload(value => value + 1);
    };
    return <div className="fd-posting-readiness" aria-label="Phase 10 release controls">
        <div className="fd-posting-readiness-head"><h3>Phase 10 Release Controls</h3>
            <RefreshButton loading={loading} onClick={refresh}/>
        </div>
        {loading && <p className="fd-note">Checking release controls…</p>}
        {error && <p className="fd-error" role="alert">{error}</p>}
        {!loading && report && <>
            <p className="fd-note">{report.message}</p>
            <div className="fd-posting-checks">
                {report.gates.map(gate => <div className="fd-posting-check" key={gate.code}>
                    <span
                        className={`fd-posting-check-status fd-check-${gate.status === 'PASSED' ? 'passed' : gate.status === 'BLOCKED' ? 'blocked' : 'pending'}`}>
                        {gate.status === 'REVIEW_REQUIRED' ? 'Review required' : gate.status === 'PASSED' ? 'Passed' : 'Blocked'}
                    </span>
                    <div><strong>{gate.label}</strong><small>{gate.detail}</small></div>
                </div>)}
            </div>
        </>}
    </div>;
}
