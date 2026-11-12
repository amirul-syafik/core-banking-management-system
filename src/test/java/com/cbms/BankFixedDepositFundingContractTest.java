package com.cbms;

import com.cbms.entity.BankAccount;
import com.cbms.entity.BankAccountBalance;
import com.cbms.entity.BankLedgerAccount;
import com.cbms.entity.BankLedgerEntry;
import com.cbms.entity.BankTransaction;
import com.cbms.repository.BankAccountRepository;
import com.cbms.repository.BankAccountBalanceRepository;
import com.cbms.repository.BankLedgerAccountRepository;
import com.cbms.repository.BankLedgerEntryRepository;
import com.cbms.repository.BankTransactionRepository;
import com.cbms.repository.IdempotencyLockRepository;
import com.cbms.service.BankFixedDepositFundingAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Isolated funding-contract tests. Database-backed atomicity requires separate integration tests. */
class BankFixedDepositFundingContractTest {
    private final BankAccountRepository accounts = mock(BankAccountRepository.class);
    private final BankAccountBalanceRepository balances = mock(BankAccountBalanceRepository.class);
    private final BankTransactionRepository transactions = mock(BankTransactionRepository.class);
    private final BankLedgerAccountRepository gls = mock(BankLedgerAccountRepository.class);
    private final BankLedgerEntryRepository entries = mock(BankLedgerEntryRepository.class);
    private final IdempotencyLockRepository locks = mock(IdempotencyLockRepository.class);
    private BankFixedDepositFundingAdapter adapter;
    private BankAccount account;
    private BankAccountBalance balance;

    @BeforeEach
    void setUp() {
        reset(accounts, balances, transactions, gls, entries, locks);
        adapter = new BankFixedDepositFundingAdapter(accounts, balances, transactions, gls, entries, locks);
        account = new BankAccount();
        account.setAcctId(10L);
        account.setCustId(20L);
        account.setAcctSts("ACTIVE");
        account.setCcyCd("MYR");
        account.setAcctTyp("SAVINGS");
        balance = new BankAccountBalance();
        balance.setAcctId(10L);
        balance.setAvailBal(new BigDecimal("100.00"));
        balance.setLedgerBal(new BigDecimal("100.00"));
        when(transactions.findByIdempotencyKey("FD:place-001")).thenReturn(Optional.empty());
        when(accounts.findByIdForUpdate(10L)).thenReturn(Optional.of(account));
        when(balances.findByIdForUpdate(10L)).thenReturn(Optional.of(balance));
    }

    private String fund(BigDecimal amount) {
        return adapter.debitForPlacement(10L, 20L, amount, "place-001", "maker");
    }

    private void noFinancialWrites() {
        verify(transactions, never()).saveAndFlush(any(BankTransaction.class));
        verify(entries, never()).save(any(BankLedgerEntry.class));
        verify(balances, never()).save(any(BankAccountBalance.class));
    }

    @Test
    void rejectsInvalidAmountWithoutFinancialWrites() {
        assertThrows(IllegalArgumentException.class, () -> fund(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> fund(new BigDecimal("10.001")));
        noFinancialWrites();
    }

    @Test
    void rejectsReusedPlacementReferenceBeforeAccountAccess() {
        when(transactions.findByIdempotencyKey("FD:place-001"))
                .thenReturn(Optional.of(new BankTransaction()));
        assertThrows(IllegalArgumentException.class, () -> fund(new BigDecimal("25.00")));
        verify(locks).lock("FD:place-001");
        verify(accounts, never()).findByIdForUpdate(anyLong());
        noFinancialWrites();
    }

    @Test
    void rejectsFundingAccountOfAnotherCustomer() {
        account.setCustId(999L);
        assertThrows(IllegalArgumentException.class, () -> fund(new BigDecimal("25.00")));
        noFinancialWrites();
    }

    @Test
    void rejectsInactiveAccountAndUnsupportedFundingType() {
        account.setAcctSts("CLOSED");
        assertThrows(IllegalArgumentException.class, () -> fund(new BigDecimal("25.00")));
        account.setAcctSts("ACTIVE");
        account.setAcctTyp("FIXED_DEPOSIT");
        assertThrows(IllegalArgumentException.class, () -> fund(new BigDecimal("25.00")));
        noFinancialWrites();
    }

    @Test
    void rejectsInsufficientAvailableOrLedgerBalance() {
        balance.setAvailBal(new BigDecimal("10.00"));
        assertThrows(IllegalArgumentException.class, () -> fund(new BigDecimal("25.00")));
        balance.setAvailBal(new BigDecimal("100.00"));
        balance.setLedgerBal(new BigDecimal("10.00"));
        assertThrows(IllegalArgumentException.class, () -> fund(new BigDecimal("25.00")));
        noFinancialWrites();
    }

    @Test
    void successfulFundingDebitsBalancesAndCreatesBalancedLedgerEntries() {
        BankLedgerAccount demandGl = new BankLedgerAccount();
        demandGl.setGlId(100L);
        demandGl.setCcyCd("MYR");
        demandGl.setActFlg(true);
        BankLedgerAccount fdGl = new BankLedgerAccount();
        fdGl.setGlId(200L);
        fdGl.setCcyCd("MYR");
        fdGl.setActFlg(true);
        when(gls.findByGlCd("200100")).thenReturn(Optional.of(demandGl));
        when(gls.findByGlCd("200200")).thenReturn(Optional.of(fdGl));
        when(transactions.saveAndFlush(any(BankTransaction.class))).thenAnswer(invocation -> {
            BankTransaction transaction = invocation.getArgument(0);
            transaction.setTxnId(50L);
            return transaction;
        });
        String reference = fund(new BigDecimal("25.00"));
        assertNotNull(reference);
        assertTrue(reference.startsWith("TXN-"));
        assertEquals(0, balance.getLedgerBal().compareTo(new BigDecimal("75.00")));
        assertEquals(0, balance.getAvailBal().compareTo(new BigDecimal("75.00")));
        verify(transactions).saveAndFlush(argThat(t -> "FD_PLACEMENT".equals(t.getTxnTyp())
                && "FD:place-001".equals(t.getIdempotencyKey())
                && t.getTxnAmt().compareTo(new BigDecimal("25.00")) == 0));
        verify(entries).save(argThat(e -> "DEBIT".equals(e.getEntrySide())
                && Long.valueOf(100L).equals(e.getGlId())
                && Long.valueOf(10L).equals(e.getAcctId())
                && Long.valueOf(50L).equals(e.getTxnId())
                && e.getEntryAmt().compareTo(new BigDecimal("25.00")) == 0));
        verify(entries).save(argThat(e -> "CREDIT".equals(e.getEntrySide())
                && Long.valueOf(200L).equals(e.getGlId())
                && e.getAcctId() == null
                && Long.valueOf(50L).equals(e.getTxnId())
                && e.getEntryAmt().compareTo(new BigDecimal("25.00")) == 0));
        verify(entries, times(2)).save(any(BankLedgerEntry.class));
        verify(balances).save(balance);
        verify(entries).flush();
        verify(balances).flush();
    }

    @Test
    void fundingAdapterRequiresAnExistingTransaction() throws Exception {
        Transactional annotation = BankFixedDepositFundingAdapter.class
                .getMethod("debitForPlacement", Long.class, Long.class, BigDecimal.class, String.class, String.class)
                .getAnnotation(Transactional.class);
        assertNotNull(annotation);
        assertEquals(Propagation.MANDATORY, annotation.propagation());
    }
}
