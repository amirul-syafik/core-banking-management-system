import {useCallback, useEffect, useId, useRef, useState} from 'react';
import {createPortal} from 'react-dom';
import './PopupView.css';

type PopupViewProps = { value: string | number | null | undefined; maxLength?: number };
type Position = { top: number; left: number; maxWidth: number };

/** Show long table values without widening columns or clipping the floating content. */
export default function PopupView({value, maxLength = 15}: PopupViewProps) {
    const text = String(value ?? '—');
    const triggerRef = useRef<HTMLButtonElement>(null);
    const tooltipRef = useRef<HTMLSpanElement>(null);
    const tooltipId = useId();
    const [open, setOpen] = useState(false);
    const [position, setPosition] = useState<Position>({top: 0, left: 0, maxWidth: 320});
    const updatePosition = useCallback(() => {
        const trigger = triggerRef.current;
        if (!trigger) return;
        const bounds = trigger.getBoundingClientRect();
        const maxWidth = Math.min(320, Math.max(160, window.innerWidth - 24));
        const popupHeight = tooltipRef.current?.getBoundingClientRect().height ?? 48;
        const left = Math.max(12, Math.min(bounds.left, window.innerWidth - maxWidth - 12));
        const above = bounds.top >= popupHeight + 12;
        setPosition({
            top: above ? bounds.top - popupHeight - 8 : Math.max(8, Math.min(window.innerHeight - popupHeight - 8, bounds.bottom + 8)),
            left,
            maxWidth
        });
    }, []);
    useEffect(() => {
        if (!open) return;
        const update = () => updatePosition();
        window.addEventListener('scroll', update, true);
        window.addEventListener('resize', update);
        return () => {
            window.removeEventListener('scroll', update, true);
            window.removeEventListener('resize', update);
        };
    }, [open, updatePosition]);

    if (text.length <= maxLength) return <span>{text}</span>;
    return <>
        <button ref={triggerRef} type="button" className="cbms-popup-view-trigger"
                aria-label={`Full value: ${text}`} aria-describedby={open ? tooltipId : undefined}
                aria-expanded={open}
                onMouseEnter={() => {
                    updatePosition();
                    setOpen(true);
                }}
                onMouseLeave={() => setOpen(false)}
                onFocus={() => {
                    updatePosition();
                    setOpen(true);
                }}
                onBlur={() => setOpen(false)}
                onClick={() => {
                    updatePosition();
                    setOpen(previous => !previous);
                }}
                onKeyDown={event => {
                    if (event.key === 'Escape') setOpen(false);
                }}>
            {text.slice(0, maxLength)}...
        </button>
        {open && createPortal(<span ref={tooltipRef} id={tooltipId} role="tooltip"
                                    className="cbms-popup-view-content"
                                    style={{top: position.top, left: position.left, maxWidth: position.maxWidth}}>
            {text}
        </span>, document.body)}
    </>;
}
