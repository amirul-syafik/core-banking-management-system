import {
    Children,
    isValidElement,
    useEffect,
    useId,
    useState,
    type ButtonHTMLAttributes,
    type InputHTMLAttributes,
    type SelectHTMLAttributes,
    type TextareaHTMLAttributes,
    type ReactNode
} from 'react';
import {Link, useNavigate} from 'react-router-dom';
import {
    Search,
    X,
    RefreshCw,
    ChevronLeft,
    ChevronRight,
    AlertCircle,
    CheckCircle2,
    AlertTriangle,
    Info,
    ArrowLeft
} from 'lucide-react';
import './UiStandards.css';
import {formatBusinessValue} from '../utils/display';
import {SortableTable} from './SortableTable';

type FieldLabelProps = {
    children: ReactNode;
    required?: boolean;
    className?: string;
};

/** Required-field marks are derived from a boolean, never hard-coded in captions. */
export function FieldLabel({children, required = false, className = ''}: FieldLabelProps) {
    return <span className={`cbms-standard-field-label ${className}`.trim()}>
        {children}{required && <span className="cbms-required-star" aria-hidden="true"> *</span>}
    </span>;
}

type LookupButtonProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> & {
    subject?: 'customer' | 'account' | 'vehicle' | 'reference';
};

/** Shared customer/account lookup trigger for every new-application workflow. */
export function CustomerLookupButton({subject = 'customer', className = '', ...props}: LookupButtonProps) {
    return <button {...props} type="button" className={`cbms-lookup-trigger ${className}`.trim()}
                   aria-label={props['aria-label'] || `Search ${subject}s`}
                   title={`Search ${subject}s`}>
        <Search size={16} strokeWidth={2} aria-hidden="true"/>
    </button>;
}

/** Matches the Customers paging controls in every module. */
export function PageArrow({direction}: { direction: 'previous' | 'next' }) {
    return direction === 'previous' ? <ChevronLeft className="cbms-page-arrow" size={16} aria-hidden="true"/> :
        <ChevronRight className="cbms-page-arrow" size={16} aria-hidden="true"/>;
}

/** A shared, readable status display. Backend status values are never altered. */
export function StatusBadge({status, showIcon = true, className = ''}: {
    status: string | null | undefined;
    showIcon?: boolean;
    className?: string
}) {
    const raw = String(status || 'UNKNOWN');
    const value = raw.toUpperCase();
    const positive = ['ACTIVE', 'APPROVED', 'POSTED', 'COMPLETED', 'SUCCESS', 'RECONCILED', 'PAID'];
    const negative = ['REJECTED', 'FAILED', 'CANCELLED', 'DISABLED', 'SUSPENDED', 'ERROR', 'OVERDUE', 'FROZEN'];
    const warning = ['PENDING', 'IN_REVIEW', 'SUBMITTED', 'MATURED', 'UNPAID', 'EXPIRED'];
    const neutral = ['DRAFT', 'INACTIVE', 'CLOSED', 'DORMANT', 'UNKNOWN'];

    let tone = 'neutral';
    if (positive.includes(value)) {
        tone = 'positive';
    } else if (negative.includes(value)) {
        tone = 'negative';
    } else if (warning.includes(value)) {
        tone = 'warning';
    } else if (neutral.includes(value)) {
        tone = 'neutral';
    }

    const label = formatBusinessValue(value);

    return (
        <span className={`cbms-status-badge cbms-status-${tone} ${className}`.trim()} role="status"
              aria-label={`Status: ${label}`}>
            {showIcon && <span className="cbms-status-indicator-dot" aria-hidden="true"/>}
            <span className="cbms-status-text">{label}</span>
        </span>
    );
}

export type BackNavigationProps = {
    label?: string;
    to?: string;
    onBack?: () => void;
    className?: string;
};

/**
 * Standardized Back / Return navigation link across CBMS detail screens and sub-workflows.
 * Always renders top-left with an ArrowLeft icon, consistent typography, and no underlines.
 */
export function BackNavigation({
                                   label = 'Back to Main Screen',
                                   to,
                                   onBack,
                                   className = ''
                               }: BackNavigationProps) {
    const navigate = useNavigate();

    const handleClick = () => {
        if (onBack) {
            onBack();
        } else if (to) {
            navigate(to);
        } else {
            navigate(-1);
        }
    };

    return (
        <div className={`cbms-back-nav ${className}`.trim()}>
            <button
                type="button"
                className="cbms-back-button"
                onClick={handleClick}
                aria-label={label}
            >
                <ArrowLeft size={16} strokeWidth={2.2} aria-hidden="true"/>
                <span>{label}</span>
            </button>
        </div>
    );
}

