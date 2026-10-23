import {useCallback, useEffect, useRef, useState, type ReactNode} from 'react';
import {AlertCircle, AlertTriangle, CheckCircle2, Info, X} from 'lucide-react';
import {ToastContext, type ToastKind, type Notify} from './toastContext';
import './ToastProvider.css';

type Toast = { id: number; kind: ToastKind; message: string };
const icons = {info: Info, warning: AlertTriangle, success: CheckCircle2, error: AlertCircle};

export function ToastProvider({children}: { children: ReactNode }) {
    const [toasts, setToasts] = useState<Toast[]>([]);
    const nextId = useRef(0);
    const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());
    const dismiss = useCallback((id: number) => {
        const timer = timers.current.get(id);
        if (timer) clearTimeout(timer);
        timers.current.delete(id);
        setToasts(items => items.filter(item => item.id !== id));
    }, []);
    const notify = useCallback<Notify>((kind, message) => {
        if (!message.trim()) return;
        const id = ++nextId.current;
        setToasts(items => [...items.slice(-3), {id, kind, message}]);
        if (kind !== 'error') timers.current.set(id, setTimeout(() => dismiss(id), kind === 'warning' ? 6500 : 4500));
    }, [dismiss]);
    useEffect(() => {
        const pending = timers.current;
        return () => {
            pending.forEach(timer => clearTimeout(timer));
            pending.clear();
        };
    }, []);
    return <ToastContext.Provider value={notify}>
        <div onInvalidCapture={event => {
            const target = event.target;
            if (target instanceof HTMLInputElement || target instanceof HTMLSelectElement || target instanceof HTMLTextAreaElement) {
                const form = target.form;
                if (form && !form.classList.contains('cbms-validation-attempted')) {
                    form.classList.add('cbms-validation-attempted');
                    notify('warning', 'Please complete the required fields and correct highlighted values.');
                }
            }
        }}>{children}</div>
        <div className="cbms-toast-stack" aria-label="Notifications">
            {toasts.map(item => {
                const Icon = icons[item.kind];
                return <div key={item.id} className={`cbms-toast cbms-toast-${item.kind}`}
                            role={item.kind === 'error' ? 'alert' : 'status'}>
                    <Icon size={21} aria-hidden="true"/>
                    <span>{item.message}</span>
                    <button type="button" onClick={() => dismiss(item.id)} aria-label="Dismiss notification"><X
                        size={17}/></button>
                </div>;
            })}
        </div>
    </ToastContext.Provider>;
}
