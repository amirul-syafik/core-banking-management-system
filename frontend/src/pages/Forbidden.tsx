import {Link} from 'react-router-dom';

export function Forbidden() {
    return <div className="access-denied"><h1>Access restricted</h1><p>Your current role does not permit access to this
        page.</p><Link to="/">Return to overview</Link></div>;
}
