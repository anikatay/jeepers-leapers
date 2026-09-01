package com.neueda.leap.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Portfolio response containing total value and a list of individual holdings.
 */
public class PortfolioResponse {

    private BigDecimal totalPortfolioValue;
    private List<HoldingDto> holdings;

    public PortfolioResponse() {
    }

    public PortfolioResponse(BigDecimal totalPortfolioValue, List<HoldingDto> holdings) {
        this.totalPortfolioValue = totalPortfolioValue;
        this.holdings = holdings;
    }

    public BigDecimal getTotalPortfolioValue() {
        return totalPortfolioValue;
    }

    public void setTotalPortfolioValue(BigDecimal totalPortfolioValue) {
        this.totalPortfolioValue = totalPortfolioValue;
    }

    public List<HoldingDto> getHoldings() {
        return holdings;
    }

    public void setHoldings(List<HoldingDto> holdings) {
        this.holdings = holdings;
    }

    /**
     * A single holding within a portfolio: instrument name and its current value.
     */
    public static class HoldingDto {

        private String instrumentName;
        private BigDecimal holdingValue;

        public HoldingDto() {
        }

        public HoldingDto(String instrumentName, BigDecimal holdingValue) {
            this.instrumentName = instrumentName;
            this.holdingValue = holdingValue;
        }

        public String getInstrumentName() {
            return instrumentName;
        }

        public void setInstrumentName(String instrumentName) {
            this.instrumentName = instrumentName;
        }

        public BigDecimal getHoldingValue() {
            return holdingValue;
        }

        public void setHoldingValue(BigDecimal holdingValue) {
            this.holdingValue = holdingValue;
        }
    }
}
