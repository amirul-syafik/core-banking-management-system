import {useCallback, useEffect, useMemo, useState} from 'react';
import {Link} from 'react-router-dom';
import {
    ArrowDownLeft,
    ArrowLeft,
    ArrowRight,
    ArrowUpRight,
    Landmark,
    RefreshCw,
    Users,
    Wallet,
    Workflow
} from 'lucide-react';
import {api} from '../api/client';
import './dashboard.css';
import './dashboard-typography.css';

type CurrencyBalance = { currency: string; ledgerBalance: number; availableBalance: number };
type CurrencyAmount = { currency: string; approvedAmount?: number; principalAmount?: number };
type MonthlyTransaction = { month: string; currency: string; transactionCount: number; transactionAmount: number };
type PortfolioRow = { product: string; status: string; currency: string; facilityCount: number; totalAmount: number };
type DashboardData = {
    customers: number; accounts: number; financingFacilities: number;
    fixedDeposits: number; collaterals: number;
    accountBalancesByCurrency: CurrencyBalance[];
    financingApprovedByCurrency: CurrencyAmount[];
    fixedDepositPrincipalByCurrency: CurrencyAmount[];
    transactionMonthly: MonthlyTransaction[];
    portfolioByStatus: PortfolioRow[];
};
const safeNumber = (value: unknown): number => {
    const numeric = Number(value ?? 0);
    return Number.isFinite(numeric) ? numeric : 0;
};
const money = (value: number, currency: string) => new Intl.NumberFormat('en-MY', {
    style: 'currency',
    currency,
    maximumFractionDigits: 2
}).format(safeNumber(value));
const integer = (value: number) => new Intl.NumberFormat('en-MY').format(safeNumber(value));

