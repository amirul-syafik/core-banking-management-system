const businessLabels: Record<string, string> = {
    FD_PLACEMENT: 'Fixed Deposit Placement',
    FD_INTEREST_ACCRUAL: 'Fixed Deposit Interest Accrual',
    FD_INTEREST_PAYOUT: 'Fixed Deposit Interest Payout',
    FD_RENEWAL: 'Fixed Deposit Renewal',
    FD_PREMATURE_WITHDRAWAL: 'Fixed Deposit Premature Withdrawal',
    FD_MATURITY_SETTLEMENT: 'Fixed Deposit Maturity Settlement',
    RECOND: 'Reconditioned',
    MYKAD: 'MyKad',
    IN_PROGRESS: 'In Progress'
};

const knownBusinessValues = new Set([
    'ACTIVE', 'INACTIVE', 'BLOCKED', 'PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED', 'CLOSED',
    'DRAFT', 'COMPLETED', 'FAILED', 'DISABLED', 'DORMANT', 'FROZEN', 'MATURED', 'RENEWED',
    'SUCCESS', 'RECONCILED', 'PAID', 'CANCELLED', 'OVERDUE', 'UNPAID', 'EXPIRED', 'UNKNOWN',
    'REGISTERED', 'IN_REVIEW', 'IN_PROGRESS', 'VERIFIED', 'LOW', 'MEDIUM', 'HIGH', 'MALE',
    'FEMALE', 'OTHER', 'NOT_SPECIFIED', 'SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED', 'MOBILE',
    'PHONE', 'EMAIL', 'WORK_PHONE', 'RESIDENTIAL', 'BUSINESS', 'MAILING', 'SAVINGS', 'CURRENT',
    'INDIVIDUAL', 'CORPORATE', 'FIXED_DEPOSIT', 'TERM_DEPOSIT', 'ISLAMIC', 'CONVENTIONAL',
    'REVOLVING_CREDIT', 'FLOOR_STOCKING', 'VEHICLE', 'PROPERTY', 'CASH', 'ASSET', 'LIABILITY',
    'EQUITY', 'INCOME', 'EXPENSE', 'FIXED', 'FLOATING', 'POSTED', 'REQUESTED', 'REVERSED',
    'DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'REVERSAL', 'STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN',
    'CAR', 'MOTORCYCLE', 'VAN', 'LORRY', 'BUS', 'NEW', 'USED', 'RECOND', 'SUBMITTED', 'BOOKED',
    'CREATED', 'ALL', 'MATCHED', 'DISCREPANCY', 'UNAVAILABLE', 'CLAIMED', 'FINANCED',
    'CURRENT', 'PREVIOUS', 'FULL_TIME', 'PART_TIME', 'SELF_EMPLOYED', 'CONTRACT', 'UNEMPLOYED',
    'RETIRED', 'SIGNATORY', 'DIRECTOR', 'REPRESENTATIVE', 'ACCOUNT_OPERATOR', 'SPOUSE',
    'JOINT_APPLICANT', 'GUARANTOR', 'SHAREHOLDER', 'PARENT', 'SUBSIDIARY', 'PARENT_GUARDIAN',
    'TAWARRUQ', 'WAKALAH', 'MUDARABAH', 'MURABAHAH', 'IJARAH', 'HIRE_PURCHASE',
    'PERSONAL_FINANCING', 'WHOLESALE_FINANCING', 'TERM_LOAN', 'CREDIT_LINE', 'INITIAL',
    'REVALUATION', 'FLAT', 'REDUCING_BALANCE', 'ISLAMIC_PROFIT', 'MATURITY_SETTLEMENT',
    'BANK_TRANSFER', 'INTERNAL_TRANSFER', 'DEBIT', 'CREDIT', 'WARNING', 'CRITICAL'
]);

/** Formats business codes for display without changing their API/database value. */
export function formatBusinessValue(value: string | null | undefined): string {
    const code = String(value ?? '').trim();
    if (!code) return '';
    const normalized = code.toUpperCase();
    const knownLabel = businessLabels[normalized];
    if (knownLabel) return knownLabel;
    if (!knownBusinessValues.has(normalized)) return code;
    return code.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, character => character.toUpperCase());
}
