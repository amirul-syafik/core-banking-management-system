import {
    useCallback,
    useEffect,
    useMemo,
    useState,
    type ReactNode,
} from 'react';

import {api} from '../api/client';

import {
    clearSession,
    getSession,
    saveSession,
    sessionFromLogin,
    tokenExpiryMs,
    type Session,
} from './session';

import {AuthContext} from './auth-context';
import {SESSION_EXPIRED_EVENT} from './sessionEvents';

export function AuthProvider({children}: { children: ReactNode }) {
    const [session, setSession] = useState<Session | null>(() => getSession());

    useEffect(() => {
        if (!session) return;
        const expiry = tokenExpiryMs(session.token);
        if (expiry == null) return;
        let timer = 0;
        const check = () => {
            const remaining = expiry - Date.now();
            if (remaining <= 0) {
                clearSession();
                if (window.location.pathname !== '/login') window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
            } else timer = window.setTimeout(check, Math.min(remaining, 60000));
        };
        check();
        return () => window.clearTimeout(timer);
    }, [session]);

    const logout = useCallback(() => {
        clearSession();
        setSession(null);
    }, []);

    const login = useCallback(async (username: string, password: string) => {
        const {data} = await api.post('/auth/login', {
            username,
            password,
        });

        const next = sessionFromLogin(data, username);

        saveSession(next);
        setSession(next);
    }, []);

    const value = useMemo(
        () => ({
            session,
            login,
            logout,
        }),
        [session, login, logout]
    );

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
}
