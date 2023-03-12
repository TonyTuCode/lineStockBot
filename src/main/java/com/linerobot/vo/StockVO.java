package com.linerobot.vo;

import java.math.BigDecimal;

public class StockVO {

    private String stockID;

    private String stockName;

    private BigDecimal stockPrice;

    private BigDecimal stockIncrease;

    private Long BuyOverQty;

    public String getStockID() {
        return stockID;
    }

    public void setStockID(String stockID) {
        this.stockID = stockID;
    }

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

    public Long getBuyOverQty() {
        return BuyOverQty;
    }

    public void setBuyOverQty(Long buyOverQty) {
        BuyOverQty = buyOverQty;
    }
}
