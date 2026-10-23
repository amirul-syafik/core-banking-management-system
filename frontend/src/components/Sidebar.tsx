import {NavLink, useLocation} from 'react-router-dom';
import {useState} from 'react';
import {
    Activity,
    ArrowLeftRight,
    Building2,
    ChevronLeft,
    ChevronRight,
    CircleUserRound,
    CreditCard,
    LayoutDashboard,
    ShieldCheck,
    Wallet,
    ChevronDown,
    Search,
    Send,
    RotateCcw,
    ClipboardList,
    Scale,
    Landmark,
    Package,
    Layers,
    Percent
} from 'lucide-react';
import {useAuth} from '../auth/useAuth';
import {hasAnyRole, type Role} from '../auth/session';

const items: { to: string; label: string; icon: typeof LayoutDashboard; roles?: Role[] }[] = [
    {to: '/', label: 'Overview', icon: LayoutDashboard},
    {to: '/customers', label: 'Customers', icon: CircleUserRound},
    {to: '/accounts', label: 'Accounts', icon: Wallet},
    {to: '/financing', label: 'Financing', icon: Landmark},
    {to: '/credit-lines', label: 'Credit Lines', icon: CreditCard},
    {to: '/operations', label: 'Operations', icon: CreditCard},
    {to: '/audit', label: 'Administration', icon: ShieldCheck, roles: ['ADMIN']},
];

