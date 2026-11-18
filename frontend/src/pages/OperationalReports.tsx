import {SortableTable} from '../components/SortableTable';
import {FieldLabel, RefreshButton} from '../components/UiStandards';
import {useCallback, useEffect, useState, type FormEvent} from 'react';
import {api} from '../api/client';
import {useToast} from '../components/toastContext';
import {Badge, Empty, ModuleHeader, Paging, Panel} from './PageComponents';
import {CustomerField} from './CustomerField';
import ReferenceCodeSelect from '../components/ReferenceCodeSelect';
import PopupView from '../components/PopupView';
import {dateOnly, explainError, money, readable, titleCase} from './pageUtils';

type ReportType = 'accounts' | 'financing' | 'fixed-deposits' | 'collaterals';
type Report = {
    report: string;
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    records: Array<Record<string, unknown>>
};
const columns: Record<ReportType, string[]> = {
    accounts: ['acct_no', 'acct_typ', 'ccy_cd', 'acct_sts', 'open_dt', 'close_dt'],
    financing: ['fac_ref', 'fac_typ', 'ccy_cd', 'appr_amt', 'fac_sts', 'appr_dt', 'book_dt', 'maturity_dt'],
    'fixed-deposits': ['fd_ref', 'ccy_cd', 'principal_amt', 'interest_rate', 'fd_sts', 'placement_dt', 'maturity_dt'],
    collaterals: ['collateral_ref', 'collateral_typ', 'collateral_desc', 'collateral_sts', 'crt_dt']
};
const empty = {custId: '', status: '', currency: '', fromDate: '', toDate: ''};
const columnLabels: Record<string, string> = {
    acct_no: 'Account Number',
    acct_typ: 'Account Type',
    ccy_cd: 'Currency',
    acct_sts: 'Status',
    open_dt: 'Opened On',
    close_dt: 'Closed On',
    fac_ref: 'Facility Reference',
    fac_typ: 'Facility Type',
    appr_amt: 'Approved Amount',
    fac_sts: 'Status',
    appr_dt: 'Approved On',
    book_dt: 'Booked On',
    maturity_dt: 'Maturity Date',
    fd_ref: 'Fixed Deposit Reference',
    principal_amt: 'Principal Amount',
    interest_rate: 'Interest Rate (%)',
    fd_sts: 'Status',
    placement_dt: 'Placement Date',
    collateral_ref: 'Collateral Reference',
    collateral_typ: 'Collateral Type',
    collateral_desc: 'Description',
    collateral_sts: 'Status',
    crt_dt: 'Created On'
};

function reportCell(key: string, value: unknown) {
    if (key.endsWith('_sts')) return <Badge status={typeof value === 'string' ? value : null}/>;
    if (key.endsWith('_amt') && (typeof value === 'number' || typeof value === 'string')) return money(value);
    if ((key.endsWith('_dt') || key.endsWith('_date')) && typeof value === 'string') return dateOnly(value);
    return <PopupView value={readable(value)} maxLength={24}/>;
}

