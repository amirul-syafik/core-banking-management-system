import {SortableTable, type TableSortState} from '../components/SortableTable';
import {sortTableRecords} from '../utils/tableSorting';
import {
    TablePagination,
    StatusBadge,
    CloseIcon,
    RefreshButton,
    ConfirmDialog,
    FieldLabel,
    CustomerLookupButton,
    RequiredFieldError,
    PageHeader,
    CreateButton,
    BackNavigation,
    SubscreenTitle
} from '../components/UiStandards';
import CustomerLovDialog from '../components/CustomerLovDialog';
import {useCallback, useEffect, useRef, useState, type FormEvent} from 'react';
import {AxiosError} from 'axios';
import {api} from '../api/client';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {
    fixedDeposits,
    fdApprovals,
    type FdApprovalRequest,
    type FixedDeposit,
    type FdAction,
    type FdMaturitySettlement,
    type FdUpliftResult
} from '../api/fixedDeposits';
import {listAccounts, listProducts, listSchemes, type Account, type Product, type Scheme} from '../api/accounts';
import {searchCustomers, getCustomer, type Customer} from '../api/customers';
import PopupView from '../components/PopupView';
import IslamicDepositProfitTerms from '../components/IslamicDepositProfitTerms';
import IslamicDepositExecutionEvidence from '../components/IslamicDepositExecutionEvidence';
import IslamicDepositLedgerMappingReview from '../components/IslamicDepositLedgerMappingReview';
import IslamicDepositPostingReadiness from '../components/IslamicDepositPostingReadiness';
import IslamicDepositExecutionTimeline from '../components/IslamicDepositExecutionTimeline';
import IslamicExecutionPolicy from '../components/IslamicExecutionPolicy';
import {useAuth} from '../auth/useAuth';
import {useToast} from '../components/toastContext';
import SchemeRateSelect, {type RateOptions} from '../components/SchemeRateSelect';
import {TenorSelect, TotalInterestField, SchemeLimitsSummary} from '../components/BankingTermFields';
import './FixedDeposits.css';
import {businessReferenceFromResponse} from '../utils/businessReference';
// Use an ISO local date for date inputs; locale formatting is not consistent across browsers.
const today = () => {
    const date = new Date();
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
};
const fmt = (n: number | string | null | undefined) => n == null ? '—' : Number(n).toLocaleString('en-MY', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
});
const message = (error: unknown) => error instanceof AxiosError ? String(error.response?.data?.detail || error.response?.data?.message || error.response?.data?.error || error.message) : error instanceof Error ? error.message : 'Request failed';
const actions: Record<FdAction, string> = {
    place: 'Fund Placement',
    accrue: 'Accrue Interest',
    payout: 'Pay Interest',
    maturity: 'Process Maturity',
    settle: 'Settle Maturity Principal',
    renew: 'Renew Fixed Deposit',
    uplift: 'Premature Uplift'
};
type Panel = FdAction | 'create' | null;
type IslamicDraftReadiness = {
    schemeId: number; canCreateDraft: boolean; approvedProfileId: number | null;
    contractReference: string | null; contractType: string | null; profitModel: string | null;
    shariahApprovalReference: string | null; message: string;
};
type IslamicDraftSnapshot = {
    fd_id: number; contract_ref: string; contract_typ: string; profit_model: string;
    terms_summary: string; shariah_approval_ref: string; profile_approved_by: string;
    indicative_rate_pct: number | string | null; snapshot_at: string; deposit_status: string;
};
type MonitoringView = 'summary' | 'upcoming-maturities' | 'overdue-maturities' | 'missing-accruals' | 'closures';
const monitoringViews: ReadonlyArray<readonly [MonitoringView, string]> = [['summary', 'Portfolio Summary'], ['upcoming-maturities', 'Upcoming Maturities'], ['overdue-maturities', 'Overdue Maturities'], ['missing-accruals', 'Missing Interest Accruals'], ['closures', 'Closed Fixed Deposits']];

type Datum = Record<string, unknown>;
const internalKey = (key: string) => /^(id|fdId|custId|customerId|productId|schemeId|acctId|accountId|requestId|transactionId|glId|.*_id|.*Id)$/i.test(key);
const labels: Record<string, string> = {
    fdSts: 'Fixed Deposit Status',
    fd_sts: 'Fixed Deposit Status',
    sts: 'Status',
    statusCd: 'Status',
    maturityDt: 'Maturity Date',
    maturity_dt: 'Maturity Date',
    acctDt: 'Account Date',
    crtDt: 'Created On',
    crt_dt: 'Created On',
    updDt: 'Updated On',
    upd_dt: 'Updated On',
    clsrDt: 'Closure Date',
    prodCd: 'Product Code',
    prod_cd: 'Product Code',
    schmCd: 'Scheme Code',
    schm_cd: 'Scheme Code',
    placement_dt: 'Placement Date',
    placementDt: 'Placement Date',
    placement_date: 'Placement Date',
    placementReference: 'Placement Reference',
    placement_ref: 'Placement Reference',
    fdReference: 'Fixed Deposit Reference',
    fd_ref: 'Fixed Deposit Reference',
    fdType: 'Deposit Type',
    currency: 'Currency',
    ccyCd: 'Currency',
    ccy_cd: 'Currency',
    acctNo: 'Account Number',
    acctTyp: 'Account Type',
    custNm: 'Customer Name',
    cifNo: 'Customer Identification Number',
    prodNm: 'Product Name',
    schmNm: 'Scheme Name',
    principalAmount: 'Principal Amount',
    principal_amt: 'Principal Amount',
    tenorMonths: 'Tenor (Months)',
    interestRate: 'Annual Interest Rate (%)',
    interest_rate: 'Annual Interest Rate (%)',
    placementDate: 'Placement Date',
    maturityDate: 'Maturity Date',
    accruedInterest: 'Accrued Interest',
    payoutAmount: 'Interest Payout Amount',
    transactionReference: 'Transaction Reference',
    transactionType: 'Transaction Type',
    request_status: 'Request Status',
    action_type: 'Requested Operation',
    requested_by: 'Requested By',
    requested_at: 'Request Date and Time',
    decided_by: 'Reviewed By',
    decision_reason: 'Review Reason',
    settlementAmount: 'Settlement Amount',
    interestRecovery: 'Interest Recovery Amount',
    fd_id: 'Fixed Deposit Reference'
};
const labelize = (key: string) => labels[key] || key.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/_/g, ' ').replace(/\b(Dt|Sts|Cd|Amt|No|Typ|Cnt|Nm|Ref)\b/gi, m => ({
    dt: 'Date',
    sts: 'Status',
    cd: 'Code',
    amt: 'Amount',
    no: 'Number',
    typ: 'Type',
    cnt: 'Count',
    nm: 'Name',
    ref: 'Reference'
}[m.toLowerCase()] || m)).replace(/\b(fd|gl|myr|ccy|cust|acct|prod|schm)\b/gi, m => ({
    fd: 'Fixed Deposit',
    gl: 'General Ledger',
    myr: 'MYR',
    ccy: 'Currency',
    cust: 'Customer',
    acct: 'Account',
    prod: 'Product',
    schm: 'Scheme'
}[m.toLowerCase()] || m)).replace(/^./, m => m.toUpperCase());
const readable = (value: unknown): string => {
    if (value === null || value === undefined || value === '') return '—';
    if (typeof value === 'boolean') return value ? 'Yes' : 'No';
    if (typeof value === 'object') return Array.isArray(value) ? `${value.length} records` : 'See details';
    if (typeof value === 'number') return value.toLocaleString('en-MY', {maximumFractionDigits: 6});
    return formatBusinessValue(String(value));
};
const record = (value: unknown): value is Datum => typeof value === 'object' && value !== null && !Array.isArray(value);

function PlainReference({value, maxLength = 24}: { value: string; maxLength?: number }) {
    const shown = value.length > maxLength ? `${value.slice(0, maxLength)}…` : value;
    return <span className="fd-plain-reference" title={value} aria-label={value}>{shown}</span>;
}

const isReferenceKey = (key: string) => /ref(erence)?$/i.test(key) || /(_ref|Ref)$/i.test(key);
const recordCount = (count: number) => `(${count} ${count === 1 ? 'record' : 'records'})`;

function InfoGrid({data}: { data: Datum }) {
    return <dl
        className="fd-information">{Object.entries(data).filter(([k, v]) => !internalKey(k) && !record(v) && !Array.isArray(v)).map(([k, v]) =>
        <div key={k}>
            <dt>{labelize(k)}</dt>
            <dd>{/status$/i.test(k) ?
                <span className="fd-plain-status">{readable(v)}</span> : isReferenceKey(k) ?
                    <PlainReference value={readable(v)} maxLength={46}/> :
                    <PopupView value={readable(v)} maxLength={55}/>}</dd>
        </div>)}</dl>
}

