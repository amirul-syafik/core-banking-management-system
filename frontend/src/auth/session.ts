export type Role = 'VIEWER' | 'MAKER' | 'ADMIN';
export type Session = { token: string; username: string; roles: Role[] };
const KEY = 'cbms.auth.session';
const ROLES: Role[] = ['VIEWER', 'MAKER', 'ADMIN'];

export function getSession(): Session | null {
    try {
        const raw = sessionStorage.getItem(KEY);
        if (!raw) return null;
        const value = JSON.parse(raw) as Session;
        if (typeof value.token !== 'string' || !value.token || !Array.isArray(value.roles)) return null;
        if (isExpired(value.token)) {
            clearSession();
            return null;
        }
        return {
            token: value.token,
            username: String(value.username || ''),
            roles: value.roles.filter(r => ROLES.includes(r))
        };
    } catch {
        return null;
    }
}

export function saveSession(session: Session) {
    sessionStorage.setItem(KEY, JSON.stringify(session));
}

export function clearSession() {
    sessionStorage.removeItem(KEY);
}

function decodePayload(token: string): Record<string, unknown> {
    const part = token.split('.')[1];
    if (!part) throw new Error('Invalid JWT');
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '='))) as Record<string, unknown>;
}

function isExpired(token: string): boolean {
    try {
        const exp = decodePayload(token).exp;
        return typeof exp === 'number' && Date.now() >= exp * 1000;
    } catch {
        return true;
    }
}

function parseRoles(input: unknown): Role[] {
    const values = Array.isArray(input) ? input : typeof input === 'string' ? input.split(/[ ,]+/) : [];
    return values.map(v => String(v).replace(/^ROLE_/, '').toUpperCase()).filter((v): v is Role => ROLES.includes(v as Role));
}

export function sessionFromLogin(data: unknown, submittedUsername: string): Session {
    if (!data || typeof data !== 'object') throw new Error('Unexpected login response');
    const body = data as Record<string, unknown>;
    const token = [body.accessToken, body.access_token, body.token, body.jwt].find(v => typeof v === 'string');
    if (typeof token !== 'string') throw new Error('Login succeeded but no JWT was found. Check the login response field names.');
    const claims = decodePayload(token);
    const roles = parseRoles(body.roles ?? body.role ?? claims.roles ?? claims.role ?? claims.authorities ?? claims.scope);
    const username = String(body.username ?? claims.sub ?? submittedUsername);
    return {token, username, roles};
}

export function hasAnyRole(session: Session | null, roles: Role[]): boolean {
    return !!session && (roles.length === 0 || roles.some(role => session.roles.includes(role)));
}
