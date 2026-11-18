import {useEffect, useState} from 'react';
import {AlertTriangle} from 'lucide-react';
import {clearSession} from '../auth/session';
import {SESSION_EXPIRED_EVENT} from '../auth/sessionEvents';
import './SessionExpiredOverlay.css';

/** Covers the banking UI on expiry. Clicking anywhere returns to a clean login screen. */
export function SessionExpiredOverlay() {
    const [expired, setExpired] = useState(false);
    useEffect(() => {
        const onExpired = () => {
            if (window.location.pathname !== '/login') setExpired(true);
        };
        window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
        return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
    }, []);
    if (!expired) return null;
    const signIn = () => {
        clearSession();
        window.location.replace('/login?sessionExpired=1');
    };
    return <button type="button" className="cbms-session-expired-overlay" onClick={signIn}
                   aria-label="Session expired. Please sign in again. Click to return to login.">
        <span className="cbms-session-expired-banner" role="alert">
            <AlertTriangle size={21} aria-hidden="true"/>
            <span><strong>Session over.</strong> Please re-login.</span>
            <span className="cbms-session-expired-action">Click anywhere to return to login</span>
        </span>
    </button>;
}
