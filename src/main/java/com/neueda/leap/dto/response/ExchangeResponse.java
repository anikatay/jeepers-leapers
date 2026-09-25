package com.neueda.leap.dto.response;

public class ExchangeResponse {

    private String exchangeId;
    private String name;
    private String region;
    private String timezone;
    private String currency;

    public ExchangeResponse() {
    }

    public ExchangeResponse(String exchangeId, String name, String region, String timezone, String currency) {
        this.exchangeId = exchangeId;
        this.name = name;
        this.region = region;
        this.timezone = timezone;
        this.currency = currency;
    }

    public String getExchangeId() { return exchangeId; }
    public void setExchangeId(String exchangeId) { this.exchangeId = exchangeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
