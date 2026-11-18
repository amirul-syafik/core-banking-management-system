import {SortableTable} from '../components/SortableTable';
import {useCallback, useEffect, useState, type FormEvent} from 'react';
import {useSearchParams} from 'react-router-dom';
import {api} from '../api/client';
import {useAuth} from '../auth/useAuth';
import {useToast} from '../components/toastContext';
import {ConfirmDialog, FieldLabel, RefreshButton, BackNavigation, SubscreenTitle} from '../components/UiStandards';
import {CustomerField} from './CustomerField';
import {FinancingField} from './CollateralLookupFields';
import PopupView from '../components/PopupView';
import {Badge, CreateButton, Empty, FormDialog, ModuleHeader, Paging, Panel, Tabs} from './PageComponents';
import {dateOnly, explainError, money} from './pageUtils';
import type {ApiPage} from './pageUtils';

type Collateral = {
    collateralId: number;
    collateralRef: string;
    originApplId: number | null;
    collateralTyp: string;
    collateralDesc: string;
    ownerCustId: number;
    ownerCustNm: string;
    vehicleId: number | null;
    collateralSts: string
};
type Valuation = {
    valuationId: number;
    valuationTyp: string;
    valuationDt: string;
    marketValue: number;
    forcedSaleValue: number;
    currencyCd: string;
    valuationSource: string
};
type Link = {
    linkId: number;
    applId: number;
    applRef: string;
    facRef: string | null;
    collateralRef: string;
    allocatedAmount: number;
    linkSts: string
};
type Exposure = {
    latestForcedSaleValue: number | null;
    latestValuationDate: string | null;
    activeAllocatedAmount: number;
    availableAmount: number;
    exposureStatus: string;
    valuationStale: boolean
};
type Kind = 'create' | 'edit' | 'valuation' | 'link' | 'vehicle';
const initial = {collateralTyp: 'VEHICLE', collateralDesc: '', ownerCustId: '', vehicleId: '', originApplId: ''};
const initialVehicle = {registrationNo: '', registrationDt: '', chassisNo: '', engineNo: '', vehicleMake: '', vehicleModel: '', vehicleVariant: '', manufactureYear: '', vehicleType: 'CAR', vehicleCondition: 'USED', colour: '', engineCapacityCc: ''};

