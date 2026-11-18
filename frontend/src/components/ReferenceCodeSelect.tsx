import {useEffect, useState} from 'react';
import {getReferenceOptions, type ReferenceCodeType, type ReferenceOption} from '../api/referenceData';

type Props = {
    kind: ReferenceCodeType;
    value: string;
    onChange: (value: string) => void;
    required?: boolean;
    autoFocus?: boolean;
    disabled?: boolean;
};

/** Native dropdown for ISO country and currency codes. */
export default function ReferenceCodeSelect({
                                                kind,
                                                value,
                                                onChange,
                                                required = false,
                                                autoFocus = false,
                                                disabled = false
                                            }: Props) {
    const [options, setOptions] = useState<ReferenceOption[]>([]);
    const [loading, setLoading] = useState(true);
    const [failed, setFailed] = useState(false);

    useEffect(() => {
        let active = true;
        void getReferenceOptions(kind).then(items => {
            if (active) {
                setOptions(items);
                setLoading(false);
                setFailed(false);
            }
        }).catch(() => {
            if (active) {
                setLoading(false);
                setFailed(true);
            }
        });
        return () => {
            active = false;
        };
    }, [kind]);

    const code = value.trim().toUpperCase();
    // Preserve saved codes when the catalog is still loading, unavailable,
    // or doesn't contain a legacy value. Never silently clear the record.
    const existingValueNotListed = code.length > 0 && (loading || failed || !options.some(option => option.code === code));
    const fieldName = kind === 'countries' ? 'country' : 'currency';
    return <>
        <select autoFocus={autoFocus} disabled={disabled} required={required}
                value={code} onChange={event => onChange(event.target.value)}
                aria-label={`Select ${fieldName}`} aria-busy={loading}>
            <option
                value="">{loading ? 'Loading options…' : failed ? 'Options unavailable' : `Select ${fieldName}`}</option>
            {existingValueNotListed && <option value={code}>{code} — Existing value</option>}
            {!failed && options.map(option => <option key={option.code} value={option.code}>
                {kind === 'countries' ? `${option.name} (${option.code})` : `${option.code} — ${option.name}`}
            </option>)}
        </select>
        {failed &&
            <small role="status">Reference options are unavailable. Reopen the form to retry; your existing value is
                preserved.</small>}
    </>;
}
