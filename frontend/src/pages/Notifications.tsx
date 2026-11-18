import {useCallback, useEffect, useState} from 'react';
import {Bell, Check, CheckCheck, ExternalLink} from 'lucide-react';
import {useNavigate} from 'react-router-dom';
import {approvalNotifications, type ApprovalNotification} from '../api/notifications';
import {notificationDestination} from '../components/notificationDestination';
import {PageHeader, RefreshButton} from '../components/UiStandards';
import './Notifications.css';

const displayDate = (value: string) => {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString('en-MY', {
        dateStyle: 'medium',
        timeStyle: 'short'
    });
};
const errorMessage = (error: unknown) => error instanceof Error ? error.message : 'Unable to load notifications.';
const notifyCountChanged = () => window.dispatchEvent(new Event('cbms:notifications-changed'));

export default function Notifications() {
    const navigate = useNavigate();
    const [unreadOnly, setUnreadOnly] = useState(false);
    const [items, setItems] = useState<ApprovalNotification[]>([]);
    const [unread, setUnread] = useState(0);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');

    const refresh = useCallback(async (onlyUnread: boolean) => {
        setLoading(true);
        setError('');
        try {
            const [rows, count] = await Promise.all([
                approvalNotifications.list(onlyUnread), approvalNotifications.unreadCount(),
            ]);
            setItems(rows);
            setUnread(count);
            notifyCountChanged();
        } catch (err) {
            setError(errorMessage(err));
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        void Promise.resolve().then(() => refresh(unreadOnly));
        const timer = window.setInterval(() => {
            if (!document.hidden) void refresh(unreadOnly);
        }, 30000);
        const focus = () => {
            void refresh(unreadOnly);
        };
        window.addEventListener('focus', focus);
        return () => {
            window.clearInterval(timer);
            window.removeEventListener('focus', focus);
        };
    }, [refresh, unreadOnly]);

    const markRead = async (item: ApprovalNotification) => {
        if (busy || item.readAt) return;
        setBusy(true);
        try {
            await approvalNotifications.markRead(item.notificationId);
            await refresh(unreadOnly);
        } catch (err) {
            setError(errorMessage(err));
        } finally {
            setBusy(false);
        }
    };
    const markAll = async () => {
        if (busy || !unread) return;
        setBusy(true);
        try {
            await approvalNotifications.markAllRead();
            await refresh(unreadOnly);
        } catch (err) {
            setError(errorMessage(err));
        } finally {
            setBusy(false);
        }
    };
    const openRequest = (item: ApprovalNotification) => {
        if (!item.actionAvailable) return;
        const to = notificationDestination(item);
        if (!to) return;
        if (!item.readAt) {
            void approvalNotifications.markRead(item.notificationId)
                .then(notifyCountChanged).catch(() => undefined);
        }
        navigate(to);
    };

    return <section className="cbms-inbox-page">
        <PageHeader
            title="Notification Inbox"
            description="Review approval requests, assignments and decision updates."
            actions={<RefreshButton loading={loading} onClick={() => void refresh(unreadOnly)}/>}
        />
        <div className="cbms-inbox-panel">
            <div className="cbms-inbox-toolbar">
                <div className="cbms-inbox-filters" aria-label="Notification filters">
                    <button type="button" aria-pressed={!unreadOnly} className={!unreadOnly ? 'active' : ''}
                            onClick={() => setUnreadOnly(false)}>All
                    </button>
                    <button type="button" aria-pressed={unreadOnly} className={unreadOnly ? 'active' : ''}
                            onClick={() => setUnreadOnly(true)}>Unread {unread > 0 ? `(${unread})` : ''}</button>
                </div>
                <button type="button" className="cbms-inbox-mark-all" disabled={busy || unread === 0}
                        onClick={() => void markAll()}><CheckCheck size={17}/> Mark all as read
                </button>
            </div>
            {error && <p className="cbms-inbox-error" role="alert">{error}</p>}
            <div className="cbms-inbox-list" aria-live="polite">
                {loading ? <div className="cbms-inbox-empty">Loading notifications…</div>
                    : items.length === 0 ? <div className="cbms-inbox-empty"><Bell size={30}/>
                        <strong>{unreadOnly ? 'No unread notifications' : 'No notifications yet'}</strong>
                        <span>Notifications for your banking role and requests will appear here.</span>
                    </div> : items.map(item => {
                        const destination = item.actionAvailable ? notificationDestination(item) : null;
                        return <article key={item.notificationId}
                                        className={`cbms-inbox-item${item.readAt ? '' : ' unread'}`}>
                            <div className="cbms-inbox-item-body">
                                <div className="cbms-inbox-item-title">
                                    <h2>{item.subject}</h2>
                                    {!item.readAt && <span className="cbms-inbox-unread-dot" title="Unread"/>}
                                </div>
                                <p>{item.message}</p>
                                {item.targetReference &&
                                    <div className="cbms-inbox-ref"><strong>Record:</strong> {item.targetReference}
                                    </div>}
                                <div className="cbms-inbox-meta"><span>{displayDate(item.createdAt)}</span>
                                    {!item.actionAvailable &&
                                        <span>{item.completedByCurrentUser ? 'Completed' : 'Information only'}</span>}
                                </div>
                            </div>
                            <div className="cbms-inbox-item-actions">
                                {!item.readAt &&
                                    <button type="button" disabled={busy} onClick={() => void markRead(item)}
                                            className="cbms-inbox-read"><Check size={16}/> Mark read</button>}
                                {destination && <button type="button" className="cbms-inbox-open"
                                                        onClick={() => openRequest(item)}><ExternalLink size={15}/> Open
                                    request</button>}
                            </div>
                        </article>;
                    })}
            </div>
            <footer className="cbms-inbox-footer">Showing the latest 100 relevant notifications. Pending tasks are
                hidden when completed or assigned to another checker.
            </footer>
        </div>
    </section>;
}
