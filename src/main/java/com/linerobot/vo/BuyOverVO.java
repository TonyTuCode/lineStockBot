package com.linerobot.vo;

public class BuyOverVO {

    //外資
    private Long foreignQty;

    //投信
    private Long invTruQty;

    //自營商
    private Long dealerQty;

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
}
