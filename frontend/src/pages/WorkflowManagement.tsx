import {SortableTable} from '../components/SortableTable';
import {useCallback, useEffect, useState} from 'react';
import {api} from '../api/client';
import {useAuth} from '../auth/useAuth';
import {useToast} from '../components/toastContext';
import {businessReferenceFromResponse} from '../utils/businessReference';
import {ConfirmDialog, FieldLabel, RefreshButton} from '../components/UiStandards';
import {Badge, CreateButton, Empty, FormDialog, ModuleHeader, Paging, Panel, Tabs} from './PageComponents';
import {dateOnly, explainError} from './pageUtils';
import PopupView from '../components/PopupView';
import {WorkflowEntityField} from './WorkflowEntityField';

type Tab = 'queue' | 'mine' | 'submissions' | 'definitions' | 'monitoring';
type Task = {
    taskId: number;
    taskRef: string;
    instanceId: number;
    instanceRef: string;
    stepNm: string;
    assignedRole: string;
    assignedTo: string | null;
    taskSts: string;
    entityRef: string | null;
    actionDt: string | null
};
type Step = { stepId: number; stepNo: number; stepNm: string; approverRole: string; requiredApprovals: number };
type Definition = {
    workflowId: number;
    workflowCd: string;
    workflowNm: string;
    moduleCd: string;
    entityTyp: string;
    workflowSts: string;
    steps: Step[]
};
type Instance = {
    instanceId: number;
    instanceRef: string;
    workflowCd: string;
    entityRef: string | null;
    submittedDt: string;
    workflowSts: string;
    currentStepNo: number
};
type Summary = {
    totalInstances: number;
    pendingInstances: number;
    inProgressInstances: number;
    approvedInstances: number;
    rejectedInstances: number;
    openTasks: number;
    overdueTasks: number
};
type Decision = { kind: 'claim' | 'approve' | 'reject' | 'activate'; task?: Task; workflow?: Definition };

