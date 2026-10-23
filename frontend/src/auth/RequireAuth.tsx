import {Navigate, Outlet, useLocation} from 'react-router-dom';
import {useAuth} from './useAuth';
import {hasAnyRole, type Role} from './session';

export function RequireAuth({roles = []}: { roles?: Role[] }) {
    const {session} = useAuth();
    const location = useLocation();
    if (!session) return <Navigate to="/login" replace state={{from: location.pathname}}/>;
    if (!hasAnyRole(session, roles)) return <Navigate to="/forbidden" replace/>;
    return <Outlet/>;
}
