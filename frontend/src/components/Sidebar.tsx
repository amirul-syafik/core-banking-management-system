import {NavLink, useLocation, useNavigate} from 'react-router-dom';
import {useEffect, useState, type ReactNode} from 'react';
import {
    Activity,
    ArrowLeftRight,
    BookOpen,
    Building2,
    Car,
    ChartColumn,
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    CircleUserRound,
    ClipboardList,
    CreditCard,
    FileClock,
    GitBranch,
    Handshake,
    Landmark,
    LayoutDashboard,
    Layers,
    Package,
    Percent,
    PiggyBank,
    RotateCcw,
    Scale,
    Search,
    Send,
    Settings2,
    Shield,
    ShieldCheck,
    SlidersHorizontal,
    UsersRound,
    Wallet,
    X,
    type LucideIcon
} from 'lucide-react';
import {useAuth} from '../auth/useAuth';
import {hasAnyRole, type Role} from '../auth/session';

type SidebarSection = 'financing' | 'administration' | 'operations' | 'transactions';
type NavigationLink = {
    kind: 'link';
    to: string;
    label: string;
    icon: LucideIcon;
    roles?: Role[];
    keywords?: string[];
};
type NavigationGroup = {
    kind: 'group';
    id: SidebarSection;
    label: string;
    icon: LucideIcon;
    roles?: Role[];
    children: NavigationEntry[];
};
type NavigationEntry = NavigationLink | NavigationGroup;
type SearchResult = NavigationLink & {parents: string[]};

// This tree is the source for both the normal sidebar and its navigation search.
const navigation: NavigationEntry[] = [
    {kind: 'link', to: '/', label: 'Overview', icon: LayoutDashboard, keywords: ['home', 'dashboard']},
    {kind: 'link', to: '/customers', label: 'Customers', icon: CircleUserRound, keywords: ['customer management']},
    {kind: 'link', to: '/accounts', label: 'Accounts', icon: Wallet, keywords: ['account management']},
    {kind: 'link', to: '/fixed-deposits', label: 'Fixed Deposits', icon: PiggyBank, keywords: ['term deposits']},
    {
        kind: 'group', id: 'financing', label: 'Financing', icon: Handshake, children: [
            {kind: 'link', to: '/financing/applications', label: 'Financing Applications', icon: ClipboardList},
            {kind: 'link', to: '/financing/collaterals', label: 'Collateral Management', icon: Shield},
            {kind: 'link', to: '/financing/vehicles', label: 'Vehicle Management', icon: Car},
            {kind: 'link', to: '/financing/facilities', label: 'Financing Facilities', icon: Landmark}
        ]
    },
    {kind: 'link', to: '/workflows', label: 'Workflows', icon: GitBranch, keywords: ['workflow management']},
    {kind: 'link', to: '/credit-lines', label: 'Credit Lines', icon: CreditCard, keywords: ['credit line management']},
    {
        kind: 'group', id: 'operations', label: 'Operations', icon: SlidersHorizontal, children: [
            {kind: 'link', to: '/operations/products', label: 'Product Maintenance', icon: Package},
            {kind: 'link', to: '/operations/schemes', label: 'Scheme Maintenance', icon: Layers},
            {
                kind: 'link', to: '/operations/rates', label: 'Rate Maintenance', icon: Percent,
                keywords: ['fixed deposit rate maintenance', 'base rate', 'scheme rate']
            },
            {kind: 'link', to: '/operations/reports', label: 'Operational Reports', icon: ChartColumn},
            {kind: 'link', to: '/operations/gl-accounts', label: 'GL Account Maintenance', icon: BookOpen,
                keywords: ['general ledger', 'account maintenance']}
        ]
    },
    {
        kind: 'group', id: 'transactions', label: 'Transactions', icon: ArrowLeftRight, children: [
            {kind: 'link', to: '/transactions/posting', label: 'Transaction Posting', icon: Send, roles: ['MAKER', 'ADMIN']},
            {kind: 'link', to: '/transactions/inquiry', label: 'Transaction Inquiry', icon: Search},
            {kind: 'link', to: '/transactions/reversal', label: 'Transaction Reversal', icon: RotateCcw, roles: ['MAKER', 'ADMIN']},
            {kind: 'link', to: '/transactions/reconciliation', label: 'Transaction Reconciliation', icon: Scale, roles: ['ADMIN']},
            {kind: 'link', to: '/transactions/audit', label: 'Transaction Audit', icon: FileClock, roles: ['ADMIN']}
        ]
    },
    {
        kind: 'group', id: 'administration', label: 'Administration', icon: ShieldCheck, roles: ['ADMIN'], children: [
            {kind: 'link', to: '/admin/users', label: 'User Management', icon: UsersRound},
            {kind: 'link', to: '/admin/monitoring', label: 'System Monitoring', icon: Activity},
            {kind: 'link', to: '/admin/activity-audit', label: 'Activity Audit', icon: FileClock},
            {kind: 'link', to: '/admin/parameters', label: 'System Parameters', icon: Settings2},
            {kind: 'link', to: '/admin/logs', label: 'Application Logging', icon: Activity}
        ]
    }
];

