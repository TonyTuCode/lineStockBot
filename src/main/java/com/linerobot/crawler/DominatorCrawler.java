package com.linerobot.crawler;

import com.linerobot.tools.SSLHelper;
import com.linerobot.vo.BuyOverVO;
import com.linerobot.vo.IncreasePeriodVO;
import com.linerobot.vo.StockVO;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.chrono.MinguoDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 演算法:
 * 1.先下載股價檔案
 * 2.再下載法人檔案
 * 3.找出上漲區間回傳List<IncreasePeriodVO>
 * 4.將法人檔案寫成List<BuyOverVO>
 * 5.用串流filter過濾上漲區間
 * 6.比對該區間誰的買超最多(該法人counter++)，回傳結果
 */
@Component
public class DominatorCrawler {

    private static int PERIOD_MONTH = 60;

    private static String INCREASE_PATH = "./StockFiles/increase/%Sincrease.csv";

    private static String BUYOVER_PATH = "./StockFiles/buyover/%SBuyover.csv";

    /**
     * get 個股主導籌碼
     * @Param stockNum 股票號碼
     * @return finalAnalyzeResult
     */
    public String dominateCrawlingAndAnalyze(String stockNum) throws IOException, InterruptedException {

        StringBuffer finalAnalyzeResult = new StringBuffer();

        // TODO 判斷何時不需再下載
        this.downloadIncreaseFile(stockNum ,PERIOD_MONTH);

        // TODO 判斷何時不需再下載
        this.downloadBuyOverFile(stockNum ,PERIOD_MONTH);

        //收集上漲區間資料
        List<IncreasePeriodVO> increaseAnalyzeResult = this.analyzeIncreaseFile(stockNum);

        //收集所有區間資料
        Set<BuyOverVO> buyOverHistoryData = this.collectBuyOverHistoryData(stockNum);

        Integer invTruCtrl = 0, dealerCtrl = 0, foreignCtrl = 0;

        for (IncreasePeriodVO eachResult : increaseAnalyzeResult){
            //過濾特定區間
            List<BuyOverVO> findPeriod = buyOverHistoryData
                    .stream()
                    .filter( buyOverVO -> (buyOverVO.getDataDate().isAfter(eachResult.getStartDate()) ||
                            buyOverVO.getDataDate().isEqual(eachResult.getStartDate()))
                            && (buyOverVO.getDataDate().isBefore(eachResult.getEndDate()) ||
                            buyOverVO.getDataDate().isEqual(eachResult.getEndDate()) ))
                    .collect(Collectors.toList());

            //加總期間總買超
            Long dealerQtyTotal = 0L, invTruQtyTotal =  0L, foreignQtyTotal = 0L;
            for (BuyOverVO eachDayBuy : findPeriod){
                invTruQtyTotal += eachDayBuy.getInvTruQty();
                dealerQtyTotal += eachDayBuy.getDealerQty();
                foreignQtyTotal += eachDayBuy.getForeignQty();
            }
            Long[] totalArr = {invTruQtyTotal, dealerQtyTotal, foreignQtyTotal};
            Long max = 0L;
            Integer maxIdx = 0;

            //找最大值位置，判斷哪個
            for (int i = 0 ; i < totalArr.length ; i++){
                if (totalArr[i] > max){
                    maxIdx = i;
                }
            }
            switch (maxIdx) {
                case 0: invTruCtrl++ ;
                    break;
                case 1: dealerCtrl++ ;
                    break;
                case 2: foreignCtrl++ ;
                    break;
            }
            finalAnalyzeResult.append("●"+eachResult.getStartDate()+" ~ ");
            finalAnalyzeResult.append(eachResult.getEndDate()+" 期間上漲" +eachResult.getIncreaseRate()+"%");
            finalAnalyzeResult.append("投信期間買超:" + invTruQtyTotal+" 自營期間買超:"+ dealerQtyTotal + " 外資期間買超:"+ foreignQtyTotal);
        }
        finalAnalyzeResult.append("===============================");
        finalAnalyzeResult.append("投信控盤次數:"+invTruCtrl);
        finalAnalyzeResult.append(" 自營控盤次數:"+dealerCtrl);
        finalAnalyzeResult.append(" 外資控盤次數:"+foreignCtrl);

        return finalAnalyzeResult.toString();
    }

