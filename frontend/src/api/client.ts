import axios from 'axios';
import {clearSession, getSession} from '../auth/session';

export const api = axios.create({baseURL: '/api', timeout: 15000, headers: {'Content-Type': 'application/json'}});
api.interceptors.request.use(config => {
    const session = getSession();
    if (session) config.headers.set('Authorization', `Bearer ${session.token}`);
    return config;
});
api.interceptors.response.use(response => response, error => {
    // A failed login must not invalidate an unrelated session.
    const url = String(error.config?.url || '');
    if (error.response?.status === 401 && !url.includes('/auth/login')) {
        clearSession();
        if (window.location.pathname !== '/login') window.location.assign('/login');
    }
    return Promise.reject(error);
});