function sectionForPath(pathname: string): SidebarSection | null {
    if (pathname.startsWith('/financing/')) return 'financing';
    if (pathname.startsWith('/admin/')) return 'administration';
    if (pathname.startsWith('/operations/')) return 'operations';
    if (pathname.startsWith('/transactions/')) return 'transactions';
    return null;
}

function isAllowed(entry: NavigationEntry, session: ReturnType<typeof useAuth>['session']): boolean {
    return hasAnyRole(session, entry.roles ?? []);
}

function visibleChildren(group: NavigationGroup, session: ReturnType<typeof useAuth>['session']) {
    return group.children.filter(entry => isAllowed(entry, session));
}

function flattenLinks(
    entries: NavigationEntry[],
    session: ReturnType<typeof useAuth>['session'],
    parents: string[] = []
): SearchResult[] {
    return entries.flatMap(entry => {
        if (!isAllowed(entry, session)) return [];
        if (entry.kind === 'link') return [{...entry, parents}];
        return flattenLinks(visibleChildren(entry, session), session, [...parents, entry.label]);
    });
}

function escapeRegExp(value: string) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function matchesNavigation(text: string, query: string) {
    const wildcard = [...query.trim()].map(character => {
        if (character === '*' || character === '%') return '.*';
        if (character === '?') return '.';
        return escapeRegExp(character);
    }).join('');
    return new RegExp(`.*${wildcard}.*`, 'i').test(text);
}

