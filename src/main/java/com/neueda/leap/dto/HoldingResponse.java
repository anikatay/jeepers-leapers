package com.neueda.leap.dto;

import java.math.BigDecimal;
import java.util.List;

public class HoldingResponse {

    private BigDecimal totalPortfolioValue;
    private List<HoldingDto> holdings;

    public HoldingResponse() {
    }

    public HoldingResponse(BigDecimal totalPortfolioValue, List<HoldingDto> holdings) {
        this.totalPortfolioValue = totalPortfolioValue;
        this.holdings = holdings;
    }

    public BigDecimal getTotalPortfolioValue() { return totalPortfolioValue; }
    public void setTotalPortfolioValue(BigDecimal totalPortfolioValue) { this.totalPortfolioValue = totalPortfolioValue; }

    public List<HoldingDto> getHoldings() { return holdings; }
    public void setHoldings(List<HoldingDto> holdings) { this.holdings = holdings; }

    public static class HoldingDto {
        private String instrumentName;
        private String ticker;
        private BigDecimal quantity;
        private BigDecimal holdingValue;

        public HoldingDto() {
        }

        public HoldingDto(String instrumentName, String ticker, BigDecimal quantity, BigDecimal holdingValue) {
            this.instrumentName = instrumentName;
            this.ticker = ticker;
            this.quantity = quantity;
            this.holdingValue = holdingValue;
        }

        public String getInstrumentName() { return instrumentName; }
        public void setInstrumentName(String instrumentName) { this.instrumentName = instrumentName; }

        public String getTicker() { return ticker; }
        public void setTicker(String ticker) { this.ticker = ticker; }

        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

        public BigDecimal getHoldingValue() { return holdingValue; }
        public void setHoldingValue(BigDecimal holdingValue) { this.holdingValue = holdingValue; }
    }
}