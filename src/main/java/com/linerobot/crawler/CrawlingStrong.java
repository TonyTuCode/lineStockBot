package com.linerobot.crawler;

import com.linerobot.tools.SSLHelper;
import com.linerobot.vo.StockVO;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;


@Component
public class CrawlingStrong {
    private static final String WTX_HISTORY = "https://www.taiwanindex.com.tw/index/history/t00";

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
        SSLHelper.init();
        SSLHelper helper = new SSLHelper();
        BigDecimal diffPercent = new BigDecimal(0);
        Document htmlTag = helper.getSSLConn(WTX_HISTORY).post();
        if (htmlTag != null) {
            Elements stockIndex = htmlTag.getElementsByTag("tr");
            BigDecimal backDays = getDouble(stockIndex.get(days).getElementsByTag("td").get(1).text());
            BigDecimal today = getDouble(stockIndex.get(1).getElementsByTag("td").get(1).text());
            BigDecimal diff = today.subtract(backDays);
            diffPercent = diff.multiply(new BigDecimal(100)).divide(backDays,2, RoundingMode.HALF_UP);
        }
        return diffPercent;
    }

    //Purge字串裡的數字
    private BigDecimal getPurgedStockIncrease(String rawStockIncreaseStr){
        Matcher matcher = Pattern.compile(("[0-9]*[.]{1}[0-9]{2}")).matcher(rawStockIncreaseStr);
        BigDecimal purgedIncrease = new BigDecimal(0);
        while (matcher.find()) {
            if (!"".equals(matcher.group()))
                purgedIncrease = this.getDouble(matcher.group());
        }
        return purgedIncrease;
    }


    private BigDecimal getDouble(String unParsedStr){
        double doubleVal =Double.parseDouble(unParsedStr);
        BigDecimal calDecimal = new BigDecimal(doubleVal).setScale(2, RoundingMode.HALF_UP); //取到小數點後2位4捨5入
        return calDecimal;
    }
}
