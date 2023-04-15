package com.linerobot.tools;

import org.json.JSONArray;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class Convertor {

    /**
     * 將傳入的JSONArray字串資料轉成字串陣列
     * @param jsonArrayData
     * @param idx
     * @return String[]
     */
    public String[] getArrayByIdx(JSONArray jsonArrayData, int idx){
        String [] arr = jsonArrayData.get(idx).toString()
                .replaceAll("\\[\"", "")
                .replaceAll("\"]", "")
                .split("\",\"");
        return arr;
    }

    /**
     * 將字串轉成BigDecimal並以億為單位
     * @param unParsedStr
     * @return BigDecimal
     */
    public BigDecimal convertStr2CalculatedVal(String unParsedStr){
        double doubleVal =Double.parseDouble(unParsedStr.replaceAll(",",""));
        BigDecimal calDecimal = new BigDecimal(doubleVal/100000000).setScale(2, RoundingMode.HALF_UP);
        return calDecimal;
    }

    /**
     * 將字串轉成BigDecimal
     * @param unParsedStr
     * @return BigDecimal
     */
    public BigDecimal convertStr2Decimal (String unParsedStr){
        double doubleVal =Double.parseDouble(unParsedStr.replaceAll(",",""));
        BigDecimal calDecimal = new BigDecimal(doubleVal).setScale(2, RoundingMode.HALF_UP); //取到小數點後2位4捨5入
        return calDecimal;
    }

}