export function Sidebar({collapsed, onToggle}: { collapsed: boolean; onToggle: () => void }) {
    const {session} = useAuth();
    const location = useLocation();
    const [transactionsExpanded, setTransactionsExpanded] = useState(true);
    const [financingExpanded, setFinancingExpanded] = useState(true);
    const [adminExpanded, setAdminExpanded] = useState(true);
    const [operationsExpanded, setOperationsExpanded] = useState(true);
    const financingActive = location.pathname.startsWith('/financing/');
    const transactionsActive = location.pathname.startsWith('/transactions');
    const canOperate = hasAnyRole(session, ['MAKER', 'ADMIN']);
    const isAdmin = hasAnyRole(session, ['ADMIN']);
    return <aside className={`sidebar ${collapsed ? 'collapsed' : ''}`}>
        <div className="brand">
            <div className="brand-mark"><Building2 size={22}/></div>
            {!collapsed && <div><strong>CBMS</strong><small>Banking Platform</small></div>}</div>
        <div className="sidebar-caption">{collapsed ? '•••' : 'WORKSPACE'}</div>
        <nav aria-label="Main navigation">
            {items.filter(item => hasAnyRole(session, item.roles ?? [])).map(({to, label, icon: Icon}) =>
                <div key={to}>
                    {to === '/financing' && <div className="cbms-sidebar-group">
                        <button type="button"
                                className={`nav-item cbms-sidebar-parent ${financingActive ? 'active' : ''}`}
                                aria-expanded={financingExpanded} aria-label="Toggle Financing menu"
                                onClick={() => setFinancingExpanded(value => !value)}>
                            <Landmark size={19}/><span>Financing</span>
                            {!collapsed && (financingExpanded ? <ChevronDown size={16}/> : <ChevronRight size={16}/>)}
                        </button>
                        {financingExpanded && !collapsed && <div className="cbms-sidebar-submenu">
                            <NavLink to="/financing/applications"
                                     className={({isActive}) => `nav-item cbms-sidebar-child ${isActive ? 'active' : ''}`}>
                                <ClipboardList size={16}/><span>Financing Applications</span>
                            </NavLink>
                            <NavLink to="/financing/facilities"
                                     className={({isActive}) => `nav-item cbms-sidebar-child ${isActive ? 'active' : ''}`}>
                                <Landmark size={16}/><span>Financing Facilities</span>
                            </NavLink>
                        </div>}
                    </div>}
                    {to === '/audit' && <div className="cbms-sidebar-group">
                        <button type="button"
                                className={`nav-item cbms-sidebar-parent ${location.pathname.startsWith('/admin/') ? 'active' : ''}`}
                                aria-expanded={adminExpanded} onClick={() => setAdminExpanded(v => !v)}>
                            <ShieldCheck size={19}/><span>Administration</span>
                            {!collapsed && (adminExpanded ? <ChevronDown size={16}/> : <ChevronRight size={16}/>)}
                        </button>
                        {adminExpanded && !collapsed && <div className="cbms-sidebar-submenu">
                            <NavLink to="/admin/users"
                                     className={({isActive}) => `nav-item cbms-sidebar-child ${isActive ? 'active' : ''}`}><CircleUserRound
                                size={16}/><span>User Management</span></NavLink>
                            <NavLink to="/admin/logs"
                                     className={({isActive}) => `nav-item cbms-sidebar-child ${isActive ? 'active' : ''}`}><Activity
                                size={16}/><span>Application Logging</span></NavLink>
                        </div>}
                    </div>}
                    {to === '/operations' && <div className="cbms-sidebar-group">
                        <button type="button"
                                className={`nav-item cbms-sidebar-parent ${location.pathname.startsWith('/operations') ? 'active' : ''}`}
                                aria-expanded={operationsExpanded} onClick={() => setOperationsExpanded(v => !v)}>
                            <Package size={19}/><span>Operations</span>{!collapsed && (operationsExpanded ?
                            <ChevronDown size={16}/> : <ChevronRight size={16}/>)}</button>
                        {operationsExpanded && !collapsed && <div className="cbms-sidebar-submenu">
                            <NavLink to="/operations/products"
                                     className={({isActive}) => `nav-item cbms-sidebar-child ${isActive ? 'active' : ''}`}><Package
                                size={16}/><span>Product Maintenance</span></NavLink>
                            <NavLink to="/operations/schemes"
                                     className={({isActive}) => `nav-item cbms-sidebar-child ${isActive ? 'active' : ''}`}><Layers
                                size={16}/><span>Scheme Maintenance</span></NavLink>
                            <NavLink to="/operations/rates"
                                     className={({isActive}) => `nav-item cbms-sidebar-child ${isActive ? 'active' : ''}`}><Percent
                                size={16}/><span>Rate Maintenance</span></NavLink>
                        </div>}
                    </div>}
                    {to === '/operations' && <div className="cbms-sidebar-group">
                        <button type="button"
                                className={`nav-item cbms-sidebar-parent ${transactionsActive ? 'active' : ''}`}
                                aria-expanded={transactionsExpanded} aria-label="Toggle Transactions menu"
                                onClick={() => setTransactionsExpanded(value => !value)}>
                            <ArrowLeftRight size={19}/><span>Transactions</span>
                            {!collapsed && (transactionsExpanded ? <ChevronDown size={16}/> :
                                <ChevronRight size={16}/>)}
                        </button>
                        {transactionsExpanded && !collapsed && <div className="cbms-sidebar-submenu">
                            {canOperate && <NavLink to="/transactions/posting"
                                                    className={({isActive}) => `nav-item cbms-sidebar-child ${isActive ? 'active' : ''}`}><Send
                                size={16}/><span>Transaction Posting</span></NavLink>}
                            <NavLink to="/transactions/inquiry"
                                     className={({isActive}) => `nav-item cbms-sidebar-child ${isActive ? 'active' : ''}`}><Search
                                size={16}/><span>Transaction Inquiry</span></NavLink>
                            {canOperate && <NavLink to="/transactions/reversal"
                                                    className={({isActive}) => `nav-item cbms-sidebar-child ${isActive ? 'active' : ''}`}><RotateCcw
                                size={16}/><span>Transaction Reversal</span></NavLink>}
                            {isAdmin && <NavLink to="/transactions/reconciliation"
                                                 className={({isActive}) => `nav-item cbms-sidebar-child ${isActive ? 'active' : ''}`}><Scale
                                size={16}/><span>Transaction Reconciliation</span></NavLink>}
                            {isAdmin && <NavLink to="/transactions/audit"
                                                 className={({isActive}) => `nav-item cbms-sidebar-child ${isActive ? 'active' : ''}`}><ClipboardList
                                size={16}/><span>Transaction Audit</span></NavLink>}
                        </div>}
                    </div>}
                    {to !== '/financing' && to !== '/audit' && to !== '/operations' &&
                        <NavLink to={to} end={to === '/'} title={collapsed ? label : undefined}
                                 className={({isActive}) => `nav-item ${isActive ? 'active' : ''}`}><Icon
                            size={19}/><span>{label}</span></NavLink>}
                </div>
            )}
        </nav>
        <div className="sidebar-bottom">
            <div className="system-status"><Activity size={17}/><span>Authenticated session</span></div>
            <button className="collapse-button" onClick={onToggle}
                    aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}>{collapsed ?
                <ChevronRight size={18}/> : <><ChevronLeft size={18}/> Collapse sidebar</>}</button>
        </div>
    </aside>;
}
