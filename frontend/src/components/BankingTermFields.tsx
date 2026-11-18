import './BankingTermFields.css';
import {useMemo} from 'react';
import {interestEstimate} from './bankingInterest';
import type {InterestMethod} from './bankingInterest';
import type {RateOptions} from './SchemeRateSelect';

/** Scheme tenure is an inclusive number of whole months. */
export function TenorSelect({value, onChange, minMonths, maxMonths, disabled = false}: {
    value: string;
    onChange: (next: string) => void;
    minMonths?: number | null;
    maxMonths?: number | null;
    disabled?: boolean;
}) {
    const min = Number.isFinite(Number(minMonths)) && minMonths != null ? Math.max(1, Math.ceil(Number(minMonths))) : 1;
    const max = Number.isFinite(Number(maxMonths)) && maxMonths != null ? Math.min(600, Math.floor(Number(maxMonths))) : 600;
    const options = useMemo(() => max >= min ? Array.from({length: max - min + 1}, (_, i) => i + min) : [], [min, max]);
    return <select required value={options.some(n => String(n) === value) ? value : ''}
                   disabled={disabled || options.length === 0} onChange={e => onChange(e.target.value)}>
        <option value="">Select tenure</option>
        {options.map(n => <option key={n} value={n}>{n} {n === 1 ? 'month' : 'months'}</option>)}
    </select>;
}

export function TotalInterestField({amount, rate, tenor, method, startDate, label = 'Total Interest (MYR)'}: {
    amount: string; rate: string; tenor: string; method: InterestMethod;
    startDate?: string; label?: string;
}) {
    const amountValue = interestEstimate(amount, rate, tenor, method, startDate);
    const amountTerm = /profit/i.test(label) ? 'profit' : 'interest';
    const explanation = method === 'credit-full-utilization'
        ? `Estimated ${amountTerm} at full utilization throughout the tenure. Actual charges depend on the utilized balance.`
        : method === 'financing-flat'
            ? `Indicative flat ${amountTerm} amount. The approved repayment schedule controls the final amount.`
            : `Indicative ACT/365 ${amountTerm}. Final amounts are calculated by the backend.`;
    return <label className="cbms-interest-preview">
        <span>{label}</span>
        <input type="text" value={amountValue == null ? '—' : amountValue.toLocaleString('en-MY', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        })}
               readOnly aria-label={label} title={explanation} className="cbms-readonly-interest"/>
    </label>;
}

/** Compact scheme-policy summary, kept outside the form grid so input rows stay aligned. */
export function SchemeLimitsSummary({scheme, policy, effectiveDate, rateType}: {
    scheme: {
        schmId: number;
        minAmt: number | null;
        maxAmt: number | null;
        minTenor: number | null;
        maxTenor: number | null;
    } | null | undefined;
    policy?: RateOptions | null;
    effectiveDate?: string;
    rateType?: string;
}) {
    if (!scheme) return null;
    const money = (value: number | null) => value == null ? '—' :
        Number(value).toLocaleString('en-MY', {minimumFractionDigits: 2, maximumFractionDigits: 2});
    const rate = (value: number) => `${Number(value).toFixed(2)}%`;
    const activePolicy = policy?.schmId === scheme.schmId &&
    (!effectiveDate || policy.effectiveDate === effectiveDate) &&
    (!rateType || policy.rateType === rateType) ? policy : null;
    return <div className="cbms-scheme-limits" aria-label="Selected scheme limits">
        <span><strong>Scheme limits:</strong> RM {money(scheme.minAmt)} – RM {money(scheme.maxAmt)}</span>
        <span>{scheme.minTenor ?? '—'}–{scheme.maxTenor ?? '—'} months</span>
        {activePolicy && <>
            <span><strong>{activePolicy.rateType === 'FIXED' ? 'Fixed rate:' : 'Reference rate:'}</strong> {rate(activePolicy.defaultRatePct)}</span>
            <span><strong>Minimum:</strong> {rate(activePolicy.minimumRatePct)}</span>
            <span><strong>Maximum:</strong> {rate(activePolicy.maximumRatePct)}</span>
        </>}
    </div>;
}
