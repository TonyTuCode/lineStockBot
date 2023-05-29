package com.linerobot.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

public class IncreasePeriodVO {

    private Integer itemNumber;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal increaseRate;

    public Integer getItemNumber() {
        return itemNumber;
    }

    public void setItemNumber(Integer itemNumber) {
        this.itemNumber = itemNumber;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getIncreaseRate() {
        return increaseRate;
    }

    public void setIncreaseRate(BigDecimal increaseRate) {
        this.increaseRate = increaseRate;
    }
}
