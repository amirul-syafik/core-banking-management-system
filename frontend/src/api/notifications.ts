import {api} from './client';

export type ApprovalNotification = {
    notificationId: number;
    moduleCode: string;
    sourceType: string;
    sourceId: number;
    targetId?: number | null;
    targetReference?: string | null;
    eventCode: string;
    subject: string;
    message: string;
    createdAt: string;
    readAt: string | null;
    actionAvailable: boolean;
    completedByCurrentUser: boolean;
};

export const approvalNotifications = {
    list: async (unreadOnly = false) =>
        (await api.get<ApprovalNotification[]>('/notifications', {params: {unreadOnly, limit: 100}})).data,
    unreadCount: async () =>
        (await api.get<{ unreadCount: number }>('/notifications/unread-count')).data.unreadCount,
    markRead: async (notificationId: number) =>
        (await api.post(`/notifications/${notificationId}/read`)).data,
    markAllRead: async () =>
        (await api.post<{ updated: number }>('/notifications/read-all')).data,
};
