const BUSINESS_REFERENCE_FIELDS = [
    'applRef', 'clFacRef', 'collateralRef', 'facRef', 'fdReference', 'placementReference',
    'postingReference', 'payoutReference', 'maturityReference', 'renewalReference',
    'withdrawalReference', 'disbRef', 'closureRef', 'repayRef', 'stockReference',
    'txnRef', 'transactionReference', 'reversalTxnRef', 'instanceRef', 'taskRef',
    'policy_ref', 'execution_ref', 'timeline_ref', 'mapping_ref', 'reference'
] as const;

/** Reads a human-facing business reference from a resource or Axios response body. */
export function businessReferenceFromResponse(value: unknown): string | null {
    if (!value || typeof value !== 'object') return null;
    const outer = value as Record<string, unknown>;
    const payload = outer.data && typeof outer.data === 'object'
        ? outer.data as Record<string, unknown>
        : outer;
    for (const field of BUSINESS_REFERENCE_FIELDS) {
        const reference = payload[field];
        if (typeof reference === 'string' && reference.trim()) return reference.trim();
    }
    return null;
}
