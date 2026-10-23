import {useState} from 'react';
import {AxiosError} from 'axios';
import AccountLov from '../components/AccountLov';
import {useToast} from '../components/toastContext';
import {useAuth} from '../auth/useAuth';
import {postAccountTransaction, postAccountTransfer, type Account} from '../api/accounts';
import './Transactions.css';

type Kind = 'deposit' | 'withdrawal' | 'transfer';

function message(error: unknown) {
    if (error instanceof AxiosError) {
        const body: unknown = error.response?.data;
        if (body && typeof body === 'object') {
            if ('detail' in body && typeof body.detail === 'string') return body.detail;
            if ('message' in body && typeof body.message === 'string') return body.message;
        }
        return `Request failed (${error.response?.status ?? 'network'}).`;
    }
    return error instanceof Error ? error.message : 'Request failed.';
}

export default function Transactions() {
    const {session} = useAuth();
    const notify = useToast();
    const canPost = Boolean(session?.roles.some(r => r === 'MAKER' || r === 'ADMIN'));
    const [kind, setKind] = useState<Kind>('deposit');
    const [source, setSource] = useState<Account | null>(null);
    const [destination, setDestination] = useState<Account | null>(null);
    const [amount, setAmount] = useState('');
    const [confirm, setConfirm] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [key, setKey] = useState('');
    const validAmount = /^(?:0|[1-9]\d{0,16})(?:\.\d{1,2})?$/.test(amount.trim()) && Number(amount) > 0;

    function reset() {
        setSource(null);
        setDestination(null);
        setAmount('');
        setConfirm(false);
        setKey('');
        setError('');
    }

    function chooseKind(next: Kind) {
        setKind(next);
        reset();
    }

    function review() {
        if (!canPost || busy) return;
        if (!source || source.acctSts !== 'ACTIVE') {
            setError('Select an ACTIVE account.');
            return;
        }
        if (!validAmount) {
            setError('Enter a positive amount with at most two decimal places.');
            return;
        }
        if (kind === 'transfer') {
            if (!destination || destination.acctSts !== 'ACTIVE') {
                setError('Select an ACTIVE destination account.');
                return;
            }
            if (source.acctId === destination.acctId) {
                setError('Source and destination must differ.');
                return;
            }
            if (source.ccyCd !== destination.ccyCd) {
                setError('Source and destination currencies must match.');
                return;
            }
        }
        setError('');
        setKey(crypto.randomUUID());
        setConfirm(true);
    }

    async function post() {
        if (!canPost || !source || !validAmount || !confirm || !key || busy || (kind === 'transfer' && !destination)) return;
        setBusy(true);
        setError('');
        try {
            const result = kind === 'transfer'
                ? await postAccountTransfer(source.acctId, destination!.acctId, amount.trim(), key)
                : await postAccountTransaction(source.acctId, kind, amount.trim(), key);
            notify('success', `Transaction posted successfully. Reference: ${result.txnRef}`);
            reset();
        } catch (e) {
            const msg = message(e);
            setError(msg);
            notify('error', msg);
        } finally {
            setBusy(false);
        }
    }

    return <main className="cbms-transactions">
        <header><h1>Transactions</h1><p>Post deposits, withdrawals and transfers. Posting occurs immediately after
            confirmation.</p></header>
        <section className="cbms-transactions-card">
            <div className="cbms-transactions-tabs" role="tablist"
                 aria-label="Transaction type">{(['deposit', 'withdrawal', 'transfer'] as const).map(k => <button
                key={k} type="button" role="tab" aria-selected={kind === k} className={kind === k ? 'active' : ''}
                onClick={() => chooseKind(k)}>{k[0].toUpperCase() + k.slice(1)}</button>)}</div>
            {!canPost ? <p>You have read-only access to Transactions.</p> : <>
                {error && <p className="cbms-transactions-error" role="alert">{error}</p>}
                {!confirm ? <form className="cbms-transactions-form" onSubmit={e => {
                    e.preventDefault();
                    review();
                }}>
                    <AccountLov label={kind === 'deposit' ? 'Receiving account' : 'Source account'} value={source}
                                onChange={a => {
                                    setSource(a);
                                    setDestination(null);
                                    setError('');
                                }} disabled={busy}/>
                    {kind === 'transfer' && <AccountLov label="Destination account" value={destination} onChange={a => {
                        setDestination(a);
                        setError('');
                    }} excludeAccountId={source?.acctId} disabled={busy}/>}
                    <label><span className="cbms-field-label">Amount ({source?.ccyCd ?? 'MYR'}) <span
                        className="cbms-required-star" aria-hidden="true">*</span></span><input required
                                                                                                inputMode="decimal"
                                                                                                placeholder="0.00"
                                                                                                value={amount}
                                                                                                disabled={busy}
                                                                                                onChange={e => {
                                                                                                    setAmount(e.target.value);
                                                                                                    setError('');
                                                                                                }}/></label>
                    <p className="cbms-transactions-note">Transactions are posted immediately. There is no maker-checker
                        approval for these operations.</p>
                    <div className="cbms-transactions-actions">
                        <button type="button" onClick={reset}>Clear</button>
                        <button type="submit"
                                disabled={busy || !source || !validAmount || (kind === 'transfer' && !destination)}>Review
                            transaction
                        </button>
                    </div>
                </form> : <div className="cbms-transactions-review"><h2>Confirm transaction</h2>
                    <dl>
                        <div>
                            <dt>Type</dt>
                            <dd>{kind.toUpperCase()}</dd>
                        </div>
                        <div>
                            <dt>{kind === 'deposit' ? 'Receiving account' : 'Source account'}</dt>
                            <dd>{source?.acctNo}</dd>
                        </div>
                        {kind === 'transfer' && <div>
                            <dt>Destination account</dt>
                            <dd>{destination?.acctNo}</dd>
                        </div>}
                        <div>
                            <dt>Amount</dt>
                            <dd>{source?.ccyCd} {Number(amount).toLocaleString('en-MY', {
                                minimumFractionDigits: 2,
                                maximumFractionDigits: 2
                            })}</dd>
                        </div>
                    </dl>
                    <p className="cbms-transactions-error">Confirming will post this financial transaction
                        immediately.</p>
                    <div className="cbms-transactions-actions">
                        <button type="button" disabled={busy} onClick={() => {
                            setConfirm(false);
                            setKey('');
                        }}>Back
                        </button>
                        <button type="button" disabled={busy}
                                onClick={() => void post()}>{busy ? 'Posting…' : 'Confirm and post'}</button>
                    </div>
                </div>}
            </>}</section>
    </main>;
}
