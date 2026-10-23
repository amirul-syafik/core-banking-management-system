import {useToast} from '../components/toastContext';
import {decideStatus, getStatusRequests, proposeStatus, type StatusRequest} from '../api/customerStatusRequests';
import {useEffect, useState} from 'react';
import {AxiosError} from 'axios';
import {ArrowLeft, RefreshCw, Pencil, Plus} from 'lucide-react';
import {Link, useParams} from 'react-router-dom';
import {
    listCustomerDocuments,
    downloadCustomerDocument,
    type CustomerDocument
} from '../api/customerDocuments';
import {getCustomer, type Customer} from '../api/customers';
import {
    createProfileRecord,
    registerIdentification,
    updateProfileRecord,
    getProfileSection,
    type ProfileSection
} from '../api/customerProfile';
import {useAuth} from '../auth/useAuth';
import './CustomerProfile.css';

const sections: { id: ProfileSection; title: string }[] = [
    {id: 'individual-details', title: 'Individual details'},
    {id: 'corporate-details', title: 'Corporate details'},
    {id: 'identifications', title: 'Identification'},
    {id: 'contacts', title: 'Contacts'},
    {id: 'addresses', title: 'Addresses'},
    {id: 'employment', title: 'Employment'},
    {id: 'kyc-assessments', title: 'KYC assessments'},
];
const names: Record<string, string> = {
    dob: 'Date of Birth', gndr: 'Gender', natCd: 'Nationality', mrtlSts: 'Marital Status',
    regNo: 'Registration Number', regCty: 'Country of Registration', incDt: 'Incorporation Date',
    bizTyp: 'Business Type', docTyp: 'Document Type', docNo: 'Document Number',
    docNoMasked: 'Document Number', maskedDocNo: 'Document Number', issCty: 'Issuing Country', issDt: 'Issue Date',
    expDt: 'Expiry Date', adrTyp: 'Address Type', adrLn1: 'Address Line 1',
    adrLn2: 'Address Line 2', adrLn3: 'Address Line 3', city: 'City',
    stProv: 'State / Province', pstCd: 'Postal Code', ctyCd: 'Country',
    conTyp: 'Contact Type', conVal: 'Contact Details', priFlg: 'Primary',
    actFlg: 'Active', vfyDt: 'Verification Date', asmtSts: 'Assessment Status',
    riskRtng: 'Risk Rating', revDt: 'Review Date', nxtRevDt: 'Next Review Date',
    rmk: 'Remarks', empTyp: 'Employment Type', empSts: 'Employment Status',
    empNm: 'Employer Name', occ: 'Occupation', indCd: 'Industry',
    startDt: 'Start Date', endDt: 'End Date', mthInc: 'Monthly Income',
    incCcy: 'Income Currency', cifNo: 'Customer CIF', sts: 'Status',
};

function documentNumberLabel(type: unknown): string {
    const value = String(type ?? '').trim().toUpperCase().replace(/[\s-]/g, '_');
    if (['MYKAD', 'MY_KAD', 'NRIC', 'IC', 'I_C'].includes(value)) return 'I/C No.';
    if (value === 'PASSPORT') return 'Passport No.';
    return 'Document Number';
}

function contactValueLabel(type: unknown): string {
    const value = String(type ?? '').trim().toUpperCase();
    const labels: Record<string, string> = {
        MOBILE: 'Mobile No.', PHONE: 'Phone No.', EMAIL: 'Email Address',
        WORK_PHONE: 'Work Phone No.', HOME_PHONE: 'Home Phone No.',
        FAX: 'Fax No.', OTHER: 'Contact Details',
    };
    return labels[value] ?? 'Contact Details';
}

function fieldLabel(key: string, record: Record<string, unknown> = {}): string {
    if (key === 'docNo' || key === 'docNoMasked' || key === 'maskedDocNo') return documentNumberLabel(record.docTyp);
    if (key === 'conVal') return contactValueLabel(record.conTyp);
    return (names[key] ?? key).replace(/^./, c => c.toUpperCase());
}

