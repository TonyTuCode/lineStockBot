package com.linerobot.vo;

import java.time.LocalDate;
import java.util.Objects;

public class BuyOverVO {

    //資料日期
    private LocalDate dataDate;

    //外資買超
    private Long foreignQty;

    //投信買超
    private Long invTruQty;

    //自營商買超
    private Long dealerQty;

    public LocalDate getDataDate() {
        return dataDate;
    }

    public void setDataDate(LocalDate dataDate) {
        this.dataDate = dataDate;
    }

    public Long getForeignQty() {
        return foreignQty;
    }

    public void setForeignQty(Long foreignQty) {
        this.foreignQty = foreignQty;
    }

    public Long getInvTruQty() {
        return invTruQty;
    }

    public void setInvTruQty(Long invTruQty) {
        this.invTruQty = invTruQty;
    }

    public Long getDealerQty() {
        return dealerQty;
    }

    public void setDealerQty(Long dealerQty) {
        this.dealerQty = dealerQty;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuyOverVO buyOverVO = (BuyOverVO) o;
        return Objects.equals(dataDate, buyOverVO.dataDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataDate);
    }
}
