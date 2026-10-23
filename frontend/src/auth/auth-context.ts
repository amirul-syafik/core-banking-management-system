import {createContext} from 'react';
import type {Session} from './session';

export type AuthContextValue = {
    session: Session | null;
    login: (username: string, password: string) => Promise<void>;
    logout: () => void;
};

export const AuthContext = createContext<AuthContextValue | undefined>(
    undefined
);