import {type ButtonHTMLAttributes, type ReactNode} from 'react';
import PopupView from '../components/PopupView';
import {
    StatusBadge,
    CreateButton as StandardCreateButton,
    PageHeader,
    ContentCard,
    TablePagination,
    EmptyState,
    ModalDialog
} from '../components/UiStandards';
import {dateOnly, readable, titleCase} from './pageUtils';
import './maintenance.css';
import './page-components.css';

export function CreateButton({children, className = '', ...props}: ButtonHTMLAttributes<HTMLButtonElement>) {
    return <StandardCreateButton
        className={`gap-primary ${className}`.trim()} {...props}>{children}</StandardCreateButton>;
}

export function ModuleHeader({title, description, actions}: {
    title: string;
    description: string;
    actions?: ReactNode
}) {
    return <PageHeader title={title} description={description} actions={actions}/>;
}

export function Panel({title, count, actions, children}: {
    title: string;
    count?: number;
    actions?: ReactNode;
    children: ReactNode
}) {
    return <ContentCard title={title} count={count} actions={actions} className="gap-panel">{children}</ContentCard>;
}

export function Tabs<T extends string>({current, options, onSelect}: {
    current: T;
    options: ReadonlyArray<{ id: T; label: string }>;
    onSelect: (tab: T) => void
}) {
    return <div className="gap-tabs" role="tablist" aria-label="Workspace sections">{options.map(option =>
        <button type="button" role="tab" aria-selected={current === option.id} key={option.id}
                className={current === option.id ? 'active' : ''}
                onClick={() => onSelect(option.id)}>{option.label}</button>
    )}</div>;
}

export function Badge({status}: { status: string | null | undefined }) {
    return <StatusBadge status={status}/>;
}

export function Paging({page, totalPages, onPage, busy, totalElements}: {
    page: number;
    totalPages: number;
    onPage: (n: number) => void;
    busy?: boolean;
    totalElements: number
}) {
    return <TablePagination page={page} totalPages={totalPages} onPageChange={onPage} busy={busy}
                            totalElements={totalElements} className="gap-pagination"/>;
}

export function Empty({message = 'No records found.', columns}: { message?: string; columns: number }) {
    return <EmptyState message={message} columns={columns}/>;
}

const internalKey = (key: string) => /^(id|.*_id|.*Id|.*ID|.*_pk|.*Pk)$/i.test(key);

export function DetailGrid({data}: { data: Record<string, unknown> }) {
    return <dl
        className="gap-details">{Object.entries(data).filter(([key]) => !internalKey(key)).map(([key, value]) => {
        const formatted = /(?:Date|_date|_dt|At)$/i.test(key) && typeof value === 'string'
            ? dateOnly(value) : readable(value);
        return <div key={key}>
            <dt>{titleCase(key)}</dt>
            <dd><PopupView value={formatted} maxLength={36}/></dd>
        </div>;
    })}</dl>;
}

/** Shared modal chrome: close button, Escape handling, and backdrop. */
export function FormDialog({title, eyebrow, wide = false, busy = false, onClose, children}: {
    title: string; eyebrow?: string; wide?: boolean; busy?: boolean; onClose: () => void; children: ReactNode
}) {
    return <ModalDialog title={title} eyebrow={eyebrow} wide={wide} busy={busy}
                        onClose={onClose}>{children}</ModalDialog>;
}
