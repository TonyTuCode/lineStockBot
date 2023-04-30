package com.linerobot.vo;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;

public class StockVO {

    private String stockID;

    private String stockName;

    private BigDecimal stockPrice;

    private BigDecimal stockIncrease;

    private Long BuyOverQty;

    private Date stockPriceDate;

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

    public Date getStockPriceDate() {
        return stockPriceDate;
    }

    public void setStockPriceDate(Date stockPriceDate) {
        this.stockPriceDate = stockPriceDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StockVO stockVO = (StockVO) o;
        return Objects.equals(stockID, stockVO.stockID);
    }

}
