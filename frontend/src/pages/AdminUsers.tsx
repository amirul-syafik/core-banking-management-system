import {useCallback, useEffect, useState, type FormEvent} from 'react';
import {api} from '../api/client';
import {useAuth} from '../auth/useAuth';
import {useToast} from '../components/toastContext';
import './admin.css';

type Role = 'VIEWER' | 'MAKER' | 'ADMIN';
type User = { userId: number; username: string; enabled: boolean; roles: Role[]; createdAt: string };
type Audit = { id?: number; action?: string; actor?: string; createdAt?: string; details?: string };
const roles: Role[] = ['VIEWER', 'MAKER', 'ADMIN'];
const message = (e: unknown) => {
    if (typeof e === 'object' && e && 'response' in e) {
        const x = e as { response?: { data?: { message?: string; detail?: string } } };
        return x.response?.data?.message || x.response?.data?.detail || 'Request failed'
    }
    return e instanceof Error ? e.message : 'Request failed'
};
export default function AdminUsers() {
    const notify = useToast();
    const {session} = useAuth();
    const [rows, setRows] = useState<User[]>([]), [loading, setLoading] = useState(false), [query, setQuery] = useState('');
    const [selected, setSelected] = useState<User | null>(null), [dialog, setDialog] = useState<'create' | 'roles' | 'reset' | 'history' | null>(null);
    const [username, setUsername] = useState(''), [password, setPassword] = useState(''), [chosen, setChosen] = useState<Role[]>(['VIEWER']), [history, setHistory] = useState<Audit[]>([]), [busy, setBusy] = useState(false);
    const refresh = useCallback(async () => {
        setLoading(true);
        try {
            const {data} = await api.get<User[]>('/admin/users');
            setRows(data)
        } catch (e) {
            notify('error', message(e))
        } finally {
            setLoading(false)
        }
    }, [notify]);
    useEffect(() => {
        void Promise.resolve().then(refresh)
    }, [refresh]);
    const open = (type: 'create' | 'roles' | 'reset' | 'history', user: User | null = null) => {
        setSelected(user);
        setDialog(type);
        setUsername('');
        setPassword('');
        setChosen(user ? [...user.roles] : ['VIEWER']);
        if (type === 'history' && user) void api.get<Audit[]>(`/admin/users/${user.userId}/history`).then(r => setHistory(r.data)).catch(e => notify('error', message(e)))
    };
    const submit = async (e: FormEvent) => {
        e.preventDefault();
        if (!chosen.length && dialog !== 'reset') {
            notify('warning', 'Select at least one role.');
            return
        }
        setBusy(true);
        try {
            if (dialog === 'create') await api.post('/admin/users', {
                username: username.trim(),
                password,
                roles: chosen
            });
            if (dialog === 'roles' && selected) await api.put(`/admin/users/${selected.userId}/roles`, {roles: chosen});
            if (dialog === 'reset' && selected) await api.post(`/admin/users/${selected.userId}/reset-password`, {newPassword: password});
            notify('success', 'User information updated.');
            setDialog(null);
            await refresh();
        } catch (err) {
            notify('error', message(err))
        } finally {
            setBusy(false)
        }
    };
    const toggleEnabled = async (user: User) => {
        if (!window.confirm(`${user.enabled ? 'Disable' : 'Enable'} ${user.username}?`)) return;
        try {
            await api.patch(`/admin/users/${user.userId}/enabled`, null, {params: {enabled: !user.enabled}});
            notify('success', 'User status updated.');
            await refresh()
        } catch (e) {
            notify('error', message(e))
        }
    };
    return <main className="admin-page">
        <header><h1>User Management</h1><p>Manage user access, roles, passwords and account status.</p></header>
        <section className="admin-panel">
            <div className="admin-toolbar"><label>Search Users<input value={query}
                                                                     onChange={e => setQuery(e.target.value)}
                                                                     placeholder="Username"/></label>
                <button type="button" onClick={() => setQuery('')}>Clear</button>
                <button type="button" onClick={() => void refresh()}>Search</button>
                <button className="admin-primary admin-new" type="button" onClick={() => open('create')}>New User
                </button>
            </div>
        </section>
        <section className="admin-panel">
            <h2>Users <small>({rows.filter(x => x.username.toLowerCase().includes(query.toLowerCase())).length} records)</small>
            </h2>
            <div className="admin-scroll">
                <table>
                    <thead>
                    <tr>
                        <th>Username</th>
                        <th>Roles</th>
                        <th>Status</th>
                        <th>Created</th>
                        <th>Actions</th>
                    </tr>
                    </thead>
                    <tbody>{rows.filter(x => x.username.toLowerCase().includes(query.toLowerCase())).map(u => <tr
                        key={u.userId}>
                        <td>{u.username}</td>
                        <td>{u.roles.join(', ')}</td>
                        <td>{u.enabled ? 'ACTIVE' : 'DISABLED'}</td>
                        <td>{u.createdAt ? new Date(u.createdAt).toLocaleString('en-MY') : '—'}</td>
                        <td className="admin-row-actions">
                            <button onClick={() => open('roles', u)}>Roles</button>
                            <button onClick={() => open('reset', u)}>Reset Password</button>
                            <button onClick={() => open('history', u)}>History</button>
                            <button disabled={u.username === session?.username}
                                    onClick={() => void toggleEnabled(u)}>{u.enabled ? 'Disable' : 'Enable'}</button>
                        </td>
                    </tr>)}{!rows.length && <tr>
                        <td colSpan={5}>{loading ? 'Loading…' : 'No users found.'}</td>
                    </tr>}</tbody>
                </table>
            </div>
        </section>
        {dialog && <div className="admin-overlay" onMouseDown={e => {
            if (e.target === e.currentTarget && !busy) setDialog(null)
        }}>
            <section className="admin-dialog" role="dialog" aria-modal="true">
                <div className="admin-dialog-head">
                    <h2>{dialog === 'create' ? 'New User' : dialog === 'roles' ? 'Update Roles' : dialog === 'reset' ? 'Reset Password' : 'User History'}</h2>
                    <button onClick={() => setDialog(null)} aria-label="Close">×</button>
                </div>
                {dialog === 'history' ? <div className="admin-history">{history.length ? history.map((h, i) => <pre
                        key={i}>{JSON.stringify(h, null, 2)}</pre>) : 'No audit records found.'}</div> :
                    <form onSubmit={e => void submit(e)}>{dialog === 'create' &&
                        <label>Username *<input required maxLength={100} autoComplete="off" value={username}
                                                onChange={e => setUsername(e.target.value)}/></label>}{(dialog === 'create' || dialog === 'reset') &&
                        <label>{dialog === 'reset' ? 'New Password' : 'Password'} *<input required minLength={12}
                                                                                          maxLength={128}
                                                                                          type="password"
                                                                                          autoComplete="new-password"
                                                                                          value={password}
                                                                                          onChange={e => setPassword(e.target.value)}/></label>}{dialog !== 'reset' &&
                        <fieldset>
                            <legend>Roles *</legend>
                            {roles.map(role => <label key={role} className="admin-check"><input type="checkbox"
                                                                                                checked={chosen.includes(role)}
                                                                                                onChange={e => setChosen(old => e.target.checked ? [...old, role] : old.filter(x => x !== role))}/>{role}
                            </label>)}</fieldset>}
                        <div className="admin-footer">
                            <button type="button" onClick={() => setDialog(null)}>Cancel</button>
                            <button disabled={busy} className="admin-primary">{busy ? 'Saving…' : 'Save'}</button>
                        </div>
                    </form>}</section>
        </div>}
    </main>;
}
