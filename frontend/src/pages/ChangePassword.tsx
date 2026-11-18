import {useState, type FormEvent} from 'react';
import {useNavigate} from 'react-router-dom';
import {PageHeader} from '../components/UiStandards';
import {api} from '../api/client';
import {useAuth} from '../auth/useAuth';
import './password.css';

type FormValues = { currentPassword: string; newPassword: string; confirmPassword: string };
const empty: FormValues = {currentPassword: '', newPassword: '', confirmPassword: ''};

export default function ChangePassword() {
    const [fields, setFields] = useState<FormValues>(empty);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const {logout} = useAuth();
    const navigate = useNavigate();

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError('');
        if (fields.newPassword !== fields.confirmPassword) {
            setError('New password and confirmation do not match.');
            return;
        }
        if (fields.currentPassword === fields.newPassword) {
            setError('Choose a new password that differs from your current password.');
            return;
        }
        setBusy(true);
        try {
            await api.post('/auth/change-password', {
                currentPassword: fields.currentPassword,
                newPassword: fields.newPassword
            });
            setFields(empty);
            logout();
            navigate('/login', {replace: true, state: {passwordChanged: true}});
        } catch (e: unknown) {
            const response = e as { response?: { data?: { message?: string; detail?: string } } };
            setError(response.response?.data?.message || response.response?.data?.detail || 'Unable to change password.');
        } finally {
            setBusy(false);
        }
    }

    return <main className="password-page">
        <PageHeader title="Change Password"
                    description="Update your login password. All existing sessions will be invalidated."/>
        <section className="password-panel">
            <form onSubmit={submit} className="password-form">
                <label>Current Password *<input type="password" autoComplete="current-password" required
                                                value={fields.currentPassword} onChange={e => setFields(old => ({
                    ...old,
                    currentPassword: e.target.value
                }))}/></label>
                <label>New Password *<input type="password" autoComplete="new-password" required minLength={12}
                                            maxLength={128}
                                            value={fields.newPassword}
                                            onChange={e => setFields(old => ({...old, newPassword: e.target.value}))}/></label>
                <label>Confirm New Password *<input type="password" autoComplete="new-password" required minLength={12}
                                                    maxLength={128}
                                                    value={fields.confirmPassword} onChange={e => setFields(old => ({
                    ...old,
                    confirmPassword: e.target.value
                }))}/></label>
                <p className="password-hint">Use at least 12 characters. You will need to sign in again.</p>
                {error && <div className="login-error" role="alert">{error}</div>}
                <div className="password-actions">
                    <button type="button" onClick={() => navigate(-1)} disabled={busy}>Cancel</button>
                    <button type="submit" disabled={busy}
                            className="login-submit">{busy ? 'Saving…' : 'Change Password'}</button>
                </div>
            </form>
        </section>
    </main>;
}
