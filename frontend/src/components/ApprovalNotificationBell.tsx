import {useCallback, useEffect, useState} from 'react';
import {Bell} from 'lucide-react';
import {useNavigate} from 'react-router-dom';
import {approvalNotifications} from '../api/notifications';
import './ApprovalNotificationBell.css';

/** Notification bell opens the full inbox screen instead of a clipped popover. */
export function ApprovalNotificationBell() {
    const navigate = useNavigate();
    const [unread, setUnread] = useState(0);
    const refreshCount = useCallback(async () => {
        try {
            setUnread(await approvalNotifications.unreadCount());
        } catch { /* The inbox must not block banking operations. */
        }
    }, []);
    useEffect(() => {
        void Promise.resolve().then(refreshCount);
        const intervalId = window.setInterval(() => {
            if (!document.hidden) void refreshCount();
        }, 30000);
        const onFocus = () => {
            void refreshCount();
        };
        window.addEventListener('focus', onFocus);
        window.addEventListener('cbms:notifications-changed', onFocus);
        return () => {
            window.clearInterval(intervalId);
            window.removeEventListener('focus', onFocus);
            window.removeEventListener('cbms:notifications-changed', onFocus);
        };
    }, [refreshCount]);
    return <div className="cbms-notification-wrapper">
        <button type="button" className="cbms-notification-trigger" onClick={() => navigate('/notifications')}
                aria-label={unread ? `Open notification inbox, ${unread} unread` : 'Open notification inbox'}
                title="Open notification inbox">
            <Bell size={19} aria-hidden="true"/>
            {unread > 0 &&
                <span className="cbms-notification-count" aria-hidden="true">{unread > 99 ? '99+' : unread}</span>}
        </button>
    </div>;
}
