package com.linerobot.vo;

import java.math.BigDecimal;

public class StockVO {

    private String stockName;

    private BigDecimal stockPrice;

    private BigDecimal stockIncrease;

    public String getStockName() {
        return stockName;
    }

    public void setStockName(String stockName) {
        this.stockName = stockName;
    }

    public BigDecimal getStockPrice() {
        return stockPrice;
    }

    public void setStockPrice(BigDecimal stockPrice) {
        this.stockPrice = stockPrice;
    }

    public BigDecimal getStockIncrease() {
        return stockIncrease;
    }

    public void setStockIncrease(BigDecimal stockIncrease) {
        this.stockIncrease = stockIncrease;
    }
}