/** Consistent record-count wording for zero, one and multiple results. */
export function RecordCount({count}: { count: number }) {
    return <small
        className="cbms-record-count">({count.toLocaleString('en-MY')} {count === 1 ? 'record' : 'records'})</small>;
}

/** Convert stored history values into labeled, readable fields. Raw JSON is never shown. */
const historyLabel = (key: string) => {
    const aliases: Record<string, string> = {
        ccyCd: 'Currency', ccy_cd: 'Currency', prodCd: 'Product Code', prod_cd: 'Product Code',
        prodNm: 'Product Name', prod_nm: 'Product Name', prodCat: 'Product Category',
        bankingTyp: 'Banking Type', banking_typ: 'Banking Type', schmCd: 'Scheme Code',
        schmNm: 'Scheme Name', schmSts: 'Scheme Status', prodSts: 'Product Status',
        crtDt: 'Created On', updDt: 'Updated On', crtBy: 'Created By', updBy: 'Updated By',
        minAmt: 'Minimum Amount', maxAmt: 'Maximum Amount', minTenor: 'Minimum Tenor (Months)',
        maxTenor: 'Maximum Tenor (Months)', eligibilityDesc: 'Eligibility Requirements',
        prodDesc: 'Description', schmDesc: 'Description', status: 'Status', actionType: 'Action',
        actionBy: 'Changed By', actionDt: 'Changed On'
    };
    return aliases[key] || key.replace(/([a-z])([A-Z])/g, '$1 $2')
        .replaceAll('_', ' ').replace(/\b(Dt|Cd|Amt|Nm|Sts|Typ|Desc)\b/gi,
            match => ({
                dt: 'Date',
                cd: 'Code',
                amt: 'Amount',
                nm: 'Name',
                sts: 'Status',
                typ: 'Type',
                desc: 'Description'
            }[match.toLowerCase()] || match))
        .replace(/\b\w/g, match => match.toUpperCase());
};
const historyVisible = (key: string) => !/^(id|.*_id|.*Id)$/i.test(key);
const parseHistoryValue = (value: unknown): unknown => {
    if (typeof value === 'string' && /^\s*(?:\{|\[)/.test(value)) {
        try {
            return JSON.parse(value) as unknown;
        } catch { /* Historical plain text stays text. */
        }
    }
    return value;
};
const historyText = (value: unknown): string => {
    if (value === null || value === undefined || value === '') return '—';
    if (typeof value === 'boolean') return value ? 'Yes' : 'No';
    if (typeof value === 'number') return value.toLocaleString('en-MY');
    if (Array.isArray(value)) return value.map(historyText).join(', ');
    if (typeof value === 'object') return Object.entries(value as Record<string, unknown>)
        .filter(([key]) => historyVisible(key)).map(([key, entry]) => `${historyLabel(key)}: ${historyText(entry)}`).join('; ');
    return formatBusinessValue(String(value));
};
const asHistoryObject = (value: unknown): Record<string, unknown> | null => {
    const parsed = parseHistoryValue(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as Record<string, unknown> : null;
};

export function AuditHistoryRecord({value}: { value: unknown }) {
    const item = asHistoryObject(value) || {description: value};
    const get = (...keys: string[]) => keys.map(key => item[key]).find(entry => entry !== undefined);
    const beforeRaw = get('oldValue', 'old_value', 'oldVal', 'beforeValue', 'previousValue');
    const afterRaw = get('newValue', 'new_value', 'newVal', 'afterValue', 'updatedValue');
    const before = asHistoryObject(beforeRaw);
    const after = asHistoryObject(afterRaw);
    const changedKeys = [...new Set([...Object.keys(before || {}), ...Object.keys(after || {})])].filter(historyVisible);
    const metadata = Object.entries(item).filter(([key]) => historyVisible(key) &&
        !/^(oldValue|old_value|oldVal|beforeValue|previousValue|newValue|new_value|newVal|afterValue|updatedValue)$/i.test(key));
    return <article className="cbms-audit-record">
        {metadata.length > 0 && <dl className="cbms-audit-metadata">{metadata.map(([key, entry]) => <div key={key}>
            <dt>{historyLabel(key)}</dt>
            <dd>{historyText(parseHistoryValue(entry))}</dd>
        </div>)}</dl>}
        {changedKeys.length > 0 && <div className="cbms-audit-comparison-wrap">
            <SortableTable className="cbms-audit-comparison">
                <thead>
                <tr>
                    <th>Field</th>
                    <th>Previous Value</th>
                    <th>New Value</th>
                </tr>
                </thead>
                <tbody>{changedKeys.map(key => <tr key={key}>
                    <th scope="row">{historyLabel(key)}</th>
                    <td>{historyText(before?.[key])}</td>
                    <td>{historyText(after?.[key])}</td>
                </tr>)}</tbody>
            </SortableTable>
        </div>}
        {!changedKeys.length && (beforeRaw !== undefined || afterRaw !== undefined) &&
            <dl className="cbms-audit-metadata">
                <div>
                    <dt>Previous Value</dt>
                    <dd>{historyText(parseHistoryValue(beforeRaw))}</dd>
                </div>
                <div>
                    <dt>New Value</dt>
                    <dd>{historyText(parseHistoryValue(afterRaw))}</dd>
                </div>
            </dl>}
    </article>;
}

/** One visual close icon in every dialog; the parent button supplies its action and label. */
export function CloseIcon() {
    return <X className="cbms-close-icon" size={18} strokeWidth={2} aria-hidden="true"/>;
}

/** Consistent, accessible reload button; preserves the caller's reload logic. */
export function RefreshButton({className = '', children = 'Refresh', disabled, loading = false, ...props}:
                              ButtonHTMLAttributes<HTMLButtonElement> & {loading?: boolean}) {
    return <button {...props} type="button" disabled={disabled || loading} aria-busy={loading || undefined}
                   className={`cbms-refresh-control ${className}`.trim()}>
        <RefreshCw className={loading ? 'cbms-refresh-icon cbms-spin' : 'cbms-refresh-icon'}
                    size={16} strokeWidth={2} aria-hidden="true"/>
        <span>{loading ? 'Refreshing…' : children}</span>
    </button>;
}


/** Accessible validation feedback for database lookups and other custom (non-native) fields. */
export function RequiredFieldError({show, message}: { show: boolean; message: string }) {
    return show ? <span className="cbms-field-validation-error" role="alert">{message}</span> : null;
}

/** Shared review dialog. Confirmation is presentation only; the server still enforces every permission and constraint. */
export function ConfirmDialog({
                                  title,
                                  message,
                                  confirmLabel,
                                  busy = false,
                                  onCancel,
                                  onConfirm,
                                  reasonLabel,
                                  reason,
                                  onReasonChange,
                                  reasonRequired = false,
                                  reasonMaxLength = 1000,
                                  danger = false
                              }: {
    title: string; message: string; confirmLabel: string; busy?: boolean;
    onCancel: () => void; onConfirm: () => void;
    details?: ReadonlyArray<{ label: string; value: string }>;
    notice?: string; reasonLabel?: string; reason?: string; onReasonChange?: (value: string) => void;
    reasonRequired?: boolean; reasonMaxLength?: number; danger?: boolean;
}) {
    const descriptionId = useId();
    const reasonId = useId();
    useEffect(() => {
        const closeOnEscape = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && !busy) onCancel();
        };
        window.addEventListener('keydown', closeOnEscape);
        return () => window.removeEventListener('keydown', closeOnEscape);
    }, [busy, onCancel]);
    const [reasonAttempted, setReasonAttempted] = useState(false);
    const reasonInvalid = reasonRequired && !reason?.trim();
    return <div className="cbms-confirm-backdrop" onMouseDown={event => {
        if (event.target === event.currentTarget && !busy) onCancel();
    }}>
        <section className="cbms-confirm-dialog" role="alertdialog" aria-modal="true"
                 aria-label={title} aria-describedby={descriptionId}>
            <div className="cbms-confirm-heading"><h2>{title}</h2>
                <button type="button" aria-label="Close confirmation" disabled={busy} onClick={onCancel}><CloseIcon/>
                </button>
            </div>
            <p id={descriptionId}>{message}</p>
            {/* Standard confirmations are deliberately text-only. Reason inputs remain visible when required. */}
            {onReasonChange && <label className="cbms-confirm-reason" htmlFor={reasonId}>
                <FieldLabel required={reasonRequired}>{reasonLabel || 'Reason'}</FieldLabel>
                <textarea id={reasonId} rows={3} required={reasonRequired} maxLength={reasonMaxLength}
                          value={reason || ''}
                          aria-invalid={reasonAttempted && reasonInvalid}
                          onChange={event => onReasonChange(event.target.value)} disabled={busy}/>
                <RequiredFieldError show={reasonAttempted && reasonInvalid} message="Please enter a reason."/>
            </label>}
            <div className="cbms-confirm-actions">
                <button type="button" autoFocus disabled={busy} onClick={onCancel}>Cancel</button>
                <button type="button" className={danger ? 'cbms-confirm-danger' : 'cbms-action-search'}
                        disabled={busy} onClick={() => {
                    if (busy) return;
                    if (reasonInvalid) {
                        setReasonAttempted(true);
                        return;
                    }
                    onConfirm();
                }}>
                    {busy ? 'Processing…' : confirmLabel}
                </button>
            </div>
        </section>
    </div>;
}

/* ═══════════════════════════════════════════════════════════════════════════
   UNIFIED ARCHITECTURAL COMPONENTS
   ═══════════════════════════════════════════════════════════════════════════ */

/* ─── A. Page Layout Components ─────────────────────────────────────────── */

/** Standardised page root container */
export function PageContainer({children, className = '', ...props}: React.HTMLAttributes<HTMLDivElement>) {
    return <div className={`cbms-page ${className}`.trim()} {...props}>{children}</div>;
}

/** Standardised module/page header with title, subtitle, eyebrow, and actions */
export function PageHeader({
                               title,
                               description,
                               subtitle,
                               eyebrow,
                               actions,
                               className = '',
                           }: {
    title: string;
    description?: string;
    subtitle?: string;
    eyebrow?: string;
    actions?: ReactNode;
    className?: string;
}) {
    const desc = description ?? subtitle;
    return (
        <header className={`cbms-module-header ${className}`.trim()}>
            <div>
                {eyebrow && <span className="cbms-eyebrow">{eyebrow}</span>}
                <h1>{title}</h1>
                {desc && <p>{desc}</p>}
            </div>
            {actions && <div className="cbms-header-actions">{actions}</div>}
        </header>
    );
}

/** Record title followed by its unchanged business reference. */
export function SubscreenTitle({
                                    title,
                                    reference,
                                    as = 'h1',
                                    id,
                                    className = ''
                                }: {
    title: string;
    reference?: string | null;
    as?: 'h1' | 'h2';
    id?: string;
    className?: string;
}) {
    const Heading = as;
    return <Heading id={id} className={`cbms-subscreen-title ${className}`.trim()}>
        {title}{reference ? <span className="cbms-subscreen-reference"> – {reference}</span> : null}
    </Heading>;
}

/** Section header inside cards or panels */
export function SectionHeader({
                                  title,
                                  count,
                                  subtitle,
                                  actions,
                                  className = '',
                              }: {
    title: string;
    count?: number;
    subtitle?: string;
    actions?: ReactNode;
    className?: string;
}) {
    return (
        <div className={`cbms-card-header ${className}`.trim()}>
            <div>
                <h2>{title} {count !== undefined && <RecordCount count={count}/>}</h2>
                {subtitle && <p className="cbms-card-subtitle">{subtitle}</p>}
            </div>
            {actions && <div className="cbms-card-actions">{actions}</div>}
        </div>
    );
}

/** Standardised card container */
export function ContentCard({
                                title,
                                count,
                                subtitle,
                                actions,
                                children,
                                className = '',
                                ...props
                            }: {
    title?: string;
    count?: number;
    subtitle?: string;
    actions?: ReactNode;
    children: ReactNode;
    className?: string;
} & React.HTMLAttributes<HTMLElement>) {
    return (
        <section className={`cbms-card ${className}`.trim()} {...props}>
            {title && <SectionHeader title={title} count={count} subtitle={subtitle} actions={actions}/>}
            {children}
        </section>
    );
}

/** Alias for ContentCard to match existing Panel usages */
export const ContentPanel = ContentCard;

/** Breadcrumb navigation for detail/sub-pages */
export function BreadcrumbNavigation({
                                         items,
                                         className = '',
                                     }: {
    items: Array<{ label: string; href?: string; onClick?: () => void; active?: boolean }>;
    className?: string;
}) {
    return (
        <nav aria-label="Breadcrumb" className={`cbms-breadcrumb ${className}`.trim()}>
            <ol>
                {items.map((item, index) => (
                    <li key={index} className={item.active ? 'active' : ''}>
                        {item.href ? (
                            <Link to={item.href}>{item.label}</Link>
                        ) : item.onClick ? (
                            <button type="button" onClick={item.onClick}>{item.label}</button>
                        ) : (
                            <span>{item.label}</span>
                        )}
                        {index < items.length - 1 &&
                            <span className="cbms-breadcrumb-separator" aria-hidden="true">/</span>}
                    </li>
                ))}
            </ol>
        </nav>
    );
}

/* ─── B. Form Components ────────────────────────────────────────────────── */

/** Form field wrapper with label, required asterisk, hint, and validation error */
export function FormField({
                              label,
                              required = false,
                              hint,
                              error,
                              children,
                              className = '',
                              htmlFor,
                          }: {
    label?: ReactNode;
    required?: boolean;
    hint?: string;
    error?: string;
    children: ReactNode;
    className?: string;
    htmlFor?: string;
}) {
    return (
        <label className={`cbms-form-field ${className}`.trim()} htmlFor={htmlFor}>
            {label && <FieldLabel required={required}>{label}</FieldLabel>}
            {children}
            {error && <RequiredFieldError show message={error}/>}
            {hint && <small className="cbms-field-hint">{hint}</small>}
        </label>
    );
}

export function TextInput({className = '', ...props}: InputHTMLAttributes<HTMLInputElement>) {
    return <input type="text" className={`cbms-input ${className}`.trim()} {...props} />;
}

export function NumberInput({className = '', ...props}: InputHTMLAttributes<HTMLInputElement>) {
    return <input type="number" className={`cbms-input ${className}`.trim()} {...props} />;
}

export function SelectInput({className = '', children, ...props}: SelectHTMLAttributes<HTMLSelectElement>) {
    return <select className={`cbms-select ${className}`.trim()} {...props}>{children}</select>;
}

export function TextAreaInput({className = '', ...props}: TextareaHTMLAttributes<HTMLTextAreaElement>) {
    return <textarea className={`cbms-textarea ${className}`.trim()} {...props} />;
}

/** Standardised Form Action Footer: Bottom-Right Aligned, Cancel before Primary */
export function FormActionGroup({
                                    cancelButton,
                                    primaryButton,
                                    children,
                                    className = '',
                                }: {
    cancelButton?: ReactNode;
    primaryButton?: ReactNode;
    children?: ReactNode;
    className?: string;
}) {
    return (
        <div className={`cbms-form-footer ${className}`.trim()}>
            {cancelButton}
            {children}
            {primaryButton}
        </div>
    );
}

/** Standard right-aligned action group for listing headers and toolbars */
export function HeaderActionGroup({
                                      children,
                                      className = '',
                                  }: {
    children: ReactNode;
    className?: string;
}) {
    return (
        <div className={`cbms-toolbar-actions ${className}`.trim()}>
            {children}
        </div>
    );
}

/* ─── C. Table Components ───────────────────────────────────────────────── */

/** Responsive scrolling wrapper for data tables */
export function TableContainer({children, className = ''}: { children: ReactNode; className?: string }) {
    return <div className={`cbms-table-scroll ${className}`.trim()}>{children}</div>;
}

/** Standardised Table Pagination: Always Right-Aligned with Previous / Page N of M / Next.
 * Hides Previous and Next controls automatically when totalElements is 10 or fewer. */
export function TablePagination({
                                    page,
                                    totalPages,
                                    onPageChange,
                                    busy = false,
                                    className = '',
                                    totalRecords,
                                    totalElements,
                                }: {
    page: number;
    totalPages: number;
    onPageChange: (newPage: number) => void;
    busy?: boolean;
    className?: string;
    totalRecords?: number;
    /** When provided, pagination buttons are hidden if totalElements ≤ 10. */
    totalElements?: number;
}) {
    const pages = Math.max(1, totalPages);
    // Use the server's total, not the current page length, to decide whether paging is needed.
    const recordTotal = totalElements ?? totalRecords;
    const showControls = recordTotal !== undefined && recordTotal > 10;
    return (
        <div className={`cbms-pagination ${className}`.trim()}>
            {showControls && (
                <>
                    <button
                        type="button"
                        data-cbms-pagination-control
                        disabled={busy || page === 0}
                        onClick={() => onPageChange(page - 1)}
                        aria-label="Previous page"
                    >
                        <PageArrow direction="previous"/> <span>Previous</span>
                    </button>
                    <span className="cbms-page-info">Page {page + 1} of {pages}</span>
                    <button
                        type="button"
                        data-cbms-pagination-control
                        disabled={busy || page + 1 >= pages}
                        onClick={() => onPageChange(page + 1)}
                        aria-label="Next page"
                    >
                        <span>Next</span> <PageArrow direction="next"/>
                    </button>
                </>
            )}
        </div>
    );
}

/** Standard Empty State display */
export function EmptyState({
                               message = 'No records found.',
                               icon,
                               action,
                               columns,
                               className = '',
                           }: {
    message?: string;
    icon?: ReactNode;
    action?: ReactNode;
    columns?: number;
    className?: string;
}) {
    const content = (
        <div className={`cbms-empty-state ${className}`.trim()}>
            {icon}
            <p>{message}</p>
            {action}
        </div>
    );
    if (columns !== undefined) {
        return (
            <tr className="cbms-empty-row">
                <td colSpan={columns} className="cbms-empty-cell">
                    {content}
                </td>
            </tr>
        );
    }
    return content;
}

/** Standard loading spinner / state */
export function LoadingIndicator({message = 'Loading...', className = ''}: { message?: string; className?: string }) {
    return (
        <div className={`cbms-loading-indicator ${className}`.trim()}>
            <RefreshCw className="cbms-spin" size={18} aria-hidden="true"/>
            <span>{message}</span>
        </div>
    );
}

/** Toolbar container for search and filtering controls */
export function ToolbarContainer({children, className = ''}: { children: ReactNode; className?: string }) {
    return <div className={`cbms-toolbar ${className}`.trim()}>{children}</div>;
}

/** Standardised single-row search form container with intelligent field distribution */
export function SearchPanel({
                                title,
                                count,
                                headerActions,
                                children,
                                onSubmit,
                                className = '',
                            }: {
    title?: string;
    count?: number;
    headerActions?: ReactNode;
    children: ReactNode;
    onSubmit?: (e: React.FormEvent<HTMLFormElement>) => void;
    className?: string;
}) {
    const fieldCount = Children.toArray(children).filter(child => {
        if (!isValidElement<{ className?: string }>(child)) return false;
        const classes = child.props.className ?? '';
        return child.type === 'label' || /\b(cbms-lov-field|gap-customer-field)\b/.test(classes);
    }).length;
    const layoutClass = fieldCount <= 3 ? 'cbms-search-row--few-fields' : 'cbms-search-row--many-fields';
    const formContent = (
        <form
            className={`cbms-search-row ${layoutClass}`}
            onSubmit={e => {
                if (onSubmit) {
                    e.preventDefault();
                    onSubmit(e);
                }
            }}
        >
            {children}
        </form>
    );

    if (title) {
        return (
            <section className={`cbms-card cbms-search-panel ${className}`.trim()}>
                <SectionHeader title={title} count={count} actions={headerActions}/>
                {formContent}
            </section>
        );
    }

    return (
        <section className={`cbms-card cbms-search-panel ${className}`.trim()}>
            {formContent}
        </section>
    );
}

/** Standardised Search & Clear button pair positioned at the end of the search row */
export function SearchActions({
                                  onClear,
                                  isSearching = false,
                                  searchLabel = 'Search',
                                  clearLabel = 'Clear',
                                  disabled = false,
                                  className = '',
                              }: {
    onClear?: () => void;
    isSearching?: boolean;
    searchLabel?: string;
    clearLabel?: string;
    disabled?: boolean;
    className?: string;
}) {
    return (
        <div className={`cbms-search-actions ${className}`.trim()}>
            <button
                type="submit"
                className="cbms-btn cbms-btn-primary cbms-action-search"
                disabled={disabled || isSearching}
            >
                {isSearching ? 'Searching…' : searchLabel}
            </button>
            {onClear && (
                <button
                    type="button"
                    className="cbms-btn cbms-btn-secondary cbms-action-clear"
                    onClick={onClear}
                    disabled={disabled}
                >
                    {clearLabel}
                </button>
            )}
        </div>
    );
}

/* ─── D. Dialog Components ──────────────────────────────────────────────── */

/** Reusable modal container with sticky header, close trigger, Escape handler, and backdrop */
export function ModalDialog({
                                title,
                                subtitle,
                                eyebrow,
                                isOpen = true,
                                wide = false,
                                busy = false,
                                onClose,
                                footer,
                                children,
                                className = '',
                            }: {
    title: string;
    subtitle?: string;
    eyebrow?: string;
    isOpen?: boolean;
    wide?: boolean;
    busy?: boolean;
    onClose: () => void;
    footer?: ReactNode;
    children: ReactNode;
    className?: string;
}) {
    useEffect(() => {
        const handleEscape = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && !busy) onClose();
        };
        if (isOpen) {
            window.addEventListener('keydown', handleEscape);
            return () => window.removeEventListener('keydown', handleEscape);
        }
    }, [isOpen, busy, onClose]);

    if (!isOpen) return null;

    return (
        <div
            className="cbms-modal-backdrop"
            onMouseDown={event => {
                if (event.target === event.currentTarget && !busy) onClose();
            }}
        >
            <section
                className={`cbms-modal-dialog ${wide ? 'cbms-modal-wide' : ''} ${className}`.trim()}
                role="dialog"
                aria-modal="true"
                aria-label={title}
            >
                <div className="cbms-modal-heading">
                    <div>
                        {eyebrow && <small className="cbms-modal-eyebrow">{eyebrow}</small>}
                        <h2>{title}</h2>
                        {subtitle && <p className="cbms-modal-subtitle">{subtitle}</p>}
                    </div>
                    <button
                        type="button"
                        className="cbms-modal-close"
                        onClick={onClose}
                        disabled={busy}
                        aria-label={`Close ${title}`}
                    >
                        <CloseIcon/>
                    </button>
                </div>
                <div className="cbms-modal-body">
                    {children}
                </div>
                {footer && <div className="cbms-modal-footer">{footer}</div>}
            </section>
        </div>
    );
}

