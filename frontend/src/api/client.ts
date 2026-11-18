import axios from 'axios';
import {clearSession, getSession} from '../auth/session';
import {SESSION_EXPIRED_EVENT} from '../auth/sessionEvents';

export const api = axios.create({baseURL: '/api', timeout: 15000, headers: {'Content-Type': 'application/json'}});
api.interceptors.request.use(config => {
    // Login is public. Never send a cached/expired bearer token to the JWT filter:
    // the filter may reject it before the login controller has a chance to run.
    const path = String(config.url ?? '').split('?')[0];
    if (['/auth/login', '/api/auth/login', '/auth/forgot-password', '/api/auth/forgot-password'].includes(path)) {
        config.headers.delete('Authorization');
        return config;
    }
    const session = getSession();
    if (session) config.headers.set('Authorization', `Bearer ${session.token}`);
    return config;
});
api.interceptors.response.use(response => response, error => {
    // A failed login must not invalidate an unrelated session.
    const url = String(error.config?.url || '');
    if (error.response?.status === 401 && !url.includes('/auth/login') && !url.includes('/auth/forgot-password')) {
        clearSession();
        if (window.location.pathname !== '/login') window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    }
    return Promise.reject(error);
});
