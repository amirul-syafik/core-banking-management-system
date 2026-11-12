package com.cbms;

import com.cbms.entity.BankBaseRate;
import com.cbms.repository.BankBaseRateRepository;
import com.cbms.repository.BankSchemeRateRepository;
import com.cbms.repository.BankSchemeRepository;
import com.cbms.service.BankRateService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BankBaseRateCodeCatalogTest {
    BankBaseRateRepository baseRates = mock(BankBaseRateRepository.class);
    BankSchemeRateRepository schemeRates = mock(BankSchemeRateRepository.class);
    BankSchemeRepository schemes = mock(BankSchemeRepository.class);
    BankRateService service = new BankRateService(baseRates, schemeRates, schemes);

    private static BankBaseRate rate(String code, String status) {
        BankBaseRate r = new BankBaseRate();
        r.setRateCd(code);
        r.setRateNm(code + " reference rate");
        r.setCcyCd("MYR");
        r.setRatePct(new BigDecimal("3.25000"));
        r.setEffDt(LocalDate.of(2026, 11, 1));
        r.setRateSts(status);
        return r;
    }

    @Test void allCodesUseLatestRowAndHideInternalDatabaseId() {
        when(baseRates.findLatestPerCode()).thenReturn(List.of(rate("BR", "ACTIVE"), rate("OLD", "INACTIVE")));
        var result = service.baseRateCodes(null, false);
        assertEquals(2, result.size());
        assertEquals("BR", result.get(0).rateCd());
        assertEquals("MYR", result.get(0).ccyCd());
        assertEquals(new BigDecimal("3.25000"), result.get(0).ratePct());
        verify(baseRates).findLatestPerCode();
    }

    @Test void effectiveDateAndActiveFilterAreAppliedToLatestRow() {
        LocalDate asOf = LocalDate.of(2026, 11, 12);
        when(baseRates.findLatestPerCodeAsOf(asOf))
                .thenReturn(List.of(rate("BR", "ACTIVE"), rate("OLD", "INACTIVE")));
        var result = service.baseRateCodes(asOf, true);
        assertEquals(1, result.size());
        assertEquals("BR", result.get(0).rateCd());
        verify(baseRates).findLatestPerCodeAsOf(asOf);
    }

    @Test void unknownCodesReturnEmptyCatalog() {
        when(baseRates.findLatestPerCode()).thenReturn(List.of());
        assertTrue(service.baseRateCodes(null, false).isEmpty());
    }
}
