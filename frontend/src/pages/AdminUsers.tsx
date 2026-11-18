import {SortableTable, type TableSortState} from '../components/SortableTable';
import {sortTableRecords} from '../utils/tableSorting';
import {
    RecordCount,
    CloseIcon,
    ConfirmDialog,
    StatusBadge,
    FieldLabel,
    PageHeader,
    CreateButton,
    RefreshButton,
    EmptyState,
    TablePagination
} from '../components/UiStandards';
import {useCallback, useEffect, useState, type FormEvent} from 'react';
import {useNavigate} from 'react-router-dom';
import {api} from '../api/client';
import {useAuth} from '../auth/useAuth';
import {useToast} from '../components/toastContext';
import './admin.css';

type Role = 'VIEWER' | 'MAKER' | 'ADMIN';
type User = { userId: number; username: string; enabled: boolean; roles: Role[]; createdAt: string };
type Recovery = { requestId: number; userId: number; username: string; requestedAt: string };
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
    const {session, logout} = useAuth();
    const navigate = useNavigate();
    const [rows, setRows] = useState<User[]>([]), [loading, setLoading] = useState(false), [query, setQuery] = useState('');
    const [appliedQuery, setAppliedQuery] = useState('');
    const [userPage, setUserPage] = useState(0);
    const [recoveryPage, setRecoveryPage] = useState(0);
    const [userSort, setUserSort] = useState<TableSortState>(null);
    const [recoverySort, setRecoverySort] = useState<TableSortState>(null);
    const [selected, setSelected] = useState<User | null>(null), [dialog, setDialog] = useState<'create' | 'roles' | 'reset' | 'history' | null>(null);
    const [username, setUsername] = useState(''), [password, setPassword] = useState(''), [chosen, setChosen] = useState<Role[]>(['VIEWER']), [history, setHistory] = useState<Audit[]>([]), [busy, setBusy] = useState(false);
    const [pendingStatus, setPendingStatus] = useState<User | null>(null);
    const [statusBusy, setStatusBusy] = useState(false);
    const [recoveryRows, setRecoveryRows] = useState<Recovery[]>([]);
    const filteredUsers = rows.filter(x => x.username.toLowerCase().includes(appliedQuery.toLowerCase()));
    const currentUserPage = Math.min(userPage, Math.max(0, Math.ceil(filteredUsers.length / 10) - 1));
    const currentRecoveryPage = Math.min(recoveryPage, Math.max(0, Math.ceil(recoveryRows.length / 10) - 1));
    const sortedUsers = sortTableRecords(filteredUsers, userSort, [u => u.username, u => u.roles.join(', '), u => u.enabled ? 'ACTIVE' : 'DISABLED', u => u.createdAt]);
    const sortedRecoveryRows = sortTableRecords(recoveryRows, recoverySort, [r => r.username, r => r.requestedAt]);
    const visibleUsers = sortedUsers.slice(currentUserPage * 10, (currentUserPage + 1) * 10);
    const visibleRecoveryRows = sortedRecoveryRows.slice(currentRecoveryPage * 10, (currentRecoveryPage + 1) * 10);
    const refresh = useCallback(async () => {
        setLoading(true);
        try {
            const {data} = await api.get<User[]>('/admin/users');
            setRows(data);
            try {
                const pending = await api.get<Recovery[]>('/admin/users/recovery-requests');
                setRecoveryRows(pending.data);
            } catch (err) {
                notify('error', 'Recovery requests could not be loaded: ' + message(err));
            }
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
    const closeDialog = () => {
        if (busy) return;
        setDialog(null);
        setSelected(null);
        setUsername('');
        setPassword('');
        setChosen(['VIEWER']);
        setHistory([]);
    };
    const submit = async (e: FormEvent) => {
        e.preventDefault();
        if (dialog === 'roles' && selected && chosen.length === selected.roles.length &&
            chosen.every(role => selected.roles.includes(role))) {
            notify('warning', 'No changes to save.');
            return;
        }
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
            if (dialog === 'reset' && selected?.username === session?.username) {
                logout();
                navigate('/login', {replace: true, state: {passwordChanged: true}});
                return;
            }
            await refresh();
        } catch (err) {
            notify('error', message(err))
        } finally {
            setBusy(false)
        }
    };
    const toggleEnabled = async (user: User) => {
        if (statusBusy || user.username === session?.username) return;
        setStatusBusy(true);
        try {
            await api.patch(`/admin/users/${user.userId}/enabled`, null, {params: {enabled: !user.enabled}});
            setPendingStatus(null);
            notify('success', 'User status updated.');
            await refresh();
        } catch (e) {
            notify('error', message(e));
        } finally {
            setStatusBusy(false);
        }
    };
    return <main className="admin-page">
        <PageHeader
            title="User Management"
            description="Manage user access, roles, passwords and account status."
            actions={
                <div className="cbms-toolbar-actions">
                    <RefreshButton onClick={() => void refresh()} loading={loading}/>
                    <CreateButton onClick={() => open('create')}>New User</CreateButton>
                </div>
            }
        />
        <section className="admin-panel">
            <h2>Password Recovery Requests <RecordCount count={recoveryRows.length}/></h2>
            <p>Verify the user's identity through an approved channel before resetting their password.
                A reset also invalidates existing login tokens.</p>
            <div className="admin-scroll">
                <SortableTable sortState={recoverySort} onSortChange={sort => { setRecoverySort(sort); setRecoveryPage(0); }}>
                    <thead>
                    <tr>
                        <th>Username</th>
                        <th>Requested At</th>
                        <th>Action</th>
                    </tr>
                    </thead>
            <tbody>{visibleRecoveryRows.map(r => <tr key={r.requestId}>
                        <td>{r.username}</td>
                        <td>{new Date(r.requestedAt).toLocaleString('en-MY')}</td>
                        <td>
                            <button type="button" onClick={() => {
                                const user = rows.find(u => u.userId === r.userId);
                                if (user) open('reset', user);
                            }}>Reset Password
                            </button>
                        </td>
                    </tr>)}{recoveryRows.length === 0 &&
                        <EmptyState message="No pending recovery requests." columns={3}/>}</tbody>
                </SortableTable>
            </div>
            <TablePagination page={currentRecoveryPage} totalPages={Math.max(1, Math.ceil(recoveryRows.length / 10))}
                             totalElements={recoveryRows.length} onPageChange={setRecoveryPage} busy={loading}
                             className="admin-pagination"/>
        </section>
        <section className="admin-panel">
            <div className="admin-toolbar"><label>Search Users<input value={query}
                                                                     onChange={e => setQuery(e.target.value)}
                                                                     placeholder="Username"/></label>
                <button className="cbms-action-clear" type="button" onClick={() => {
                    setUserPage(0);
                    setQuery('');
                    setAppliedQuery('')
                }}>Clear
                </button>
                <button className="cbms-action-search" type="button"
                        onClick={() => { setUserPage(0); setAppliedQuery(query.trim()); }}>Search
                </button>

            </div>
        </section>
        <section className="admin-panel">
            <h2>Users <RecordCount
                count={filteredUsers.length}/>
            </h2>
            <div className="admin-scroll">
                <SortableTable sortState={userSort} onSortChange={sort => { setUserSort(sort); setUserPage(0); }}>
                    <thead>
                    <tr>
                        <th>Username</th>
                        <th>Roles</th>
                        <th>Status</th>
                        <th>Created Date</th>
                        <th>Action</th>
                    </tr>
                    </thead>
                    <tbody>{visibleUsers.map(u => <tr
                        key={u.userId}>
                        <td>{u.username}</td>
                        <td>{u.roles.join(', ')}</td>
                        <td><StatusBadge status={u.enabled ? 'ACTIVE' : 'DISABLED'}/></td>
                        <td>{u.createdAt ? new Date(u.createdAt).toLocaleString('en-MY') : '—'}</td>
                        <td className="admin-row-actions">
                            <button onClick={() => open('roles', u)}>Roles</button>
                            <button onClick={() => open('reset', u)}>Reset Password</button>
                            <button onClick={() => open('history', u)}>History</button>
                            <button disabled={u.username === session?.username}
                                    onClick={() => setPendingStatus(u)}>{u.enabled ? 'Disable' : 'Enable'}</button>
                        </td>
                    </tr>)}{!filteredUsers.length && <tr>
                        <td colSpan={5}>{loading ? 'Loading…' : 'No users found.'}</td>
                    </tr>}</tbody>
                </SortableTable>
            </div>
            <TablePagination page={currentUserPage} totalPages={Math.max(1, Math.ceil(filteredUsers.length / 10))}
                             totalElements={filteredUsers.length} onPageChange={setUserPage} busy={loading}
                             className="admin-pagination"/>
        </section>
        {pendingStatus && <ConfirmDialog
            title={`${pendingStatus.enabled ? 'Disable' : 'Enable'} User Account?`}
            message={pendingStatus.enabled ? 'Are you sure you want to disable this user? Sign-in will be blocked.' :
                'Are you sure you want to enable this user?'}
            details={[{label: 'Username', value: pendingStatus.username}, {
                label: 'Current Status',
                value: pendingStatus.enabled ? 'Enabled' : 'Disabled'
            },
                {label: 'Roles', value: pendingStatus.roles.join(', ')}]}
            notice="This is a security-sensitive administrative change. The server will check authorization again."
            confirmLabel={pendingStatus.enabled ? 'Disable Account' : 'Enable Account'} danger={pendingStatus.enabled}
            busy={statusBusy} onCancel={() => setPendingStatus(null)} onConfirm={() => {
            void toggleEnabled(pendingStatus);
        }}/>}
        {dialog && <div className="admin-overlay" onMouseDown={e => {
            if (e.target === e.currentTarget) closeDialog()
        }}>
            <section className="admin-dialog" role="dialog" aria-modal="true">
                <div className="admin-dialog-head">
                    <h2>{dialog === 'create' ? 'New User' : dialog === 'roles' ? 'Update Roles' : dialog === 'reset' ? 'Reset Password' : 'User History'}</h2>
                    <button onClick={closeDialog} aria-label="Close"><CloseIcon/></button>
                </div>
                {dialog === 'history' ? <div className="admin-history">{history.length ? history.map((h, i) =>
                        <dl className="cbms-audit-history-entry" key={i}>
                            <div>
                                <dt>Action</dt>
                                <dd>{h.action || '—'}</dd>
                            </div>
                            <div>
                                <dt>Performed By</dt>
                                <dd>{h.actor || '—'}</dd>
                            </div>
                            <div>
                                <dt>Date and Time</dt>
                                <dd>{h.createdAt ? new Date(h.createdAt).toLocaleString('en-MY') : '—'}</dd>
                            </div>
                            {h.details && <div className="cbms-audit-history-details">
                                <dt>Details</dt>
                                <dd>{h.details}</dd>
                            </div>}
                        </dl>) : 'No audit records found.'}</div> :
                    <form onSubmit={e => void submit(e)}>{dialog === 'create' &&
                        <label><FieldLabel required>Username</FieldLabel><input required maxLength={100}
                                                                                autoComplete="off" value={username}
                                                                                onChange={e => setUsername(e.target.value)}/></label>}{(dialog === 'create' || dialog === 'reset') &&
                        <label><FieldLabel
                            required>{dialog === 'reset' ? 'New Password' : 'Password'}</FieldLabel><input required
                                                                                                           minLength={12}
                                                                                                           maxLength={128}
                                                                                                           type="password"
                                                                                                           autoComplete="new-password"
                                                                                                           value={password}
                                                                                                           onChange={e => setPassword(e.target.value)}/></label>}{dialog !== 'reset' &&
                        <fieldset>
                            <legend><FieldLabel required>Roles</FieldLabel></legend>
                            {roles.map(role => <label key={role} className="admin-check"><input type="checkbox"
                                                                                                checked={chosen.includes(role)}
                                                                                                onChange={e => setChosen(old => e.target.checked ? [...old, role] : old.filter(x => x !== role))}/>{role}
                            </label>)}</fieldset>}
                        <div className="admin-footer">
                            <button type="button" onClick={closeDialog}>Cancel</button>
                            <button disabled={busy}
                                    className="admin-primary">{busy ? 'Saving…' : dialog === 'create' ? 'Create' : dialog === 'roles' ? 'Save Changes' : 'Reset Password'}</button>
                        </div>
                    </form>}</section>
        </div>}
    </main>;
}
