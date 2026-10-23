import {
    useCallback,
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
    type Session,
} from './session';

import {AuthContext} from './auth-context';

export function AuthProvider({children}: { children: ReactNode }) {
    const [session, setSession] = useState<Session | null>(() => getSession());

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