const editFields: Partial<Record<ProfileSection, string[]>> = {
    identifications: ['docTyp', 'docNo', 'issCty', 'issDt', 'expDt', 'priFlg'],
    'individual-details': ['dob', 'gndr', 'natCd', 'mrtlSts'],
    'corporate-details': ['regNo', 'regCty', 'incDt', 'bizTyp'],
    contacts: ['conTyp', 'conVal', 'priFlg'],
    addresses: ['adrTyp', 'adrLn1', 'adrLn2', 'adrLn3', 'city', 'stProv', 'pstCd', 'ctyCd', 'priFlg'],
    employment: ['empTyp', 'empSts', 'empNm', 'occ', 'indCd', 'startDt', 'endDt', 'mthInc', 'incCcy'],
    'kyc-assessments': ['asmtSts', 'riskRtng', 'revDt', 'nxtRevDt', 'rmk'],
};
const dateFields = new Set(['dob', 'incDt', 'issDt', 'expDt', 'startDt', 'endDt', 'revDt', 'nxtRevDt']);
const dateTimeFields = new Set(['crtDt', 'updDt', 'vfyDt']);
const idFields: Partial<Record<ProfileSection, string>> = {
    contacts: 'conId',
    addresses: 'adrId',
    employment: 'empId',
    'kyc-assessments': 'kycId'
};
const hidden = new Set(['custId', 'adrId', 'conId', 'kycId', 'idnId', 'empId', 'audId', 'crtDt', 'crtBy', 'updDt', 'updBy', 'docId']);

function display(value: unknown, key = ""): string {
    if (value === null || value === undefined || value === '') return '—';
    if (dateFields.has(key) && typeof value === 'string') {
        const date = new Date(`${value.slice(0, 10)}T12:00:00`);
        return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('en-MY', {
            day: '2-digit',
            month: 'short',
            year: 'numeric'
        }).format(date);
    }
    if (dateTimeFields.has(key) && typeof value === 'string') {
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('en-MY', {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            hour12: true,
            timeZone: 'Asia/Kuala_Lumpur'
        }).format(date);
    }
    if (typeof value === 'boolean') return value ? 'Yes' : 'No';
    if (typeof value === 'object') return JSON.stringify(value);
    return String(value);
}

function errorText(error: unknown): string {
    if (error instanceof AxiosError) {
        if (error.response?.status === 403) return 'You do not have permission to view this information.';
        if (error.response?.status === 401) return 'Your session has expired. Please sign in again.';
        return `Request failed (${error.response?.status ?? 'network error'}).`;
    }
    return error instanceof Error ? error.message : 'Unable to load customer information.';
}

function Records({data}: { data: unknown }) {
    const rows = Array.isArray(data) ? data : (data && typeof data === 'object' && 'content' in data && Array.isArray(data.content)) ? data.content : data ? [data] : [];
    if (rows.length === 0) return <p className="cbms-profile-empty">No records available.</p>;
    return <div className="cbms-profile-records">{rows.map((row: unknown, i: number) => {
        if (!row || typeof row !== 'object') return <p key={i}>{display(row)}</p>;
        return <div key={i}>
            <dl className="cbms-profile-fields">{Object.entries(row as Record<string, unknown>)
                .filter(([key]) => !hidden.has(key) && !(/^.*Id$/.test(key) && key !== 'cifNo'))
                .map(([key, value]) => <div key={key}>
                    <dt>{fieldLabel(key, row as Record<string, unknown>)}</dt>
                    <dd>{display(value, key)}</dd>
                </div>)}</dl>
        </div>;
    })}</div>;
}

