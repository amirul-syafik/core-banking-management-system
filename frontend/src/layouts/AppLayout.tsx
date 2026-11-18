import {useEffect, useState} from 'react';
import {Link, Outlet, useLocation} from 'react-router-dom';
import {KeyRound, LogOut, UserRound} from 'lucide-react';
import {Sidebar} from '../components/Sidebar';
import {useAuth} from '../auth/useAuth';
import {ApprovalNotificationBell} from '../components/ApprovalNotificationBell';

function ScrollToTopOnNavigation() {
    const {pathname, search} = useLocation();
    useEffect(() => {
        window.scrollTo({top: 0, left: 0, behavior: 'instant'});
        document.querySelector('.main-area')?.scrollTo({top: 0, left: 0, behavior: 'instant'});
    }, [pathname, search]);
    return null;
}

export function AppLayout() {
    const [collapsed, setCollapsed] = useState(false);
    const {session, logout} = useAuth();
    const scrollOnPageAction = (event: React.MouseEvent<HTMLDivElement>) => {
        const target = event.target;
        if (!(target instanceof Element)) return;
        const control = target.closest<HTMLElement>('[data-cbms-scroll-top], [data-cbms-pagination-control], button');
        if (!control || control.getAttribute('aria-disabled') === 'true' || ('disabled' in control && (control as HTMLButtonElement).disabled)) return;
        const isPagination = control.hasAttribute('data-cbms-pagination-control') ||
            control.querySelector('.cbms-page-arrow') !== null;
        if (isPagination) {
            const fallbackSection = control.closest<HTMLElement>('section');
            let section: HTMLElement | null = control.parentElement;
            while (section && section !== document.body && !section.querySelector('table')) section = section.parentElement;
            (section?.querySelector('table') ? section : fallbackSection)?.scrollIntoView({block: 'start', behavior: 'smooth'});
            return;
        }
        if (!control.hasAttribute('data-cbms-scroll-top')) return;
        window.scrollTo({top: 0, left: 0, behavior: 'instant'});
        document.querySelector('.main-area')?.scrollTo({top: 0, left: 0, behavior: 'instant'});
    };
    return <div className="app-shell" onClickCapture={scrollOnPageAction}><ScrollToTopOnNavigation/><Sidebar
        collapsed={collapsed} onToggle={() => setCollapsed(v => !v)}/>
        <div className="main-area">
            <header className="topbar">
                <Link to="/" className="topbar-title cbms-topbar-home" title="Go to Overview">Core Banking Management
                    System</Link>
                <div className="topbar-actions">
                    <ApprovalNotificationBell/>
                    <div className="user-info"><UserRound
                        size={17}/><span>{session?.username}</span><small>{session?.roles.map(role => role.charAt(0) + role.slice(1).toLowerCase()).join(', ') || 'No assigned role'}</small>
                    </div>
                    <Link className="logout-button" to="/change-password" title="Change Password"><KeyRound
                        size={17}/><span>Change Password</span></Link>
                    <button className="logout-button" onClick={logout} title="Sign out"><LogOut size={17}/><span>Sign out</span>
                    </button>
                </div>
            </header>
            <main className="page-content"><Outlet/></main>
        </div>
    </div>;
}
