import {useLocation} from 'react-router-dom';
import {Construction} from 'lucide-react';

const descriptions: Record<string, string> = {
    '/customers': 'Customer management screens will be connected to the existing customer APIs soon.',
    '/accounts': 'Account search, balances, and account detail views will be built soon.',
    '/transactions': 'Transaction history and details will be implemented soon.',
    '/operations': 'Transfers, posting, reversal, and approval workflows will be implemented soon.',
    '/audit': 'Role-based access and audit views will be connected after authentication integration.',
};

export function Placeholder() {
    const {pathname} = useLocation();
    const title = pathname.slice(1).split('-').map(w => w[0]?.toUpperCase() + w.slice(1)).join(' ') || 'Page';
    return <>
        <div className="eyebrow">WORKSPACE / {title.toUpperCase()}</div>
        <h1>{title}</h1>
        <div className="placeholder"><Construction size={36}/><h2>{title} module</h2>
            <p>{descriptions[pathname] ?? 'This page is not yet available.'}</p></div>
    </>;
}