    //下載買超
    private void downloadBuyOverFile(String stockNum , Integer months) throws IOException, InterruptedException {
        String filePath = String.format(BUYOVER_PATH, stockNum);
        FileOutputStream fos = new FileOutputStream(filePath,true);
        BufferedOutputStream bos = new BufferedOutputStream(fos);

        for (int backMonth = 0; backMonth < months; backMonth++) {
            String url = getBeforeMonthUrlByStockNum(stockNum,backMonth);
            try {
                SSLHelper helper = new SSLHelper();
                Document doc = helper.getSSLConn(url).get();
                Elements tbody = doc.select("tbody").select("tr");
                for (int i = 2; i < tbody.size() - 1; i++) {
                    //清洗資料
                    String purgedData = tbody.get(i).text()
                            .replace("+ ", "+")
                            .replace(",", "");
                    String[] splitContent = purgedData.split(" ");
                    String eachRawData = String.join(" , ",splitContent) + "\n";
                    byte[] bytes = eachRawData.getBytes();
                    bos.write(bytes);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            Thread.currentThread().sleep(500);
        }
        bos.close();
    }


    //取得by月份網址
    private String getBeforeMonthUrlByStockNum (String stockNum, Integer backMonth){
        String rawUrl = "https://stock.wearn.com/netbuy.asp?Year=%S&month=%S&kind=%S";
        LocalDate beforeDate = LocalDate.now().minusMonths(backMonth);
        MinguoDate beforeMinguoDate = MinguoDate.from(beforeDate);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyMM");
        String beforeMinguoString = formatter.format(beforeMinguoDate);
        String year = beforeMinguoString.substring(0, 3);
        String month =beforeMinguoString.substring(3, 5);
        String url = String.format(rawUrl, year,month,stockNum);
        return url;
    }

    //取得指定時間戳
    private Long getTimestamp8amByBackMonths (Integer backMonths) {
        LocalDateTime backMonthDateTime = LocalDateTime.now().minusMonths(backMonths);
        LocalDateTime  backMonthDateTimeSet8am=  backMonthDateTime.withHour(8).withMinute(0).withSecond(0).withNano(0);
        Long timestamp =backMonthDateTimeSet8am.toEpochSecond(ZoneOffset.ofHours(8));
        return  timestamp;
    }


    private List<IncreasePeriodVO> analyzeIncreaseFile (String stockNum) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(String.format(INCREASE_PATH, stockNum)));
        sc.useDelimiter("\n");
        List<StockVO> dataList = new LinkedList();
        while (sc.hasNext()) {  //returns a boolean value
            try {
                String val = sc.next();
                if (val.contains("Date") || val.contains("null")) {
                    continue;
                }
                String[] valArr = val.split(",");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                LocalDate dataDate = LocalDate.parse(valArr[0],formatter);
                String priceString = valArr[4];
                BigDecimal closePrice = BigDecimal.valueOf(Double.valueOf(priceString)).setScale(0, RoundingMode.HALF_UP);

                StockVO stockVO = new StockVO();
                stockVO.setStockPriceDate(dataDate);
                stockVO.setStockPrice(closePrice);
                dataList.add(stockVO);
            } catch (NumberFormatException ne) {
                ne.printStackTrace();
            }
        }
        sc.close();

        int counter = 1;
        //5天內上漲10%的區間
        List<IncreasePeriodVO> increasePeriodVOList = new LinkedList<>();
        for (int i = 0; i < dataList.size() - 4; i++) {
            if ((countIncRate(dataList.get(i).getStockPrice(), dataList.get(i + 4).getStockPrice()).compareTo(new BigDecimal(15))) == 1) {
                //拿到起始日期與結束日期
                IncreasePeriodVO increaseVO = new IncreasePeriodVO();
                increaseVO.setItemNumber(counter);
                increaseVO.setStartDate(dataList.get(i).getStockPriceDate());
                increaseVO.setEndDate(dataList.get(i + 4).getStockPriceDate());
                increaseVO.setIncreaseRate(countIncRate(dataList.get(i).getStockPrice(), dataList.get(i + 4).getStockPrice()));
                increasePeriodVOList.add(increaseVO);
                ++counter;
            }
        }
        return increasePeriodVOList;
    }

    private void downloadIncreaseFile(String stockNum , Integer months) throws IOException {
        long currentTime = getTimestamp8amByBackMonths(0);
        long pastTime = getTimestamp8amByBackMonths( months - 1 );
        String rawUrl = "https://query1.finance.yahoo.com/v7/finance/download/%S.TW?period1=%S&period2=%S&interval=1d&events=history&includeAdjustedClose=true";
        String url = String.format(rawUrl,stockNum,pastTime,currentTime);
        downloadFile (url,String.format(INCREASE_PATH, stockNum));
    }

    private Set<BuyOverVO> collectBuyOverHistoryData(String stockNum) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(String.format(BUYOVER_PATH, stockNum)));
        sc.useDelimiter("\n");
        Set<BuyOverVO> buyOverHistoryData = new HashSet<>();
        while (sc.hasNext()){
            String val = sc.next();
            String[] valArr = val.split(",");

            BuyOverVO buyOverVO = new BuyOverVO();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyy/MM/dd");
            LocalDate adDate = LocalDate.parse(valArr[0].trim(), formatter).plusYears(1911);

            buyOverVO.setDataDate(adDate);
            buyOverVO.setInvTruQty(Long.parseLong(valArr[1].trim()));
            buyOverVO.setDealerQty(Long.parseLong(valArr[2].trim()));
            buyOverVO.setForeignQty(Long.parseLong(valArr[3].trim()));
            buyOverHistoryData.add(buyOverVO);
        }
        return buyOverHistoryData;
    }

    //比較2個bigDecimal
    private static BigDecimal countIncRate(BigDecimal baseNum, BigDecimal IncreaseNum) {
        BigDecimal twoNumDiff = IncreaseNum.subtract(baseNum);
        BigDecimal increasePercent = twoNumDiff.multiply(new BigDecimal(100).divide(baseNum, 2, RoundingMode.HALF_UP));
        return increasePercent;
    }

    private static void downloadFile (String urlStr ,String destStr) throws IOException{
        URL url = new URL(urlStr);
        ReadableByteChannel src = Channels.newChannel(url.openStream());
        FileChannel dest = new FileOutputStream(destStr).getChannel();
        ByteBuffer buffer = ByteBuffer.allocate(1024); //設定Buffer大小

        try (src ; dest) {
            while (src.read(buffer) != -1){
                buffer.flip();  //set limit to current position, position to 0, so it can read from start
                dest.write(buffer);
                buffer.clear(); //init the buffer ,set position to 0, limit to max
            }
        }
    }
}