export function Sidebar({collapsed, onToggle}: {collapsed: boolean; onToggle: () => void}) {
    const {session} = useAuth();
    const location = useLocation();
    const navigate = useNavigate();
    const [freshLoginPath] = useState<string | null>(() => {
        const state = location.state as {sidebarFreshLogin?: boolean} | null;
        return state?.sidebarFreshLogin ? location.pathname : null;
    });
    const [manualExpansion, setManualExpansion] = useState<{
        locationKey: string;
        section: SidebarSection | null
    } | null>(null);
    const [searchValue, setSearchValue] = useState('');
    const [searchFocused, setSearchFocused] = useState(false);
    const expandedSection = freshLoginPath === location.pathname ? null :
        manualExpansion?.locationKey === location.key ? manualExpansion.section : sectionForPath(location.pathname);
    const searchQuery = searchValue.trim();
    const searchActive = Boolean(searchQuery) || searchFocused;
    const visibleNavigation = navigation.filter(entry => {
        if (!isAllowed(entry, session)) return false;
        return entry.kind === 'link' || visibleChildren(entry, session).length > 0;
    });
    const accessibleLinks = flattenLinks(visibleNavigation, session);
    const uniqueResults = new Map<string, SearchResult>();
    if (searchQuery) {
        accessibleLinks.forEach(result => {
            const searchable = [result.label, ...(result.keywords ?? []), ...result.parents].join(' ');
            if (matchesNavigation(searchable, searchQuery) && !uniqueResults.has(result.to)) {
                uniqueResults.set(result.to, result);
            }
        });
    }
    const searchResults = [...uniqueResults.values()];
    const groupedResults = searchResults.reduce<Record<string, SearchResult[]>>((groups, result) => {
        const parent = result.parents.join(' · ') || 'Workspace';
        (groups[parent] ??= []).push(result);
        return groups;
    }, {});

    useEffect(() => {
        const state = location.state as (Record<string, unknown> & {sidebarFreshLogin?: boolean}) | null;
        if (!state?.sidebarFreshLogin) return;
        const remainingState = {...state};
        delete remainingState.sidebarFreshLogin;
        navigate(`${location.pathname}${location.search}${location.hash}`, {
            replace: true,
            state: Object.keys(remainingState).length ? remainingState : null
        });
    }, [location.hash, location.pathname, location.search, location.state, navigate]);

    const toggleSection = (section: SidebarSection) => {
        setManualExpansion({
            locationKey: location.key,
            section: expandedSection === section ? null : section
        });
    };
    const clearSearch = () => {
        setSearchValue('');
        setSearchFocused(false);
    };
    const renderLink = (entry: NavigationLink, className = 'nav-item') => {
        const Icon = entry.icon;
        return <NavLink key={entry.to} to={entry.to} end={entry.to === '/'} title={collapsed ? entry.label : undefined}
                        className={({isActive}) => `${className}${isActive ? ' active' : ''}`}
                        onClick={searchQuery ? clearSearch : undefined}>
            <Icon size={className.includes('search-result') ? 16 : 19}/><span>{entry.label}</span>
        </NavLink>;
    };
    const renderEntry = (entry: NavigationEntry): ReactNode => {
        if (entry.kind === 'link') return renderLink(entry);
        const children = visibleChildren(entry, session);
        if (!children.length) return null;
        const Icon = entry.icon;
        const active = sectionForPath(location.pathname) === entry.id;
        const isExpanded = expandedSection === entry.id;
        return <div className="cbms-sidebar-group" key={entry.id}>
            <button type="button" className={`nav-item cbms-sidebar-parent ${active ? 'active' : ''}`}
                    aria-expanded={isExpanded} aria-label={`Toggle ${entry.label} menu`}
                    onClick={() => toggleSection(entry.id)}>
                <Icon size={19}/><span>{entry.label}</span>
                {!collapsed && (isExpanded ? <ChevronDown size={16}/> : <ChevronRight size={16}/>)}
            </button>
            {isExpanded && !collapsed && <div className="cbms-sidebar-submenu">
                {children.map(child => renderEntry(child))}
            </div>}
        </div>;
    };

    return <aside className={`sidebar ${collapsed ? 'collapsed' : ''} ${searchActive && collapsed ? 'search-active' : ''}`}>
        <NavLink to="/" className="brand cbms-brand-link" aria-label="Go to Overview">
            <div className="brand-mark"><Building2 size={22}/></div>
            {!collapsed && <div><strong>Core Banking</strong><small>Management System</small></div>}
        </NavLink>
        <div className="sidebar-caption">{collapsed ? '•••' : 'WORKSPACE'}</div>
        <div className="cbms-sidebar-search" role="search">
            <Search size={16} aria-hidden="true"/>
            <input type="search" value={searchValue} placeholder={collapsed ? '' : 'Search'}
                   aria-label="Search" title="Search"
                   onChange={event => setSearchValue(event.target.value)}
                   onFocus={() => setSearchFocused(true)}
                   onBlur={() => setSearchFocused(false)}
                   onKeyDown={event => {
                       if (event.key === 'Escape') clearSearch();
                   }}/>
            {searchValue && <button type="button" className="cbms-sidebar-search-clear"
                                    aria-label="Clear navigation search" title="Clear search"
                                    onMouseDown={event => event.preventDefault()}
                                    onClick={clearSearch}><X size={15}/></button>}
        </div>
        <nav aria-label="Main navigation">
            {searchQuery ? <div className="cbms-sidebar-search-results" aria-live="polite">
                {searchResults.length ? Object.entries(groupedResults).map(([parent, results]) => <section
                    className="cbms-sidebar-search-group" key={parent}>
                    <h2>{parent.split(' · ').map((part, index) => <span key={`${part}-${index}`}>
                        {index > 0 && <span aria-hidden="true"> · </span>}
                        {part}
                    </span>)}</h2>
                    {results.map(result => {
                        const Icon = result.icon;
                        return <NavLink key={result.to} to={result.to} end={result.to === '/'}
                                        className={({isActive}) => `nav-item cbms-sidebar-search-result${isActive ? ' active' : ''}`}
                                        onClick={clearSearch}>
                            <Icon size={16}/><span>{result.label}</span>
                        </NavLink>;
                    })}
                </section>) : <p className="cbms-sidebar-no-results" role="status">No matching screens found.</p>}
            </div> : <>
                {visibleNavigation.map(entry => renderEntry(entry))}
            </>}
        </nav>
        <div className="sidebar-bottom">
            <div className="system-status"><Activity size={17}/><span>Authenticated session</span></div>
            <button className="collapse-button" onClick={onToggle}
                    aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}>{collapsed ?
                <ChevronRight size={18}/> : <><ChevronLeft size={18}/> Collapse sidebar</>}</button>
        </div>
    </aside>;
}
