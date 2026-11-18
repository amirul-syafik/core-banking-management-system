import {useState, type FormEvent} from 'react';
import {AxiosError} from 'axios';
import {Link, Navigate, useLocation, useNavigate} from 'react-router-dom';
import {Landmark, LockKeyhole, ArrowRight} from 'lucide-react';
import {useAuth} from '../auth/useAuth';

export function Login() {
    const {session, login} = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    if (session) return <Navigate to="/" replace/>;

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError('');
        setLoading(true);
        try {
            await login(username.trim(), password);
            const destination = (location.state as { from?: string } | null)?.from;
            navigate(destination?.startsWith('/') && !destination.startsWith('//') ? destination : '/', {
                replace: true,
                state: {sidebarFreshLogin: true}
            });
        } catch (err) {
            const axiosError = err as AxiosError<{ message?: string }>;
            setError(axiosError.response?.status === 401 ? 'Invalid username or password.' : axiosError.response?.data?.message || (err instanceof Error ? err.message : 'Unable to sign in.'));
        } finally {
            setLoading(false);
        }
    }

    return <div className="login-page">
        <div className="login-card">
            <div className="login-logo"><Landmark size={25}/></div>
            <div className="login-kicker">CORE BANKING MANAGEMENT SYSTEM</div>
            <h1>Welcome back</h1><p>Sign in to Core Banking Management System.</p>
            {(location.state as { passwordChanged?: boolean } | null)?.passwordChanged &&
                <div className="login-notice" role="status">Password changed successfully. Sign in again.</div>}
            <form onSubmit={submit} className="login-form">
                <label htmlFor="username">Username</label><input id="username" autoComplete="username" required
                                                                 value={username}
                                                                 onChange={e => setUsername(e.target.value)}/>
                <label htmlFor="password">Password</label><input id="password" type="password"
                                                                 autoComplete="current-password" required
                                                                 value={password}
                                                                 onChange={e => setPassword(e.target.value)}/>
                {error && <div className="login-error" role="alert">{error}</div>}
                <button className="login-submit" disabled={loading} type="submit"><LockKeyhole
                    size={17}/>{loading ? 'Signing in…' : 'Sign in'}<ArrowRight size={17}/></button>
            </form>
            <div className="login-account-help"><Link to="/forgot-password">Forgot Password?</Link></div>
            <div className="login-footnote">Sign in using your assigned banking system credentials. Contact your system
                administrator if you require access.
            </div>
        </div>
    </div>;
}