/* ─── E. Button Components ──────────────────────────────────────────────── */

/** Primary action button */
export function PrimaryButton({children, className = '', ...props}: ButtonHTMLAttributes<HTMLButtonElement>) {
    return (
        <button className={`cbms-action-search ${className}`.trim()} {...props}>
            {children}
        </button>
    );
}

/** Secondary / neutral button */
export function SecondaryButton({children, className = '', ...props}: ButtonHTMLAttributes<HTMLButtonElement>) {
    return (
        <button type="button" className={`cbms-action-clear ${className}`.trim()} {...props}>
            {children}
        </button>
    );
}

/** Danger action button */
export function DangerButton({children, className = '', ...props}: ButtonHTMLAttributes<HTMLButtonElement>) {
    return (
        <button type="button" className={`cbms-confirm-danger ${className}`.trim()} {...props}>
            {children}
        </button>
    );
}

/** Standard "+ Create / New" action button */
export function CreateButton({children = 'Create', className = '', ...props}: ButtonHTMLAttributes<HTMLButtonElement>) {
    return (
        <button type="button" className={`cbms-action-create ${className}`.trim()} {...props}>
            {children}
        </button>
    );
}

/* ─── F. Feedback Components ────────────────────────────────────────────── */

/** Alert banner for informational, warning, success, or error notifications */
export function AlertNotice({
                                type = 'info',
                                title,
                                message,
                                children,
                                className = '',
                            }: {
    type?: 'info' | 'success' | 'warning' | 'error';
    title?: string;
    message?: string;
    children?: ReactNode;
    className?: string;
}) {
    const iconMap = {
        info: <Info size={18} className="cbms-alert-icon" aria-hidden="true"/>,
        success: <CheckCircle2 size={18} className="cbms-alert-icon" aria-hidden="true"/>,
        warning: <AlertTriangle size={18} className="cbms-alert-icon" aria-hidden="true"/>,
        error: <AlertCircle size={18} className="cbms-alert-icon" aria-hidden="true"/>,
    };

    return (
        <div className={`cbms-alert cbms-alert-${type} ${className}`.trim()} role="alert">
            {iconMap[type]}
            <div className="cbms-alert-content">
                {title && <strong className="cbms-alert-title">{title}</strong>}
                {message && <p className="cbms-alert-message">{message}</p>}
                {children}
            </div>
        </div>
    );
}