function DataView({data, empty = 'No records available', showCount = true, pageSize}: {
    data: unknown;
    empty?: string;
    showCount?: boolean;
    pageSize?: number
}) {
    const [expanded, setExpanded] = useState<Datum | null>(null);
    const [pageIndex, setPageIndex] = useState(0);
    const [sort, setSort] = useState<TableSortState>(null);
    if (data === undefined) return <div className="fd-empty">Loading information…</div>;
    if (data === null) return <div className="fd-empty">{empty}</div>;
    if (Array.isArray(data)) {
        if (!data.length) return <div className="fd-empty">{empty}</div>;
        if (data.every(v => record(v))) {
            const items = data as Datum[];
            const keys = [...new Set(items.flatMap(item => Object.keys(item)))].filter(k => !internalKey(k) && items.some(item => !record(item[k]) && !Array.isArray(item[k])));
            const primary = keys.slice(0, 4);
            const effectivePageSize = pageSize && pageSize > 0 ? pageSize : 10;
            const totalPages = Math.max(1, Math.ceil(items.length / effectivePageSize));
            const currentPage = Math.min(pageIndex, totalPages - 1);
            const sortedItems = sortTableRecords(items, sort, primary.map(key => (item: Datum) => item[key]));
            const visibleItems = sortedItems.slice(currentPage * effectivePageSize, (currentPage + 1) * effectivePageSize);
            return <>{showCount && <p className="fd-history-count">{recordCount(items.length)}</p>}
                <div className="fd-table-wrap">
                    <SortableTable className="fd-table" sortState={sort} onSortChange={next => { setSort(next); setPageIndex(0); }}>
                        <thead>
                        <tr>{primary.map(k => <th key={k}>{labelize(k)}</th>)}
                            <th>Action</th>
                        </tr>
                        </thead>
                        <tbody>{visibleItems.map((item, index) => <tr
                            key={currentPage * effectivePageSize + index}>{primary.map(k => <td
                            key={k}>{/status$/i.test(k) ?
                            <StatusBadge status={readable(item[k])}/> : isReferenceKey(k) ?
                                <PopupView value={readable(item[k])} maxLength={24} truncate={false}/> :
                                <PopupView value={readable(item[k])} maxLength={26}/>}</td>)}
                            <td>
                                <button type="button" className="fd-detail-button cbms-action-details"
                                        onClick={() => setExpanded(item)}>View Details
                                </button>
                            </td>
                        </tr>)}</tbody>
                    </SortableTable>
                </div>
                <TablePagination page={currentPage} totalPages={totalPages} totalElements={items.length}
                                 onPageChange={next => { setExpanded(null); setPageIndex(next); }}
                                 className="fd-pager fd-history-pager"/>{expanded && <div className="fd-overlay" onMouseDown={e => {
                    if (e.target === e.currentTarget) setExpanded(null)
                }}>
                    <div className="fd-modal fd-record-modal" role="dialog" aria-modal="true"
                         aria-label="Record details">
                        <div className="fd-modal-heading"><h2>Record Details</h2>
                            <button type="button" className="fd-close" onClick={() => setExpanded(null)}
                                    aria-label="Close record details"><CloseIcon/></button>
                        </div>
                        <div className="fd-modal-content"><InfoGrid
                            data={expanded}/>{Object.entries(expanded).filter(([, v]) => record(v) || Array.isArray(v)).map(([k, v]) =>
                            <section key={k}><h3>{labelize(k)}</h3><DataView data={v}/></section>)}</div>
                        <div className="fd-modal-footer">
                            <button type="button" onClick={() => setExpanded(null)}>Close</button>
                        </div>
                    </div>
                </div>}</>;
        }
        return <div className="fd-list">{data.map((item, index) => <div key={index}>{record(item) ?
            <InfoGrid data={item}/> : readable(item)}</div>)}</div>;
    }
    if (record(data)) {
        const simple = Object.fromEntries(Object.entries(data).filter(([k, v]) => !internalKey(k) && !record(v) && !Array.isArray(v)));
        const nested = Object.entries(data).filter(([, v]) => record(v) || Array.isArray(v));
        return <div className="fd-data-sections">{Object.keys(simple).length > 0 &&
            <InfoGrid data={simple}/>}{nested.map(([key, value]) => <section key={key} className="fd-nested">
                <h4>{labelize(key)}</h4><DataView data={value}/></section>)}</div>;
        }
            return <p>{readable(data)}</p>;
            }

            export default function FixedDeposits(){
                const {session}=useAuth();const notify=useToast();
                const navigate=useNavigate();const [searchParams,setSearchParams]=useSearchParams();const fdFromUrl=searchParams.get('fd');
                const linkedApprovalId=searchParams.get('requestId');
                const requestedIslamicTab=searchParams.get('tab');
                const legacyLedgerReview=searchParams.get('review')==='ledger';
                const canOperate=Boolean(session?.roles.some(r=>r==='MAKER'||r==='ADMIN'));const admin=Boolean(session?.roles.includes('ADMIN'));
                const [approvalDetail,setApprovalDetail]=useState<FdApprovalRequest|null>(null);
                const [checkerUsers,setCheckerUsers]=useState<{username:string;enabled:boolean;roles:string[]}[]>([]);
                const [checkerChoice,setCheckerChoice]=useState('');
                const [checkerError,setCheckerError]=useState('');
                const [assigningChecker,setAssigningChecker]=useState(false);
                const [approvalRows,setApprovalRows]=useState<FdApprovalRequest[]>([]);const [approvalError,setApprovalError]=useState('');const [approvalFilter,setApprovalFilter]=useState('PENDING');const [approvalPage,setApprovalPage]=useState(0);const [approvalSort,setApprovalSort]=useState<TableSortState>(null);const [rows,setRows]=useState<FixedDeposit[]>([]);const [selected,setSelected]=useState<FixedDeposit|null>(null);
                const [loading,setLoading]=useState(false);const [busy,setBusy]=useState(false);const [panel,setPanel]=useState<Panel>(null);
                const [panelAttempted, setPanelAttempted] = useState(false);const [filter,setFilter]=useState('');const [status,setStatus]=useState('ALL');const [appliedFilter,setAppliedFilter]=useState('');const [appliedStatus,setAppliedStatus]=useState('ALL');const [error,setError]=useState('');
                const [fields,setFields]=useState<Record<string,string>>({date:today(),fdType:'FIXED_DEPOSIT',tenor:'',rate:'',remarks:''});
                const [customerNames,setCustomerNames]=useState<Record<number,string>>({});
                const [products,setProducts]=useState<Product[]>([]);
                const [schemes,setSchemes]=useState<Scheme[]>([]);
                const [customerSearch,setCustomerSearch]=useState('');
                const [customerMatches,setCustomerMatches]=useState<Customer[]>([]);
                const [chosenCustomer,setChosenCustomer]=useState<Customer|null>(null);
                const [lookupBusy,setLookupBusy]=useState(false);
                const [lookupError,setLookupError]=useState('');
                const [customerLovOpen,setCustomerLovOpen]=useState(false);
                const [customerLovPage,setCustomerLovPage]=useState(0);
                const [customerLovTotalPages,setCustomerLovTotalPages]=useState(1);
                const [customerLovTotalElements,setCustomerLovTotalElements]=useState(0);
                const [detailsOpen,setDetailsOpen]=useState(Boolean(fdFromUrl));
                const [detailsBusy,setDetailsBusy]=useState(false);
                const [view,setView]=useState<'deposits'|'approvals'|'monitoring'>(()=>searchParams.get('view')==='approvals'?'approvals':'deposits');
                const [monitoringView,setMonitoringView]=useState<MonitoringView>('summary');
                const [monitoringResults,setMonitoringResults]=useState<Partial<Record<MonitoringView,unknown>>>({});
                const [monitoringFailures,setMonitoringFailures]=useState<Partial<Record<MonitoringView,string>>>({});
                const [page,setPage]=useState(0);const [depositSort,setDepositSort]=useState<TableSortState>(null);
                const [history,setHistory]=useState<Record<string,unknown>>({});const [tab,setTab]=useState('overview');
                const [contractSection,setContractSection]=useState<'contract'|'profit-terms'|'execution-evidence'|'execution-timeline'|'execution-policy'|'ledger-mapping'|'posting-readiness'>('contract');
                const [settlementAccounts,setSettlementAccounts]=useState<Account[]>([]);
                const [operationAccounts,setOperationAccounts]=useState<Account[]>([]);
                const [operationAccountsLoading,setOperationAccountsLoading]=useState(false);
                const [operationAccountsError,setOperationAccountsError]=useState('');
                const [settlementLoading,setSettlementLoading]=useState(false);
                const [settlementError,setSettlementError]=useState('');
                const [settlementTotals,setSettlementTotals]=useState<{accrued:number;paid:number}|null>(null);
                const [settlementResult,setSettlementResult]=useState<FdMaturitySettlement|null>(null);
                const [upliftResult,setUpliftResult]=useState<FdUpliftResult|null>(null);
                const [confirmSubmission, setConfirmSubmission] = useState(false);
                const [pendingApprovalDecision, setPendingApprovalDecision] = useState<{request: FdApprovalRequest; approve: boolean} | null>(null);
                const [approvalLoading,setApprovalLoading]=useState(false);
                const [approvalDecisionReason, setApprovalDecisionReason] = useState('');
                useEffect(()=>{let active=true;listProducts().then(data=>{if(active)setProducts(data.filter(p=>['FIXED_DEPOSIT','TERM_DEPOSIT'].includes(p.prodCat)&&p.prodSts==='ACTIVE'))}).catch(err=>{if(active)setLookupError(message(err))});return()=>{active=false}},[]);
                useEffect(()=>{if(!fields.product)return;let active=true;listSchemes(Number(fields.product)).then(data=>{if(active)setSchemes(data.filter(s=>s.schmSts==='ACTIVE'))}).catch(err=>{if(active)setLookupError(message(err))});return()=>{active=false}},[fields.product]);
                useEffect(()=>{let active=true;const ids=[...new Set(rows.map(r=>r.customerId))];Promise.all(ids.map(async id=>{try{const c=await getCustomer(id);return [id,`${c.cifNo} · ${c.custNm}`] as const}catch{return [id,'Customer record unavailable'] as const}})).then(items=>{if(active)setCustomerNames(Object.fromEntries(items))});return()=>{active=false}},[rows]);
                useEffect(()=>{
                if(view!=='monitoring')return;
                let active=true;
                monitoringViews.forEach(([key])=>{
                const request=key==='summary'?fixedDeposits.summary():fixedDeposits.monitoring(key);
                void request.then(data=>{if(active)setMonitoringResults(prev=>({...prev,[key]:data}))})
                .catch(err=>{if(active){setMonitoringFailures(prev=>({...prev,[key]:message(err)}));setMonitoringResults(prev=>({...prev,[key]:null}))}});
            });
                return ()=>{active=false};
            },[view]);
                useEffect(()=>{
                if(searchParams.get('view')==='approvals')void Promise.resolve().then(()=>setView('approvals'));
            },[searchParams]);
                useEffect(()=>{
                if(!linkedApprovalId||!/^\d+$/.test(linkedApprovalId))return;
                let active=true;
                void fdApprovals.get(Number(linkedApprovalId)).then(request=>{
                if(!active)return;
                setView('approvals');setApprovalFilter('ALL');setCheckerChoice(request.assigned_checker||'');setApprovalDetail(request);
                setApprovalError('');
                setSearchParams(previous=>{const next=new URLSearchParams(previous);next.delete('requestId');return next;},{replace:true});
            }).catch(e=>{if(active)setApprovalError(message(e))});
                return()=>{active=false};
            },[linkedApprovalId,setSearchParams]);
                const activeRequestId=approvalDetail?.request_id;
                const activeRequestStatus=approvalDetail?.request_status;
                useEffect(()=>{
                if(!admin||!activeRequestId||activeRequestStatus!=='PENDING')return;
                let active=true;
                void api.get<{username:string;enabled:boolean;roles:string[]}[]>('/admin/users')
                .then(({data})=>{if(active){setCheckerUsers(data.filter(u=>u.enabled&&u.roles.includes('ADMIN')));setCheckerError('')}})
                .catch(err=>{if(active)setCheckerError(message(err))});
                return()=>{active=false};
            },[admin,activeRequestId,activeRequestStatus]);
                const assignChecker=async()=>{
                if(!approvalDetail||assigningChecker)return;
                if(!checkerChoice){setCheckerError('Please select an administrator.');return;}
                setAssigningChecker(true);setCheckerError('');
                try{
                await fdApprovals.assign(approvalDetail.request_id,checkerChoice);
                const latest=await fdApprovals.list();setApprovalRows(latest);
                setApprovalDetail(latest.find(r=>r.request_id===approvalDetail.request_id)||null);
                notify('success','The checker has been assigned and notified.');
            }catch(err){setCheckerError(message(err));notify('error',message(err))}
                finally{setAssigningChecker(false)}
            };
                const monitoringData=monitoringResults[monitoringView];
                const monitoringError=monitoringFailures[monitoringView]||'';
                const findCustomers=async(nextPage=0, name=customerSearch)=>{
                setLookupBusy(true);setLookupError('');
                try{
                const result=await searchCustomers({name:name.trim(),status:'ACTIVE',page:nextPage,size:10});
                setCustomerMatches(result.content.filter(c=>c.sts==='ACTIVE'));
                setCustomerLovPage(nextPage);setCustomerLovTotalPages(Math.max(1,result.totalPages));
                setCustomerLovTotalElements(result.totalElements);
            }catch(err){setLookupError(message(err))}finally{setLookupBusy(false)}
            };
                const openCustomerLov=()=>{setCustomerSearch('');setCustomerMatches([]);setCustomerLovPage(0);setCustomerLovTotalPages(1);setLookupError('');setCustomerLovOpen(true);void findCustomers(0,'');};
                const chooseCustomer=(c:Customer)=>{if(c.sts!=='ACTIVE'){setLookupError('Only active customers can open a new Fixed Deposit.');return}setChosenCustomer(c);setFields(prev=>({...prev,customer:String(c.custId)}));setCustomerMatches([]);setCustomerSearch('');setCustomerLovOpen(false)};
                const closePanel=()=>{setPanelAttempted(false);
                if(busy)return;
                setPanel(null);setSchemeRatePolicy(null);setFields({date:today(),fdType:'FIXED_DEPOSIT',tenor:'',rate:'',remarks:''});
                setChosenCustomer(null);setCustomerMatches([]);setCustomerSearch('');setCustomerLovOpen(false);
                setLookupError('');setSettlementError('');setSettlementAccounts([]);
                setOperationAccountsError('');setOperationAccounts([]);setSettlementTotals(null);setConfirmSubmission(false);
            };
                const inspectionSequence=useRef(0);
                const closeDetails=()=>{inspectionSequence.current+=1;setDetailsOpen(false);setSelected(null);setHistory({});setPanel(null);navigate('/fixed-deposits')};
                const loadApprovals=useCallback(async()=>{if(approvalLoading)return;setApprovalLoading(true);try{setApprovalRows(await fdApprovals.list());setApprovalError('')}catch(err){setApprovalError(message(err))}finally{setApprovalLoading(false)}},[approvalLoading]);
                const reload=useCallback(async()=>{setLoading(true);try{const data=await fixedDeposits.list();setRows(data);setSelected(old=>old ? data.find(r=>r.fdId===old.fdId)??null:null);setError('')}catch(e){setError(message(e))}finally{setLoading(false)}},[]);
                useEffect(()=>{let mounted=true;fdApprovals.list().then(data=>{if(mounted)setApprovalRows(data)}).catch(err=>{if(mounted)setApprovalError(message(err))});return()=>{mounted=false}},[]);
                useEffect(()=>{let cancelled=false;fixedDeposits.list().then(data=>{if(!cancelled){setRows(data);setError('')}}).catch(e=>{if(!cancelled)setError(message(e))});return()=>{cancelled=true}},[]);
                const open=(p:Panel)=>{setConfirmSubmission(false);setSchemeRatePolicy(null);setFields({date:today(),fdType:'FIXED_DEPOSIT',tenor:p==='create'?'':selected?.tenorMonths?.toString()||'',rate:'',product:p==='renew'?String(selected?.productId||''):'',scheme:selected?.schemeId?.toString()||'',remarks:''});if(p==='create'){setChosenCustomer(null);setCustomerMatches([]);setCustomerLovOpen(false);setLookupError('')}setPanel(p)};
                const inspect=useCallback(async(row:FixedDeposit)=>{const requestSequence=++inspectionSequence.current;setDetailsOpen(true);setDetailsBusy(true);setSettlementResult(null);setSelected(row);setHistory({});
                const targetSection=legacyLedgerReview && requestedIslamicTab==='execution-evidence'?'ledger-mapping':requestedIslamicTab;
                setTab(current=>['contract','islamic-contract','profit-terms','execution-evidence','execution-timeline','execution-policy','ledger-mapping','posting-readiness'].includes(targetSection||'')?'islamic-contract':current==='overview'?'overview':current);
                if(targetSection==='profit-terms'||targetSection==='execution-evidence'||targetSection==='execution-timeline'||targetSection==='execution-policy'||targetSection==='ledger-mapping'||targetSection==='posting-readiness')setContractSection(targetSection);
                else if(targetSection==='contract'||targetSection==='islamic-contract')setContractSection('contract');const calls=[['placement',fixedDeposits.placement],['accruals',fixedDeposits.accruals],['payouts',fixedDeposits.payouts],['maturity',fixedDeposits.validate],['preview',fixedDeposits.preview]] as const;const values=await Promise.allSettled(calls.map(([,fn])=>fn(row.fdId)));if(requestSequence!==inspectionSequence.current)return;const data:Record<string,unknown>={};calls.forEach(([name],i)=>{const r=values[i];data[name]=r.status==='fulfilled'?r.value:{notice:'Not available for this deposit'}});setHistory(data);setDetailsBusy(false)},[requestedIslamicTab,legacyLedgerReview]);
                useEffect(()=>{if(!fdFromUrl||!/^\d+$/.test(fdFromUrl))return;let live=true;fixedDeposits.detail(Number(fdFromUrl)).then(row=>{if(live)void inspect(row)}).catch(err=>{if(live){setError(message(err));setDetailsOpen(false)}});return()=>{live=false};/* URL is the detail-screen identity; loading is intentional on navigation. */},[fdFromUrl, inspect]);
                const openAccountAction=async(action:'place'|'payout'|'uplift')=>{
                if(!selected||!canOperate)return;
                const customerId=selected.customerId;
                open(action);setOperationAccounts([]);setOperationAccountsError('');setOperationAccountsLoading(true);
                try{
                const accounts=await listAccounts(customerId);
                const eligible=accounts.filter(a=>a.custId===customerId && a.acctSts==='ACTIVE' && a.ccyCd==='MYR' && (a.acctTyp==='SAVINGS'||a.acctTyp==='CURRENT'));
                setOperationAccounts(eligible);
                if(eligible.length===1)setFields(prev=>({...prev,account:String(eligible[0].acctId)}));
            }catch{setOperationAccountsError('Unable to load customer accounts. Close and retry this action.');}
                finally{setOperationAccountsLoading(false)}
            };
                const openSettlement=async()=>{
                if(!selected||selected.status!=='MATURED'||!canOperate)return;
                setSettlementError('');setSettlementTotals(null);setSettlementAccounts([]);
                open('settle');setSettlementLoading(true);
                const [accountsResult,accrualResult,payoutResult]=await Promise.allSettled([
                listAccounts(selected.customerId),fixedDeposits.accruals(selected.fdId),fixedDeposits.payouts(selected.fdId)
                ]);
                if(accountsResult.status==='fulfilled'){
                const eligible=accountsResult.value.filter(a=>a.custId===selected.customerId && a.acctSts==='ACTIVE' && a.ccyCd==='MYR' && (a.acctTyp==='SAVINGS'||a.acctTyp==='CURRENT'));
                setSettlementAccounts(eligible);
                if(eligible.length===1)setFields(prev=>({...prev,account:String(eligible[0].acctId)}));
            }else setSettlementError('Unable to retrieve eligible customer accounts. Retry before settling.');
                // Amounts are read-only guidance. The server independently validates fully paid interest.
                if(accrualResult.status==='fulfilled'&&payoutResult.status==='fulfilled' && Array.isArray(accrualResult.value)&&Array.isArray(payoutResult.value)){
                const accruals=accrualResult.value as Array<{accruedInterest?:number|string;status?:string}>;
                const payouts=payoutResult.value as Array<{payoutAmount?:number|string;status?:string}>;
                const accrued=accruals.filter(a=>a.status==='POSTED').reduce((sum,a)=>sum+Number(a.accruedInterest||0),0);
                const paid=payouts.filter(p=>p.status==='POSTED').reduce((sum,p)=>sum+Number(p.payoutAmount||0),0);
                if(Number.isFinite(accrued)&&Number.isFinite(paid))setSettlementTotals({accrued,paid});
            }
                setSettlementLoading(false);
            };
                const update=(name:string,value:string)=>setFields(prev=>({...prev,[name]:value}));
                const performSubmit=async()=>{if(!panel||busy||(panel==='create'&&!validCreate))return;const title=panel==='create'?(isIslamicDeposit?'Create Islamic Deposit Draft':'Create Deposit'):actions[panel];if(panel!=='create'&&!selected)return;if(panel==='settle'&&(settlementLoading||settlementError||!settlementAccounts.some(a=>a.acctId===Number(fields.account))))return;if(['place','payout','uplift'].includes(panel)&&(operationAccountsLoading||operationAccountsError||!operationAccounts.some(a=>a.acctId===Number(fields.account))))return;
                setConfirmSubmission(false);setBusy(true);let completedSettlement:FdMaturitySettlement|null=null;let completedUplift:FdUpliftResult|null=null;let completedActionReference:string|null=null;let completedDepositReference:string|null=null;try{
                if(panel==='create'){const created=await fixedDeposits.create({custId:Number(fields.customer),prodId:Number(fields.product),schmId:Number(fields.scheme),fdType:fields.fdType,principalAmount:Number(fields.amount),tenorMonths:Number(fields.tenor),interestRate:Number(fields.rate),placementDate:fields.date,remarks:fields.remarks||''});completedDepositReference=created.fdReference;}
                else {const result=await fixedDeposits.act(selected!.fdId,panel,fields);completedActionReference=businessReferenceFromResponse(result);if(panel==='settle'&&'principalRepaid' in (result as object))completedSettlement=result as FdMaturitySettlement;if(panel==='uplift'&&'interestRecovery' in (result as object))completedUplift=result as FdUpliftResult;}
                notify('success',panel==='create' && isIslamicDeposit?`Islamic deposit draft${completedDepositReference?` ${completedDepositReference}`:''} created. Placement and profit posting remain unavailable.`:`${title} ${['settle','renew','uplift'].includes(panel)?'submitted for approval':'completed'}${completedDepositReference?` · ${completedDepositReference}`:''}${completedActionReference?` · ${completedActionReference}`:''}`);setPanel(null);await reload();await loadApprovals();if(selected&&panel!=='create'){const fresh=await fixedDeposits.detail(selected.fdId);await inspect(fresh)}
                if(completedSettlement)setSettlementResult(completedSettlement);
                if(completedUplift)setUpliftResult(completedUplift);
            }catch(err){notify('error',message(err))}finally{setBusy(false)}};

                const submit=(e:FormEvent)=>{e.preventDefault();
                setPanelAttempted(true);
                if(!panel||busy)return;
                if(!validPanel){notify('warning','Please complete the required fields and select a valid scheme rate before continuing.');return;}
                setConfirmSubmission(true);
            };

                const decideApproval=async(r:FdApprovalRequest, approve:boolean, reason:string)=>{
                if(!admin||busy||(!approve&&!reason.trim()))return;
                setBusy(true);
                try{const decisionResult=await fdApprovals.decide(r.request_id,approve,reason.trim());setPendingApprovalDecision(null);setApprovalDecisionReason('');const reference=businessReferenceFromResponse(decisionResult);notify('success',`Fixed Deposit request ${approve?'approved':'rejected'}${reference?` · ${reference}`:''}`);await loadApprovals();await reload();if(selected?.fdId===r.fd_id){const fresh=await fixedDeposits.detail(r.fd_id);await inspect(fresh)}}
                catch(err){notify('error',message(err))}finally{setBusy(false)}
            };
                const filteredApprovals=approvalRows.filter(r=>approvalFilter==='ALL'||r.request_status===approvalFilter);
                const approvalTotalPages=Math.max(1,Math.ceil(filteredApprovals.length/10));
                const currentApprovalPage=Math.min(approvalPage,approvalTotalPages-1);
                const sortedApprovals=sortTableRecords(filteredApprovals,approvalSort,[r=>rows.find(fd=>fd.fdId===r.fd_id)?.fdReference,r=>r.action_type,r=>r.request_status,r=>r.requested_by]);
                const visibleApprovals=sortedApprovals.slice(currentApprovalPage*10,(currentApprovalPage+1)*10);
                const filtered=rows.filter(r=>(appliedStatus==='ALL'||r.status===appliedStatus)&&`${r.fdReference} ${customerNames[r.customerId]||''}`.toLowerCase().includes(appliedFilter.toLowerCase()));
                const field=(key:string,label:string,type='text',required=true)=><label className="fd-field"><FieldLabel required={required}>{label}
        </FieldLabel>
        <input type={type} required={required} min={type === 'number' ? '0' : undefined}
               step={key === 'amount' ? '0.01' : undefined} value={fields[key] || ''}
               onChange={e => update(key, e.target.value)}/>
    </label>
        ;
        const sortedFiltered=sortTableRecords(filtered,depositSort,[r=>r.fdReference,r=>customerNames[r.customerId]||'',r=>r.principalAmount,r=>r.maturityDate,r=>r.status]);
        const displayed = sortedFiltered.slice(page * 10, (page + 1) * 10);
        const totalPages = Math.max(1, Math.ceil(filtered.length / 10));
        const [schemeRatePolicy, setSchemeRatePolicy] = useState<RateOptions | null>(null);
        const currentScheme = schemes.find(s => s.schmId === Number(fields.scheme));
        const activeProduct = products.find(p => p.prodId === Number(panel === 'create' ? fields.product : selected?.productId));
        const isIslamicDeposit = activeProduct?.bankingTyp === 'ISLAMIC';
        const selectedIsIslamic = products.find(p => p.prodId === selected?.productId)?.bankingTyp === 'ISLAMIC';
        const [islamicReadiness, setIslamicReadiness] = useState<IslamicDraftReadiness | null>(null);
        const [islamicReadinessError, setIslamicReadinessError] = useState('');
        const [islamicSnapshot, setIslamicSnapshot] = useState<IslamicDraftSnapshot | null>(null);
        const [islamicSnapshotMessage, setIslamicSnapshotMessage] = useState('');
        useEffect(() => {
            if (panel !== 'create' || !isIslamicDeposit || !fields.scheme) return;
            let active = true;
            const schemeId = Number(fields.scheme);
            void api.get<IslamicDraftReadiness>(`/islamic/deposits/schemes/${schemeId}/readiness`)
                .then(({data}) => {
                    if (active) {
                        setIslamicReadiness(data);
                        setIslamicReadinessError('');
                    }
                })
                .catch((error: unknown) => {
                    if (active) {
                        setIslamicReadiness(null);
                        setIslamicReadinessError(message(error));
                    }
                });
            return () => {
                active = false;
            };
        }, [panel, isIslamicDeposit, fields.scheme]);
        useEffect(() => {
            if (!detailsOpen || !selectedIsIslamic || !selected?.fdId) return;
            let active = true;
            const fdId = selected.fdId;
            void api.get<IslamicDraftSnapshot>(`/islamic/deposits/${fdId}/contract`)
                .then(({data}) => {
                    if (active) {
                        setIslamicSnapshot(data);
                        setIslamicSnapshotMessage('');
                    }
                })
                .catch((error: unknown) => {
                    if (active) {
                        setIslamicSnapshot(null);
                        setIslamicSnapshotMessage(message(error));
                    }
                });
            return () => {
                active = false;
            };
        }, [detailsOpen, selectedIsIslamic, selected?.fdId]);
        const depositName = activeProduct?.prodCat === 'TERM_DEPOSIT'
            ? (isIslamicDeposit ? 'Term Deposit-i' : 'Term Deposit')
            : (isIslamicDeposit ? 'Fixed Deposit-i' : 'Fixed Deposit');
        const rateCaption = isIslamicDeposit ? 'Indicative Annual Profit Rate (%)' :
            schemeRatePolicy?.rateType === 'FLOATING' ? 'Indicative Annual Interest Rate (%)' : 'Annual Interest Rate (%)';
        const renewalRateCaption = isIslamicDeposit ? 'Renewal Profit Rate (%)' :
            schemeRatePolicy?.rateType === 'FLOATING' ? 'Indicative Renewal Interest Rate (%)' : 'Renewal Interest Rate (%)';
        const totalCaption = isIslamicDeposit ? 'Total Profit (MYR)' : 'Total Interest (MYR)';
        const policyDate = panel === 'renew' ? today() : fields.date;
        const numericRate = Number(fields.rate);
        const selectedRateValid = Boolean(fields.rate?.trim()) && Number.isFinite(numericRate) &&
            numericRate >= 0 && numericRate <= 100;
        const validSchemeRate = Boolean(schemeRatePolicy && currentScheme &&
            schemeRatePolicy.schmId === currentScheme.schmId && schemeRatePolicy.effectiveDate === policyDate &&
            (schemeRatePolicy.rateType === 'FIXED' || (schemeRatePolicy.rateType === 'FLOATING' && !isIslamicDeposit)) &&
            selectedRateValid &&
            numericRate >= Number(schemeRatePolicy.minimumRatePct) &&
            numericRate <= Number(schemeRatePolicy.maximumRatePct) &&
            (schemeRatePolicy.rateType !== 'FLOATING' || numericRate === Number(schemeRatePolicy.defaultRatePct)));
        // For Islamic DRAFT preparation, the selected profit rate is indicative only.
        // SchemeRateSelect restricts the UI choices; the backend validates the active rate again on create.
        // Do not block an approved-contract draft on a stale rate-policy response in the browser.
        const validCreateRate = isIslamicDeposit ? selectedRateValid : validSchemeRate;
        const positiveAmount = Boolean(fields.amount?.trim()) && Number.isFinite(Number(fields.amount)) && Number(fields.amount) > 0;
        const hasApprovedIslamicContract = islamicReadiness !== null && currentScheme !== undefined &&
            islamicReadiness.schemeId === currentScheme.schmId && islamicReadiness.canCreateDraft;
        const validCreate = Boolean(chosenCustomer && fields.product && currentScheme && fields.scheme &&
            fields.date && fields.date === today() && validCreateRate && fields.tenor && Number.isInteger(Number(fields.tenor)) &&
            Number(fields.tenor) >= (currentScheme?.minTenor ?? 1) && Number(fields.tenor) <= (currentScheme?.maxTenor ?? 600) &&
            positiveAmount && Number(fields.amount) >= (currentScheme?.minAmt ?? 0.01) &&
            Number(fields.amount) <= (currentScheme?.maxAmt ?? Infinity) &&
            (!isIslamicDeposit || hasApprovedIslamicContract));
        const validPanel = panel === 'create' ? validCreate
            : panel === 'renew' ? Boolean(currentScheme && fields.scheme && fields.tenor && validSchemeRate && fields.remarks?.trim() &&
                    Number(fields.tenor) >= (currentScheme?.minTenor ?? 1) && Number(fields.tenor) <= (currentScheme?.maxTenor ?? 600))
                : panel === 'settle' ? Boolean(fields.remarks?.trim() && !settlementLoading && !settlementError &&
                        settlementAccounts.some(a => a.acctId === Number(fields.account)))
                    : panel === 'place' ? Boolean(!operationAccountsLoading && !operationAccountsError &&
                            operationAccounts.some(a => a.acctId === Number(fields.account)))
                        : panel === 'payout' ? Boolean(!operationAccountsLoading && !operationAccountsError &&
                                operationAccounts.some(a => a.acctId === Number(fields.account)))
                            : panel === 'uplift' ? Boolean(fields.remarks?.trim() && !operationAccountsLoading && !operationAccountsError &&
                                    operationAccounts.some(a => a.acctId === Number(fields.account)))
                                : panel === 'accrue' ? Boolean(fields.date)
                                    : Boolean(panel);
        const actionButtons = selected && canOperate && !selectedIsIslamic ?
            <div className="fd-actions">{selected.status === 'DRAFT' &&
                <button onClick={() => void openAccountAction('place')}>Fund
                    Placement</button>}{selected.status === 'ACTIVE' && <>
                <button
                    onClick={() => open('accrue')}>{selectedIsIslamic ? 'Accrue Profit' : 'Accrue Interest'}</button>
                <button
                    onClick={() => void openAccountAction('payout')}>{selectedIsIslamic ? 'Pay Profit' : 'Pay Interest'}</button>
                <button onClick={() => open('maturity')}>Process Maturity</button>
                <button onClick={() => void openAccountAction('uplift')}>Request Premature Uplift</button>
            </>}{selected.status === 'MATURED' && <>
                <button
                    onClick={() => void openAccountAction('payout')}>{selectedIsIslamic ? 'Pay Outstanding Profit' : 'Pay Outstanding Interest'}</button>
                <button onClick={() => open('renew')}>Request Renewal</button>
                <button className="fd-primary" onClick={() => void openSettlement()}>Request Settlement</button>
            </>}</div> : null;
        return <div className="fd-page">
            {!detailsOpen && <><PageHeader title="Fixed Deposits"
                                           description="Deposit opening, account servicing, interest and maturity management"
                                           actions={<div className="cbms-toolbar-actions"><RefreshButton
                                               onClick={() => {
                                                   void reload();
                                                   void loadApprovals()
                                               }} loading={loading || approvalLoading}/>{canOperate &&
                                               <CreateButton onClick={() => open('create')}>New Fixed
                                                   Deposit</CreateButton>}</div>}/>
                <div className="fd-section-tabs" role="tablist" aria-label="Fixed Deposit workspaces">
                    <button role="tab" aria-selected={view === 'deposits'} onClick={() => setView('deposits')}>Fixed
                        Deposit Accounts
                    </button>
                    <button role="tab" aria-selected={view === 'approvals'}
                            onClick={() => setView('approvals')}>Approval Requests
                        ({approvalRows.filter(r => r.request_status === 'PENDING').length})
                    </button>
                    <button role="tab" aria-selected={view === 'monitoring'} onClick={() => setView('monitoring')}>Fixed
                        Deposit Monitoring
                    </button>
                </div>
                {error && <p className="fd-error" role="alert">{error}</p>}
                {view === 'deposits' && <>
                    <section className="fd-card fd-search-panel">
                        <div className="fd-heading"><h2>Search Fixed Deposits</h2></div>
                        <form className="fd-toolbar fd-filter" onSubmit={e => {
                            e.preventDefault();
                            setAppliedFilter(filter.trim());
                            setAppliedStatus(status);
                            setPage(0)
                        }}><label>Fixed Deposit Reference or Customer Name<input
                            placeholder="Enter fixed deposit reference or customer name" value={filter}
                            onChange={e => setFilter(e.target.value)}/></label><label>Fixed Deposit Status<select
                            value={status} onChange={e => setStatus(e.target.value)}>
                            <option value="ALL">All statuses</option>
                            {['DRAFT', 'ACTIVE', 'MATURED', 'CLOSED', 'CANCELLED'].map(s => <option key={s}
                                value={s}>{formatBusinessValue(s)}</option>)}
                        </select></label>
                            <div className="cbms-search-actions">
                                <button type="submit" className="fd-primary cbms-action-search">Search</button>
                                <button className="cbms-action-clear" type="button" onClick={() => {
                                    setFilter('');
                                    setStatus('ALL');
                                    setAppliedFilter('');
                                    setAppliedStatus('ALL');
                                    setPage(0)
                                }}>Clear
                                </button>
                            </div>
                        </form>
                    </section>
                    <section className="fd-card fd-list-panel">
                        <div className="fd-heading">
                            <div><h2>Fixed Deposit List <small>{recordCount(filtered.length)}</small></h2><p>Search
                                fixed deposit accounts and review their details.</p></div>
                        </div>
                        <div className="fd-table-wrap">
                            <SortableTable className="fd-table" sortState={depositSort} onSortChange={sort=>{setDepositSort(sort);setPage(0)}}>
                                <thead>
                                <tr>
                                    <th>Fixed Deposit Reference</th>
                                    <th>Customer Name</th>
                                    <th className="fd-num">Principal Amount (MYR)</th>
                                    <th>Maturity Date</th>
                                    <th>Status</th>
                                    <th>Action</th>
                                </tr>
                                </thead>
                                <tbody>{displayed.map(r => <tr key={r.fdId}>
                                    <td className="fd-value-popup-cell">
                                        <PopupView value={r.fdReference} maxLength={20} truncate={false}/>
                                    </td>
                                    <td className="fd-value-popup-cell">
                                        <PopupView value={customerNames[r.customerId] || 'Customer record unavailable'} maxLength={20} truncate={false}/>
                                    </td>
                                    <td className="fd-num">{fmt(r.principalAmount)}</td>
                                    <td>{r.maturityDate}</td>
                                    <td><StatusBadge status={r.status}/></td>
                                    <td>
                                        <button type="button" className="fd-detail-button cbms-action-details"
                                                onClick={() => {
                                                    setTab('overview');
                                                    setDetailsOpen(true);
                                                    navigate(`/fixed-deposits?fd=${r.fdId}`)
                                                }}>View Details
                                        </button>
                                    </td>
                                </tr>)}{!displayed.length && <tr>
                                    <td colSpan={6}
                                        className="fd-empty">{loading ? 'Loading deposits…' : 'No fixed deposits match the selected filters.'}</td>
                                </tr>}</tbody>
                            </SortableTable>
                        </div>
                        <TablePagination page={page} totalPages={totalPages} totalElements={filtered.length}
                                         onPageChange={setPage} className="fd-pager"/>
                    </section>
                </>}
                {view === 'approvals' && <section className="fd-card">
                    <div className="fd-heading">
                        <div><h2>Fixed Deposit Approval Requests <small>{recordCount(visibleApprovals.length)}</small>
                        </h2><p>Review and authorize sensitive financial operations</p></div>
                        <RefreshButton onClick={() => void loadApprovals()} loading={approvalLoading}/></div>
                    <div className="fd-toolbar fd-filter"><label>Status<select value={approvalFilter}
                                                                               onChange={e => {setApprovalPage(0);setApprovalFilter(e.target.value)}}>
                        <option value="PENDING">Pending</option>
                        <option value="APPROVED">Approved</option>
                        <option value="REJECTED">Rejected</option>
                        <option value="ALL">All</option>
                    </select></label></div>
                    {approvalError && <p className="fd-error" role="alert">{approvalError}</p>}
                    <div className="fd-table-wrap">
                        <SortableTable className="fd-table" sortState={approvalSort} onSortChange={sort=>{setApprovalSort(sort);setApprovalPage(0)}}>
                            <thead>
                            <tr>
                                <th>Fixed Deposit</th>
                                <th>Operation</th>
                                <th>Status</th>
                                <th>Submitted By</th>
                                <th>Action</th>
                            </tr>
                            </thead>
                            <tbody>{visibleApprovals.map(r => <tr key={r.request_id}>
                                <td className="fd-value-popup-cell"><PopupView
                                    value={rows.find(fd => fd.fdId === r.fd_id)?.fdReference || 'Fixed Deposit'}
                                    maxLength={20} truncate={false}/></td>
                                <td>{labelize(r.action_type)}</td>
                                <td><StatusBadge status={r.request_status}/></td>
                                <td><PopupView value={r.requested_by} maxLength={25}/></td>
                                <td>
                                    <button type="button" className="fd-detail-button cbms-action-details"
                                            onClick={() => {
                                                setCheckerChoice(r.assigned_checker || '');
                                                setApprovalDetail(r)
                                            }}>View Details
                                    </button>
                                </td>
                            </tr>)}{!visibleApprovals.length && <tr>
                                <td colSpan={5} className="fd-empty">No matching approval requests</td>
                            </tr>}</tbody>
                        </SortableTable>
                    </div>
                    <TablePagination page={currentApprovalPage} totalPages={approvalTotalPages}
                                     totalElements={filteredApprovals.length} onPageChange={setApprovalPage}
                                     busy={approvalLoading} className="fd-pager"/>
                    <p className="fd-note">Approval requires another authorized administrator. The queue displays the
                        latest 50 requests.</p></section>}
                {view === 'monitoring' && <section className="fd-card">
                    <div className="fd-heading">
                        <div><h2>Fixed Deposit Monitoring</h2><p>Operational maturity, accrual and closure views</p>
                        </div>
                    </div>
                    <nav className="fd-monitoring-tabs" role="tablist"
                         aria-label="Fixed Deposit monitoring categories">{monitoringViews.map(([key, title]) => <button
                        type="button" key={key} className={monitoringView === key ? 'active' : ''} role="tab"
                        aria-selected={monitoringView === key}
                        onClick={() => setMonitoringView(key)}>{title}{key !== 'summary' && Array.isArray(monitoringResults[key]) ? ` (${(monitoringResults[key] as unknown[]).length})` : ''}</button>)}</nav>
                    {monitoringError && <p className="fd-error" role="alert">{monitoringError}</p>}<DataView
                    data={monitoringData} empty="No monitoring data available" showCount={false}/></section>}
            </>}
            {approvalDetail && <div className="fd-overlay" onMouseDown={e => {
                if (e.target === e.currentTarget) setApprovalDetail(null)
            }}>
                <div className="fd-modal fd-record-modal" role="dialog" aria-modal="true"
                     aria-label="Approval request details">
                    <div className="fd-modal-heading">
                        <div><small>FIXED DEPOSIT APPROVAL REQUEST</small>
                            <SubscreenTitle as="h2" title={`Fixed Deposit ${labelize(approvalDetail.action_type)}`}
                                reference={rows.find(fd => fd.fdId === approvalDetail.fd_id)?.fdReference || approvalDetail.fd_reference}/>
                        </div>
                        <button type="button" className="fd-close" onClick={() => setApprovalDetail(null)}
                                aria-label="Close approval details"><CloseIcon/></button>
                    </div>
                    <div className="fd-modal-content"><InfoGrid data={{
                        'Status': approvalDetail.request_status,
                        'Requested By': approvalDetail.requested_by,
                        'Submitted On': approvalDetail.requested_at ? new Date(approvalDetail.requested_at).toLocaleString('en-MY') : '—',
                        'Request Reason': approvalDetail.request_reason || '—',
                        'Decided By': approvalDetail.decided_by || '—',
                        'Decision Reason': approvalDetail.decision_reason || '—',
                        'Assigned Checker': approvalDetail.assigned_checker || 'Not assigned',
                        'Assigned By': approvalDetail.assigned_by || '—',
                        'Assigned On': approvalDetail.assigned_at ? new Date(approvalDetail.assigned_at).toLocaleString('en-MY') : '—'
                    }}/>{approvalDetail.request_status === 'PENDING' && admin && <div className="fd-checker-assignment">
                        <label className="fd-field"><FieldLabel>Assign Checker</FieldLabel>
                            <select value={checkerChoice} onChange={e => setCheckerChoice(e.target.value)}
                                    disabled={assigningChecker}>
                                <option value="">Select an administrator</option>
                                {checkerUsers.filter(u => u.username.toLowerCase() !== approvalDetail.requested_by.toLowerCase()).map(u =>
                                    <option key={u.username} value={u.username}>{u.username}</option>)}
                            </select></label>
                        <button type="button" className="fd-detail-button"
                                disabled={assigningChecker || Boolean(checkerChoice) && checkerChoice.toLowerCase() === approvalDetail.assigned_checker?.toLowerCase()}
                                onClick={() => void assignChecker()}>{assigningChecker ? 'Assigning…' : 'Assign Checker'}</button>
                        {checkerError && <p className="fd-error" role="alert">{checkerError}</p>}
                        <p className="fd-note">The assigned administrator receives a notification. Only that checker can
                            approve or reject this request.</p>
                    </div>}
                        {approvalDetail.request_status === 'PENDING' && admin && approvalDetail.requested_by.toLowerCase() !== session?.username.toLowerCase() && (!approvalDetail.assigned_checker || approvalDetail.assigned_checker.toLowerCase() === session?.username.toLowerCase()) &&
                            <div className="fd-actions">
                                <button disabled={busy} onClick={() => {
                                    setApprovalDecisionReason('');
                                    setApprovalDetail(null);
                                    setPendingApprovalDecision({request: approvalDetail, approve: true})
                                }}>Approve
                                </button>
                                <button disabled={busy} onClick={() => {
                                    setApprovalDecisionReason('');
                                    setApprovalDetail(null);
                                    setPendingApprovalDecision({request: approvalDetail, approve: false})
                                }}>Reject
                                </button>
                            </div>}{approvalDetail.request_status === 'PENDING' && approvalDetail.requested_by.toLowerCase() === session?.username.toLowerCase() &&
                            <p className="fd-note">Awaiting review by another authorized
                                checker.</p>}{approvalDetail.request_status === 'PENDING' && approvalDetail.assigned_checker && approvalDetail.assigned_checker.toLowerCase() !== session?.username.toLowerCase() && admin &&
                            <p className="fd-note">This request is assigned to another checker. You may reassign it if
                                needed.</p>}</div>
                    <div className="fd-modal-footer">
                        <button type="button" onClick={() => setApprovalDetail(null)}>Close</button>
                    </div>
                </div>
            </div>}
            {detailsOpen && !selected &&
                <section className="fd-card" role="status">Loading Fixed Deposit account information…</section>}
            {detailsOpen && selected &&
                <section className="fd-account-screen" aria-label="Fixed Deposit account details">
                    <BackNavigation label="Back to Fixed Deposits" onBack={closeDetails}/>
                    <section className="fd-card fd-account-panel">
                        <div className="fd-account-header cbms-subscreen-heading"><SubscreenTitle title="Fixed Deposit"
                            reference={selected.fdReference}/><StatusBadge status={selected.status}/>
                        </div>
                        <nav className="fd-account-tabs" role="tablist" aria-label="Fixed Deposit account sections">
                            {[['overview', 'Overview'], ...(selectedIsIslamic ? [['islamic-contract', 'Islamic Contract']] : []), ['operations', 'Operations'], ['placement', 'Placement'], ['accruals', selectedIsIslamic ? 'Profit Accruals' : 'Interest Accruals'], ['payouts', selectedIsIslamic ? 'Profit Payouts' : 'Interest Payouts'], ['maturity', 'Maturity'], ['preview', 'Maturity Preview']].map(([key, title]) =>
                                <button type="button" key={key} className={tab === key ? 'active' : ''} role="tab"
                                        aria-selected={tab === key} onClick={() => {
                                    if (key === 'islamic-contract' && tab !== key) setContractSection('contract');
                                    setTab(key);
                                }}>{title}</button>)}
                        </nav>
                        {detailsBusy && <p className="fd-note" role="status">Loading account information…</p>}
                        {tab === 'overview' && <section className="fd-account-section">
                            <h2>{selectedIsIslamic ? (selected.fdType === 'TERM_DEPOSIT' ? 'Term Deposit-i Overview' : 'Fixed Deposit-i Overview') : 'Fixed Deposit Overview'}</h2>
                            <InfoGrid data={{
                                'Customer': customerNames[selected.customerId] || 'Customer record unavailable',
                                'Product': products.find(p => p.prodId === selected.productId)?.prodNm || '—',
                                'Scheme': schemes.find(sc => sc.schmId === selected.schemeId)?.schmNm || '—',
                                'Deposit Type': products.find(p => p.prodId === selected.productId)?.bankingTyp === 'ISLAMIC' ? (selected.fdType === 'TERM_DEPOSIT' ? 'Term Deposit-i' : 'Fixed Deposit-i') : formatBusinessValue(selected.fdType),
                                'Currency': selected.currency,
                                'Principal Amount (MYR)': fmt(selected.principalAmount),
                                'Tenor (Months)': selected.tenorMonths,
                                'Rate Type': selected.rateType === 'FLOATING' ? 'Floating' : 'Fixed',
                                [selectedIsIslamic ? 'Indicative Annual Profit Rate (%)' : selected.rateType === 'FLOATING' ? 'Opening Annual Interest Rate (%)' : 'Annual Interest Rate (%)']: fmt(selected.interestRate),
                                'Placement Date': selected.placementDate,
                                'Maturity Date': selected.maturityDate,
                                'Status': selected.status,
                                'Remarks': selected.remarks || '—'
                            }}/>
                        </section>}
                        {tab === 'islamic-contract' && selectedIsIslamic &&
                            <div className="fd-islamic-contract-workspace">
                                <nav className="fd-contract-subtabs" role="tablist"
                                     aria-label="Islamic contract workflow">
                                    {([['contract', 'Contract Profile'], ['profit-terms', 'Profit Terms'], ['execution-evidence', 'Execution Evidence'], ['execution-timeline', 'Execution Timeline'], ['execution-policy', 'Execution Policy'], ['ledger-mapping', 'Ledger Mapping'], ['posting-readiness', 'Posting Readiness']] as const).map(([key, title]) =>
                                        <button
                                            key={key} type="button" role="tab" aria-selected={contractSection === key}
                                            className={contractSection === key ? 'active' : ''}
                                            onClick={() => setContractSection(key)}>{title}</button>)}
                                </nav>
                                <div className="fd-contract-content" role="tabpanel"
                                     aria-label={contractSection === 'contract' ? 'Contract Profile' : contractSection === 'profit-terms' ? 'Profit Terms' : contractSection === 'execution-evidence' ? 'Execution Evidence' : contractSection === 'execution-timeline' ? 'Execution Timeline' : contractSection === 'execution-policy' ? 'Execution Policy' : contractSection === 'ledger-mapping' ? 'Ledger Mapping' : 'Posting Readiness'}>
                                    {contractSection === 'contract' && <section className="fd-account-section">
                                        <h2>Approved Islamic Contract</h2>
                                        {islamicSnapshot?.fd_id === selected.fdId ? <>
                                                <InfoGrid data={{
                                                    'Contract Reference': islamicSnapshot.contract_ref,
                                                    'Contract Type': formatBusinessValue(islamicSnapshot.contract_typ),
                                                    'Profit Model': formatBusinessValue(islamicSnapshot.profit_model),
                                                    'Shariah Approval Reference': islamicSnapshot.shariah_approval_ref,
                                                    'Approved By': islamicSnapshot.profile_approved_by,
                                                    'Indicative Rate (%)': islamicSnapshot.indicative_rate_pct ?? '—',
                                                    'Execution Status': 'Not executed — no financial posting'
                                                }}/>
                                                <details className="fd-nested">
                                                    <summary>View approved contract terms</summary>
                                                    <p>{islamicSnapshot.terms_summary}</p></details>
                                            </> :
                                            <p className="fd-note">{islamicSnapshotMessage || 'Loading approved contract snapshot…'}</p>}
                                    </section>}
                                    {contractSection === 'profit-terms' &&
                                        <IslamicDepositProfitTerms key={selected.fdId} fdId={selected.fdId}
                                                                   depositStatus={selected.status}
                                                                   contractType={islamicSnapshot?.fd_id === selected.fdId ? islamicSnapshot.contract_typ : null}/>}
                                    {contractSection === 'execution-evidence' &&
                                        <IslamicDepositExecutionEvidence key={selected.fdId} fdId={selected.fdId}
                                                                         depositStatus={selected.status}
                                                                         contractType={islamicSnapshot?.fd_id === selected.fdId ? islamicSnapshot.contract_typ : null}/>}
                                    {contractSection === 'execution-timeline' &&
                                        <IslamicDepositExecutionTimeline key={selected.fdId} fdId={selected.fdId}
                                                                         depositStatus={selected.status}/>}
                                    {contractSection === 'execution-policy' &&
                                        <IslamicExecutionPolicy key={selected.fdId}
                                                                contractType={islamicSnapshot?.fd_id === selected.fdId ? islamicSnapshot.contract_typ : null}/>}
                                    {contractSection === 'ledger-mapping' &&
                                        <IslamicDepositLedgerMappingReview key={selected.fdId} fdId={selected.fdId}
                                                                           depositStatus={selected.status}/>}
                                    {contractSection === 'posting-readiness' &&
                                        <IslamicDepositPostingReadiness key={selected.fdId} fdId={selected.fdId}/>}
                                </div>
                            </div>}
                        {tab === 'operations' && <section className="fd-account-section"><h2>Fixed Deposit
                            Operations</h2>{selectedIsIslamic &&
                            <p className="fd-note" role="note">Islamic deposit processing is not enabled yet. A contract
                                profile may be registered in Scheme Maintenance, but placement, profit accrual, payout,
                                renewal and settlement require a separate Islamic ledger
                                workflow.</p>}{actionButtons || (!selectedIsIslamic &&
                            <p className="fd-note">No operations are available for this account or your access
                                level.</p>)}
                            {upliftResult?.fdId === selected.fdId &&
                                <div role="status" className="fd-success"><strong>Premature uplift
                                    posted</strong><InfoGrid data={{
                                    'Original Principal': fmt(upliftResult.principalAmount),
                                    'Eligible Interest': fmt(upliftResult.eligibleInterest),
                                    'Previously Paid Interest': fmt(upliftResult.previouslyPaidInterest),
                                    'Interest Recovered': fmt(upliftResult.interestRecovery),
                                    'Net Settlement': fmt(upliftResult.settlementAmount),
                                    'Destination Account': operationAccounts.find(a => a.acctId === upliftResult.destinationAccountId)?.acctNo || 'Account selected',
                                    'Transaction Reference': upliftResult.transactionReference
                                }}/></div>}
                            {settlementResult?.fdId === selected.fdId &&
                                <div role="status" className="fd-success"><strong>Principal settled</strong><InfoGrid
                                    data={{
                                        'Settlement Amount': fmt(settlementResult.settlementAmount),
                                        'Destination Account': settlementAccounts.find(a => a.acctId === settlementResult.destinationAccountId)?.acctNo || 'Account selected',
                                        'Transaction Reference': settlementResult.transactionReference
                                    }}/></div>}
                            <p className="fd-note">Renewal, maturity settlement and premature uplift require review by a
                                different authorized checker. Submission does not immediately transfer funds.</p>
                        </section>}
                        {['placement', 'accruals', 'payouts', 'maturity', 'preview'].includes(tab) &&
                            <section className="fd-account-section">
                                <div className="fd-history-heading">
                                    <h2>{({
                                        placement: 'Placement',
                                        accruals: selectedIsIslamic ? 'Profit Accruals' : 'Interest Accruals',
                                        payouts: selectedIsIslamic ? 'Profit Payouts' : 'Interest Payouts',
                                        maturity: 'Maturity',
                                        preview: 'Maturity Preview'
                                    } as Record<string, string>)[tab]}</h2>
                                    {(tab === 'accruals' || tab === 'payouts') && Array.isArray(history[tab]) && <span
                                        className="fd-history-heading-count">{recordCount(history[tab].length)}</span>}
                                </div>
                                <DataView key={`${selected.fdId}-${tab}`} data={history[tab]}
                                          empty="No records available"
                                          showCount={tab !== 'accruals' && tab !== 'payouts'}
                                          pageSize={tab === 'accruals' || tab === 'payouts' ? 10 : undefined}/>
                            </section>}
                    </section>
                </section>}
            {panel && <div className="fd-overlay" onMouseDown={e => {
                if (e.target === e.currentTarget) closePanel()
            }}>
                <div className={`fd-modal fd-action-modal${panel === 'create' ? ' fd-new-application-modal' : ''}`}
                     role="dialog" aria-modal="true"
                     aria-label={panel === 'create' ? `New ${depositName} Application` : actions[panel]}>
                    <div className="fd-modal-heading">
                        <div>
                            <small>{activeProduct?.prodCat === 'TERM_DEPOSIT' ? 'TERM DEPOSITS' : 'FIXED DEPOSITS'}</small>
                            <SubscreenTitle as="h2" title={panel === 'create' ? `New ${depositName} Application` : actions[panel]}
                                reference={selected && panel !== 'create' ? selected.fdReference : undefined}/></div>
                        <button type="button" className="fd-close" onClick={closePanel} disabled={busy}
                                aria-label="Close"><CloseIcon/></button>
                    </div>
                    <form onSubmit={e => void submit(e)}>
                        <div className="fd-modal-content fd-form">
                            {panel === 'create' && <div className="fd-new-application-form">
                                <label className="fd-field fd-wide"><FieldLabel required>Customer</FieldLabel>
                                    <div className="fd-lov-input-row"><input autoFocus readOnly required
                                                                             value={chosenCustomer ? `${chosenCustomer.cifNo} · ${chosenCustomer.custNm}` : ''}
                                                                             placeholder="Select an active customer"
                                                                             onClick={openCustomerLov}/><CustomerLookupButton
                                        type="button" onClick={openCustomerLov}/></div>
                                    <RequiredFieldError show={panelAttempted && !chosenCustomer}
                                                        message="Please select an active customer."/></label>
                                <label className="fd-field"><FieldLabel required>Product</FieldLabel><select required
                                                                                                             value={fields.product || ''}
                                                                                                             onChange={e => {
                                                                                                                 setSchemes([]);
                                                                                                                 setSchemeRatePolicy(null);
                                                                                                                 setFields(prev => ({
                                                                                                                     ...prev,
                                                                                                                     product: e.target.value,
                                                                                                                     fdType: products.find(p => String(p.prodId) === e.target.value)?.prodCat || 'FIXED_DEPOSIT',
                                                                                                                     scheme: '',
                                                                                                                     rate: '',
                                                                                                                     tenor: ''
                                                                                                                 }))
                                                                                                             }}>
                                    <option value="">Select product</option>
                                    {products.map(p => <option key={p.prodId}
                                                               value={p.prodId}>{p.prodCd} · {p.prodNm}</option>)}
                                </select></label>
                                <label className="fd-field"><FieldLabel required>Scheme</FieldLabel><select required
                                                                                                            disabled={!fields.product}
                                                                                                            value={fields.scheme || ''}
                                                                                                            onChange={e => {
                                                                                                                setSchemeRatePolicy(null);
                                                                                                                setFields(prev => ({
                                                                                                                    ...prev,
                                                                                                                    scheme: e.target.value,
                                                                                                                    rate: '',
                                                                                                                    tenor: ''
                                                                                                                }))
                                                                                                            }}>
                                    <option value="">Select scheme</option>
                                    {schemes.map(s => <option key={s.schmId}
                                                              value={s.schmId}>{s.schmCd} · {s.schmNm}</option>)}
                                </select></label>
                                <label className="fd-field"><FieldLabel required>Principal Amount
                                    (MYR)</FieldLabel><input type="number" required step="0.01"
                                                             min={currentScheme?.minAmt ?? 0.01}
                                                             max={currentScheme?.maxAmt ?? undefined}
                                                             value={fields.amount || ''}
                                                             onChange={e => update('amount', e.target.value)}/></label>
                                <label className="fd-field"><FieldLabel required>Tenure
                                    (Months)</FieldLabel><TenorSelect value={fields.tenor || ''}
                                                                      minMonths={currentScheme?.minTenor}
                                                                      maxMonths={currentScheme?.maxTenor}
                                                                      disabled={!currentScheme}
                                                                      onChange={v => update('tenor', v)}/><RequiredFieldError
                                    show={panelAttempted && !fields.tenor} message="Please select a tenure."/></label>
                                <label className="fd-field"><FieldLabel
                                    required>{rateCaption}</FieldLabel><SchemeRateSelect
                                    schemeId={currentScheme?.schmId ?? null} effectiveDate={fields.date || ''}
                                    value={fields.rate || ''}
                                    onChange={v => update('rate', v)} onPolicyChange={setSchemeRatePolicy}
                                    mismatchMessage="The selected scheme rate is not supported for this deposit."/><RequiredFieldError
                                    show={panelAttempted && !validCreateRate}
                                    message="Please select a valid scheme rate."/></label>
                                {isIslamicDeposit ?
                                    <label className="fd-field"><span>{totalCaption}</span><input readOnly
                                                                                                  value="Pending profit rules"/></label> :
                                    <TotalInterestField amount={fields.amount || ''} rate={fields.rate || ''}
                                                        tenor={fields.tenor || ''}
                                                        method="deposit-actual-365" startDate={fields.date || ''}
                                                        label={schemeRatePolicy?.rateType === 'FLOATING' ? `Indicative ${totalCaption}` : totalCaption}/>}
                                <label className="fd-field"><FieldLabel required>Deposit Type</FieldLabel><select
                                    required disabled value={fields.fdType || 'FIXED_DEPOSIT'}
                                    aria-label="Deposit type determined by the selected product"
                                    title="The deposit category is configured in Product Maintenance and cannot be changed in this application.">
                                    <option
                                        value="FIXED_DEPOSIT">{isIslamicDeposit ? 'Fixed Deposit-i' : 'Fixed Deposit'}</option>
                                    <option
                                        value="TERM_DEPOSIT">{isIslamicDeposit ? 'Term Deposit-i' : 'Term Deposit'}</option>
                                </select></label>
                                <label className="fd-field"><FieldLabel required>Placement Date</FieldLabel><input
                                    type="date" required value={fields.date || ''} onChange={e => {
                                    setSchemeRatePolicy(null);
                                    setFields(prev => ({...prev, date: e.target.value, rate: ''}))
                                }}/></label>
                                <SchemeLimitsSummary scheme={currentScheme} policy={schemeRatePolicy}
                                                     effectiveDate={fields.date || ''}/>
                                {isIslamicDeposit && <small className="fd-note fd-wide" role="status">
                                    {islamicReadinessError || (islamicReadiness !== null && currentScheme !== undefined && islamicReadiness.schemeId === currentScheme.schmId
                                        ? islamicReadiness.canCreateDraft
                                            ? `Approved ${formatBusinessValue(islamicReadiness.contractType)} contract ${islamicReadiness.contractReference} · Draft only`
                                            : islamicReadiness.message
                                        : 'Checking approved Islamic contract…')}
                                </small>}
                                <label className="fd-field fd-wide">Remarks<textarea rows={2} maxLength={1000}
                                                                                     value={fields.remarks || ''}
                                                                                     onChange={e => update('remarks', e.target.value)}/></label>
                            </div>}
                            {(panel === 'place' || panel === 'payout' || panel === 'uplift') && <>
                                <div className="fd-subheading">
                                    <h3>{panel === 'place' ? 'Funding Account' : 'Payout Account'}</h3></div>
                                {operationAccountsLoading ? <p>Loading eligible accounts…</p> : operationAccountsError ?
                                    <p className="fd-error">{operationAccountsError}</p> : operationAccounts.length ?
                                        <label
                                            className="fd-field">{panel === 'place' ? 'Source Account' : 'Destination Account'}<select
                                            required value={fields.account || ''}
                                            onChange={e => update('account', e.target.value)}>
                                            <option value="">Select customer account</option>
                                            {operationAccounts.map(a => <option key={a.acctId}
                                                                                value={a.acctId}>{a.acctNo} · {a.acctTyp} </option>)}
                                        </select></label> :
                                        <p className="fd-error">No eligible active MYR savings/current accounts
                                            available.</p>}</>}
                            {panel === 'settle' && <>
                                <div className="fd-subheading"><h3>Maturity Principal Repayment</h3></div>
                                <InfoGrid data={{
                                    'Fixed Deposit': selected?.fdReference,
                                    'Principal Amount (MYR)': fmt(selected?.principalAmount),
                                    'Posted Accrued Interest': settlementTotals ? fmt(settlementTotals.accrued) : 'Verified by server',
                                    'Paid Interest': settlementTotals ? fmt(settlementTotals.paid) : 'Verified by server',
                                    'Outstanding Interest': settlementTotals ? fmt(Math.max(0, settlementTotals.accrued - settlementTotals.paid)) : 'Verified by server'
                                }}/>{settlementLoading ? <p>Loading accounts…</p> : settlementError ?
                                <p className="fd-error">{settlementError}</p> : settlementAccounts.length ?
                                    <label className="fd-field">Destination Account <select required
                                                                                            value={fields.account || ''}
                                                                                            onChange={e => update('account', e.target.value)}>
                                        <option value="">Select customer account</option>
                                        {settlementAccounts.map(a => <option key={a.acctId}
                                                                             value={a.acctId}>{a.acctNo} · {a.acctTyp}</option>)}
                                    </select></label> : <p className="fd-error">No eligible account found.</p>}</>}
                            {panel === 'renew' && <>
                                <div className="fd-subheading"><h3>Renewal Terms</h3></div>
                                <label className="fd-field">Renewal Type<select
                                    value={fields.renewalType || 'PRINCIPAL_ONLY'}
                                    onChange={e => update('renewalType', e.target.value)}>
                                    <option value="PRINCIPAL_ONLY">Principal Only</option>
                                    <option
                                        value="PRINCIPAL_PLUS_INTEREST">{isIslamicDeposit ? 'Principal + Unpaid Accrued Profit' : 'Principal + Unpaid Accrued Interest'}</option>
                                </select></label><label className="fd-field">New Scheme<select required
                                                                                               value={fields.scheme || ''}
                                                                                               onChange={e => {
                                                                                                   setSchemeRatePolicy(null);
                                                                                                   setFields(prev => ({
                                                                                                       ...prev,
                                                                                                       scheme: e.target.value,
                                                                                                       rate: '',
                                                                                                       tenor: ''
                                                                                                   }))
                                                                                               }}>{schemes.length === 0 &&
                                <option value={selected?.schemeId || ''}>Current Scheme</option>}{schemes.map(s =>
                                <option key={s.schmId}
                                        value={s.schmId}>{s.schmCd} · {s.schmNm}</option>)}</select></label><label
                                className="fd-field"><FieldLabel required>New Tenure (Months)</FieldLabel><TenorSelect
                                value={fields.tenor || ''} minMonths={currentScheme?.minTenor}
                                maxMonths={currentScheme?.maxTenor} onChange={v => update('tenor', v)}/></label><label
                                className="fd-field"><FieldLabel
                                required>{renewalRateCaption}</FieldLabel><SchemeRateSelect
                                schemeId={Number(fields.scheme) || null} effectiveDate={today()}
                                value={fields.rate || ''} onChange={v => update('rate', v)}
                                onPolicyChange={setSchemeRatePolicy}
                                mismatchMessage="The selected renewal scheme rate is not supported."/><RequiredFieldError
                                show={panelAttempted && !validSchemeRate}
                                message="Please select a valid renewal scheme rate."/></label>
                                <TotalInterestField amount={selected?.principalAmount?.toString() || ''}
                                                    rate={fields.rate || ''} tenor={fields.tenor || ''}
                                                    startDate={today()} method="deposit-actual-365"
                                                    label={schemeRatePolicy?.rateType === 'FLOATING' ? `Indicative ${totalCaption}` : totalCaption}/>
                                <p className="fd-note">{isIslamicDeposit ? 'Profit preview uses the principal before capitalization. Final amounts require checker approval.' : 'Interest preview uses principal before capitalization. Final amounts require checker approval.'}</p></>}
                            {panel === 'accrue' && field('date', 'Accrual Date', 'date')}
                            {panel === 'maturity' &&
                                <p className="fd-note">Maturity processing changes status only. Principal settlement
                                    requires separate approval.</p>}
                            {panel === 'uplift' &&
                                <p className="fd-note">Excess previously paid interest may be recovered from principal
                                    after checking the premature uplift entitlement.</p>}
                            {['place', 'renew', 'uplift', 'settle'].includes(panel) &&
                                <label className="fd-field"><FieldLabel
                                    required={['renew', 'uplift', 'settle'].includes(panel)}>{['renew', 'uplift', 'settle'].includes(panel) ? 'Approval Reason' : 'Remarks'}</FieldLabel><textarea
                                    rows={3} required={['renew', 'uplift', 'settle'].includes(panel)}
                                    value={fields.remarks || ''}
                                    onChange={e => update('remarks', e.target.value)}/></label>}
                        </div>
                        <div className="fd-modal-footer">
                            <button type="button" onClick={closePanel} disabled={busy}>Cancel</button>
                            <button type="submit" className="fd-primary"
                                    disabled={busy}>{busy ? 'Processing…' : ['settle', 'renew', 'uplift'].includes(panel) ? 'Submit for Approval' : panel === 'create' ? 'Create' : 'Confirm'}</button>
                        </div>
                    </form>
                </div>
            </div>}
            {confirmSubmission && panel && <ConfirmDialog
                title={panel === 'create' ? `Create ${depositName} Draft?` : `${['settle', 'renew', 'uplift'].includes(panel) ? 'Submit' : 'Confirm'} ${actions[panel]}`}
                message={panel === 'create' ? 'The application will be saved in DRAFT. No funds will move until a permitted placement is performed.' :
                    ['settle', 'renew', 'uplift'].includes(panel) ? 'This request will be sent to an independent checker. No funds move on submission.' :
                        'Are you sure you want to continue? This operation may update balances or deposit status immediately.'}
                notice={panel === 'create' && isIslamicDeposit ? 'Islamic placement, profit posting and settlement remain disabled.' :
                    ['settle', 'renew', 'uplift'].includes(panel) ? 'Awaiting independent approval; the bank will not post this transaction now.' :
                        panel === 'create' ? 'Draft creation only — funding requires a separate placement action.' : 'This operation can take effect immediately.'}
                details={[
                    {
                        label: 'Deposit Reference',
                        value: panel === 'create' ? 'New application' : selected?.fdReference || '—'
                    },
                    ...(panel === 'create' ? [{label: 'Principal (MYR)', value: fmt(fields.amount)}] : []),
                    ...(['place', 'payout', 'uplift', 'settle'].includes(panel) ? [{
                        label: 'Selected Account', value:
                            (operationAccounts.find(a => a.acctId === Number(fields.account)) || settlementAccounts.find(a => a.acctId === Number(fields.account)))?.acctNo || '—'
                    }] : []),
                    ...(panel === 'accrue' ? [{label: 'Accrual Date', value: fields.date || '—'}] : [])
                ]}
                confirmLabel={panel === 'create' ? 'Create' : ['settle', 'renew', 'uplift'].includes(panel) ? 'Submit for Approval' : `Confirm ${actions[panel]}`}
                busy={busy} onCancel={() => setConfirmSubmission(false)} onConfirm={() => {
                void performSubmit();
            }}/>}
            {pendingApprovalDecision && <ConfirmDialog
                title={`${pendingApprovalDecision.approve ? 'Approve' : 'Reject'} ${labelize(pendingApprovalDecision.request.action_type)}?`}
                message={pendingApprovalDecision.approve ? 'Approval may execute the financial posting or account action. Verify the original request first.' :
                    'Rejecting closes the request without financial posting.'}
                notice={pendingApprovalDecision.approve ? 'This decision may post funds immediately.' : 'No funds will move on rejection.'}
                details={[{
                    label: 'Fixed Deposit',
                    value: rows.find(fd => fd.fdId === pendingApprovalDecision.request.fd_id)?.fdReference || pendingApprovalDecision.request.fd_reference || '—'
                },
                    {label: 'Maker', value: pendingApprovalDecision.request.requested_by},
                    {label: 'Operation', value: labelize(pendingApprovalDecision.request.action_type)}]}
                reasonLabel={pendingApprovalDecision.approve ? 'Approval Note' : 'Rejection Reason'}
                reasonRequired={!pendingApprovalDecision.approve}
                reason={approvalDecisionReason} onReasonChange={setApprovalDecisionReason}
                danger={!pendingApprovalDecision.approve} busy={busy}
                confirmLabel={pendingApprovalDecision.approve ? 'Approve Request' : 'Reject Request'}
                onCancel={() => {
                    setApprovalDetail(pendingApprovalDecision.request);
                    setPendingApprovalDecision(null);
                    setApprovalDecisionReason('');
                }}
                onConfirm={() => {
                    void decideApproval(pendingApprovalDecision.request, pendingApprovalDecision.approve, approvalDecisionReason);
                }}/>}
            {customerLovOpen && panel === 'create' && <CustomerLovDialog
                query={customerSearch} onQueryChange={setCustomerSearch} onSearch={() => void findCustomers(0)}
                customers={customerMatches} page={customerLovPage} totalPages={customerLovTotalPages} totalElements={customerLovTotalElements}
                loading={lookupBusy} error={lookupError} onPageChange={next => void findCustomers(next)}
                onSelect={chooseCustomer} onClose={() => setCustomerLovOpen(false)}/>}
        </div>;
    }
import {formatBusinessValue} from '../utils/display';
