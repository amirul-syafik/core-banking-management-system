import {SortableTable} from '../components/SortableTable';
import {useEffect, useState} from 'react';
import {createPortal} from 'react-dom';
import {api} from '../api/client';
import {CloseIcon, CustomerLookupButton, FieldLabel, StatusBadge, TablePagination} from '../components/UiStandards';
import {explainError, type ApiPage} from './pageUtils';
import PopupView from '../components/PopupView';

type VehicleChoice = {
    vehicleId: number; registrationNo: string | null; chassisNo: string;
    vehicleMake: string; vehicleModel: string; vehicleSts: string;
};
const display = (vehicle: VehicleChoice) => `${vehicle.registrationNo || vehicle.chassisNo} · ${vehicle.vehicleMake} ${vehicle.vehicleModel}`;

/** A business-reference LOV; vehicleId is retained for API payloads only. */
export function VehicleField({value, ownerCustId, onChange, required = true}: {
    value: string; ownerCustId: string; onChange: (id: string) => void; required?: boolean
}) {
    const [open, setOpen] = useState(false);
    const [selected, setSelected] = useState<VehicleChoice | null>(null);
    const [query, setQuery] = useState('');
    const [applied, setApplied] = useState('');
    const [page, setPage] = useState(0);
    const [refresh, setRefresh] = useState(0);
    const [data, setData] = useState<ApiPage<VehicleChoice> | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [unavailableId, setUnavailableId] = useState<number | null>(null);
    useEffect(() => {
        if (!value || selected?.vehicleId === Number(value)) return;
        let active = true;
        void api.get<VehicleChoice>(`/vehicles/${Number(value)}`).then(result => {
            if (active) {
                setSelected(result.data);
                setUnavailableId(null);
            }
        }).catch(() => {
            if (active) {
                setSelected(null);
                setUnavailableId(Number(value));
            }
        });
        return () => {
            active = false;
        };
    }, [value, selected?.vehicleId]);
    useEffect(() => {
        if (!open) return;
        const escape = (event: KeyboardEvent) => {
            if (event.key === 'Escape') setOpen(false);
        };
        window.addEventListener('keydown', escape);
        return () => window.removeEventListener('keydown', escape);
    }, [open]);
    useEffect(() => {
        if (!open || !ownerCustId) return;
        let active = true;
        void api.get<ApiPage<VehicleChoice>>('/vehicles', {
            params: {
                ownerCustId: Number(ownerCustId), registrationNo: applied || undefined, page, size: 10
            }
        }).then(result => {
            if (active) {
                setData(result.data);
                setError('');
            }
        }).catch(err => {
            if (active) {
                setData(null);
                setError(explainError(err));
            }
        }).finally(() => {
            if (active) setBusy(false);
        });
        return () => {
            active = false;
        };
    }, [open, ownerCustId, applied, page, refresh]);
    return <>
        <div className="gap-customer-field">
            <label><FieldLabel required={required}>Vehicle</FieldLabel></label>
            <div className="gap-inline cbms-lov-input-row">
                <input readOnly required={required}
                       value={value && selected?.vehicleId === Number(value) ? display(selected) : value ? (unavailableId === Number(value) ? 'Vehicle unavailable' : 'Loading vehicle…') : ''}
                       placeholder={ownerCustId ? 'Select vehicle' : 'Select a customer first'} aria-label="Vehicle"
                       onClick={() => {
                           if (ownerCustId) {
                               setQuery('');
                               setApplied('');
                               setPage(0);
                               setData(null);
                               setError('');
                               setBusy(true);
                               setOpen(true);
                               setRefresh(x => x + 1);
                           }
                       }}/>
                <CustomerLookupButton subject="vehicle" aria-label="Search vehicles" disabled={!ownerCustId}
                                      onClick={() => {
                                          setQuery('');
                                          setApplied('');
                                          setPage(0);
                                          setData(null);
                                          setError('');
                                          setBusy(true);
                                          setOpen(true);
                                          setRefresh(x => x + 1);
                                      }}/>
            </div>
        </div>
        {open && createPortal(<div className="cbms-lov-backdrop" onMouseDown={event => {
            if (event.target === event.currentTarget) setOpen(false);
        }}>
            <section className="cbms-lov-dialog cbms-customer-lov-dialog" role="dialog" aria-modal="true"
                     aria-label="Search Vehicles">
                <div className="cbms-lov-heading"><h2>Search Vehicles</h2>
                    <button type="button" onClick={() => setOpen(false)} aria-label="Close vehicle search"><CloseIcon/>
                    </button>
                </div>
                <form className="cbms-lov-search" onSubmit={event => {
                    event.preventDefault();
                    setPage(0);
                    setBusy(true);
                    setApplied(query.trim());
                    setRefresh(x => x + 1);
                }}>
                    <input autoFocus type="search" maxLength={30} value={query}
                           onChange={event => setQuery(event.target.value)}
                           placeholder="Registration number" aria-label="Search registration number"/>
                    <button className="cbms-action-search" type="submit" disabled={busy}>Search</button>
                </form>
                {error && <p className="cbms-lov-error" role="alert">{error}</p>}
                <div className="cbms-lov-table">
                    <SortableTable>
                        <thead>
                        <tr>
                            <th>Registration / Chassis</th>
                            <th>Make / Model</th>
                            <th>Status</th>
                        </tr>
                        </thead>
                        <tbody>{data?.content.map(vehicle => <tr key={vehicle.vehicleId}
                                                                 className="cbms-lov-selectable-row"
                                                                 tabIndex={0} role="button" onClick={() => {
                            setSelected(vehicle);
                            setUnavailableId(null);
                            onChange(String(vehicle.vehicleId));
                            setOpen(false);
                        }}
                                                                 onKeyDown={event => {
                                                                     if (event.key === 'Enter' || event.key === ' ') {
                                                                         event.preventDefault();
                                                                         setSelected(vehicle);
                                                                         setUnavailableId(null);
                                                                         onChange(String(vehicle.vehicleId));
                                                                         setOpen(false);
                                                                     }
                                                                 }}>
                            <td><PopupView value={vehicle.registrationNo || vehicle.chassisNo}/></td>
                            <td><PopupView value={`${vehicle.vehicleMake} ${vehicle.vehicleModel}`}/></td>
                            <td><StatusBadge status={vehicle.vehicleSts}/></td>
                        </tr>)}{!data?.content.length && <tr>
                            <td colSpan={3}>{busy ? 'Loading vehicles…' : 'No matching vehicles.'}</td>
                        </tr>}</tbody>
                    </SortableTable>
                </div>
                <TablePagination page={page} totalPages={Math.max(1, data?.totalPages ?? 1)}
                                 totalElements={data?.totalElements ?? 0} onPageChange={next => {
                                     setBusy(true);
                                     setPage(next);
                                 }} busy={busy} className="cbms-lov-pagination"/>
            </section>
        </div>, document.body)}
    </>;
}