export default function CustomerProfile() {
    const notify = useToast();
    const {session} = useAuth();
    const canAmend = Boolean(session?.roles.some(role => role === 'MAKER' || role === 'ADMIN'));
    const {custId} = useParams();
    const id = Number(custId);
    const [customer, setCustomer] = useState<Customer | null>(null);
    const [statusRequests, setStatusRequests] = useState<StatusRequest[]>([]);
    const [statusTarget, setStatusTarget] = useState('');
    const [statusReason, setStatusReason] = useState('');
    const [statusError, setStatusError] = useState('');
    const [statusBusy, setStatusBusy] = useState(false);
    const [reviewingRequestId, setReviewingRequestId] = useState<number | null>(null);
    const [statusRefresh, setStatusRefresh] = useState(0);
    const allowedTransitions: Record<string, string[]> = {
        PENDING: ['ACTIVE', 'INACTIVE', 'BLOCKED'],
        ACTIVE: ['INACTIVE', 'BLOCKED'],
        INACTIVE: ['ACTIVE', 'BLOCKED'],
        BLOCKED: ['INACTIVE'],
    };
    const nextStatuses = allowedTransitions[customer?.sts?.toUpperCase() ?? ''] ?? [];
    const hasPendingStatus = statusRequests.some(r => r.requestStatus === 'PENDING');

    async function requestStatusChange(event: React.SyntheticEvent<HTMLFormElement, SubmitEvent>) {
        event.preventDefault();
        if (!canAmend || !nextStatuses.includes(statusTarget) || !statusReason.trim()) return;
        setStatusBusy(true);
        setStatusError('');
        try {
            await proposeStatus(id, statusTarget, statusReason.trim());
            setStatusTarget('');
            setStatusReason('');
            setStatusRefresh(v => v + 1);
            notify('success', 'Status request submitted successfully.');
        } catch (err) {
            setStatusError(errorText(err));
            notify('error', errorText(err));
        } finally {
            setStatusBusy(false);
        }
    }

    async function reviewStatusChange(request: StatusRequest, approve: boolean) {
        if (!request.canReview || reviewingRequestId !== null) return;
        setReviewingRequestId(request.requestId);
        setStatusError('');
        try {
            await decideStatus(id, request.requestId, approve);
            const [requests, latestCustomer] = await Promise.all([getStatusRequests(id), getCustomer(id)]);
            setStatusRequests(requests);
            setCustomer(latestCustomer);
            setStatusTarget('');
            setStatusReason('');
            notify('success', approve ? 'Customer status change approved successfully.' : 'Customer status change rejected successfully.');
        } catch (err) {
            setStatusError(errorText(err));
            notify('error', errorText(err));
        } finally {
            setReviewingRequestId(null);
        }
    }

    const [documents, setDocuments] = useState<CustomerDocument[]>([]);
    const [docError, setDocError] = useState('');
    const [file, setFile] = useState<File | null>(null);
    const [downloadingId, setDownloadingId] = useState<number | null>(null);
    const [customerError, setCustomerError] = useState('');
    const [section, setSection] = useState<ProfileSection>('individual-details');
    const isCorporate = customer?.custTyp?.toUpperCase() === 'CORPORATE';
    const activeSection: ProfileSection = isCorporate && (section === 'employment' || section === 'individual-details')
        ? 'corporate-details'
        : customer && !isCorporate && section === 'corporate-details' ? 'individual-details' : section;
    const [sectionData, setSectionData] = useState<unknown>(null);
    const [sectionError, setSectionError] = useState('');
    const [loading, setLoading] = useState(true);
    const [refresh, setRefresh] = useState(0);
    const [editing, setEditing] = useState(false);
    const [saving, setSaving] = useState(false);
    const [saveError, setSaveError] = useState('');
    const [editingRecord, setEditingRecord] = useState<Record<string, unknown> | null>(null);
    const [choosingRecord, setChoosingRecord] = useState(false);
    const [form, setForm] = useState<Record<string, string | boolean>>({});
    const editable = Boolean(editFields[section]);
    const records = Array.isArray(sectionData) ? sectionData : sectionData && typeof sectionData === 'object' && 'content' in sectionData && Array.isArray(sectionData.content) ? sectionData.content : sectionData ? [sectionData] : [];
    const hasRecords = records.length > 0;
    const hasSingleton = (section === 'individual-details' || section === 'corporate-details') && Boolean(sectionData);

    function startEdit(record: Record<string, unknown> | null) {
        if (!canAmend || (section === 'identifications' && record !== null)) return;
        setChoosingRecord(false);
        setEditingRecord(record);
        setForm(Object.fromEntries((editFields[section] ?? []).map(k => [k, k === 'docNo' ? '' : typeof record?.[k] === 'boolean' ? record[k] : String(record?.[k] ?? '')])) as Record<string, string | boolean>);
        setFile(null);
        setSaveError('');
        setEditing(true);
    }

    function change(key: string, value: string | boolean) {
        setForm(prev => ({...prev, [key]: value}));
    }

    async function submit(event: React.SyntheticEvent<HTMLFormElement, SubmitEvent>) {
        event.preventDefault();
        if (!canAmend) {
            setSaveError('You do not have permission to amend customer records.');
            return;
        }
        setSaving(true);
        setSaveError('');
        try {
            const payload: Record<string, string | boolean | number | null> = {};
            for (const key of editFields[section] ?? []) {
                const raw = form[key];
                payload[key] = typeof raw === 'boolean' ? raw : String(raw ?? '').trim() || null;
            }
            if (section === 'identifications' && (!payload.docTyp || !payload.docNo || !payload.issCty)) throw new Error('Document type, full document number and issuing country are required.');
            if (section === 'contacts' && (!payload.conTyp || !payload.conVal)) throw new Error('Contact type and value are required.');
            if (section === 'addresses' && !payload.adrLn1) throw new Error('Address line 1 is required.');
            if (section === 'employment' && (!payload.empTyp || !payload.empSts)) throw new Error('Employment type and status are required.');
            if (section === 'kyc-assessments' && (!payload.asmtSts || !payload.riskRtng)) throw new Error('Assessment status and risk rating are required.');
            if (section === 'identifications') {
                if (editingRecord) throw new Error('Identification records cannot be amended. Register a new identification instead.');
                if (!file) throw new Error('Supporting document is required.');
                if (file.size > 5 * 1024 * 1024) throw new Error('Maximum file size is 5 MB.');
                if (!['application/pdf', 'image/png', 'image/jpeg'].includes(file.type)) throw new Error('Only PDF, PNG and JPEG are supported.');
                await registerIdentification(id, payload, file);
            } else if (editingRecord) {
                const recordKey = idFields[section];
                await updateProfileRecord(id, section, recordKey ? Number(editingRecord[recordKey]) : undefined, payload);
            } else {
                await createProfileRecord(id, section, payload);
            }
            setEditing(false);
            setForm({});
            setFile(null);
            setEditingRecord(null);
            setRefresh(v => v + 1);
            notify('success', editingRecord ? 'Customer record updated successfully.' : 'Customer record saved successfully.');
        } catch (err) {
            setSaveError(errorText(err));
            notify('error', errorText(err));
        } finally {
            setSaving(false);
        }
    }

    useEffect(() => {
        if (!Number.isSafeInteger(id) || id < 1) return;
        const controller = new AbortController();
        getCustomer(id).then(value => {
            if (!controller.signal.aborted) setCustomer(value);
        })
            .catch(err => {
                if (!controller.signal.aborted) setCustomerError(errorText(err));
            });
        return () => controller.abort();
    }, [id]);
    useEffect(() => {
        if (!Number.isSafeInteger(id) || id < 1 || !customer) return;
        const controller = new AbortController();

        async function load() {
            setLoading(true);
            setSectionError('');
            setSectionData(null);
            try {
                const data = await getProfileSection(id, activeSection, controller.signal);
                if (!controller.signal.aborted) setSectionData(data);
            } catch (err) {
                if (!controller.signal.aborted) {
                    if (err instanceof AxiosError && err.response?.status === 404) setSectionData(null);
                    else setSectionError(errorText(err));
                }
            } finally {
                if (!controller.signal.aborted) setLoading(false);
            }
        }

        void load();
        return () => controller.abort();
    }, [id, activeSection, refresh, customer]);
    useEffect(() => {
        if (section !== 'identifications' || !Number.isSafeInteger(id) || id < 1) return;
        const controller = new AbortController();
        listCustomerDocuments(id, controller.signal)
            .then(data => {
                if (!controller.signal.aborted) {
                    setDocuments(data);
                    setDocError('');
                }
            })
            .catch(err => {
                if (!controller.signal.aborted) setDocError(errorText(err));
            });
        return () => controller.abort();
    }, [id, section, refresh]);

    useEffect(() => {
        if (!Number.isSafeInteger(id) || id < 1) return;
        let active = true;
        Promise.all([getStatusRequests(id), getCustomer(id)]).then(([requests, latest]) => {
            if (active) {
                setStatusRequests(requests);
                setCustomer(latest);
            }
        }).catch(err => {
            if (active) setStatusError(errorText(err));
        });
        return () => {
            active = false;
        };
    }, [id, statusRefresh]);

    async function downloadDocument(doc: CustomerDocument) {
        setDownloadingId(doc.docId);
        setDocError('');
        try {
            await downloadCustomerDocument(id, doc);
        } catch (err) {
            setDocError(errorText(err));
        } finally {
            setDownloadingId(null);
        }
    }

    if (!Number.isSafeInteger(id) || id < 1) return <div className="cbms-profile"><p>Invalid customer identifier.</p>
        <Link to="/customers">Back to customers</Link></div>;
    return <section className="cbms-profile">
        <Link className="cbms-profile-back" to="/customers"><ArrowLeft size={17}/> Back to customers</Link>
        <div className="cbms-profile-heading">
            <div>
                <h1>{customer?.custNm ?? 'Customer profile'}</h1><p>CIF
                Number: <strong>{customer?.cifNo ?? '—'}</strong> · {customer?.custTyp ?? '—'} · {customer?.sts ?? '—'}
            </p></div>
            <button type="button" className="cbms-profile-refresh" onClick={() => setRefresh(v => v + 1)}><RefreshCw
                size={16}/> Refresh
            </button>
        </div>
        {customerError && <p className="cbms-profile-error" role="alert">{customerError}</p>}
        <div className="cbms-status-panel">
            <h2>Customer status</h2>
            <p>Current status: <strong>{customer?.sts ?? '—'}</strong></p>
            {statusError && <p className="cbms-profile-error" role="alert">{statusError}</p>}
            {canAmend && !hasPendingStatus && nextStatuses.length > 0 &&
                <form onSubmit={e => void requestStatusChange(e)} className="cbms-status-form">
                    <label><span className="cbms-field-label">Requested status <span className="cbms-required-star"
                                                                                     aria-hidden="true">*</span></span>
                        <select required value={statusTarget} onChange={e => setStatusTarget(e.target.value)}>
                            <option value="">Select status</option>
                            {nextStatuses.map(st => <option key={st} value={st}>{st}</option>)}
                        </select>
                    </label>
                    <label><span className="cbms-field-label">Reason <span className="cbms-required-star"
                                                                           aria-hidden="true">*</span></span>
                        <input required maxLength={500} value={statusReason}
                               onChange={e => setStatusReason(e.target.value)} placeholder="Reason for status change"/>
                    </label>
                    <button type="submit" disabled={statusBusy || !statusTarget || !statusReason.trim()}>Submit for
                        approval
                    </button>
                </form>}
            {statusRequests.filter(request => request.requestStatus === 'PENDING').map(request => (
                <div className="cbms-status-request" key={request.requestId}>
                    <div className="cbms-status-request-details">
                        <p><span
                            className="cbms-status-request-label">Requested status</span><strong>{request.fromStatus} → {request.toStatus}</strong>
                        </p>
                        <p><span className="cbms-status-request-label">Reason</span><strong>{request.reason}</strong>
                        </p>
                        <p className="cbms-status-pending-note">Awaiting approval from an eligible ADMIN.</p>
                    </div>
                    {request.canReview && <div className="cbms-status-actions">
                        <button type="button" disabled={reviewingRequestId !== null}
                                onClick={() => void reviewStatusChange(request, false)}>Reject
                        </button>
                        <button type="button" className="cbms-status-approve" disabled={reviewingRequestId !== null}
                                onClick={() => void reviewStatusChange(request, true)}>
                            {reviewingRequestId === request.requestId ? 'Processing…' : 'Approve'}
                        </button>
                    </div>}
                </div>
            ))}
        </div>
        <div className="cbms-profile-layout">
            <nav className="cbms-profile-nav"
                 aria-label="Customer profile sections">{sections.filter(s => isCorporate ? s.id !== 'individual-details' && s.id !== 'employment' : s.id !== 'corporate-details').map(s =>
                <button key={s.id} type="button" className={activeSection === s.id ? 'active' : ''} onClick={() => {
                    setSection(s.id);
                    setEditing(false);
                    setChoosingRecord(false);
                    setSaveError('');
                    setForm({});
                }}>{s.title}</button>)}</nav>
            <article className="cbms-profile-content">
                <div className="cbms-profile-section-header">
                    <h2>{sections.find(s => s.id === activeSection)?.title}</h2>
                    {canAmend && editable && !loading && !sectionError && !editing && !choosingRecord &&
                        <div className="cbms-profile-record-actions">
                            {!hasSingleton &&
                                <button type="button" className="cbms-profile-refresh" onClick={() => startEdit(null)}>
                                    <Plus
                                        size={16}/> {section === 'individual-details' || section === 'corporate-details' ? 'Register details' : 'Add record'}
                                </button>}
                            {hasRecords && section !== 'identifications' &&
                                <button type="button" className="cbms-profile-refresh" onClick={() => {
                                    if (records.length === 1) startEdit(records[0] as Record<string, unknown>);
                                    else setChoosingRecord(true);
                                }}><Pencil size={16}/> Edit record</button>}
                        </div>}

                </div>
                {choosingRecord && !editing ? <div className="cbms-profile-picker">
                    <p>Select the record you want to edit:</p>
                    {records.map((row, index) => {
                        const record = row as Record<string, unknown>;
                        const label = [record.conTyp, record.conVal, record.adrTyp, record.docTyp, record.docNoMasked, record.empNm, record.asmtSts].filter(Boolean).join(' · ');
                        return <button type="button" className="cbms-profile-refresh" key={index}
                                       onClick={() => startEdit(record)}>{label || `Record ${index + 1}`}</button>;
                    })}
                    <button type="button" className="cbms-profile-refresh"
                            onClick={() => setChoosingRecord(false)}>Cancel
                    </button>
                </div> : null}
                {editing && canAmend ? <form className="cbms-profile-form" onSubmit={e => void submit(e)}>
                    {(editFields[section] ?? []).map(key => {
                        const requiredFields: Record<string, string[]> = {
                            identifications: ['docTyp', 'docNo', 'issCty'],
                            contacts: ['conTyp', 'conVal'],
                            addresses: ['adrLn1'],
                            employment: ['empTyp', 'empSts'],
                            'kyc-assessments': ['asmtSts', 'riskRtng'],
                        };
                        const mandatory = (requiredFields[section] ?? []).includes(key);
                        const options: Record<string, string[]> = {
                            gndr: ['MALE', 'FEMALE', 'OTHER', 'NOT_SPECIFIED'],
                            mrtlSts: ['SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED', 'OTHER'],
                            conTyp: ['MOBILE', 'PHONE', 'EMAIL', 'WORK_PHONE', 'OTHER'],
                            adrTyp: ['RESIDENTIAL', 'BUSINESS', 'MAILING', 'REGISTERED', 'OTHER'],
                            asmtSts: ['PENDING', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'EXPIRED'],
                            riskRtng: ['LOW', 'MEDIUM', 'HIGH'],
                        };
                        return <label key={key}><span
                            className="cbms-field-label">{fieldLabel(key, form)}{mandatory &&
                            <span className="cbms-required-star" aria-hidden="true"> *</span>}</span>
                            {key === 'priFlg' ? <input type="checkbox" checked={Boolean(form[key])}
                                                       onChange={e => change(key, e.target.checked)}/> :
                                options[key] ?
                                    <select required={mandatory} value={String(form[key] ?? '')}
                                            onChange={e => change(key, e.target.value)}>
                                        <option value="">Select</option>
                                        {options[key].map(v => <option key={v} value={v}>{v}</option>)}</select> :
                                    <input required={mandatory}
                                           type={dateFields.has(key) ? 'date' : key === 'mthInc' ? 'number' : 'text'}
                                           step={key === 'mthInc' ? '0.01' : undefined} value={String(form[key] ?? '')}
                                           onChange={e => change(key, e.target.value)}/>}
                        </label>;
                    })}
                    {section === 'identifications' && <label
                        className="cbms-ident-file"><span className="cbms-field-label">Supporting document <span
                        className="cbms-required-star" aria-hidden="true">*</span></span>
                        <input type="file" required accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
                               onChange={e => setFile(e.target.files?.[0] ?? null)}/>
                        <small>PDF, PNG or JPEG · maximum 5 MB. The document ID is generated automatically.</small>
                    </label>}
                    {saveError && <p className="cbms-profile-error" role="alert">{saveError}</p>}
                    <div className="cbms-profile-form-actions">
                        <button type="button" disabled={saving} onClick={() => setEditing(false)}>Cancel</button>
                        <button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
                    </div>
                </form> : loading ? <p>Loading information…</p> : sectionError ?
                    <p className="cbms-profile-error" role="alert">{sectionError}</p> : <>
                        {section === 'kyc-assessments' && Array.isArray(sectionData) && sectionData.some((r: Record<string, unknown>) => r.riskRtng === 'HIGH' || (typeof r.nxtRevDt === 'string' && r.nxtRevDt.slice(0, 10) < new Date().toISOString().slice(0, 10))) &&
                            <p className="cbms-kyc-warning">Attention: High-risk assessment or overdue KYC review.</p>}
                        {section === 'identifications' && docError &&
                            <p className="cbms-profile-error" role="alert">{docError}</p>}
                        {section === 'identifications' && records.some((r: Record<string, unknown>) => r.docId != null) &&
                            <div className="cbms-ident-files"><h3>Supporting
                                documents</h3>{records.map((r: Record<string, unknown>, index: number) => {
                                const doc = documents.find(d => d.docId === Number(r.docId));
                                return doc ? <div className="cbms-doc-row" key={index}>
                                    <span>{r.docTyp ? String(r.docTyp) : 'Identification'} · {doc.fileNm}</span>
                                    <button type="button" disabled={downloadingId === doc.docId}
                                            onClick={() => void downloadDocument(doc)}>{downloadingId === doc.docId ? 'Downloading…' : 'Download'}</button>
                                </div> : null;
                            })}</div>}
                        <Records data={sectionData}/>
                    </>}
            </article>
        </div>
    </section>;
}
