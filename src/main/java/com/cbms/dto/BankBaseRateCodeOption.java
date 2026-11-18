package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One selectable code, sourced from the latest effective history row for the code. */
public record BankBaseRateCodeOption(String rateCd, String rateNm, String ccyCd,
                                     BigDecimal ratePct, LocalDate effDt, String rateSts) {}