/** Financing reference choices use the existing application's list endpoint, never manual numeric IDs. */
export function FinancingField({value, ownerCustId, onChange}: {
    value: string;
    ownerCustId: string;
    onChange: (id: string) => void
}) {
    const [choices, setChoices] = useState<Array<{ applId: number; applRef: string; applSts: string }>>([]);
    const [error, setError] = useState('');
    useEffect(() => {
        if (!ownerCustId) return;
        let active = true;
        void api.get<Array<{ applId: number; applRef: string; applSts: string }>>('/financing/applications',
            {params: {custId: Number(ownerCustId)}}).then(result => {
            if (active) {
                setChoices(result.data);
                setError('');
            }
        }).catch(err => {
            if (active) {
                setChoices([]);
                setError(explainError(err));
            }
        });
        return () => {
            active = false;
        };
    }, [ownerCustId]);
    return <label><FieldLabel required>Financing Application</FieldLabel>
        <select required value={value} disabled={!ownerCustId} onChange={event => onChange(event.target.value)}>
            <option value="">{ownerCustId ? 'Select financing application' : 'Select a customer first'}</option>
            {choices.map(item => <option key={item.applId}
                                         value={item.applId}>{item.applRef} · {item.applSts}</option>)}
        </select>
        {error && <span className="cbms-field-validation-error" role="alert">{error}</span>}
    </label>;
}