export function Dashboard() {
    const [data, setData] = useState<DashboardData | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [currency, setCurrency] = useState('MYR');
    const [metric, setMetric] = useState<'COUNT' | 'AMOUNT'>('COUNT');
    const [portfolioStatus, setPortfolioStatus] = useState<'COMPLETE' | 'PENDING'>('PENDING');
    const load = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            const response = await api.get<DashboardData>('/reports/dashboard');
            setData(response.data);
        } catch {
            setData(null);
            setError('Unable to load banking overview. Please try again.');
        } finally {
            setLoading(false);
        }
    }, []);
    useEffect(() => {
        void Promise.resolve().then(load);
    }, [load]);
    const transactionCurrencies = useMemo(() => [...new Set((data?.transactionMonthly ?? []).map(x => x.currency))].sort(), [data]);
    const months = useMemo(() => {
        const now = new Date();
        return Array.from({length: 6}, (_, i) => {
            const d = new Date(now.getFullYear(), now.getMonth() - 5 + i, 1);
            return {
                key: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`,
                label: d.toLocaleString('en-MY', {month: 'short'})
            };
        });
    }, []);
    const monthly = months.map(month => {
        const rows = (data?.transactionMonthly ?? []).filter(x => x.month === month.key && x.currency === currency);
        return {
            ...month,
            count: rows.reduce((n, x) => n + safeNumber(x.transactionCount ?? (x as MonthlyTransaction & {
                transactioncount?: number
            }).transactioncount), 0),
            amount: rows.reduce((n, x) => n + safeNumber(x.transactionAmount ?? (x as MonthlyTransaction & {
                transactionamount?: number
            }).transactionamount), 0)
        };
    });
    const maxMetric = Math.max(1, ...monthly.map(x => metric === 'COUNT' ? x.count : x.amount));
    const portfolio = (data?.portfolioByStatus ?? []).filter(x => portfolioStatus === 'COMPLETE' ? x.status === 'CLOSED' : x.status !== 'CLOSED');
    const stats = data ? [
        {
            title: 'Customers',
            value: integer(data.customers),
            detail: 'Total customer records',
            icon: Users,
            tone: 'blue'
        },
        {title: 'Accounts', value: integer(data.accounts), detail: 'Total bank accounts', icon: Wallet, tone: 'teal'},
        {
            title: 'Financing Facilities',
            value: integer(data.financingFacilities),
            detail: 'Total financing facilities',
            icon: Landmark,
            tone: 'purple'
        },
        {
            title: 'Fixed Deposits',
            value: integer(data.fixedDeposits),
            detail: 'Total fixed deposit records',
            icon: Workflow,
            tone: 'orange'
        },
    ] : [];
    return <>
        <div className="page-heading">
            <div>
                <h1>Banking overview</h1>
                <p>Current banking records and portfolio balances.</p></div>
            <button type="button" className="dashboard-refresh" onClick={() => void load()} disabled={loading}>
                <RefreshCw size={16}/> {loading ? 'Loading…' : 'Refresh'}
            </button>
        </div>
        {error && <div className="dashboard-error" role="alert">{error}</div>}
        {loading && !data ? <div className="panel dashboard-state" role="status">Loading banking overview…</div> : null}
        {data && <>
            <div className="stat-grid">{stats.map(({title, value, detail, icon: Icon, tone}) => <article
                className="stat-card" key={title}>
                <div className="stat-top"><span>{title}</span><span className={`stat-icon ${tone}`}><Icon
                    size={20}/></span></div>
                <strong>{value}</strong><small className="metric-detail">{detail}</small>
            </article>)}</div>
            <div className="dashboard-grid">
                <section className="panel">
                    <div className="panel-head dashboard-chart-head">
                        <div><h2>Transaction activity</h2><p>Posted transactions over the last six months</p></div>
                        <div className="dashboard-chart-controls">
                            <select aria-label="Transaction metric" className="dashboard-select" value={metric}
                                    onChange={e => setMetric(e.target.value as 'COUNT' | 'AMOUNT')}>
                                <option value="COUNT">Total Transaction Count</option>
                                <option value="AMOUNT">Total Transaction Amount</option>
                            </select>
                            <select aria-label="Transaction currency" className="dashboard-select" value={currency}
                                    onChange={e => setCurrency(e.target.value)}>
                                {transactionCurrencies.length === 0 && <option value="MYR">MYR</option>}
                                {transactionCurrencies.length > 0 && !transactionCurrencies.includes(currency) &&
                                    <option value={currency}>{currency} (no records)</option>}
                                {transactionCurrencies.map(ccy => <option key={ccy} value={ccy}>{ccy}</option>)}
                            </select>
                        </div>
                    </div>
                    <div
                        className="dashboard-y-label">{metric === 'COUNT' ? 'Transaction count' : `Transaction amount (${currency})`}</div>
                    <div className="dashboard-xy-chart" role="img"
                         aria-label={`${metric === 'COUNT' ? 'Transaction count' : 'Transaction amount'} by month in ${currency}`}>
                        <div className="dashboard-xy-yaxis">
                            <span>{metric === 'COUNT' ? integer(maxMetric) : money(maxMetric, currency)}</span><span>{metric === 'COUNT' ? integer(maxMetric / 2) : money(maxMetric / 2, currency)}</span><span>0</span>
                        </div>
                        <div className="dashboard-xy-plot">
                            {monthly.map(row => {
                                const value = metric === 'COUNT' ? row.count : row.amount;
                                return <div className="dashboard-xy-col" key={row.key}
                                            title={`${row.key}: ${metric === 'COUNT' ? integer(value) : money(value, currency)}`}>
                                    <div className="dashboard-xy-bar-space">
                                        <div className="dashboard-xy-bar"
                                             style={{height: `${value === 0 ? 0 : Math.max(2, value / maxMetric * 100)}%`}}/>
                                    </div>
                                    <span>{row.label}</span>
                                </div>;
                            })}
                        </div>
                    </div>
                    <p className="dashboard-hint">X: Month ·
                        Y: {metric === 'COUNT' ? 'Transaction count' : `Transaction amount (${currency})`}. Only POSTED
                        transactions are included.</p>
                </section>
                <section className="panel quick-panel">
                    <div className="panel-head">
                        <div><h2>Quick actions</h2><p>Navigate to banking workflows</p></div>
                    </div>
                    <div className="action-list dashboard-actions">
                        <Link to="/transactions/posting"><span className="action-icon purple"><ArrowRight
                            size={19}/></span><span><strong>Transfers & posting</strong><small>Open transactions workspace</small></span><ArrowRight
                            className="action-chevron" size={16}/></Link>
                        <Link to="/accounts"><span className="action-icon teal"><ArrowDownLeft
                            size={19}/></span><span><strong>Account management</strong><small>Open account workspace</small></span><ArrowRight
                            className="action-chevron" size={16}/></Link>
                        <Link to="/customers"><span className="action-icon blue"><ArrowUpRight
                            size={19}/></span><span><strong>Customer management</strong><small>Open customers workspace</small></span><ArrowRight
                            className="action-chevron" size={16}/></Link>
                        <Link to="/financing/applications"><span className="action-icon orange"><ArrowLeft
                            size={19}/></span><span><strong>Apply Financing</strong><small>Open financing workspace</small></span><ArrowRight
                            className="action-chevron" size={16}/></Link>
                    </div>
                </section>
            </div>
            <section className="panel transactions-panel">
                <div className="panel-head dashboard-chart-head">
                    <div><h2>Financing &amp; Credit Lines Portfolio</h2><p>Facility lifecycle status, not individual
                        transaction settlement</p></div>
                    <div className="dashboard-portfolio-toggle" role="group" aria-label="Portfolio status">
                        <button type="button" className={portfolioStatus === 'COMPLETE' ? 'active' : ''}
                                aria-pressed={portfolioStatus === 'COMPLETE'}
                                onClick={() => setPortfolioStatus('COMPLETE')}>COMPLETE
                        </button>
                        <button type="button" className={portfolioStatus === 'PENDING' ? 'active' : ''}
                                aria-pressed={portfolioStatus === 'PENDING'}
                                onClick={() => setPortfolioStatus('PENDING')}>PENDING
                        </button>
                    </div>
                </div>
                <div className="table-scroll">
                    <table>
                        <thead>
                        <tr>
                            <th>PRODUCT</th>
                            <th>STATUS</th>
                            <th>CURRENCY</th>
                            <th>FACILITIES</th>
                            <th>APPROVED AMOUNT / LIMIT</th>
                        </tr>
                        </thead>
                        <tbody>
                        {portfolio.map((row, index) => <tr
                            key={`${row.product}-${row.status}-${row.currency}-${index}`}>
                            <td>{row.product === 'CREDIT_LINE' ? 'Credit Lines' : 'Financing'}</td>
                            <td>{row.status}</td>
                            <td>{row.currency}</td>
                            <td>{integer(safeNumber(row.facilityCount ?? (row as PortfolioRow & {
                                facilitycount?: number
                            }).facilitycount))}</td>
                            <td className="amount">{money(safeNumber(row.totalAmount ?? (row as PortfolioRow & {
                                totalamount?: number
                            }).totalamount), row.currency)}</td>
                        </tr>)}
                        {portfolio.length === 0 && <tr>
                            <td colSpan={5} className="dashboard-empty">No {portfolioStatus.toLowerCase()} facilities
                                found.
                            </td>
                        </tr>}
                        </tbody>
                    </table>
                </div>
                <p className="dashboard-hint">COMPLETE = CLOSED facilities. PENDING = DRAFT, ACTIVE, SUSPENDED or other
                    non-closed facilities. Amounts are approved facility values, not transaction turnover or outstanding
                    balances.</p>
            </section>
        </>}
    </>;
}
