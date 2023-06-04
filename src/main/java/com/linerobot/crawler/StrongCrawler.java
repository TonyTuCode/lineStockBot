package com.linerobot.crawler;

import com.linerobot.tools.Convertor;
import com.linerobot.tools.RequestSender;
import com.linerobot.tools.SSLHelper;
import com.linerobot.vo.StockVO;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import static java.time.format.DateTimeFormatter.BASIC_ISO_DATE;


@Component
public class StrongCrawler {

    private Convertor convertor;

    private RequestSender requestSender;

    public StrongCrawler(Convertor convertor, RequestSender requestSender) {
        this.convertor = convertor;
        this.requestSender = requestSender;
    }

    private static final String WTX_HISTORY = "https://www.twse.com.tw/rwd/zh/TAIEX/MI_5MINS_HIST?response=json";

    private static final String WTX_HISTORY_LAST_MONTH = "https://www.twse.com.tw/rwd/zh/TAIEX/MI_5MINS_HIST?date=%S01&response=json";

    private static String STOCK_3DAYS_RISE_TOP = "https://concords.moneydj.com/z/zg/zg_A_0_%S.djhtm";

    /**
     * 依傳入日數抓取較大盤強勢10%個股
     * @param days
     * @return
     * @throws IOException
     */
    public String getRiseTop(int days) throws IOException {
        SSLHelper.init();
        SSLHelper helper = new SSLHelper();
        days = days > 5 ? 5: days;
        Document htmlTag = helper.getSSLConn(getDaysRiseUpUrl(days)).get();

        BigDecimal wtx = getWTXDiffByDays(days);
        BigDecimal wtx10PercentStronger = wtx.add(new BigDecimal(10));

        String returnMessage = "";
        if (htmlTag != null){
            Elements stockInfo = htmlTag.select("#oMainTable tr");

            //蒐集及篩選比大盤強勢10%的Bean
            List<StockVO> riseTopStocks = new ArrayList();
            for (int i = 2 ; i <= 51 ; i++){
                StockVO vo = new StockVO();
                vo.setStockName(stockInfo.get(i).getElementsByTag("td").get(1).text());
                vo.setStockIncrease(getPurgedStockIncrease(stockInfo.get(i).getElementsByTag("td").get(7).text()));
                if (vo.getStockIncrease().compareTo(wtx10PercentStronger) == 1){
                    riseTopStocks.add(vo);
                }
            }

            StringBuilder messageCombine = new StringBuilder();
            messageCombine.append("勝大盤"+days+"日漲幅"+ wtx + "%強勢10%股:\n");
            riseTopStocks.forEach(eachStocks->{
                messageCombine.append(eachStocks.getStockName()+" ");
                messageCombine.append(eachStocks.getStockIncrease()+"%\n");
            });

            returnMessage = messageCombine.toString();
        }
        return returnMessage;
    }

    //抓幾日的網址
    private String getDaysRiseUpUrl(int days) {
        return String.format(STOCK_3DAYS_RISE_TOP, days);
    }

    /**
     * 依傳入日數計算大盤漲跌幅
     * @param days
     * @return diffPercent
     * @throws IOException
     */
    private BigDecimal getWTXDiffByDays (int days) throws IOException {
        JSONArray mergedDataJArr = new JSONArray();
        BigDecimal todayVal = new BigDecimal(0);
        BigDecimal backDaysVal = new BigDecimal(0);

        String response = requestSender.getRequester(WTX_HISTORY);
        JSONObject originData = new JSONObject(response);
        JSONArray dataJArr = originData.getJSONArray("data");

        //如果dataJArr<=傳入天數，則需取到上月，並合併JSONArray
        if (dataJArr.length() <= days) {
            String lastMonth = LocalDate.now().minusMonths(1).format(BASIC_ISO_DATE).substring(0, 6);
            String responseForLastMonth = requestSender.getRequester(String.format(WTX_HISTORY_LAST_MONTH,lastMonth));
            JSONObject extraData = new JSONObject(responseForLastMonth);
            JSONArray extraJArr = extraData.getJSONArray("data");
            extraJArr.forEach(e -> { mergedDataJArr.put(e); });
        }
        dataJArr.forEach(e-> { mergedDataJArr.put(e); });

        int historyDataLength = mergedDataJArr.length() - 1;
        for (int i = historyDataLength ; i >= 0  ; i--) {
            BigDecimal wtxIndexByDay = convertor.convertStr2Decimal(convertor.getArrayByIdx(mergedDataJArr, i)[4]);
            if (i == historyDataLength) {
                todayVal = wtxIndexByDay;
            }
            if (i == historyDataLength - (days - 1)) {
                backDaysVal = wtxIndexByDay;
            }
        }
        BigDecimal diff = todayVal.subtract(backDaysVal);
        BigDecimal diffPercent = diff.multiply(new BigDecimal(100)).divide(backDaysVal, 2, RoundingMode.HALF_UP);

        return diffPercent;
    }

    //Purge字串裡的數字及小數點部分
    private BigDecimal getPurgedStockIncrease(String rawStockIncreaseStr){
        Matcher matcher = Pattern.compile(("[0-9]*[.]{1}[0-9]{2}")).matcher(rawStockIncreaseStr);
        BigDecimal purgedIncrease = new BigDecimal(0);
        while (matcher.find()) {
            if (!"".equals(matcher.group()))
                purgedIncrease = convertor.convertStr2Decimal(matcher.group());
        }
        return purgedIncrease;
    }


}
