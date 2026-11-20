import {SortableTable, type TableSortState} from '../components/SortableTable';
import {sortTableRecords} from '../utils/tableSorting';
import {useCallback, useEffect, useState, type FormEvent} from 'react';
import {api} from '../api/client';
import {useToast} from '../components/toastContext';
import {ConfirmDialog, FieldLabel, RefreshButton} from '../components/UiStandards';
import {CreateButton, Empty, FormDialog, ModuleHeader, Paging, Panel} from './PageComponents';
import {dateOnly, explainError} from './pageUtils';
import PopupView from '../components/PopupView';

type Parameter = {
    param_key: string;
    param_value: string;
    value_type: string;
    description: string | null;
    upd_dt: string | null;
    upd_by: string | null
};
type History = {
    history_id: number;
    param_key: string;
    action: string;
    old_value: string | null;
    new_value: string | null;
    changed_by: string;
    changed_dt: string
};
const sensitive = (key: string) => /(?:PASSWORD|SECRET|TOKEN|API[_-]?KEY|PRIVATE[_-]?KEY|CREDENTIAL)/i.test(key);
const empty = {key: '', value: '', valueType: 'STRING', description: ''};

export default function SystemParameters() {
    const notify = useToast();
    const [rows, setRows] = useState<Parameter[]>([]);
    const [query, setQuery] = useState('');
    const [applied, setApplied] = useState('');
    const [page, setPage] = useState(0);
    const [historyPage, setHistoryPage] = useState(0);
    const [parameterSort, setParameterSort] = useState<TableSortState>(null);
    const [historySort, setHistorySort] = useState<TableSortState>(null);
    const [selected, setSelected] = useState<Parameter | null>(null);
    const [history, setHistory] = useState<History[]>([]);
    const [modal, setModal] = useState<'create' | 'edit' | null>(null);
    const [form, setForm] = useState(empty);
    const [confirm, setConfirm] = useState(false);
    const [busy, setBusy] = useState(false);
    const reload = useCallback(async () => {
        setBusy(true);
        try {
            const parameterResponse = await api.get<Parameter[]>('/admin/parameters');
            setRows(parameterResponse.data);
        } catch (error) {
            setRows([]);
            notify('error', explainError(error));
        } finally {
            setBusy(false);
        }
    }, [notify]);
    useEffect(() => {
        void Promise.resolve().then(reload);
    }, [reload]);
    const inspect = async (item: Parameter) => {
        setSelected(item);
        setHistory([]);
        setHistoryPage(0);
        try {
            const {data} = await api.get<History[]>(`/admin/parameters/${encodeURIComponent(item.param_key)}/history`);
            setHistory(data);
        } catch (error) {
            notify('error', explainError(error));
        }
    };
    const open = (item?: Parameter) => {
        setSelected(item ?? null);
        setForm(item ? {
            key: item.param_key,
            value: item.param_value,
            valueType: item.value_type,
            description: item.description ?? ''
        } : empty);
        setModal(item ? 'edit' : 'create');
        setConfirm(false);
    };
    const validate = (event: FormEvent) => {
        event.preventDefault();
        if (modal === 'edit' && selected && form.value === selected.param_value && form.description === (selected.description ?? '')) {
            notify('warning', 'No changes to save.');
            return;
        }
        setConfirm(true);
    };
    const save = async () => {
        if (busy || !modal) return;
        setBusy(true);
        try {
            if (modal === 'create') await api.post('/admin/parameters', {
                key: form.key.trim().toUpperCase(),
                value: form.value,
                valueType: form.valueType,
                description: form.description.trim()
            });
            else await api.put(`/admin/parameters/${encodeURIComponent(form.key)}`, {
                value: form.value, description: form.description.trim()
            });
            notify('success', modal === 'create' ? 'Parameter created.' : 'Changes saved.');
            setModal(null);
            setConfirm(false);
        } catch (error) {
            notify('error', explainError(error));
            setBusy(false);
            return;
        }
        setBusy(false);
        await reload();
    };
    const filtered = rows.filter(row => !applied || [row.param_key, row.description ?? ''].some(value => value.toLowerCase().includes(applied.toLowerCase())));
    const totalPages = Math.max(1, Math.ceil(filtered.length / 10));
    const currentPage = Math.min(page, totalPages - 1);
    const historyPages = Math.max(1, Math.ceil(history.length / 10));
    const currentHistoryPage = Math.min(historyPage, historyPages - 1);
    const sortedParameters = sortTableRecords(filtered, parameterSort, [r => r.param_key, r => sensitive(r.param_key) ? '••••••' : r.param_value, r => r.value_type, r => r.description, r => r.upd_by]);
    const sortedHistory = sortTableRecords(history, historySort, [r => r.changed_dt, r => r.action, r => sensitive(selected?.param_key ?? '') ? '••••••' : r.old_value, r => sensitive(selected?.param_key ?? '') ? '••••••' : r.new_value, r => r.changed_by]);
    return <main className="gap-page">
        <ModuleHeader title="System Parameter Maintenance"
                      description="Manage permitted application settings and review change history. Only administrators can access this screen."
                      actions={
                          <div className="cbms-toolbar-actions">
                              <RefreshButton onClick={() => void reload()} loading={busy}/>
                              <CreateButton onClick={() => open()}>New Parameter</CreateButton>
                          </div>
                      }/>
        <Panel title="System Parameters" count={filtered.length}>
            <form className="gap-toolbar" onSubmit={e => {
                e.preventDefault();
                setPage(0);
                setApplied(query.trim());
            }}>
                <label>Search Parameters<input type="search" value={query} onChange={e => setQuery(e.target.value)}
                                               placeholder="Key or description"/></label>
                <div className="cbms-search-actions">
                    <button className="cbms-action-search" type="submit">Search</button>
                    <button type="button" className="cbms-action-clear" onClick={() => {
                        setPage(0);
                        setQuery('');
                        setApplied('');
                    }}>Clear
                    </button>
                </div>
            </form>
            <div className="gap-table-wrap">
                <SortableTable sortState={parameterSort} onSortChange={sort => { setParameterSort(sort); setPage(0); }}>
                    <thead>
                    <tr>
                        <th>Key</th>
                        <th>Value</th>
                        <th>Type</th>
                        <th>Description</th>
                        <th>Updated By</th>
                        <th>Action</th>
                    </tr>
                    </thead>
                    <tbody>{sortedParameters.slice(currentPage * 10, (currentPage + 1) * 10).map(item => <tr
                        key={item.param_key}>
                        <td><PopupView value={item.param_key} maxLength={24}/></td>
                        <td><PopupView value={sensitive(item.param_key) ? '••••••' : item.param_value} maxLength={20}/>
                        </td>
                        <td>{item.value_type}</td>
                        <td><PopupView value={item.description || '—'} maxLength={24}/></td>
                        <td><PopupView value={item.upd_by || '—'} maxLength={24}/></td>
                        <td className="gap-actions">
                            <button type="button" onClick={() => open(item)}>Edit</button>
                            <button type="button" onClick={() => void inspect(item)}>History</button>
                        </td>
                    </tr>)}{!filtered.length && <Empty columns={6}/>}</tbody>
                </SortableTable>
            </div>
            <Paging page={currentPage} totalPages={totalPages} totalElements={filtered.length} onPage={setPage} busy={busy}/>
        </Panel>
        {selected && !modal && <Panel title={`History — ${selected.param_key}`}
                                      actions={<button type="button" onClick={() => setSelected(null)}>Close</button>}>
            <div className="gap-table-wrap">
                <SortableTable sortState={historySort} onSortChange={sort => { setHistorySort(sort); setHistoryPage(0); }}>
                    <thead>
                    <tr>
                        <th>Changed On</th>
                        <th>Action</th>
                        <th>Previous Value</th>
                        <th>New Value</th>
                        <th>Changed By</th>
                    </tr>
                    </thead>
                    <tbody>{sortedHistory.slice(currentHistoryPage * 10, (currentHistoryPage + 1) * 10).map(entry => <tr
                        key={entry.history_id}>
                        <td>{dateOnly(entry.changed_dt)}</td>
                        <td>{entry.action}</td>
                        <td><PopupView value={sensitive(selected.param_key) ? '••••••' : entry.old_value ?? '—'}
                                       maxLength={24}/></td>
                        <td><PopupView value={sensitive(selected.param_key) ? '••••••' : entry.new_value ?? '—'}
                                       maxLength={24}/></td>
                        <td>{entry.changed_by}</td>
                    </tr>)}
                    {!history.length && <Empty columns={5} message="No change history."/>}</tbody>
                </SortableTable>
            </div>
            <Paging page={currentHistoryPage} totalPages={historyPages} totalElements={history.length} onPage={setHistoryPage} busy={busy}/>
        </Panel>}
        {modal && <FormDialog title={modal === 'create' ? 'Create Parameter' : 'Edit Parameter'} busy={busy}
                              onClose={() => setModal(null)}>
            <form className="gap-form" onSubmit={validate}>
                <div className="gap-grid">
                    <label><FieldLabel required>Parameter Key</FieldLabel><input required readOnly={modal === 'edit'}
                                                                                 maxLength={100}
                                                                                 pattern="[A-Za-z][A-Za-z0-9_.-]{1,99}"
                                                                                 value={form.key}
                                                                                 onChange={e => setForm({
                                                                                     ...form,
                                                                                     key: e.target.value
                                                                                 })}/></label>
                    <label><FieldLabel required>Value Type</FieldLabel><select disabled={modal === 'edit'}
                                                                               value={form.valueType}
                                                                               onChange={e => setForm({
                                                                                   ...form,
                                                                                   valueType: e.target.value
                                                                               })}>
                        {['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN'].map(x => <option
                            key={x}>{x}</option>)}</select></label>
                    <label><FieldLabel required>Value</FieldLabel>{form.valueType === 'BOOLEAN' ?
                        <select value={form.value} required onChange={e => setForm({...form, value: e.target.value})}>
                            <option value="">Select Value</option>
                            <option value="true">True</option>
                            <option value="false">False</option>
                        </select> :
                        <input required maxLength={500} type={sensitive(form.key) ? 'password' : 'text'}
                               autoComplete="off" value={form.value}
                               onChange={e => setForm({...form, value: e.target.value})}/>}</label>
                </div>
                <label>Description<textarea maxLength={500} value={form.description}
                                            onChange={e => setForm({...form, description: e.target.value})}/></label>
                <div className="gap-form-actions">
                    <button type="button" onClick={() => setModal(null)}>Cancel</button>
                    <button className="gap-primary"
                            disabled={busy}>{modal === 'create' ? 'Create' : 'Save Changes'}</button>
                </div>
            </form>
        </FormDialog>}
        {confirm && <ConfirmDialog title="Confirm Parameter Change"
                                   message={modal === 'create' ? 'Create this system parameter?' : 'Save changes to this system parameter?'}
                                   confirmLabel={modal === 'create' ? 'Create' : 'Save Changes'}
                                   onCancel={() => setConfirm(false)} onConfirm={() => void save()} busy={busy}/>}
    </main>;
}
