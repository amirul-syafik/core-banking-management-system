package com.cbms.service.islamic.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure dry-run controls. There is no repository, no event rule
 * catalogue, no financial posting, no payout and no balance mutation here.
 * Bank-approved contracts must supply all monetary amounts and GL legs.
 * Validation of a proposal is NOT authorization to book it.
 */
public final class IslamicFinancialControlValidator {
    private IslamicFinancialControlValidator() { }

    public enum Side { DEBIT, CREDIT }
    public record JournalLeg(String glCode, Side side, String currency, BigDecimal amount) { }
    public record JournalProposal(String contractType, String financialEventCode,
                                  String approvedAccountingPolicyReference,
                                  String sourceExternalEventReference,
                                  String idempotencyKey, List<JournalLeg> legs) { }
    public record SettlementProof(String externalReference, String externalSystem, Instant occurredAt,
                                  String currency, BigDecimal settledAmount,
                                  String linkedVerifiedContractEventReference) { }
    public record SettlementExpectation(String approvedSettlementRuleReference,
                                        String sourceExternalEventReference,
                                        String currency, BigDecimal authorizedAmount) { }
    public record ReversalProof(String originalSettledReference, String independentReversalReference,
                                String reviewReference, boolean alreadyReversed,
                                boolean originalPostedAndSettled) { }
    public record Validation(boolean structurallyValid, List<String> blockers) {
        public Validation { blockers = List.copyOf(blockers); }
        // A structurally valid journal or settlement is NEVER authorized for financial posting.
        public boolean postingAuthorized() { return false; }
        public boolean settlementAuthorized() { return false; }
        public boolean reversalAuthorized() { return false; }
    }

    public static Validation validateJournal(JournalProposal proposal) {
        List<String> blocks = new ArrayList<>();
        if (proposal == null) return invalid("JOURNAL_PROPOSAL_MISSING");
        if (!validContract(proposal.contractType())) blocks.add("UNSUPPORTED_ISLAMIC_CONTRACT");
        if (!present(proposal.financialEventCode())) blocks.add("EVENT_CODE_REQUIRED");
        if (!present(proposal.approvedAccountingPolicyReference())) blocks.add("ACCOUNTING_POLICY_REFERENCE_REQUIRED");
        if (!present(proposal.sourceExternalEventReference())) blocks.add("VERIFIED_EXTERNAL_EVENT_REFERENCE_REQUIRED");
        if (!present(proposal.idempotencyKey())) blocks.add("IDEMPOTENCY_KEY_REQUIRED");
        List<JournalLeg> legs = proposal.legs();
        if (legs == null || legs.size() < 2 || legs.size() > 30) return append(blocks, "JOURNAL_LEG_COUNT_INVALID");
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        Set<String> currencies = new HashSet<>();
        for (JournalLeg leg : legs) {
            if (leg == null || !present(leg.glCode()) || leg.side() == null ||
                !present(leg.currency()) || !positive(leg.amount())) {
                blocks.add("JOURNAL_LEG_INVALID");
                continue;
            }
            if (leg.amount().scale() > 2) blocks.add("JOURNAL_AMOUNT_SCALE_INVALID");
            currencies.add(leg.currency());
            if (leg.side() == Side.DEBIT) debit = debit.add(leg.amount());
            else credit = credit.add(leg.amount());
        }
        if (currencies.size() != 1) blocks.add("JOURNAL_CURRENCY_MISMATCH");
        if (debit.signum() <= 0 || credit.signum() <= 0 || debit.compareTo(credit) != 0)
            blocks.add("UNBALANCED_JOURNAL");
        return new Validation(blocks.isEmpty(), blocks);
    }

    public static Validation validateSettlement(SettlementExpectation expected, SettlementProof actual, Instant now) {
        List<String> blocks = new ArrayList<>();
        if (expected == null || actual == null || now == null) return invalid("SETTLEMENT_EVIDENCE_MISSING");
        if (!present(expected.approvedSettlementRuleReference())) blocks.add("SETTLEMENT_RULE_REFERENCE_REQUIRED");
        if (!present(expected.sourceExternalEventReference())) blocks.add("VERIFIED_EXTERNAL_EVENT_REFERENCE_REQUIRED");
        if (!present(actual.externalReference()) || !present(actual.externalSystem()))
            blocks.add("COUNTERPARTY_PROOF_INCOMPLETE");
        if (actual.occurredAt() == null || actual.occurredAt().isAfter(now))
            blocks.add("INVALID_SETTLEMENT_TIMESTAMP");
        if (!Objects.equals(expected.currency(), actual.currency()) || !present(expected.currency()))
            blocks.add("SETTLEMENT_CURRENCY_MISMATCH");
        if (!positive(expected.authorizedAmount()) || !positive(actual.settledAmount()) ||
            expected.authorizedAmount().compareTo(actual.settledAmount()) != 0)
            blocks.add("SETTLEMENT_AMOUNT_MISMATCH");
        if (!Objects.equals(expected.sourceExternalEventReference(), actual.linkedVerifiedContractEventReference()))
            blocks.add("SETTLEMENT_SOURCE_EVENT_MISMATCH");
        return new Validation(blocks.isEmpty(), blocks);
    }

    public static Validation validateReversal(ReversalProof reversal) {
        List<String> blockers = new ArrayList<>();
        if (reversal == null) return invalid("REVERSAL_EVIDENCE_MISSING");
        if (!present(reversal.originalSettledReference()) || !present(reversal.independentReversalReference()) ||
            reversal.originalSettledReference().equals(reversal.independentReversalReference()))
            blockers.add("REVERSAL_SOURCE_REFERENCE_INVALID");
        if (!present(reversal.reviewReference())) blockers.add("INDEPENDENT_REVERSAL_REVIEW_REQUIRED");
        if (reversal.alreadyReversed()) blockers.add("DUPLICATE_REVERSAL");
        if (!reversal.originalPostedAndSettled()) blockers.add("ORIGINAL_NOT_POSTED_SETTLED");
        return new Validation(blockers.isEmpty(), blockers);
    }

    private static boolean validContract(String code) {
        return "TAWARRUQ".equals(code) || "WAKALAH".equals(code) || "MUDARABAH".equals(code);
    }
    private static boolean positive(BigDecimal amount) { return amount != null && amount.signum() > 0; }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static Validation invalid(String reason) { return new Validation(false, List.of(reason)); }
    private static Validation append(List<String> blockers, String reason) {
        blockers.add(reason);
        return new Validation(false, blockers);
    }
}
