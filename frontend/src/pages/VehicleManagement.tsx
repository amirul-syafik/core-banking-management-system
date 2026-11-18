import {SortableTable, type TableSortState} from '../components/SortableTable';
import {useCallback, useEffect, useState, type FormEvent} from 'react';
import {api} from '../api/client';
import {useAuth} from '../auth/useAuth';
import {useToast} from '../components/toastContext';
import {ConfirmDialog, FieldLabel, RefreshButton} from '../components/UiStandards';
import {CustomerField} from './CustomerField';
import {Badge, Empty, FormDialog, ModuleHeader, Paging, Panel} from './PageComponents';
import {explainError} from './pageUtils';
import type {ApiPage} from './pageUtils';
import PopupView from '../components/PopupView';

type Vehicle = {
    vehicleId: number;
    ownerCustId: number;
    ownerCustNm: string;
    registrationNo: string | null;
    registrationDt: string | null;
    chassisNo: string;
    engineNo: string | null;
    vehicleMake: string;
    vehicleModel: string;
    vehicleVariant: string | null;
    manufactureYear: number;
    vehicleType: string;
    vehicleCondition: string;
    colour: string | null;
    engineCapacityCc: number | null;
    vehicleSts: string
};
const fresh = {
    ownerCustId: '',
    registrationNo: '',
    registrationDt: '',
    chassisNo: '',
    engineNo: '',
    vehicleMake: '',
    vehicleModel: '',
    vehicleVariant: '',
    manufactureYear: '',
    vehicleType: 'CAR',
    vehicleCondition: 'USED',
    colour: '',
    engineCapacityCc: ''
};

