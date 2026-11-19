import {ConfirmDialog, FieldLabel, RefreshButton, StatusBadge, BackNavigation, SubscreenTitle} from '../components/UiStandards';
import ReferenceCodeSelect from '../components/ReferenceCodeSelect';
import {useToast} from '../components/toastContext';
import {decideStatus, getStatusRequests, proposeStatus, type StatusRequest} from '../api/customerStatusRequests';
import {useEffect, useRef, useState} from 'react';
import {AxiosError} from 'axios';
import {Pencil, UserRound} from 'lucide-react';
import {useParams, useSearchParams} from 'react-router-dom';
import {
    listCustomerDocuments,
    downloadCustomerDocument,
    getCustomerProfilePicture,
    replaceCustomerProfilePicture,
    removeCustomerProfilePicture,
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
    {id: 'kyc-assessments', title: 'Know Your Customer Assessments'},
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
    incCcy: 'Income Currency', cifNo: 'Customer Identification Number', sts: 'Status',
};

function documentNumberLabel(type: unknown): string {
    const value = String(type ?? '').trim().toUpperCase();
    return value === 'MYKAD' || value === 'IC'
        ? 'National Registration Identity Card Number'
        : value === 'PASSPORT' ? 'Passport Number' : 'Document Number';
}

function contactValueLabel(type: unknown): string {
    const value = String(type ?? '').trim().toUpperCase();
    const labels: Record<string, string> = {
        MOBILE: 'Mobile Number', PHONE: 'Phone Number', EMAIL: 'Email Address',
        WORK_PHONE: 'Work Phone Number', HOME_PHONE: 'Home Phone Number',
        FAX: 'Fax Number', OTHER: 'Contact Details',
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
    if (typeof value === 'object') return Array.isArray(value)
        ? value.map(item => display(item)).join(', ')
        : Object.entries(value as Record<string, unknown>).filter(([name]) => !hidden.has(name) && !/Id$/.test(name))
            .map(([name, item]) => `${fieldLabel(name)}: ${display(item, name)}`).join(' · ') || '—';
    return formatBusinessValue(String(value));
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
    const [searchParams] = useSearchParams();
    const linkedRequestId = searchParams.get('requestId');
    const id = typeof custId === 'string' && /^\d+$/.test(custId) ? Number(custId) : Number.NaN;
    const validCustomerId = Number.isSafeInteger(id) && id > 0;
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
    useEffect(() => {
        if (!linkedRequestId || !statusRequests.some(request => String(request.requestId) === linkedRequestId)) return;
        const task = window.setTimeout(() => document.getElementById(`customer-request-${linkedRequestId}`)?.scrollIntoView({block: 'center'}), 0);
        return () => window.clearTimeout(task);
    }, [linkedRequestId, statusRequests]);

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
    const [pictureUrl, setPictureUrl] = useState<string | null>(null);
    const [pictureVersion, setPictureVersion] = useState(0);
    const [picturePreviewUrl, setPicturePreviewUrl] = useState<string | null>(null);
    const [pictureFile, setPictureFile] = useState<File | null>(null);
    const [pictureLoading, setPictureLoading] = useState(true);
    const [pictureBusy, setPictureBusy] = useState(false);
    const [pictureError, setPictureError] = useState('');
    const [confirmRemovePicture, setConfirmRemovePicture] = useState(false);
    const pictureInput = useRef<HTMLInputElement>(null);
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
            if (editingRecord) {
                const changed = (editFields[section] ?? []).some(key => {
                    const proposed = payload[key];
                    const original = editingRecord[key];
                    if (typeof proposed === 'boolean') return proposed !== (original === true || original === 1 || original === 'true');
                    if (key === 'mthInc' && proposed !== null && original != null) return Number(proposed) !== Number(original);
                    if (dateFields.has(key)) return String(proposed ?? '').slice(0, 10) !== String(original ?? '').slice(0, 10);
                    return String(proposed ?? '').trim() !== String(original ?? '').trim();
                });
                if (!changed) {notify('warning', 'No changes to save.'); return;}
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
        if (!validCustomerId) return;
        const controller = new AbortController();
        getCustomer(id).then(value => {
            if (!controller.signal.aborted) setCustomer(value);
        })
            .catch(err => {
                if (!controller.signal.aborted) setCustomerError(errorText(err));
            });
        return () => controller.abort();
    }, [id, validCustomerId]);
    useEffect(() => {
        if (!validCustomerId || !customer) return;
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
    }, [id, validCustomerId, activeSection, refresh, customer]);
    useEffect(() => {
        if (section !== 'identifications' || !validCustomerId) return;
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
    }, [id, validCustomerId, section, refresh]);

    useEffect(() => {
        if (!validCustomerId) return;
        let active = true;
        getStatusRequests(id).then(requests => {
            if (active) {
                setStatusRequests(requests);
            }
        }).catch(err => {
            if (active) setStatusError(errorText(err));
        });
        return () => {
            active = false;
        };
    }, [id, validCustomerId, statusRefresh]);

    useEffect(() => {
        if (!validCustomerId) return;
        let active = true;
        let objectUrl: string | null = null;
        getCustomerProfilePicture(id).then(blob => {
            if (!active) return;
            objectUrl = URL.createObjectURL(blob);
            setPictureUrl(objectUrl);
        }).catch(() => {
            if (active) setPictureUrl(null);
        }).finally(() => {
            if (active) setPictureLoading(false);
        });
        return () => {
            active = false;
            if (objectUrl) URL.revokeObjectURL(objectUrl);
        };
    }, [id, validCustomerId, pictureVersion]);

    useEffect(() => () => {
        if (picturePreviewUrl) URL.revokeObjectURL(picturePreviewUrl);
    }, [picturePreviewUrl]);

    async function selectProfilePicture(selected: File | undefined) {
        setPictureError('');
        if (!selected) return;
        const allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];
        if (!allowedTypes.includes(selected.type)) {
            setPictureFile(null);
            setPicturePreviewUrl(null);
            setPictureError('Choose a JPG, PNG or WebP image.');
            return;
        }
        if (selected.size === 0 || selected.size > 5 * 1024 * 1024) {
            setPictureFile(null);
            setPicturePreviewUrl(null);
            setPictureError('Choose a non-empty image that is 5 MB or smaller.');
            return;
        }
        const preview = URL.createObjectURL(selected);
        let keepPreview = false;
        try {
            const dimensions = await new Promise<{width: number; height: number}>((resolve, reject) => {
                const image = new Image();
                image.onload = () => resolve({width: image.naturalWidth, height: image.naturalHeight});
                image.onerror = () => reject(new Error('The selected file is not a valid image.'));
                image.src = preview;
            });
            const pixels = dimensions.width * dimensions.height;
            if (dimensions.width < 64 || dimensions.height < 64 || dimensions.width > 4096 || dimensions.height > 4096 || pixels > 16_000_000) {
                setPictureFile(null);
                setPictureError('Image dimensions must be at least 64 × 64 and no larger than 4096 × 4096 (16 megapixels).');
                return;
            }
            setPictureFile(selected);
            setPicturePreviewUrl(preview);
            keepPreview = true;
        } catch (error) {
            setPictureFile(null);
            setPicturePreviewUrl(null);
            setPictureError(error instanceof Error ? error.message : 'The selected file is not a valid image.');
        } finally {
            if (!keepPreview) URL.revokeObjectURL(preview);
            if (pictureInput.current) pictureInput.current.value = '';
        }
    }

    async function uploadProfilePicture() {
        if (!pictureFile || pictureBusy) return;
        setPictureBusy(true);
        setPictureError('');
        try {
            await replaceCustomerProfilePicture(id, pictureFile);
            setPictureFile(null);
            setPicturePreviewUrl(null);
            setPictureLoading(true);
            setPictureVersion(version => version + 1);
            notify('success', 'Customer profile picture updated successfully.');
        } catch (error) {
            setPictureError(errorText(error));
            notify('error', errorText(error));
            setPictureLoading(false);
        } finally {
            setPictureBusy(false);
        }
    }

    async function removeProfilePicture() {
        if (pictureBusy) return;
        setPictureBusy(true);
        setPictureError('');
        try {
            await removeCustomerProfilePicture(id);
            setPictureFile(null);
            setPicturePreviewUrl(null);
            setPictureUrl(null);
            setPictureLoading(true);
            setPictureVersion(version => version + 1);
            setConfirmRemovePicture(false);
            notify('success', 'Customer profile picture removed.');
        } catch (error) {
            setPictureError(errorText(error));
            notify('error', errorText(error));
        } finally {
            setPictureBusy(false);
        }
    }

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

    if (!validCustomerId) return <div className="cbms-profile">
        <BackNavigation className="cbms-profile-back-button" label="Back to Customers" to="/customers"/>
        <p>Invalid customer identifier.</p>
    </div>;
    return <section className="cbms-profile">
        <BackNavigation className="cbms-profile-back-button" label="Back to Customers" to="/customers" />
        <div className="cbms-profile-heading">
            <div className="cbms-profile-identity">
                <div className="cbms-profile-avatar-wrap">
                    <div className="cbms-profile-avatar" role="img"
                         aria-label={picturePreviewUrl ? `Selected profile picture preview for ${customer?.custNm ?? 'customer'}` :
                             pictureUrl ? `Profile picture of ${customer?.custNm ?? 'customer'}` : 'Default customer avatar'}>
                        {picturePreviewUrl ? <img src={picturePreviewUrl} alt={`Preview for ${customer?.custNm ?? 'customer'}`}/> :
                            pictureLoading ? <span className="cbms-profile-avatar-loading" aria-label="Loading profile picture"/> :
                                pictureUrl ? <img src={pictureUrl} alt={`Profile picture of ${customer?.custNm ?? 'customer'}`}
                                                 onError={() => setPictureUrl(null)}/> : <UserRound aria-hidden="true"/>}
                    </div>
                    {pictureFile && <span className="cbms-profile-picture-preview-tag">Preview</span>}
                </div>
                <div className="cbms-profile-identity-details">
                    <SubscreenTitle title={customer?.custNm ?? 'Customer Profile'}/>
                    <div className="cbms-profile-meta">
                        <span>Customer Identification Number: <strong>{customer?.cifNo ?? '—'}</strong></span>
                        <span>{customer?.custTyp === 'INDIVIDUAL' ? 'Individual Customer' : customer?.custTyp === 'CORPORATE' ? 'Corporate Customer' : customer?.custTyp ?? '—'}</span>
                        <StatusBadge status={customer?.sts}/>
                    </div>
                    {canAmend && <div className="cbms-profile-picture-actions">
                        <input ref={pictureInput} className="cbms-profile-picture-input" type="file"
                               accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp"
                               aria-label="Choose a customer profile picture"
                               onChange={event => void selectProfilePicture(event.target.files?.[0])}/>
                        <button type="button" disabled={pictureBusy}
                                onClick={() => pictureInput.current?.click()}>
                            Upload / Change Photo
                        </button>
                        {pictureFile && <>
                            <button type="button" className="cbms-profile-picture-primary" disabled={pictureBusy}
                                    onClick={() => void uploadProfilePicture()}>
                                {pictureBusy ? 'Uploading…' : 'Upload Photo'}
                            </button>
                            <button type="button" disabled={pictureBusy} onClick={() => {
                                setPictureFile(null);
                                setPicturePreviewUrl(null);
                                setPictureError('');
                            }}>Cancel</button>
                        </>}
                        {pictureUrl && !pictureFile && <button type="button" className="cbms-profile-picture-remove"
                                                                disabled={pictureBusy}
                                                                onClick={() => setConfirmRemovePicture(true)}>Remove Photo</button>}
                    </div>}
                    {pictureError && <p className="cbms-profile-error cbms-profile-picture-error" role="alert">{pictureError}</p>}
                </div>
            </div>
            <RefreshButton onClick={() => setRefresh(v => v + 1)}/>
        </div>
        {customerError && <p className="cbms-profile-error" role="alert">{customerError}</p>}
        <div className="cbms-status-panel">
            <h2>Customer status</h2>
            <p className="cbms-profile-current-status">Current Status: <StatusBadge status={customer?.sts}/></p>
            {statusError && <p className="cbms-profile-error" role="alert">{statusError}</p>}
            {canAmend && !hasPendingStatus && nextStatuses.length > 0 &&
                <form onSubmit={e => void requestStatusChange(e)} className="cbms-status-form">
                    <label><FieldLabel className="cbms-field-label" required>Requested Status</FieldLabel>
                        <select required value={statusTarget} onChange={e => setStatusTarget(e.target.value)}>
                            <option value="">Select status</option>
                            {nextStatuses.map(st => <option key={st} value={st}>{formatBusinessValue(st)}</option>)}
                        </select>
                    </label>
                    <label><FieldLabel className="cbms-field-label" required>Reason</FieldLabel>
                        <input required maxLength={500} value={statusReason}
                               onChange={e => setStatusReason(e.target.value)} placeholder="Reason for status change"/>
                    </label>
                    <button type="submit" disabled={statusBusy}>Submit for
                        approval
                    </button>
                </form>}
            {statusRequests.filter(request => request.requestStatus === 'PENDING').map(request => (
                <div className="cbms-status-request" key={request.requestId}
                     id={`customer-request-${request.requestId}`}
                     style={String(request.requestId) === linkedRequestId ? {outline: '2px solid #2563eb', outlineOffset: 3} : undefined}>
                    <div className="cbms-status-request-details">
                        <p><span
                            className="cbms-status-request-label">Requested status</span><strong>{request.fromStatus} → {request.toStatus}</strong>
                        </p>
                        <p><span className="cbms-status-request-label">Reason</span><strong>{request.reason}</strong>
                        </p>
                        <p className="cbms-status-pending-note">Awaiting approval from an authorized administrator.</p>
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
            <nav className="cbms-profile-nav" role="tablist" aria-orientation="vertical"
                 aria-label="Customer profile sections">{sections.filter(s => isCorporate ? s.id !== 'individual-details' && s.id !== 'employment' : s.id !== 'corporate-details').map(s =>
                <button key={s.id} type="button" role="tab" aria-selected={activeSection === s.id} className={activeSection === s.id ? 'active' : ''} onClick={() => {
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
                                <button type="button" className="cbms-profile-refresh cbms-action-create" onClick={() => startEdit(null)}>
                                    {section === 'individual-details' || section === 'corporate-details' ? 'Register Details' : 'Add Record'}
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
                    {(editFields[section] ?? []).map((key, fieldIndex) => {
                        const requiredFields: Record<string, string[]> = {
                            identifications: ['docTyp', 'docNo', 'issCty'],
                            contacts: ['conTyp', 'conVal'],
                            addresses: ['adrLn1'],
                            employment: ['empTyp', 'empSts'],
                            'kyc-assessments': ['asmtSts', 'riskRtng'],
                        };
                            const mandatory = (requiredFields[section] ?? []).includes(key)
                                || section === 'kyc-assessments' && form.asmtSts === 'VERIFIED'
                                && (key === 'revDt' || key === 'nxtRevDt');
                            const today = new Date().toISOString().slice(0, 10);
                            const minimum = key === 'mthInc' ? '0'
                                : section === 'identifications' && key === 'expDt' ? String(form.issDt ?? '') || undefined
                                    : section === 'employment' && key === 'endDt' ? String(form.startDt ?? '') || undefined
                                        : section === 'kyc-assessments' && key === 'nxtRevDt'
                                            ? String(form.revDt ?? '') > today ? String(form.revDt) : today
                                            : undefined;
                            const maximum = key === 'mthInc' ? '9999999999999999.99'
                                : section === 'kyc-assessments' && key === 'revDt' ? today
                                    : undefined;
                            const textMaxLengths: Record<string, number> = {
                                docTyp: 30, docNo: 100, empNm: 200, occ: 100, indCd: 20
                            };
                        const options: Record<string, string[]> = {
                            gndr: ['MALE', 'FEMALE', 'OTHER', 'NOT_SPECIFIED'],
                            mrtlSts: ['SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED', 'OTHER'],
                            conTyp: ['MOBILE', 'PHONE', 'EMAIL', 'WORK_PHONE', 'OTHER'],
                            adrTyp: ['RESIDENTIAL', 'BUSINESS', 'MAILING', 'REGISTERED', 'OTHER'],
                            empTyp: ['FULL_TIME', 'PART_TIME', 'SELF_EMPLOYED', 'CONTRACT', 'UNEMPLOYED', 'RETIRED', 'OTHER'],
                            empSts: ['CURRENT', 'PREVIOUS', 'PENDING'],
                            asmtSts: ['PENDING', 'IN_PROGRESS', 'VERIFIED', 'REJECTED', 'EXPIRED'],
                            riskRtng: ['LOW', 'MEDIUM', 'HIGH'],
                        };
                        return <label key={key}><FieldLabel className="cbms-field-label" required={mandatory}>{fieldLabel(key, form)}</FieldLabel>
                            {(['natCd', 'regCty', 'issCty', 'ctyCd', 'incCcy'].includes(key)) ?
                                <ReferenceCodeSelect kind={key === 'incCcy' ? 'currencies' : 'countries'}
                                                     value={String(form[key] ?? '')} required={mandatory} autoFocus={fieldIndex === 0}
                                                     onChange={value => change(key, value)}/> :
                            key === 'priFlg' ? <input type="checkbox" checked={Boolean(form[key])}
                                                       onChange={e => change(key, e.target.checked)}/> :
                                key === 'docTyp' ?
                                    <select autoFocus={fieldIndex === 0} required={mandatory} value={String(form[key] ?? '')}
                                            onChange={e => change(key, e.target.value)}>
                                        <option value="">Select document type</option>
                                        {String(form[key] ?? '') && !['MYKAD', 'PASSPORT'].includes(String(form[key])) &&
                                            <option value={String(form[key])}>{String(form[key])} — Existing value</option>}
                                        <option value="MYKAD">MyKad</option>
                                        <option value="PASSPORT">Passport</option>
                                    </select> : options[key] ?
                                    <select autoFocus={fieldIndex === 0} required={mandatory} value={String(form[key] ?? '')}
                                            onChange={e => change(key, e.target.value)}>
                                        <option value="">Select</option>
                                        {String(form[key] ?? '') && !options[key].includes(String(form[key])) &&
                                            <option value={String(form[key])}>{formatBusinessValue(String(form[key]))} — Existing value</option>}
                                        {options[key].map(v => <option key={v} value={v}>{formatBusinessValue(v)}</option>)}</select> :
                                    <input autoFocus={fieldIndex === 0} required={mandatory}
                                           type={dateFields.has(key) ? 'date' : key === 'mthInc' ? 'number' : 'text'}
                                            max={maximum}
                                            min={minimum}
                                            maxLength={textMaxLengths[key]}
                                           step={key === 'mthInc' ? '0.01' : undefined} value={String(form[key] ?? '')}
                                           onChange={e => change(key, e.target.value)}/>}
                        </label>;
                    })}
                    {section === 'identifications' && <label
                        className="cbms-ident-file"><FieldLabel className="cbms-field-label" required>Supporting Document</FieldLabel>
                        <input type="file" required accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
                               onChange={e => setFile(e.target.files?.[0] ?? null)}/>
                        <small>PDF, PNG or JPEG · maximum 5 MB. A document reference is generated automatically.</small>
                    </label>}
                    {saveError && <p className="cbms-profile-error" role="alert">{saveError}</p>}
                    <div className="cbms-profile-form-actions">
                        <button type="button" disabled={saving} onClick={() => setEditing(false)}>Cancel</button>
                        <button type="submit" disabled={saving}>{saving ? 'Saving…' : editingRecord ? 'Save Changes' : 'Create'}</button>
                    </div>
                </form> : loading ? <p>Loading information…</p> : sectionError ?
                    <p className="cbms-profile-error" role="alert">{sectionError}</p> : <>
                        {section === 'kyc-assessments' && Array.isArray(sectionData) && sectionData.slice(0, 1).some((r: Record<string, unknown>) => r.riskRtng === 'HIGH' || (typeof r.nxtRevDt === 'string' && r.nxtRevDt.slice(0, 10) < new Date().toISOString().slice(0, 10))) &&
                            <p className="cbms-kyc-warning">Attention: High-risk assessment or overdue Know Your Customer review.</p>}
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
        {confirmRemovePicture && <ConfirmDialog title="Remove customer profile picture?"
                                                 message="The current picture will be permanently removed and the default avatar will be shown."
                                                 confirmLabel="Remove Photo" danger={true} busy={pictureBusy}
                                                 onCancel={() => setConfirmRemovePicture(false)}
                                                 onConfirm={() => void removeProfilePicture()}/>}
    </section>;
}
import {formatBusinessValue} from '../utils/display';