export default function OperationalReports() {
    const notify = useToast();
    const [type, setType] = useState<ReportType>('accounts');
    const [draft, setDraft] = useState(empty);
    const [filters, setFilters] = useState(empty);
    const [page, setPage] = useState(0);
    const [report, setReport] = useState<Report | null>(null);
    const [busy, setBusy] = useState(false);
    const [exporting, setExporting] = useState(false);
    const load = useCallback(async () => {
        setBusy(true);
        try {
            const {data} = await api.get<Report>(`/reports/${type}`, {
                params: {
                    custId: filters.custId || undefined, status: filters.status || undefined,
                    currency: type === 'collaterals' ? undefined : filters.currency || undefined,
                    fromDate: filters.fromDate || undefined, toDate: filters.toDate || undefined, page, size: 10
                }
            });
            setReport(data);
        } catch (error) {
            setReport(null);
            notify('error', explainError(error));
        } finally {
            setBusy(false);
        }
    }, [type, filters, page, notify]);
    useEffect(() => {
        void Promise.resolve().then(load);
    }, [load]);
    const search = (event: FormEvent) => {
        event.preventDefault();
        if (draft.fromDate && draft.toDate && draft.fromDate > draft.toDate) {
            notify('warning', 'From Date must not exceed To Date.');
            return;
        }
        setPage(0);
        setFilters({
            ...draft,
            status: draft.status.trim().toUpperCase(),
            currency: draft.currency.trim().toUpperCase()
        });
    };
    const clear = () => {
        setDraft(empty);
        setFilters(empty);
        setPage(0);
    };
    const download = async () => {
        if (exporting) return;
        setExporting(true);
        try {
            const {data} = await api.get<Blob>(`/reports/${type}/export`, {
                responseType: 'blob', params: {
                    custId: filters.custId || undefined, status: filters.status || undefined,
                    currency: type === 'collaterals' ? undefined : filters.currency || undefined,
                    fromDate: filters.fromDate || undefined, toDate: filters.toDate || undefined
                }
            });
            const url = URL.createObjectURL(data);
            const a = document.createElement('a');
            a.href = url;
            a.download = `${type}-report.csv`;
            a.click();
            window.setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (error) {
            notify('error', explainError(error));
        } finally {
            setExporting(false);
        }
    };
    return <main className="gap-page">
        <ModuleHeader title="Operational Reports"
                      description="Search account, financing, fixed deposit and collateral reports. Exports follow server limits and access controls."/>
        <Panel title="Report Criteria"
               actions={<RefreshButton onClick={() => void load()} loading={busy}/> }>
            <form className="gap-form" onSubmit={search}>
                <div className="gap-grid">
                    <label><FieldLabel required>Report Type</FieldLabel><select value={type} onChange={e => {
                        setType(e.target.value as ReportType);
                        setDraft(empty);
                        setFilters(empty);
                        setPage(0);
                    }}>
                        <option value="accounts">Accounts</option>
                        <option value="financing">Financing Facilities</option>
                        <option value="fixed-deposits">Fixed Deposits</option>
                        <option value="collaterals">Collaterals</option>
                    </select></label>
                    <CustomerField label="Customer" required={false} value={draft.custId}
                                      onChange={value => setDraft({...draft, custId: value})}/>
                    <label>Status<input maxLength={30} value={draft.status}
                                        onChange={e => setDraft({...draft, status: e.target.value})}
                                        placeholder="e.g. ACTIVE"/></label>
                    {type !== 'collaterals' &&
                        <label>Currency<ReferenceCodeSelect kind="currencies" value={draft.currency}
                                                            onChange={value => setDraft({...draft, currency: value})}/></label>}
                    <label>From Date<input type="date" value={draft.fromDate}
                                           onChange={e => setDraft({...draft, fromDate: e.target.value})}/></label>
                    <label>To Date<input type="date" value={draft.toDate}
                                         onChange={e => setDraft({...draft, toDate: e.target.value})}/></label>
                </div>
                <div className="cbms-search-actions">
                    <button type="submit" className="cbms-action-search" disabled={busy}>Search</button>
                    <button type="button" className="cbms-action-clear" onClick={clear}>Clear</button>
                </div>
            </form>
        </Panel>
        <Panel title={`${type === 'fixed-deposits' ? 'Fixed Deposits' : titleCase(type)} Report`}
               count={report?.totalElements} actions={<button type="button" onClick={() => void download()}
                                                              disabled={exporting || busy || !report?.records.length}>{exporting ? 'Exporting…' : 'Export CSV'}</button>}>
            <div className="gap-table-wrap">
                <SortableTable>
                    <thead>
                    <tr>{columns[type].map(col => <th key={col}>{columnLabels[col] || titleCase(col)}</th>)}</tr>
                    </thead>
                    <tbody>{report?.records.map((record, i) => <tr key={i}>{columns[type].map(key => <td
                        key={key}>{reportCell(key, record[key])}</td>)}</tr>)}
                    {!report?.records.length && <Empty columns={columns[type].length}
                                                       message={busy ? 'Loading…' : 'No matching report records.'}/>}</tbody>
                </SortableTable>
            </div>
            <Paging page={page} totalPages={report?.totalPages ?? 0} totalElements={report?.totalElements ?? 0} onPage={setPage} busy={busy}/>
        </Panel>
    </main>;
}