export default function VehicleManagement() {
    const notify = useToast();
    const {session} = useAuth();
    const canEdit = session?.roles.some(r => r === 'MAKER' || r === 'ADMIN') ?? false;
    const isAdmin = session?.roles.includes('ADMIN') ?? false;
    const [owner, setOwner] = useState('');
    const [registration, setRegistration] = useState('');
    const [status, setStatus] = useState('');
    const [filters, setFilters] = useState({owner: '', registration: '', status: ''});
    const [page, setPage] = useState(0);
    const [sort, setSort] = useState<TableSortState>(null);
    const [result, setResult] = useState<ApiPage<Vehicle> | null>(null);
    const [selected, setSelected] = useState<Vehicle | null>(null);
    const [modal, setModal] = useState<'edit' | null>(null);
    const [form, setForm] = useState(fresh);
    const [confirm, setConfirm] = useState<Vehicle | null>(null);
    const [confirmSave, setConfirmSave] = useState(false);
    const [originalForm, setOriginalForm] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);
    const load = useCallback(async () => {
        setBusy(true);
        try {
            const {data} = await api.get<ApiPage<Vehicle>>('/vehicles', {
                params: {
                    ownerCustId: filters.owner || undefined, registrationNo: filters.registration || undefined,
                    vehicleSts: filters.status || undefined, page, size: 10,
                    ...(sort ? {sortBy: (['registrationNo', 'chassisNo', 'vehicleMake', 'manufactureYear', 'owner', 'vehicleCondition', 'vehicleSts'] as const)[sort.column], sortDirection: sort.direction} : {})
                }
            });
            setResult(data);
        } catch (error) {
            setResult(null);
            notify('error', explainError(error));
        } finally {
            setBusy(false);
        }
    }, [filters, page, sort, notify]);
    useEffect(() => {
        void Promise.resolve().then(load);
    }, [load]);
    const start = (record: Vehicle) => {
        setSelected(record);
        setModal('edit');
        setConfirmSave(false);
        const prepared = {
            ownerCustId: String(record.ownerCustId),
            registrationNo: record.registrationNo ?? '',
            registrationDt: record.registrationDt ?? '',
            chassisNo: record.chassisNo,
            engineNo: record.engineNo ?? '',
            vehicleMake: record.vehicleMake,
            vehicleModel: record.vehicleModel,
            vehicleVariant: record.vehicleVariant ?? '',
            manufactureYear: String(record.manufactureYear),
            vehicleType: record.vehicleType,
            vehicleCondition: record.vehicleCondition,
            colour: record.colour ?? '',
            engineCapacityCc: record.engineCapacityCc ? String(record.engineCapacityCc) : ''
        };
        setForm(prepared);
        setOriginalForm(JSON.stringify(prepared));
    };
    const requestSave = (event: FormEvent) => {
        event.preventDefault();
        if (busy) return;
        if (!form.ownerCustId) {
            notify('warning', 'Select a customer.');
            return;
        }
        if (JSON.stringify(form) === originalForm) {
            notify('warning', 'No changes to save.');
            return;
        }
        setConfirmSave(true);
    };
    const save = async () => {
        if (busy || !confirmSave || !selected) return;
        setBusy(true);
        try {
            const payload = {
                ...form,
                ownerCustId: Number(form.ownerCustId),
                manufactureYear: Number(form.manufactureYear),
                engineCapacityCc: form.engineCapacityCc ? Number(form.engineCapacityCc) : null,
                registrationNo: form.registrationNo.trim() || null,
                registrationDt: form.registrationDt || null,
                engineNo: form.engineNo.trim() || null,
                vehicleVariant: form.vehicleVariant.trim() || null,
                colour: form.colour.trim() || null
            };
            await api.put(`/vehicles/${selected.vehicleId}`, payload);
            notify('success', 'Changes saved.');
            setConfirmSave(false);
            setModal(null);
        } catch (error) {
            notify('error', explainError(error));
            setBusy(false);
            return;
        }
        setBusy(false);
        await load();
    };
    const changeStatus = async () => {
        if (!confirm || busy) return;
        setBusy(true);
        try {
            await api.patch(`/vehicles/${confirm.vehicleId}/status`, {vehicleSts: confirm.vehicleSts === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'});
            notify('success', 'Vehicle status updated.');
            setConfirm(null);
        } catch (error) {
            notify('error', explainError(error));
            setBusy(false);
            return;
        }
        setBusy(false);
        await load();
    };
    return <main className="gap-page">
        <ModuleHeader title="Vehicle Management"
                      description="Maintain vehicles registered through Vehicle-type collateral records."
                      actions={
                          <div className="cbms-toolbar-actions">
                              <RefreshButton loading={busy} onClick={() => void load()}/>
                          </div>
                      }/>
        <Panel title="Vehicle Register" count={result?.totalElements}>
            <form className="gap-toolbar" onSubmit={e => {
                e.preventDefault();
                setPage(0);
                setFilters({owner, registration, status});
            }}>
                <CustomerField label="Customer" required={false} value={owner} onChange={setOwner}/>
                <label>Registration Number<input maxLength={30} value={registration}
                                                 onChange={e => setRegistration(e.target.value)}/></label>
                <label>Status<select value={status} onChange={e => setStatus(e.target.value)}>
                    <option value="">All Statuses</option>
                    <option value="ACTIVE">Active</option>
                    <option value="INACTIVE">Inactive</option>
                </select></label>
                <div className="cbms-search-actions">
                    <button type="submit" className="cbms-action-search" disabled={busy}>Search</button>
                    <button type="button" className="cbms-action-clear" onClick={() => {
                        setOwner('');
                        setRegistration('');
                        setStatus('');
                        setPage(0);
                        setFilters({owner: '', registration: '', status: ''});
                    }}>Clear
                    </button>
                </div>
            </form>
            <div className="gap-table-wrap">
                <SortableTable remoteSort={sort} remoteSortColumns={[0, 1, 2, 3, 4, 5, 6]}
                               onRemoteSortChange={next => { setSort(next); setPage(0); }}>
                    <thead>
                    <tr>
                        <th>Registration</th>
                        <th>Chassis</th>
                        <th>Vehicle</th>
                        <th>Year</th>
                        <th>Owner</th>
                        <th>Condition</th>
                        <th>Status</th>
                        <th>Action</th>
                    </tr>
                    </thead>
                    <tbody>
                    {result?.content.map(record => <tr key={record.vehicleId}>
                        <td><PopupView value={record.registrationNo || 'Not Registered'} maxLength={18}/></td>
                        <td><PopupView value={record.chassisNo} maxLength={22}/></td>
                        <td><PopupView
                            value={`${record.vehicleMake} ${record.vehicleModel} ${record.vehicleVariant || ''}`.trim()}
                            maxLength={26}/></td>
                        <td>{record.manufactureYear}</td>
                        <td><PopupView value={record.ownerCustNm || 'Customer unavailable'} maxLength={28}/></td>
                        <td>{formatBusinessValue(record.vehicleCondition)}</td>
                        <td><Badge status={record.vehicleSts}/></td>
                        <td className="gap-actions">{canEdit &&
                            <button type="button" onClick={() => start(record)}>Edit</button>}
                            {isAdmin && <button type="button"
                                                onClick={() => setConfirm(record)}>{record.vehicleSts === 'ACTIVE' ? 'Deactivate' : 'Reactivate'}</button>}</td>
                    </tr>)}{!result?.content.length &&
                        <Empty columns={8} message={busy ? 'Loading…' : 'No vehicles found.'}/>}</tbody>
                </SortableTable>
            </div>
            <Paging page={page} totalPages={result?.totalPages ?? 0} totalElements={result?.totalElements ?? 0}
                    onPage={setPage} busy={busy}/>
        </Panel>
        {modal &&
            <FormDialog title="Edit Vehicle" eyebrow="VEHICLE MANAGEMENT" wide
                        busy={busy} onClose={() => {
                setModal(null);
                setConfirmSave(false);
            }}>
                <form className="gap-form" onSubmit={requestSave}>
                    <div className="gap-grid gap-form-three">
                        <label className="gap-form-wide">Customer<input readOnly value={selected?.ownerCustNm ?? ''}/></label>
                        <label>Registration Number<input maxLength={30} value={form.registrationNo}
                                                         onChange={e => setForm({
                                                             ...form,
                                                             registrationNo: e.target.value
                                                         })}/></label>
                        <label>Registration Date<input type="date" value={form.registrationDt} onChange={e => setForm({
                            ...form,
                            registrationDt: e.target.value
                        })}/></label>
                        <label><FieldLabel required>Chassis Number</FieldLabel><input required maxLength={100}
                                                                                      value={form.chassisNo}
                                                                                      onChange={e => setForm({
                                                                                          ...form,
                                                                                          chassisNo: e.target.value
                                                                                      })}/></label>
                        <label>Engine Number<input maxLength={100} value={form.engineNo} onChange={e => setForm({
                            ...form,
                            engineNo: e.target.value
                        })}/></label>
                        <label><FieldLabel required>Make</FieldLabel><input required maxLength={100}
                                                                            value={form.vehicleMake}
                                                                            onChange={e => setForm({
                                                                                ...form,
                                                                                vehicleMake: e.target.value
                                                                            })}/></label>
                        <label><FieldLabel required>Model</FieldLabel><input required maxLength={100}
                                                                             value={form.vehicleModel}
                                                                             onChange={e => setForm({
                                                                                 ...form,
                                                                                 vehicleModel: e.target.value
                                                                             })}/></label>
                        <label>Variant<input maxLength={100} value={form.vehicleVariant} onChange={e => setForm({
                            ...form,
                            vehicleVariant: e.target.value
                        })}/></label>
                        <label><FieldLabel required>Manufacture Year</FieldLabel><input required type="number"
                                                                                        min="1900" max="2100"
                                                                                        value={form.manufactureYear}
                                                                                        onChange={e => setForm({
                                                                                            ...form,
                                                                                            manufactureYear: e.target.value
                                                                                        })}/></label>
                        <label><FieldLabel required>Vehicle Type</FieldLabel><select value={form.vehicleType}
                                                                                     onChange={e => setForm({
                                                                                         ...form,
                                                                                         vehicleType: e.target.value
                                                                                     })}>{['CAR', 'MOTORCYCLE', 'VAN', 'LORRY', 'BUS', 'OTHER'].map(x =>
                    <option key={x} value={x}>{formatBusinessValue(x)}</option>)}</select></label>
                        <label><FieldLabel required>Condition</FieldLabel><select value={form.vehicleCondition}
                                                                                  onChange={e => setForm({
                                                                                      ...form,
                                                                                      vehicleCondition: e.target.value
                                                                                  })}>{['NEW', 'USED', 'RECOND'].map(x =>
                    <option key={x} value={x}>{formatBusinessValue(x)}</option>)}</select></label>
                        <label>Colour<input maxLength={50} value={form.colour}
                                            onChange={e => setForm({...form, colour: e.target.value})}/></label>
                        <label>Engine Capacity (cc)<input type="number" min="1" value={form.engineCapacityCc}
                                                          onChange={e => setForm({
                                                              ...form,
                                                              engineCapacityCc: e.target.value
                                                          })}/></label>
                    </div>
                    <div className="gap-form-actions">
                        <button type="button" onClick={() => setModal(null)}>Cancel</button>
                        <button type="submit" className="gap-primary"
                                disabled={busy}>Save Changes</button>
                    </div>
                </form>
            </FormDialog>}
        {confirmSave && <ConfirmDialog title="Confirm Changes"
                                       message="Save these vehicle changes?"
                                       confirmLabel="Save Changes" busy={busy}
                                       onCancel={() => setConfirmSave(false)} onConfirm={() => void save()}/>}
        {confirm && <ConfirmDialog title="Change Vehicle Status"
                                   message={`Change the status of this vehicle to ${confirm.vehicleSts === 'ACTIVE' ? 'Inactive' : 'Active'}?`}
                                   confirmLabel="Change Status" onCancel={() => setConfirm(null)}
                                   onConfirm={() => void changeStatus()} busy={busy}/>}
    </main>;
}
import {formatBusinessValue} from '../utils/display';
