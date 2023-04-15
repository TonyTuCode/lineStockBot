package com.linerobot.tools;

import org.json.JSONArray;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class Convertor {

    public String[] getArrayByIdx(JSONArray data, int idx){
        String [] arr = data.get(idx).toString()
                .replaceAll("\\[\"", "")
                .replaceAll("\"]", "")
                .split("\",\"");
        return arr;
    }

    public BigDecimal convertStr2CalculatedVal(String unParsedStr){
        double doubleVal =Double.parseDouble(unParsedStr.replaceAll(",",""));
        BigDecimal calDecimal = new BigDecimal(doubleVal/100000000).setScale(2, RoundingMode.HALF_UP);
        return calDecimal;
    }

}