export default function WorkflowManagement() {
    const notify = useToast();
    const {session} = useAuth();
    const isAdmin = session?.roles.includes('ADMIN') ?? false;
    const canMake = session?.roles.some(role => role === 'ADMIN' || role === 'MAKER') ?? false;
    const [tab, setTab] = useState<Tab>('queue');
    const [page, setPage] = useState(0);
    const [loadedTab, setLoadedTab] = useState<Tab | null>(null);
    const [query, setQuery] = useState('');
    const [appliedQuery, setAppliedQuery] = useState('');
    const [tasks, setTasks] = useState<Task[]>([]);
    const [definitions, setDefinitions] = useState<Definition[]>([]);
    const [submissions, setSubmissions] = useState<Instance[]>([]);
    const [summary, setSummary] = useState<Summary | null>(null);
    const [busy, setBusy] = useState(false);
    const [decision, setDecision] = useState<Decision | null>(null);
    const [remarks, setRemarks] = useState('');
    const [modal, setModal] = useState<'definition' | 'step' | 'submit' | 'assign' | null>(null);
    const [pendingAction, setPendingAction] = useState<'definition' | 'step' | 'submit' | 'assign' | null>(null);
    const [selectedWorkflow, setSelectedWorkflow] = useState<Definition | null>(null);
    const [selectedTask, setSelectedTask] = useState<Task | null>(null);
    const [form, setForm] = useState({workflowCd: '', workflowNm: '', workflowDesc: '', moduleCd: '', entityTyp: ''});
    const [step, setStep] = useState({stepNo: '1', stepNm: '', approverRole: 'ADMIN', requiredApprovals: '1'});
    const [submission, setSubmission] = useState({workflowId: '', entityId: '', entityRef: '', remarks: ''});
    const [assignee, setAssignee] = useState('');
    const selectedEntityType = definitions.find(w => String(w.workflowId) === submission.workflowId)?.entityTyp || '';

    const load = useCallback(async (target: Tab) => {
        setBusy(true);
        setLoadedTab(null);
        try {
            if (target === 'queue' || target === 'mine') {
                const {data} = await api.get<Task[]>(`/workflows/tasks/${target}`);
                setTasks(data);
            } else if (target === 'definitions') {
                const {data} = await api.get<Definition[]>('/workflows/definitions');
                setDefinitions(data);
            } else if (target === 'submissions') {
                const {data} = await api.get<Instance[]>('/workflows/instances/mine');
                setSubmissions(data);
            } else {
                const {data} = await api.get<Summary>('/workflows/monitoring/summary');
                setSummary(data);
            }
            setLoadedTab(target);
        } catch (error) {
            if (target === 'queue' || target === 'mine') setTasks([]);
            if (target === 'definitions') setDefinitions([]);
            if (target === 'submissions') setSubmissions([]);
            if (target === 'monitoring') setSummary(null);
            notify('error', explainError(error));
        } finally {
            setBusy(false);
        }
    }, [notify]);
    useEffect(() => {
        void Promise.resolve().then(() => load(tab));
    }, [load, tab]);

    const run = async (endpoint: string, body?: unknown) => {
        if (busy) return;
        setBusy(true);
        try {
            const reference = businessReferenceFromResponse(await api.post(endpoint, body));
            notify('success', `Action completed.${reference ? ` Reference: ${reference}` : ''}`);
            setModal(null);
            setPendingAction(null);
            setDecision(null);
            setRemarks('');
            setSelectedTask(null);
        } catch (error) {
            notify('error', explainError(error));
            setBusy(false);
            return;
        }
        setBusy(false);
        await load(tab);
    };
    const createDefinition = async () => {
        await run('/workflows/definitions', {
            ...form, workflowCd: form.workflowCd.trim().toUpperCase(),
            moduleCd: form.moduleCd.trim().toUpperCase(), entityTyp: form.entityTyp.trim().toUpperCase()
        });
    };
    const addStep = async () => {
        if (!selectedWorkflow) return;
        await run(`/workflows/definitions/${selectedWorkflow.workflowId}/steps`, {
            stepNo: Number(step.stepNo), stepNm: step.stepNm.trim(),
            approverRole: step.approverRole, requiredApprovals: Number(step.requiredApprovals)
        });
    };
    const submitInstance = async () => {
        if (!submission.entityId || !submission.entityRef) {
            notify('warning', 'Select a business reference.');
            return;
        }
        await run('/workflows/instances', {
            workflowId: Number(submission.workflowId), entityId: Number(submission.entityId),
            entityRef: submission.entityRef.trim(), remarks: submission.remarks.trim()
        });
    };
    const assignTask = async () => {
        if (selectedTask) await run(`/workflows/tasks/${selectedTask.taskId}/assign`, {username: assignee.trim()});
    };
    const confirmPendingAction = () => {
        const action = pendingAction;
        setPendingAction(null);
        if (action === 'definition') void createDefinition();
        if (action === 'step') void addStep();
        if (action === 'submit') void submitInstance();
        if (action === 'assign') void assignTask();
    };
    const matches = (values: Array<string | number | null | undefined>) => !appliedQuery || values.some(value =>
        String(value ?? '').toLowerCase().includes(appliedQuery.toLowerCase()));
    const visibleTasks = (loadedTab === tab ? tasks : []).filter(t => matches([t.taskRef, t.instanceRef, t.stepNm, t.assignedRole, t.assignedTo, t.taskSts]));
    const visibleSubmissions = (loadedTab === tab ? submissions : []).filter(t => matches([t.instanceRef, t.workflowCd, t.entityRef, t.workflowSts]));
    const visibleDefinitions = (loadedTab === tab ? definitions : []).filter(t => matches([t.workflowCd, t.workflowNm, t.moduleCd, t.entityTyp, t.workflowSts]));
    const records = tab === 'queue' || tab === 'mine' ? visibleTasks.length
        : tab === 'submissions' ? visibleSubmissions.length : tab === 'definitions' ? visibleDefinitions.length : 0;
    const totalPages = Math.max(1, Math.ceil(records / 10));
    const currentPage = Math.min(page, totalPages - 1);
    const pageRows = <T, >(rows: T[]) => rows.slice(currentPage * 10, (currentPage + 1) * 10);
    const confirmAction = () => {
        if (!decision) return;
        if (decision.kind === 'activate' && decision.workflow) {
            void run(`/workflows/definitions/${decision.workflow.workflowId}/activate`);
        } else if (decision.task) {
            void run(`/workflows/tasks/${decision.task.taskId}/${decision.kind}`, decision.kind === 'claim' ? undefined : {remarks: remarks.trim()});
        }
    };
    return <main className="gap-page">
        <ModuleHeader title="Workflow Management"
                      description="Review approval tasks, maintain workflow definitions and monitor submitted requests."/>
        <Panel title="Workflow Workspace"
               count={tab === 'queue' || tab === 'mine' ? visibleTasks.length : tab === 'definitions' ? visibleDefinitions.length : tab === 'submissions' ? visibleSubmissions.length : undefined}
               actions={<RefreshButton loading={busy} onClick={() => void load(tab)}/> }>
            <Tabs current={tab} onSelect={next => {
                setTab(next);
                setPage(0);
                setQuery('');
                setAppliedQuery('');
            }} options={[
                {id: 'queue', label: 'Task Queue'}, {id: 'mine', label: 'My Tasks'},
                {id: 'submissions', label: 'My Submissions'}, {
                    id: 'definitions',
                    label: 'Definitions'
                }, {id: 'monitoring', label: 'Monitoring'}
            ]}/>
            {tab !== 'monitoring' && <form className="gap-toolbar" style={{marginTop: 14}} onSubmit={event => {
                event.preventDefault();
                setPage(0);
                setAppliedQuery(query.trim());
            }}>
                <label>Search {tab === 'definitions' ? 'Definitions' : tab === 'submissions' ? 'Submissions' : 'Tasks'}
                    <input type="search" maxLength={100} placeholder="Reference, status or description" value={query}
                           onChange={event => setQuery(event.target.value)}/></label>
                <div className="cbms-search-actions">
                    <button type="submit" className="cbms-action-search">Search</button>
                    <button type="button" className="cbms-action-clear" onClick={() => {
                        setPage(0);
                        setQuery('');
                        setAppliedQuery('');
                    }}>Clear
                    </button>
                </div>
            </form>}
            {(tab === 'queue' || tab === 'mine') && <div className="gap-table-wrap">
                <SortableTable>
                    <thead>
                    <tr>
                        <th>Task</th>
                        <th>Instance</th>
                        <th>Approval Step</th>
                        <th>Assigned Role</th>
                        <th>Assigned To</th>
                        <th>Status</th>
                        <th>Action</th>
                    </tr>
                    </thead>
                    <tbody>{pageRows(visibleTasks).map(task => <tr key={task.taskId}>
                        <td><PopupView value={task.taskRef} maxLength={25}/></td>
                        <td><PopupView value={task.instanceRef} maxLength={25}/></td>
                        <td><PopupView value={task.stepNm} maxLength={25}/></td>
                        <td>{task.assignedRole}</td>
                        <td>{task.assignedTo || 'Unassigned'}</td>
                        <td><Badge status={task.taskSts}/></td>
                        <td className="gap-actions">{canMake && task.taskSts === 'PENDING' &&
                            <button type="button" disabled={busy}
                                    onClick={() => setDecision({kind: 'claim', task})}>Claim</button>}
                            {canMake && task.taskSts === 'CLAIMED' && task.assignedTo === session?.username && <>
                                <button type="button" disabled={busy} onClick={() => {
                                    setRemarks('');
                                    setDecision({kind: 'approve', task});
                                }}>Approve
                                </button>
                                <button type="button" className="gap-danger" disabled={busy} onClick={() => {
                                    setRemarks('');
                                    setDecision({kind: 'reject', task});
                                }}>Reject
                                </button>
                            </>}
                            {isAdmin && task.taskSts === 'PENDING' && <button type="button" onClick={() => {
                                setSelectedTask(task);
                                setAssignee('');
                                setModal('assign');
                            }}>Assign</button>}
                        </td>
                    </tr>)}{!visibleTasks.length && <Empty columns={7}
                                                           message={busy || loadedTab !== tab ? 'Loading tasks…' : 'No tasks available.'}/>}</tbody>
                </SortableTable>
            </div>}
            {tab === 'submissions' && <>
                <div className="gap-panel-header" style={{marginTop: 14}}>
                    <span className="gap-muted">Requests submitted by your account.</span>
                    {canMake && <CreateButton onClick={async () => {
                        try {
                            const {data} = await api.get<Definition[]>('/workflows/definitions');
                            setDefinitions(data);
                            setModal('submit');
                        } catch (error) {
                            notify('error', explainError(error));
                        }
                    }}>New Request</CreateButton>}</div>
                <div className="gap-table-wrap">
                    <SortableTable>
                        <thead>
                        <tr>
                            <th>Reference</th>
                            <th>Workflow</th>
                            <th>Entity</th>
                            <th>Submitted On</th>
                            <th>Status</th>
                        </tr>
                        </thead>
                        <tbody>{pageRows(visibleSubmissions).map(item => <tr key={item.instanceId}>
                            <td><PopupView value={item.instanceRef} maxLength={25}/></td>
                            <td><PopupView value={item.workflowCd} maxLength={25}/></td>
                            <td>{item.entityRef || 'Reference unavailable'}</td>
                            <td>{dateOnly(item.submittedDt)}</td>
                            <td><Badge status={item.workflowSts}/></td>
                        </tr>)}
                        {!visibleSubmissions.length && <Empty columns={5}
                                                              message={busy || loadedTab !== tab ? 'Loading submissions…' : 'No submissions.'}/>}</tbody>
                    </SortableTable>
                </div>
            </>}
            {tab === 'definitions' && <>
                <div className="gap-panel-header" style={{marginTop: 14}}>
                    <span className="gap-muted">Define approval steps before activation. Changes to active definitions are restricted.</span>
                    {isAdmin && <CreateButton onClick={() => {
                        setForm({workflowCd: '', workflowNm: '', workflowDesc: '', moduleCd: '', entityTyp: ''});
                        setModal('definition');
                    }}>New Definition</CreateButton>}</div>
                <div className="gap-table-wrap">
                    <SortableTable>
                        <thead>
                        <tr>
                            <th>Code</th>
                            <th>Name</th>
                            <th>Module</th>
                            <th>Entity Type</th>
                            <th>Steps</th>
                            <th>Status</th>
                            <th>Action</th>
                        </tr>
                        </thead>
                        <tbody>
                        {pageRows(visibleDefinitions).map(w => <tr key={w.workflowId}>
                            <td><PopupView value={w.workflowCd} maxLength={25}/></td>
                            <td><PopupView value={w.workflowNm} maxLength={25}/></td>
                            <td><PopupView value={w.moduleCd} maxLength={25}/></td>
                            <td><PopupView value={w.entityTyp} maxLength={25}/></td>
                            <td><PopupView
                                value={w.steps?.map(s => `${s.stepNo}. ${s.stepNm} (${s.approverRole})`).join(' · ') || 'None'}
                                maxLength={32}/></td>
                            <td><Badge status={w.workflowSts}/></td>
                            <td className="gap-actions">{isAdmin && w.workflowSts === 'INACTIVE' && <>
                                <button type="button" onClick={() => {
                                    setSelectedWorkflow(w);
                                    setStep({
                                        stepNo: String((w.steps?.length ?? 0) + 1),
                                        stepNm: '',
                                        approverRole: 'ADMIN',
                                        requiredApprovals: '1'
                                    });
                                    setModal('step');
                                }}>Add Step
                                </button>
                                {w.steps?.length > 0 && <button type="button" onClick={() => setDecision({
                                    kind: 'activate',
                                    workflow: w
                                })}>Activate</button>}
                            </>}</td>
                        </tr>)}{!visibleDefinitions.length && <Empty columns={7}
                                                                     message={busy || loadedTab !== tab ? 'Loading definitions…' : 'No definitions found.'}/>}</tbody>
                    </SortableTable>
                </div>
            </>}
            {tab !== 'monitoring' &&
                <Paging page={currentPage} totalPages={totalPages} totalElements={records} onPage={setPage}
                        busy={busy}/>}
            {tab === 'monitoring' && <div className="gap-details" style={{marginTop: 16}}>
                {summary ? Object.entries(summary).map(([key, value]) => <div key={key}>
                    <dt>{key.replace(/([A-Z])/g, ' $1').replace(/^./, letter => letter.toUpperCase())}</dt>
                    <dd className="gap-kpi">{value}</dd>
                </div>) : <p>No monitoring data loaded.</p>}
            </div>}
        </Panel>
        {decision && <ConfirmDialog title={decision.kind === 'activate' ? 'Activate Workflow' : 'Review Task'}
                                    message={decision.kind === 'activate' ? 'Activate this workflow definition?' : `${decision.kind === 'claim' ? 'Claim' : decision.kind === 'approve' ? 'Approve' : 'Reject'} this task?`}
                                    confirmLabel={decision.kind === 'activate' ? 'Activate' : decision.kind === 'claim' ? 'Claim' : decision.kind === 'approve' ? 'Approve' : 'Reject'}
                                    danger={decision.kind === 'reject'} busy={busy} onCancel={() => setDecision(null)}
                                    onConfirm={confirmAction}
                                    reasonLabel={decision.kind === 'activate' || decision.kind === 'claim' ? undefined : 'Remarks'}
                                    reason={remarks}
                                    onReasonChange={decision.kind === 'activate' || decision.kind === 'claim' ? undefined : setRemarks}/>}
        {modal && <FormDialog
            title={modal === 'definition' ? 'Create Workflow' : modal === 'step' ? 'Add Approval Step' : modal === 'submit' ? 'Submit Workflow Request' : 'Assign Task'}
            busy={busy} onClose={() => setModal(null)}>
            {modal === 'definition' && <form className="gap-form" onSubmit={e => {
                e.preventDefault();
                setPendingAction('definition');
            }}>
                <div className="gap-grid">
                    <label><FieldLabel required>Workflow Code</FieldLabel><input required maxLength={50}
                                                                                 pattern="[A-Za-z0-9_-]+"
                                                                                 value={form.workflowCd}
                                                                                 onChange={e => setForm({
                                                                                     ...form,
                                                                                     workflowCd: e.target.value
                                                                                 })}/></label>
                    <label><FieldLabel required>Workflow Name</FieldLabel><input required maxLength={150}
                                                                                 value={form.workflowNm}
                                                                                 onChange={e => setForm({
                                                                                     ...form,
                                                                                     workflowNm: e.target.value
                                                                                 })}/></label>
                    <label><FieldLabel required>Module Code</FieldLabel><input required maxLength={50}
                                                                               value={form.moduleCd}
                                                                               onChange={e => setForm({
                                                                                   ...form,
                                                                                   moduleCd: e.target.value
                                                                               })}/></label>
                    <label><FieldLabel required>Entity Type</FieldLabel><input required maxLength={100}
                                                                               value={form.entityTyp}
                                                                               onChange={e => setForm({
                                                                                   ...form,
                                                                                   entityTyp: e.target.value
                                                                               })}/></label></div>
                <label>Description<textarea maxLength={500} value={form.workflowDesc}
                                            onChange={e => setForm({...form, workflowDesc: e.target.value})}/></label>
                <div className="gap-form-actions">
                    <button type="button" onClick={() => setModal(null)}>Cancel</button>
                    <button className="gap-primary" disabled={busy}>Create</button>
                </div>
            </form>}
            {modal === 'step' && <form className="gap-form" onSubmit={e => {
                e.preventDefault();
                setPendingAction('step');
            }}>
                <div className="gap-grid">
                    <label><FieldLabel required>Step Number</FieldLabel><input required type="number" min="1"
                                                                               value={step.stepNo}
                                                                               onChange={e => setStep({
                                                                                   ...step,
                                                                                   stepNo: e.target.value
                                                                               })}/></label>
                    <label><FieldLabel required>Step Name</FieldLabel><input required maxLength={150}
                                                                             value={step.stepNm}
                                                                             onChange={e => setStep({
                                                                                 ...step,
                                                                                 stepNm: e.target.value
                                                                             })}/></label>
                    <label><FieldLabel required>Approver Role</FieldLabel><select value={step.approverRole}
                                                                                  onChange={e => setStep({
                                                                                      ...step,
                                                                                      approverRole: e.target.value
                                                                                  })}>
                        <option>ADMIN</option>
                        <option>MAKER</option>
                    </select></label>
                    <label><FieldLabel required>Required Approvals</FieldLabel><input required type="number" min="1"
                                                                                      value={step.requiredApprovals}
                                                                                      onChange={e => setStep({
                                                                                          ...step,
                                                                                          requiredApprovals: e.target.value
                                                                                      })}/></label></div>
                <div className="gap-form-actions">
                    <button type="button" onClick={() => setModal(null)}>Cancel</button>
                    <button className="gap-primary" disabled={busy}>Create</button>
                </div>
            </form>}
            {modal === 'submit' && <form className="gap-form" onSubmit={e => {
                e.preventDefault();
                setPendingAction('submit');
            }}>
                <div className="gap-grid">
                    <label><FieldLabel required>Active Workflow</FieldLabel><select required
                                                                                    value={submission.workflowId}
                                                                                    onChange={e => setSubmission({
                                                                                        ...submission,
                                                                                        workflowId: e.target.value,
                                                                                        entityId: '',
                                                                                        entityRef: ''
                                                                                    })}>
                        <option value="">Select a workflow</option>
                        {definitions.filter(w => w.workflowSts === 'ACTIVE').map(w => <option key={w.workflowId}
                                                                                              value={w.workflowId}>{w.workflowNm} ({w.workflowCd})</option>)}
                    </select></label>
                    <WorkflowEntityField entityType={selectedEntityType} value={submission.entityId}
                                            entityRef={submission.entityRef}
                                            onChange={(id, reference) => setSubmission({
                                                ...submission,
                                                entityId: id,
                                                entityRef: reference
                                            })}/></div>
                <label>Remarks<textarea maxLength={1000} value={submission.remarks} onChange={e => setSubmission({
                    ...submission,
                    remarks: e.target.value
                })}/></label>
                <div className="gap-form-actions">
                    <button type="button" onClick={() => setModal(null)}>Cancel</button>
                    <button className="gap-primary" disabled={busy}>Submit for Review</button>
                </div>
            </form>}
            {modal === 'assign' && <form className="gap-form" onSubmit={e => {
                e.preventDefault();
                setPendingAction('assign');
            }}><label><FieldLabel required>Username</FieldLabel><input required maxLength={100} value={assignee}
                                                                       onChange={e => setAssignee(e.target.value)}/></label>
                <div className="gap-form-actions">
                    <button type="button" onClick={() => setModal(null)}>Cancel</button>
                    <button className="gap-primary" disabled={busy}>Assign</button>
                </div>
            </form>}
        </FormDialog>}
        {pendingAction && <ConfirmDialog title="Confirm Workflow Action"
                                         message={pendingAction === 'definition' ? 'Create this workflow definition?' : pendingAction === 'step' ? 'Add this approval step?' : pendingAction === 'submit' ? 'Submit this workflow request for review?' : 'Assign this task to the specified user?'}
                                         confirmLabel={pendingAction === 'definition' ? 'Create' : pendingAction === 'step' ? 'Add Step' : pendingAction === 'submit' ? 'Submit for Review' : 'Assign'}
                                         busy={busy} onCancel={() => setPendingAction(null)}
                                         onConfirm={confirmPendingAction}/>}
    </main>;
}
