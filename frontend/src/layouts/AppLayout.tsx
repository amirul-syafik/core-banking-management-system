import {useEffect, useState} from 'react';
import {Outlet, useLocation} from 'react-router-dom';
import {Bell, Search, LogOut, UserRound} from 'lucide-react';
import {Sidebar} from '../components/Sidebar';
import {useAuth} from '../auth/useAuth';

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
        const control = target.closest<HTMLElement>('[data-cbms-scroll-top]');
        if (!control || control.getAttribute('aria-disabled') === 'true' || ('disabled' in control && (control as HTMLButtonElement).disabled)) return;
        window.scrollTo({top: 0, left: 0, behavior: 'instant'});
        document.querySelector('.main-area')?.scrollTo({top: 0, left: 0, behavior: 'instant'});
    };
    return <div className="app-shell" onClickCapture={scrollOnPageAction}><ScrollToTopOnNavigation/><Sidebar
        collapsed={collapsed} onToggle={() => setCollapsed(v => !v)}/>
        <div className="main-area">
            <header className="topbar">
                <div className="topbar-title">Core Banking Management System</div>
                <div className="topbar-actions"><Search size={19}/><Bell size={19}/>
                    <div className="user-info"><UserRound
                        size={17}/><span>{session?.username}</span><small>{session?.roles.join(', ') || 'NO ROLE'}</small>
                    </div>
                    <button className="logout-button" onClick={logout} title="Sign out"><LogOut size={17}/><span>Sign out</span>
                    </button>
                </div>
            </header>
            <main className="page-content"><Outlet/></main>
        </div>
    </div>;
}
