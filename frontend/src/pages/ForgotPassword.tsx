import {useState, type FormEvent} from 'react';
import {Link} from 'react-router-dom';
import {Landmark} from 'lucide-react';
import {api} from '../api/client';

export default function ForgotPassword() {
    const [username, setUsername] = useState('');
    const [busy, setBusy] = useState(false);
    const [submitted, setSubmitted] = useState(false);
    const [error, setError] = useState('');

    const request = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setBusy(true);
        setError('');
        try {
            await api.post('/auth/forgot-password', {username: username.trim()});
            setSubmitted(true);
        } catch {
            setError('Unable to submit the request. Please contact your system administrator.');
        } finally {
            setBusy(false);
        }
    };

    return <main className="login-page">
        <section className="login-card">
            <div className="login-logo"><Landmark size={25}/></div>
            <div className="login-kicker">CORE BANKING MANAGEMENT SYSTEM</div>
            <h1>Forgot Password</h1>
            <p>Request help resetting your banking system password.</p>
            {submitted ? <div className="login-notice" role="status">
                If the account is active, your request will appear in the administrator's recovery queue.
                Contact an authorized administrator to verify your identity before they reset your password.
            </div> : <form className="login-form" onSubmit={request}>
                <label htmlFor="recovery-username">Username</label>
                <input id="recovery-username" required maxLength={100} autoComplete="username"
                       value={username} onChange={event => setUsername(event.target.value)}/>
                {error && <div className="login-error" role="alert">{error}</div>}
                <button type="submit" className="login-submit" disabled={busy}>
                    {busy ? 'Submitting…' : 'Request Password Reset'}
                </button>
            </form>}
            <p className="login-footnote">For security, the response does not confirm whether an account exists.
                This is an administrator-assisted recovery process, not an emailed reset link.</p>
            <div className="login-account-help"><Link to="/login">Back to Sign In</Link></div>
        </section>
    </main>;
}
