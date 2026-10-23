import {createContext, useContext} from 'react';

export type ToastKind = 'info' | 'warning' | 'success' | 'error';
export type Notify = (kind: ToastKind, message: string) => void;

export const ToastContext = createContext<Notify | null>(null);

export function useToast(): Notify {
    const notify = useContext(ToastContext);
    if (!notify) throw new Error('ToastProvider is missing');
    return notify;
}
