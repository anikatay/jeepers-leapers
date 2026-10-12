package com.neueda.leap.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceBar(
	LocalDate date,
	BigDecimal open,
	BigDecimal high,
	BigDecimal low,
	BigDecimal close,
	Long volume
) {}
