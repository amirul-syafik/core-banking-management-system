import {SortableTable, type TableSortState} from '../components/SortableTable';
import {sortTableRecords} from '../utils/tableSorting';
import {useCallback, useEffect, useState} from 'react';
import {api} from '../api/client';
import {useToast} from '../components/toastContext';
import {RefreshButton, TablePagination} from '../components/UiStandards';
import {Badge, DetailGrid, Empty, ModuleHeader, Panel, Tabs} from './PageComponents';
import {explainError, titleCase} from './pageUtils';
import PopupView from '../components/PopupView';

type MonitoringTab = 'overview' | 'health' | 'resources' | 'database' | 'alerts';
type Section = { title: string; values: Record<string, unknown> };
export default function AdminMonitoring() {
    const notify = useToast();
    const [tab, setTab] = useState<MonitoringTab>('overview');
    const [monitor, setMonitor] = useState<Record<string, unknown> | null>(null);
    const [busy, setBusy] = useState(false);
    const [alertsPage, setAlertsPage] = useState(0);
    const [alertsSort, setAlertsSort] = useState<TableSortState>(null);
    const load = useCallback(async () => {
        setBusy(true);
        try {
            const {data} = await api.get<Record<string, unknown>>(`/admin/monitoring/${tab}`);
            setMonitor(data);
        } catch (error) {
            setMonitor(null);
            notify('error', explainError(error));
        } finally {
            setBusy(false);
        }
    }, [tab, notify]);
    useEffect(() => {
        void Promise.resolve().then(load);
    }, [load]);
    const alerts = Array.isArray(monitor?.alerts) ? monitor.alerts as Array<{ level: string; code: string; message: string }> : [];
    const currentAlertsPage = Math.min(alertsPage, Math.max(0, Math.ceil(alerts.length / 10) - 1));
    const sortedAlerts = sortTableRecords(alerts, alertsSort, [alert => alert.level, alert => alert.code, alert => alert.message]);
    const sections: Section[] = monitor ? Object.entries(monitor).map(([key, value]) => {
        if (typeof value === 'object' && value !== null && !Array.isArray(value))
            return {title: titleCase(key), values: value as Record<string, unknown>};
        return {title: titleCase(key), values: {value}};
    }) : [];
    return <main className="gap-page">
        <ModuleHeader title="System Monitoring"
                      description="Inspect application health, resources, database connections and alerts."/>
        <Panel title="Live System Monitoring"
               actions={<RefreshButton onClick={() => void load()} loading={busy}/> }>
            <Tabs current={tab} onSelect={next => { setAlertsPage(0); setTab(next); }}
                  options={(['overview', 'health', 'resources', 'database', 'alerts'] as MonitoringTab[]).map(id => ({
                      id,
                      label: titleCase(id)
                  }))}/>
            {!monitor && <p className="gap-muted">{busy ? 'Loading monitoring data…' : 'Monitoring unavailable.'}</p>}
            {monitor && tab === 'alerts' && Array.isArray(monitor.alerts) && <div className="gap-table-wrap">
                <SortableTable sortState={alertsSort} onSortChange={sort => { setAlertsSort(sort); setAlertsPage(0); }}>
                    <thead>
                    <tr>
                        <th>Level</th>
                        <th>Code</th>
                        <th>Message</th>
                    </tr>
                    </thead>
                    <tbody>
                    {sortedAlerts.slice(currentAlertsPage * 10, (currentAlertsPage + 1) * 10).map((a, i) => <tr
                        key={i}>
                        <td><Badge status={a.level}/></td>
                        <td>{a.code}</td>
                        <td><PopupView value={a.message} maxLength={45}/></td>
                    </tr>)}
                    {!monitor.alerts.length && <Empty columns={3} message="No active alerts."/>}</tbody>
                </SortableTable>
            </div>}
            {monitor && tab === 'alerts' && Array.isArray(monitor.alerts) && <TablePagination
                page={currentAlertsPage} totalPages={Math.max(1, Math.ceil(alerts.length / 10))}
                totalElements={alerts.length} onPageChange={setAlertsPage} busy={busy}
                className="gap-pagination"/>}
            {monitor && <div className="gap-grid"
                             style={{marginTop: 16}}>{sections.filter(s => s.title !== 'Alerts').map(section => <div
                className="gap-note" key={section.title}>
                <h3 style={{margin: '0 0 10px'}}>{section.title}</h3><DetailGrid data={section.values}/></div>)}</div>}
        </Panel>
    </main>;
}
