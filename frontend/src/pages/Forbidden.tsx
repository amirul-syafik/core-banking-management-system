import {BackNavigation} from '../components/UiStandards';

export function Forbidden() {
    return (
        <div className="access-denied">
            <h1>Access restricted</h1>
            <p>Your current role does not permit access to this page.</p>
            <BackNavigation label="Back to Main Screen" to="/"/>
        </div>
    );
}
