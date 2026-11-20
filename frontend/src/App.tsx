import {ToastProvider} from './components/ToastProvider';
import {BrowserRouter, Navigate, Route, Routes} from 'react-router-dom';
import {AuthProvider} from './auth/AuthContext';
import {RequireAuth} from './auth/RequireAuth';
import {AppLayout} from './layouts/AppLayout';
import {Dashboard} from './pages/Dashboard';
import {Login} from './pages/Login';
import ForgotPassword from './pages/ForgotPassword';
import ChangePassword from './pages/ChangePassword';
import {Forbidden} from './pages/Forbidden';
import Customers from './pages/Customers';
import CustomerProfile from './pages/CustomerProfile';
import Accounts from './pages/Accounts';
import Transactions from './pages/Transactions';
import TransactionInquiry from './pages/TransactionInquiry';
import TransactionReversal from './pages/TransactionReversal';
import TransactionAudit from './pages/TransactionAudit';
import TransactionReconciliation from './pages/TransactionReconciliation';
import FinancingApplications from './pages/FinancingApplications';
import FinancingFacilities from './pages/FinancingFacilities';
import CreditLines from './pages/CreditLines';
import FixedDeposits from './pages/FixedDeposits';
import './auth.css';
import AdminUsers from './pages/AdminUsers';
import AdminLogs from './pages/AdminLogs';
import ProductMaintenance from './pages/ProductMaintenance';
import SchemeMaintenance from './pages/SchemeMaintenance';
import RateMaintenance from './pages/RateMaintenance';
import GlMaintenance from './pages/GlMaintenance';
import Notifications from './pages/Notifications';
import {SessionExpiredOverlay} from './components/SessionExpiredOverlay';
import CollateralManagement from './pages/CollateralManagement';
import VehicleManagement from './pages/VehicleManagement';
import AdminMonitoring from './pages/AdminMonitoring';
import AdminActivityAudit from './pages/AdminActivityAudit';
import SystemParameters from './pages/SystemParameters';
import OperationalReports from './pages/OperationalReports';

export default function App() {
    return <BrowserRouter><AuthProvider><ToastProvider><Routes>
        <Route path="/login" element={<Login/>}/>
        <Route path="/forgot-password" element={<ForgotPassword/>}/>
        <Route element={<RequireAuth/>}><Route path="/forbidden" element={<Forbidden/>}/><Route element={<AppLayout/>}>
            <Route index element={<Dashboard/>}/>
            <Route path="change-password" element={<ChangePassword/>}/>
            <Route path="notifications" element={<Notifications/>}/>
            <Route
                path="/customers"
                element={<Customers/>}
            />
            <Route path="/customers/:custId" element={<CustomerProfile/>}/>
            <Route path="accounts" element={<Accounts/>}/>
            <Route path="transactions" element={<Navigate to="/transactions/inquiry" replace/>}/>
            <Route path="transactions/inquiry" element={<TransactionInquiry/>}/>
            <Route element={<RequireAuth roles={['MAKER', 'ADMIN']}/>}>
                <Route path="transactions/posting" element={<Transactions/>}/>
                <Route path="transactions/reversal" element={<TransactionReversal/>}/>
            </Route>
            <Route element={<RequireAuth roles={['ADMIN']}/>}>
                <Route path="transactions/audit" element={<TransactionAudit/>}/>
                <Route path="transactions/reconciliation" element={<TransactionReconciliation/>}/>
            </Route>
            <Route path="financing/applications" element={<FinancingApplications/>}/>
            <Route path="financing/collaterals" element={<CollateralManagement/>}/>
            <Route path="financing/vehicles" element={<VehicleManagement/>}/>
            <Route path="operations/reports" element={<OperationalReports/>}/>
            <Route path="financing/facilities" element={<FinancingFacilities/>}/>
            <Route path="financing/facilities/:facilityId" element={<FinancingFacilities/>}/>
            <Route path="fixed-deposits" element={<FixedDeposits/>}/>
            <Route path="credit-lines" element={<CreditLines/>}/>
            <Route path="credit-lines/:facilityId" element={<CreditLines/>}/>
            <Route path="operations" element={<Navigate to="/operations/products" replace/>}/>
            <Route path="operations/products" element={<ProductMaintenance/>}/>
            <Route path="operations/schemes" element={<SchemeMaintenance/>}/>
            <Route path="operations/rates" element={<RateMaintenance/>}/>
            <Route path="operations/gl-accounts" element={<GlMaintenance/>}/>
        </Route></Route>
        <Route element={<RequireAuth roles={['ADMIN']}/>}><Route element={<AppLayout/>}><Route path="audit"
                                                                                               element={<Navigate
                                                                                                   to="/admin/logs"
                                                                                                   replace/>}/>
            <Route path="admin/users" element={<AdminUsers/>}/>
            <Route path="admin/logs" element={<AdminLogs/>}/>
            <Route path="admin/monitoring" element={<AdminMonitoring/>}/>
            <Route path="admin/activity-audit" element={<AdminActivityAudit/>}/>
            <Route path="admin/parameters" element={<SystemParameters/>}/></Route></Route>
        <Route path="*" element={<Navigate to="/" replace/>}/>
    </Routes><SessionExpiredOverlay/></ToastProvider></AuthProvider></BrowserRouter>;
}
