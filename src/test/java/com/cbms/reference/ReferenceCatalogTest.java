package com.cbms.reference;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceCatalogTest {
    @Test void countriesAreIsoTwoLetterCodesWithNames() {
        var countries = ReferenceCatalog.countries();
        assertTrue(countries.size() > 200);
        assertEquals(countries.size(), new HashSet<>(countries.stream().map(ReferenceCatalog.Option::code).toList()).size());
        assertTrue(countries.stream().allMatch(option -> option.code().matches("[A-Z]{2}") && !option.name().isBlank()));
        assertTrue(countries.stream().anyMatch(option -> option.code().equals("MY") && option.name().equals("Malaysia")));
    }
    @Test void currenciesAreIsoThreeLetterCodesWithNames() {
        var currencies = ReferenceCatalog.currencies();
        assertTrue(currencies.size() > 100);
        assertEquals(currencies.size(), new HashSet<>(currencies.stream().map(ReferenceCatalog.Option::code).toList()).size());
        assertTrue(currencies.stream().allMatch(option -> option.code().matches("[A-Z]{3}") && !option.name().isBlank()));
        assertTrue(currencies.stream().anyMatch(option -> option.code().equals("MYR")));
        assertFalse(currencies.stream().anyMatch(option -> option.code().equals("XXX")));
    }
}
