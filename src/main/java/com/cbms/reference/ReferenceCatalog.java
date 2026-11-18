package com.cbms.reference;

import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** ISO standards catalog. These are not bank-maintained master records. */
public final class ReferenceCatalog {
    private ReferenceCatalog() {}

    public record Option(String code, String name) {}

    private static final List<Option> COUNTRIES = java.util.Arrays.stream(Locale.getISOCountries())
            .map(code -> new Option(code, new Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH)))
            .sorted(java.util.Comparator.comparing(Option::name).thenComparing(Option::code))
            .toList();
    private static final Set<String> COUNTRY_CODES = COUNTRIES.stream()
            .map(Option::code)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private static final List<Option> CURRENCIES = Currency.getAvailableCurrencies().stream()
            .filter(currency -> !"XXX".equals(currency.getCurrencyCode()))
            .map(currency -> new Option(currency.getCurrencyCode(), currency.getDisplayName(Locale.ENGLISH)))
            .sorted(java.util.Comparator.comparing(Option::code))
            .toList();

    public static List<Option> countries() { return COUNTRIES; }
    public static List<Option> currencies() { return CURRENCIES; }
    public static boolean containsCountryCode(String code) {
        return code != null && COUNTRY_CODES.contains(code.trim().toUpperCase(Locale.ROOT));
    }
}