export default function CollateralManagement() {
    const notify = useToast();
    const {session} = useAuth();
    const canEdit = session?.roles.some(r => r === 'MAKER' || r === 'ADMIN') ?? false;
    const admin = session?.roles.includes('ADMIN') ?? false;
    const [owner, setOwner] = useState('');
    const [type, setType] = useState('');
    const [status, setStatus] = useState('');
    const [filters, setFilters] = useState({owner: '', type: '', status: ''});
    const [page, setPage] = useState(0);
    const [result, setResult] = useState<ApiPage<Collateral> | null>(null);
    const [selected, setSelected] = useState<Collateral | null>(null);
    const [searchParams, setSearchParams] = useSearchParams();
    const selectedParam = searchParams.get('collateral');
    const detailTabParam = searchParams.get('tab');
    const detailTab = detailTabParam === 'valuations' || detailTabParam === 'allocations' ? detailTabParam : 'overview';
    const visibleSelected = selected && selectedParam === String(selected.collateralId) ? selected : null;
    const openDetails = (record: Collateral) => {
        setSearchParams({collateral: String(record.collateralId)});
        void inspect(record);
    };
    const closeDetails = () => {
        setSelected(null);
        setSearchParams({});
    };
    const [valuations, setValuations] = useState<Valuation[]>([]);
    const [valuationPage, setValuationPage] = useState(0);
    const [valuationTotalPages, setValuationTotalPages] = useState(1);
    const [valuationTotalElements, setValuationTotalElements] = useState(0);
    const [valuationBusy, setValuationBusy] = useState(false);
    const [links, setLinks] = useState<Link[]>([]);
    const [linkPage, setLinkPage] = useState(0);
    const [exposure, setExposure] = useState<Exposure | null>(null);
    const [modal, setModal] = useState<Kind | null>(null);
    const [confirmSave, setConfirmSave] = useState(false);
    const [originalForm, setOriginalForm] = useState<string | null>(null);
    const [form, setForm] = useState(initial);
    const [vehicleForm, setVehicleForm] = useState(initialVehicle);
    const [valuation, setValuation] = useState({
        valuationDt: '',
        marketValue: '',
        forcedSaleValue: '',
        valuationSource: '',
        remarks: ''
    });
    const [linkForm, setLinkForm] = useState({applId: '', allocatedAmount: '', remarks: ''});
    const [confirm, setConfirm] = useState<{
        kind: 'status' | 'activate' | 'release';
        id: number;
        status?: string
    } | null>(null);
    const [reason, setReason] = useState('');
    const [busy, setBusy] = useState(false);
    const [vehicleDisplay, setVehicleDisplay] = useState('');
    const [approvedHpApplications, setApprovedHpApplications] = useState<Array<{applId: number; applRef: string; custId: number; facTyp: string}>>([]);

    useEffect(() => {
        if (!canEdit) return;
        void api.get<Array<{applId: number; applRef: string; custId: number; facTyp: string; applSts: string}>>('/financing/applications', {params: {status: 'APPROVED'}})
            .then(({data}) => setApprovedHpApplications(data.filter(a => a.facTyp === 'HIRE_PURCHASE')))
            .catch(() => setApprovedHpApplications([]));
    }, [canEdit]);

    const reload = useCallback(async () => {
        setBusy(true);
        try {
            const {data} = await api.get<ApiPage<Collateral>>('/collaterals', {
                params: {
                    ownerCustId: filters.owner || undefined, collateralTyp: filters.type || undefined,
                    collateralSts: filters.status || undefined, page, size: 10
                }
            });
            setResult(data);
        } catch (error) {
            setResult(null);
            notify('error', explainError(error));
        } finally {
            setBusy(false);
        }
    }, [filters, page, notify]);
    useEffect(() => {
        void Promise.resolve().then(reload);
    }, [reload]);
    const inspect = useCallback(async (record: Collateral, resetTab = true) => {
        setSelected(record);
        if (resetTab && selectedParam === String(record.collateralId) && detailTab !== 'overview') setSearchParams({collateral: String(record.collateralId)});
        setValuations([]);
        setValuationPage(0);
        setValuationTotalPages(1);
        setValuationTotalElements(0);
        setLinks([]);
        setLinkPage(0);
        setExposure(null);
        setVehicleDisplay('');
        if (record.vehicleId) void api.get<{
            registrationNo: string | null;
            chassisNo: string;
            vehicleMake: string;
            vehicleModel: string
        }>(`/vehicles/${record.vehicleId}`)
            .then(result => setVehicleDisplay(`${result.data.registrationNo || result.data.chassisNo} · ${result.data.vehicleMake} ${result.data.vehicleModel}`))
            .catch(() => setVehicleDisplay('Vehicle unavailable'));
        const requests = await Promise.allSettled([
            api.get<ApiPage<Valuation>>(`/collaterals/${record.collateralId}/valuations`, {
                params: {
                    page: 0,
                    size: 10
                }
            }),
            api.get<Exposure>(`/collateral-monitoring/collaterals/${record.collateralId}/exposure`),
            api.get<Link[]>(`/financing-collaterals/collateral/${record.collateralId}`)
        ]);
        if (requests[0].status === 'fulfilled') {
            setValuations(requests[0].value.data.content);
            setValuationTotalPages(Math.max(1, requests[0].value.data.totalPages));
            setValuationTotalElements(requests[0].value.data.totalElements);
        }
        if (requests[1].status === 'fulfilled') setExposure(requests[1].value.data);
        if (requests[2].status === 'fulfilled') setLinks(requests[2].value.data);
        if (requests.some(x => x.status === 'rejected')) notify('warning', 'Some collateral details could not be loaded.');
    }, [notify, selectedParam, detailTab, setSearchParams, setLinkPage]);
    useEffect(() => {
        const id = Number(selectedParam);
        if (!selectedParam || !Number.isSafeInteger(id) || id <= 0 || selected?.collateralId === id) return;
        let active = true;
        void api.get<Collateral>(`/collaterals/${id}`).then(response => {
            if (active) void inspect(response.data, false);
        })
            .catch(error => {
                if (active) notify('error', explainError(error));
            });
        return () => {
            active = false;
        };
    }, [selectedParam, selected?.collateralId, inspect, notify]);
    const changeValuationPage = async (nextPage: number) => {
        if (!visibleSelected || valuationBusy) return;
        const collateralId = visibleSelected.collateralId;
        setValuationBusy(true);
        try {
            const {data} = await api.get<ApiPage<Valuation>>(`/collaterals/${collateralId}/valuations`,
                {params: {page: nextPage, size: 10}});
            setValuations(data.content);
            setValuationPage(nextPage);
            setValuationTotalPages(Math.max(1, data.totalPages));
            setValuationTotalElements(data.totalElements);
        } catch (error) {
            notify('error', explainError(error));
        } finally {
            setValuationBusy(false);
        }
    };
    const open = (kind: Kind, record?: Collateral) => {
        setModal(kind);
        setConfirmSave(false);
        const prepared = record ? {
            collateralTyp: record.collateralTyp,
            collateralDesc: record.collateralDesc,
            ownerCustId: String(record.ownerCustId),
            vehicleId: String(record.vehicleId ?? ''),
            originApplId: String(record.originApplId ?? '')
        } : initial;
        setForm(prepared);
        setOriginalForm(kind === 'edit' ? JSON.stringify(prepared) : null);
        setValuation({valuationDt: '', marketValue: '', forcedSaleValue: '', valuationSource: '', remarks: ''});
        setLinkForm({applId: '', allocatedAmount: '', remarks: ''});
        setVehicleForm(initialVehicle);
    };
    const requestSave = (event: FormEvent) => {
        event.preventDefault();
        if (busy) return;
        if ((modal === 'create' || modal === 'edit') && !form.ownerCustId) {
            notify('warning', 'Select a customer.');
            return;
        }
        if (modal === 'edit' && JSON.stringify(form) === originalForm) {
            notify('warning', 'No changes to save.');
            return;
        }
        if (modal === 'valuation' && Number(valuation.forcedSaleValue) > Number(valuation.marketValue)) {
            notify('warning', 'Forced sale value cannot exceed market value.');
            return;
        }
        if (modal === 'vehicle' && (!vehicleForm.chassisNo.trim() || !vehicleForm.vehicleMake.trim()
            || !vehicleForm.vehicleModel.trim() || !vehicleForm.manufactureYear)) {
            notify('warning', 'Complete the required vehicle details.');
            return;
        }
        setConfirmSave(true);
    };
    const save = async () => {
        if (busy || !confirmSave) return;
        setBusy(true);
        try {
            if (modal === 'create' || modal === 'edit') {
                const payload = {
                    ...form,
                    ownerCustId: Number(form.ownerCustId),
                    originApplId: form.originApplId ? Number(form.originApplId) : null,
                    vehicleId: form.collateralTyp === 'VEHICLE' && form.vehicleId ? Number(form.vehicleId) : null
                };
                if (modal === 'edit' && selected) await api.put(`/collaterals/${selected.collateralId}`, payload);
                else {
                    const created = await api.post<Collateral>('/collaterals', payload);
                    notify('success', `Collateral ${created.data.collateralRef} created.`);
                }
            } else if (modal === 'valuation' && selected) {
                await api.post(`/collaterals/${selected.collateralId}/valuations`, {
                    ...valuation,
                    marketValue: Number(valuation.marketValue),
                    forcedSaleValue: Number(valuation.forcedSaleValue)
                });
            } else if (modal === 'link' && selected) {
                await api.post('/financing-collaterals', {
                    applId: Number(linkForm.applId),
                    collateralId: selected.collateralId,
                    allocatedAmount: Number(linkForm.allocatedAmount),
                    remarks: linkForm.remarks.trim()
                });
            } else if (modal === 'vehicle' && selected) {
                const vehiclePayload = {
                    ...vehicleForm,
                    manufactureYear: Number(vehicleForm.manufactureYear),
                    engineCapacityCc: vehicleForm.engineCapacityCc ? Number(vehicleForm.engineCapacityCc) : null,
                    registrationDt: vehicleForm.registrationDt || null
                };
                const {data} = await api.post(`/collaterals/${selected.collateralId}/vehicle`, vehiclePayload);
                notify('success', `Vehicle ${data.registrationNo || data.chassisNo} registered and linked.`);
            }
            if (modal !== 'create' && modal !== 'vehicle') notify('success', modal === 'edit' ? 'Changes saved.' : modal === 'valuation' ? 'Valuation recorded.' : 'Financing link proposed.');
            const refreshSelection = (modal === 'valuation' || modal === 'link' || modal === 'vehicle') ? selected : null;
            setConfirmSave(false);
            setModal(null);
            if (refreshSelection) {
                const refreshed = modal === 'vehicle'
                    ? (await api.get<Collateral>(`/collaterals/${refreshSelection.collateralId}`)).data
                    : refreshSelection;
                await inspect(refreshed, false);
            }
            else setSelected(null);
        } catch (error) {
            notify('error', explainError(error));
            setBusy(false);
            return;
        }
        setBusy(false);
        await reload();
    };
    const confirmAction = async () => {
        if (!confirm || busy) return;
        setBusy(true);
        try {
            if (confirm.kind === 'status') await api.patch(`/collaterals/${confirm.id}/status`, {collateralSts: confirm.status});
            if (confirm.kind === 'activate') await api.post(`/financing-collaterals/${confirm.id}/activate`);
            if (confirm.kind === 'release') await api.post(`/financing-collaterals/${confirm.id}/release`, {reason: reason.trim()});
            notify('success', 'Action completed.');
            setConfirm(null);
            setReason('');
            setSelected(null);
        } catch (error) {
            notify('error', explainError(error));
            setBusy(false);
            return;
        }
        setBusy(false);
        await reload();
    };
    return <main className="gap-page">
        {!visibleSelected && <>
            <ModuleHeader title="Collateral Management"
                          description="Register security, record valuations, review allocations and link collateral to financing applications."
                          actions={
                              <div className="cbms-toolbar-actions">
                                  <RefreshButton loading={busy} onClick={() => void reload()}/>
                                  {canEdit &&
                                      <CreateButton onClick={() => open('create')}>New Collateral</CreateButton>}
                              </div>
                          }/>
            <Panel title="Collateral Register" count={result?.totalElements}>
                <form className="gap-toolbar" onSubmit={e => {
                    e.preventDefault();
                    setPage(0);
                    setFilters({owner, type, status});
                }}>
                    <CustomerField label="Customer" required={false} value={owner} onChange={setOwner}/>
                    <label>Collateral Type<select value={type} onChange={e => setType(e.target.value)}>
                        <option value="">All Types</option>
                        {['VEHICLE', 'PROPERTY', 'CASH', 'OTHER'].map(v => <option key={v} value={v}>{formatBusinessValue(v)}</option>)}
                    </select></label>
                    <label>Status<select value={status} onChange={e => setStatus(e.target.value)}>
                        <option value="">All Statuses</option>
                        <option value="REGISTERED">{formatBusinessValue('REGISTERED')}</option>
                        <option value="INACTIVE">{formatBusinessValue('INACTIVE')}</option>
                    </select></label>
                    <div className="cbms-search-actions">
                        <button className="cbms-action-search" type="submit" disabled={busy}>Search</button>
                        <button type="button" className="cbms-action-clear" onClick={() => {
                            setOwner('');
                            setType('');
                            setStatus('');
                            setPage(0);
                            setFilters({owner: '', type: '', status: ''});
                        }}>Clear
                        </button>
                    </div>
                </form>
                <div className="gap-table-wrap">
                    <SortableTable>
                        <thead>
                        <tr>
                            <th>Reference</th>
                            <th>Type</th>
                            <th>Description</th>
                            <th>Owner</th>
                            <th>Status</th>
                            <th>Action</th>
                        </tr>
                        </thead>
                        <tbody>{result?.content.map(record => <tr key={record.collateralId}>
                            <td><PopupView value={record.collateralRef} maxLength={18}/></td>
                            <td>{formatBusinessValue(record.collateralTyp)}</td>
                            <td><PopupView value={record.collateralDesc} maxLength={30}/></td>
                            <td><PopupView value={record.ownerCustNm || 'Customer unavailable'} maxLength={26}/></td>
                            <td><Badge status={record.collateralSts}/></td>
                            <td className="gap-actions">
                                <button type="button" onClick={() => openDetails(record)}>View</button>
                                {canEdit && record.collateralSts === 'REGISTERED' &&
                                    <button type="button" className="cbms-action-clear" onClick={() => {
                                        setSelected(record);
                                        open('edit', record);
                                    }}>Edit</button>}
                                {admin && <button type="button" onClick={() => setConfirm({
                                    kind: 'status',
                                    id: record.collateralId,
                                    status: record.collateralSts === 'REGISTERED' ? 'INACTIVE' : 'REGISTERED'
                                })}>{record.collateralSts === 'REGISTERED' ? 'Deactivate' : 'Reactivate'}</button>}
                            </td>
                        </tr>)}{!result?.content.length &&
                            <Empty columns={6} message={busy ? 'Loading…' : 'No collateral records.'}/>}</tbody>
                    </SortableTable>
                </div>
                <Paging page={page} totalPages={result?.totalPages ?? 0} totalElements={result?.totalElements ?? 0}
                        onPage={setPage} busy={busy}/>
            </Panel></>}
        {visibleSelected && <div className="gap-collateral-workspace" aria-label="Collateral account details">
            <BackNavigation label="Back to Collateral Register" onBack={closeDetails}/>
            <section className="gap-panel gap-collateral-account-panel">
                <div className="gap-collateral-account-header">
                    <div><SubscreenTitle title="Collateral" reference={visibleSelected.collateralRef}/>
                        <p>{visibleSelected.collateralDesc}</p></div>
                    <div className="gap-collateral-account-actions"><Badge status={visibleSelected.collateralSts}/>
                        <RefreshButton loading={busy || valuationBusy}
                                       onClick={() => void inspect(visibleSelected, false)}/>
                    </div>
                </div>
                <Tabs current={detailTab}
                      onSelect={tab => setSearchParams({collateral: String(visibleSelected.collateralId), tab})}
                      options={[{id: 'overview', label: 'Overview'}, {id: 'valuations', label: 'Valuations'},
                          {id: 'allocations', label: 'Financing Allocations'}]}/>
                <div className="gap-collateral-account-section" role="tabpanel" aria-label={detailTab}>
                    {detailTab === 'overview' && <>
                        <h2>Collateral Overview</h2>
                        <dl className="gap-details gap-collateral-information">
                            <div>
                                <dt>Collateral Type</dt>
                                <dd>{formatBusinessValue(visibleSelected.collateralTyp)}</dd>
                            </div>
                            <div>
                                <dt>Customer</dt>
                                <dd><PopupView value={visibleSelected.ownerCustNm || 'Customer unavailable'}
                                               maxLength={34}/></dd>
                            </div>
                            <div>
                                <dt>Vehicle</dt>
                                <dd><PopupView
                                    value={visibleSelected.vehicleId ? (vehicleDisplay || 'Loading vehicle…') : '—'}
                                    maxLength={32}/></dd>
                            </div>
                            <div>
                                <dt>Status</dt>
                                <dd><Badge status={visibleSelected.collateralSts}/></dd>
                            </div>
                            <div>
                                <dt>Latest Forced Sale Value (MYR)</dt>
                                <dd>{exposure ? money(exposure.latestForcedSaleValue) : '—'}</dd>
                            </div>
                            <div>
                                <dt>Allocated Amount (MYR)</dt>
                                <dd>{exposure ? money(exposure.activeAllocatedAmount) : '—'}</dd>
                            </div>
                            <div>
                                <dt>Available Amount (MYR)</dt>
                                <dd>{exposure ? money(exposure.availableAmount) : '—'}</dd>
                            </div>
                            <div>
                                <dt>Latest Valuation Date</dt>
                                <dd>{exposure?.latestValuationDate ? dateOnly(exposure.latestValuationDate) : '—'}</dd>
                            </div>
                            <div>
                                <dt>Exposure Status</dt>
                                <dd><Badge status={exposure?.exposureStatus}/></dd>
                            </div>
                        </dl>
                        {canEdit && visibleSelected.collateralTyp === 'VEHICLE'
                            && visibleSelected.collateralSts === 'REGISTERED' && !visibleSelected.vehicleId &&
                            <div className="gap-panel-header">
                                <p>Register the vehicle against this collateral to continue valuation and financing allocation.</p>
                                <button type="button" onClick={() => open('vehicle', visibleSelected)}>Register Vehicle</button>
                            </div>}
                    </>}
                    {detailTab === 'valuations' && <>
                        <div className="gap-panel-header"><h2>Valuations</h2>
                            {canEdit && visibleSelected.collateralSts === 'REGISTERED' &&
                                <button type="button" onClick={() => open('valuation')}>Record Valuation</button>}</div>
                        <div className="gap-table-wrap">
                            <SortableTable>
                                <thead>
                                <tr>
                                    <th>Date</th>
                                    <th>Type</th>
                                    <th>Market Value (MYR)</th>
                                    <th>Forced Sale Value (MYR)</th>
                                    <th>Source</th>
                                </tr>
                                </thead>
                                <tbody>{valuations.map(v => <tr key={v.valuationId}>
                                    <td>{dateOnly(v.valuationDt)}</td>
                                    <td>{formatBusinessValue(v.valuationTyp)}</td>
                                    <td>{money(v.marketValue)}</td>
                                    <td>{money(v.forcedSaleValue)}</td>
                                    <td><PopupView value={v.valuationSource}/></td>
                                </tr>)}
                                {!valuations.length && <Empty columns={5}
                                                              message={valuationBusy ? 'Loading valuations…' : 'No valuations recorded.'}/>}</tbody>
                            </SortableTable>
                        </div>
                        <Paging page={valuationPage} totalPages={valuationTotalPages}
                                totalElements={valuationTotalElements}
                                onPage={next => void changeValuationPage(next)} busy={busy || valuationBusy}/>
                    </>}
                    {detailTab === 'allocations' && <>
                        <div className="gap-panel-header"><h2>Financing Allocations</h2>
                            {canEdit && visibleSelected.collateralSts === 'REGISTERED' &&
                                <button type="button" onClick={() => open('link')}>Propose Link</button>}</div>
                        <div className="gap-table-wrap">
                            <SortableTable>
                                <thead>
                                <tr>
                                    <th>Application</th>
                                    <th>Facility</th>
                                    <th>Allocated (MYR)</th>
                                    <th>Status</th>
                                    <th>Action</th>
                                </tr>
                                </thead>
                                <tbody>
                                {links.slice(linkPage * 10, (linkPage + 1) * 10).map(link => <tr key={link.linkId}>
                                    <td><PopupView value={link.applRef}/></td>
                                    <td><PopupView value={link.facRef || '—'}/></td>
                                    <td>{money(link.allocatedAmount)}</td>
                                    <td><Badge status={link.linkSts}/></td>
                                    <td className="gap-actions">{admin && link.linkSts === 'PROPOSED' &&
                                        <button type="button" onClick={() => setConfirm({
                                            kind: 'activate',
                                            id: link.linkId
                                        })}>Activate</button>}
                                        {admin && ['PROPOSED', 'ACTIVE'].includes(link.linkSts) &&
                                            <button type="button" className="gap-danger" onClick={() => {
                                                setReason('');
                                                setConfirm({kind: 'release', id: link.linkId});
                                            }}>Release</button>}
                                    </td>
                                </tr>)}
                                {!links.length && <Empty columns={5} message="No financing links."/>}</tbody>
                            </SortableTable>
                        </div>
                        <Paging page={Math.min(linkPage, Math.max(0, Math.ceil(links.length / 10) - 1))}
                                totalPages={Math.max(1, Math.ceil(links.length / 10))} totalElements={links.length} onPage={setLinkPage}
                                busy={busy}/>
                    </>}
                </div>
            </section>
        </div>}
        {modal && <FormDialog
            title={modal === 'vehicle' ? 'Register Vehicle' : modal === 'edit' ? 'Edit Collateral' : modal === 'create' ? 'New Collateral' : modal === 'valuation' ? 'Record Valuation' : 'Propose Financing Link'}
            eyebrow="COLLATERAL MANAGEMENT" wide={modal === 'create' || modal === 'edit' || modal === 'vehicle'} busy={busy} onClose={() => {
            setModal(null);
            setConfirmSave(false);
        }}>
            <form className="gap-form" onSubmit={requestSave}>
                {(modal === 'create' || modal === 'edit') && <>
                    <div className="gap-grid">
                        {modal === 'create' ? <label className="gap-form-wide">Approved Hire Purchase Application
                            <select required value={form.originApplId} onChange={event => {
                                const selectedApplication = approvedHpApplications.find(a => String(a.applId) === event.target.value);
                                setForm({...form, originApplId: event.target.value, ownerCustId: selectedApplication ? String(selectedApplication.custId) : '', vehicleId: ''});
                            }}><option value="">Select approved application</option>
                                {approvedHpApplications.map(a => <option key={a.applId} value={a.applId}>{a.applRef} · Customer {a.custId}</option>)}
                            </select>
                        </label> : <div className="gap-form-wide"><CustomerField value={form.ownerCustId} onChange={value => setForm({...form, ownerCustId: value, vehicleId: ''})}/></div>}
                        <label><FieldLabel required>Collateral Type</FieldLabel><select disabled={modal === 'edit' && Boolean(form.vehicleId)} value={form.collateralTyp}
                                                                                        onChange={e => setForm({
                                                                                            ...form,
                                                                                            collateralTyp: e.target.value
                                                                                        })}>{['VEHICLE', 'PROPERTY', 'CASH', 'OTHER'].map(x =>
                            <option key={x} value={x}>{formatBusinessValue(x)}</option>)}</select></label>
                    </div>
                        {form.collateralTyp === 'VEHICLE' && !form.vehicleId &&
                            <p className="gap-form-wide">Register the collateral first, then link its vehicle before valuation, activation, or disbursement.</p>}
                    <label><FieldLabel required>Description</FieldLabel><textarea required maxLength={500}
                                                                                  value={form.collateralDesc}
                                                                                  onChange={e => setForm({
                                                                                      ...form,
                                                                                      collateralDesc: e.target.value
                                                                                  })}/></label></>}
                {modal === 'valuation' && <>
                    <div className="gap-grid">
                        <label><FieldLabel required>Valuation Date</FieldLabel><input type="date" required
                                                                                      max={new Date().toISOString().slice(0, 10)}
                                                                                      value={valuation.valuationDt}
                                                                                      onChange={e => setValuation({
                                                                                          ...valuation,
                                                                                          valuationDt: e.target.value
                                                                                      })}/></label>
                        <label><FieldLabel required>Market Value (MYR)</FieldLabel><input type="number" step="0.01"
                                                                                          min="0.01" required
                                                                                          value={valuation.marketValue}
                                                                                          onChange={e => setValuation({
                                                                                              ...valuation,
                                                                                              marketValue: e.target.value
                                                                                          })}/></label>
                        <label><FieldLabel required>Forced Sale Value (MYR)</FieldLabel><input type="number" step="0.01"
                                                                                               min="0" required
                                                                                               value={valuation.forcedSaleValue}
                                                                                               onChange={e => setValuation({
                                                                                                   ...valuation,
                                                                                                   forcedSaleValue: e.target.value
                                                                                               })}/></label></div>
                    <label><FieldLabel required>Valuation Source</FieldLabel><input required maxLength={150}
                                                                                    value={valuation.valuationSource}
                                                                                    onChange={e => setValuation({
                                                                                        ...valuation,
                                                                                        valuationSource: e.target.value
                                                                                    })}/></label>
                    <label>Remarks<textarea maxLength={500} value={valuation.remarks} onChange={e => setValuation({
                        ...valuation,
                        remarks: e.target.value
                    })}/></label></>}
                {modal === 'link' && <>
                    <div className="gap-grid">
                        <FinancingField ownerCustId={selected?.ownerCustId ? String(selected.ownerCustId) : ''}
                                           value={linkForm.applId}
                                           onChange={value => setLinkForm({...linkForm, applId: value})}/>
                        <label><FieldLabel required>Allocated Amount (MYR)</FieldLabel><input type="number" min="0.01"
                                                                                              step="0.01" required
                                                                                              value={linkForm.allocatedAmount}
                                                                                              onChange={e => setLinkForm({
                                                                                                  ...linkForm,
                                                                                                  allocatedAmount: e.target.value
                                                                                              })}/></label></div>
                    <label>Remarks<textarea maxLength={500} value={linkForm.remarks} onChange={e => setLinkForm({
                        ...linkForm,
                        remarks: e.target.value
                    })}/></label></>}
                {modal === 'vehicle' && <div className="gap-grid gap-form-three">
                    <label>Registration Number<input maxLength={30} value={vehicleForm.registrationNo}
                                                     onChange={e => setVehicleForm({...vehicleForm, registrationNo: e.target.value})}/></label>
                    <label>Registration Date<input type="date" value={vehicleForm.registrationDt}
                                                   onChange={e => setVehicleForm({...vehicleForm, registrationDt: e.target.value})}/></label>
                    <label><FieldLabel required>Chassis Number</FieldLabel><input required maxLength={100}
                                                                                  value={vehicleForm.chassisNo}
                                                                                  onChange={e => setVehicleForm({...vehicleForm, chassisNo: e.target.value})}/></label>
                    <label>Engine Number<input maxLength={100} value={vehicleForm.engineNo}
                                               onChange={e => setVehicleForm({...vehicleForm, engineNo: e.target.value})}/></label>
                    <label><FieldLabel required>Make</FieldLabel><input required maxLength={100} value={vehicleForm.vehicleMake}
                                                                       onChange={e => setVehicleForm({...vehicleForm, vehicleMake: e.target.value})}/></label>
                    <label><FieldLabel required>Model</FieldLabel><input required maxLength={100} value={vehicleForm.vehicleModel}
                                                                        onChange={e => setVehicleForm({...vehicleForm, vehicleModel: e.target.value})}/></label>
                    <label>Variant<input maxLength={100} value={vehicleForm.vehicleVariant}
                                         onChange={e => setVehicleForm({...vehicleForm, vehicleVariant: e.target.value})}/></label>
                    <label><FieldLabel required>Manufacture Year</FieldLabel><input required type="number" min="1900" max="2100"
                                                                                   value={vehicleForm.manufactureYear}
                                                                                   onChange={e => setVehicleForm({...vehicleForm, manufactureYear: e.target.value})}/></label>
                    <label><FieldLabel required>Vehicle Type</FieldLabel><select value={vehicleForm.vehicleType}
                                                                                 onChange={e => setVehicleForm({...vehicleForm, vehicleType: e.target.value})}>
                        {['CAR', 'MOTORCYCLE', 'VAN', 'LORRY', 'BUS', 'OTHER'].map(value =>
                            <option key={value} value={value}>{formatBusinessValue(value)}</option>)}</select></label>
                    <label><FieldLabel required>Condition</FieldLabel><select value={vehicleForm.vehicleCondition}
                                                                              onChange={e => setVehicleForm({...vehicleForm, vehicleCondition: e.target.value})}>
                        {['NEW', 'USED', 'RECOND'].map(value =>
                            <option key={value} value={value}>{formatBusinessValue(value)}</option>)}</select></label>
                    <label>Colour<input maxLength={50} value={vehicleForm.colour}
                                        onChange={e => setVehicleForm({...vehicleForm, colour: e.target.value})}/></label>
                    <label>Engine Capacity (cc)<input type="number" min="1" value={vehicleForm.engineCapacityCc}
                                                      onChange={e => setVehicleForm({...vehicleForm, engineCapacityCc: e.target.value})}/></label>
                </div>}
                <div className="gap-form-actions">
                    <button type="button" onClick={() => setModal(null)}>Cancel</button>
                    <button type="submit" className="gap-primary"
                            disabled={busy}>{modal === 'vehicle' ? 'Register Vehicle' : modal === 'edit' ? 'Save Changes' : modal === 'valuation' ? 'Record Valuation' : modal === 'link' ? 'Propose Link' : 'Create'}</button>
                </div>
            </form>
        </FormDialog>}
        {confirmSave && <ConfirmDialog title="Confirm Submission"
                                        message={modal === 'vehicle' ? 'Register and link this vehicle to the collateral?' : modal === 'edit' ? 'Save these collateral changes?' : modal === 'valuation' ? 'Record this collateral valuation?' : modal === 'link' ? 'Propose this financing link?' : 'Create this collateral record?'}
                                        confirmLabel={modal === 'vehicle' ? 'Register Vehicle' : modal === 'edit' ? 'Save Changes' : modal === 'valuation' ? 'Record Valuation' : modal === 'link' ? 'Propose Link' : 'Create'}
                                       busy={busy} onCancel={() => setConfirmSave(false)}
                                       onConfirm={() => void save()}/>}
        {confirm && <ConfirmDialog
            title={confirm.kind === 'status' ? 'Change Collateral Status' : confirm.kind === 'activate' ? 'Activate Financing Link' : 'Release Financing Link'}
            message={confirm.kind === 'status' ? 'Change this collateral status?' : confirm.kind === 'activate' ? 'Activate this financing link?' : 'Release this financing link?'}
            confirmLabel={confirm.kind === 'status' ? 'Change Status' : confirm.kind === 'activate' ? 'Activate' : 'Release'}
            busy={busy} danger={confirm.kind === 'release'} onCancel={() => setConfirm(null)}
            onConfirm={() => void confirmAction()}
            reasonLabel={confirm.kind === 'release' ? 'Release Reason' : undefined} reason={reason}
            reasonRequired={confirm.kind === 'release'}
            onReasonChange={confirm.kind === 'release' ? setReason : undefined}/>}
    </main>;
}
import {formatBusinessValue} from '../utils/display';
