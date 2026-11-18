import {useEffect, useMemo, useState} from 'react';
import {api} from '../api/client';

export type RateOptions = {
    schmId: number; rateType: 'FIXED' | 'FLOATING'; effectiveDate: string;
    minimumRatePct: number; maximumRatePct: number; defaultRatePct: number;
};

/** Dropdown is a UI convenience; the backend remains authoritative. */
export default function SchemeRateSelect({
                                             schemeId,
                                             effectiveDate,
                                             rateType,
                                             value,
                                             onChange,
                                             onPolicyChange,
                                             mismatchMessage,
                                             disabled = false
                                         }: {
    schemeId: number | null; effectiveDate: string; rateType?: string;
    value: string; onChange: (value: string) => void;
    onPolicyChange?: (policy: RateOptions | null) => void; mismatchMessage?: string; disabled?: boolean;
}) {
    const [policy, setPolicy] = useState<RateOptions | null>(null);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    useEffect(() => {
        let active = true;
        void Promise.resolve().then(() => {
            if (!active) return;
            setPolicy(null);
            onPolicyChange?.(null);
            setError('');
            setLoading(Boolean(schemeId && effectiveDate));
        });
        if (!schemeId || !effectiveDate) return () => {
            active = false;
        };
        void api.get<RateOptions>(`/rates/scheme/${schemeId}/options`, {params: {date: effectiveDate}})
            .then(({data}) => {
                if (active) {
                    setPolicy(data);
                    onPolicyChange?.(data);
                }
            })
            .catch(() => {
                if (active) {
                    setError('No effective scheme rate is available for this date.');
                    onPolicyChange?.(null);
                }
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, [schemeId, effectiveDate, onPolicyChange]);
    const options = useMemo(() => {
        if (!policy || (rateType && policy.rateType !== rateType)) return [];
        const min = Number(policy.minimumRatePct), max = Number(policy.maximumRatePct),
            normal = Number(policy.defaultRatePct);
        if (![min, max, normal].every(Number.isFinite) || min > max || min < 0 || max > 100) return [];
        // Rates of width <=10% advance in 0.01% steps; wider ranges use 0.05% steps.
        const increment = max - min > 10 ? 5 : 1;
        const values = new Set<number>([min, max, normal]);
        for (let step = Math.ceil(min * 100 / increment) * increment; step <= Math.floor(max * 100); step += increment)
            values.add(step / 100);
        return [...values].sort((a, b) => a - b).map(x => String(Number(x.toFixed(5))));
    }, [policy, rateType]);
    const compatible = !rateType || policy?.rateType === rateType;
    useEffect(() => {
        if (policy && value && !options.includes(value)) onChange('');
    }, [policy, options, value, onChange]);
    return <>
        <select required disabled={disabled || loading || !schemeId || !compatible || options.length === 0}
                value={options.includes(value) ? value : ''} onChange={e => onChange(e.target.value)}>
            <option
                value="">{!schemeId ? 'Select scheme first' : loading ? 'Loading scheme rates…' : !compatible ? 'Unsupported rate type' : 'Select effective rate'}</option>
            {options.map(rate => <option key={rate} value={rate}>{rate}%</option>)}
        </select>
        {policy && !compatible && mismatchMessage && <small role="status">{mismatchMessage}</small>}
        {error && <small role="alert">{error}</small>}
    </>;
}
