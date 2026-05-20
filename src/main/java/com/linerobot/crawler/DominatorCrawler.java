package com.linerobot.crawler;

import com.linerobot.tools.SSLHelper;
import com.linerobot.vo.BuyOverVO;
import com.linerobot.vo.IncreasePeriodVO;
import com.linerobot.vo.StockVO;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.chrono.MinguoDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

    private static String STOCK_FILE_DIR = "./stockFiles";

    private static String INCREASE_PATH = "./stockFiles/%Sincrease.csv";

    private static String BUYOVER_PATH = "./stockFiles/%SBuyover.csv";

    private static final String FINMIND_STOCK_PRICE_URL =
            "https://api.finmindtrade.com/api/v4/data?dataset=TaiwanStockPrice&data_id=%s&start_date=%s&end_date=%s";

    private static final String FINMIND_INVESTOR_BUY_SELL_URL =
            "https://api.finmindtrade.com/api/v4/data?dataset=TaiwanStockInstitutionalInvestorsBuySell&data_id=%s&start_date=%s&end_date=%s";

    /**
     * get 個股主導籌碼
     * @Param stockNum 股票號碼
     * @return finalAnalyzeResult
     */
    public String dominateCrawlingAndAnalyze(String stockNum) {

        StringBuffer finalAnalyzeResult = new StringBuffer();

        Path increaseFile = Paths.get(String.format(INCREASE_PATH, stockNum));

        Path buyoverFile = Paths.get(String.format(BUYOVER_PATH, stockNum));

        //先確認目錄創建完畢，避免錯誤
        Path path = Paths.get(STOCK_FILE_DIR);
        try {
            Files.createDirectories(path);
            System.out.println("目錄成功創建或已存在： " + path);
        } catch (IOException e) {
            System.err.println("目錄創建失敗: " + path);
            e.printStackTrace();
        }

        // 只要其中一個不存在就刪掉重撈
        boolean needDownloadIncrease = Files.notExists(increaseFile);
        boolean needDownloadBuyOver = Files.notExists(buyoverFile) || isBuyOverHistoryInsufficient(stockNum, PERIOD_MONTH);
        if (needDownloadIncrease || needDownloadBuyOver) {
            System.out.println("檔案不存在或歷史資料不足，先將檔案清除後重新下載");
            try {
                if (needDownloadIncrease) {
                    Files.deleteIfExists(increaseFile);
                    this.downloadIncreaseFile(stockNum ,PERIOD_MONTH);
                }
                if (needDownloadBuyOver) {
                    Files.deleteIfExists(buyoverFile);
                    this.downloadBuyOverFile(stockNum ,PERIOD_MONTH);
                }
            } catch (IOException ioe){
                ioe.printStackTrace();
                return "來源網站無該股票資料。";
            } catch (InterruptedException ie){
                ie.printStackTrace();
                Thread.currentThread().interrupt();
                return "主力分析資料下載中斷，請稍後再試。";
            }
        }

        List<IncreasePeriodVO> increaseAnalyzeResult = new ArrayList<>();
        Set<BuyOverVO> buyOverHistoryData = new HashSet<>();
        try {
            //收集上漲區間資料
            increaseAnalyzeResult = this.analyzeIncreaseFile(stockNum);
            //收集所有區間資料
            buyOverHistoryData = this.collectBuyOverHistoryData(stockNum);
        } catch (IOException ioe){
            ioe.printStackTrace();
        }

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

            //找最大值位置，判斷哪個位置數量最大
            for (int i = 0 ; i < totalArr.length ; i++){
                if (totalArr[i] > max){
                    max = totalArr[i];
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
            finalAnalyzeResult.append(eachResult.getEndDate()+" 期間上漲" +eachResult.getIncreaseRate()+"%\n");
            finalAnalyzeResult.append("投信期間買超:" + invTruQtyTotal+"\n");
            finalAnalyzeResult.append("自營期間買超:"+ dealerQtyTotal+"\n");
            finalAnalyzeResult.append("外資期間買超:"+ foreignQtyTotal+"\n");
        }
        finalAnalyzeResult.append("========================\n");
        finalAnalyzeResult.append("投信控盤次數:"+invTruCtrl);
        finalAnalyzeResult.append(" 自營控盤次數:"+dealerCtrl);
        finalAnalyzeResult.append(" 外資控盤次數:"+foreignCtrl);

        return finalAnalyzeResult.toString();
    }

    //下載買超
    private void downloadBuyOverFile(String stockNum , Integer months) throws IOException, InterruptedException {
        String filePath = String.format(BUYOVER_PATH, stockNum);
        if (months != null) {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusMonths(months - 1).withDayOfMonth(1);
            JSONObject originData = new JSONObject(readJsonUrlWithRetry(
                    String.format(FINMIND_INVESTOR_BUY_SELL_URL, stockNum, startDate, endDate)));
            JSONArray data = originData.optJSONArray("data");
            if (originData.optInt("status") != 200 || data == null) {
                throw new IOException("FinMind returned no investor buy sell data for stock: " + stockNum);
            }

            NavigableMap<LocalDate, long[]> buyOverByDate = new TreeMap<>();
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (int i = 0; i < data.length(); i++) {
                JSONObject row = data.getJSONObject(i);
                if (!isValidFinMindInvestorRow(row)) {
                    continue;
                }

                LocalDate dataDate = LocalDate.parse(row.getString("date"), dateFormatter);
                long netShares = row.optLong("buy") - row.optLong("sell");
                long[] buyOver = buyOverByDate.computeIfAbsent(dataDate, key -> new long[3]);
                switch (row.optString("name")) {
                    case "Investment_Trust":
                        buyOver[0] += netShares;
                        break;
                    case "Dealer_self":
                    case "Dealer_Hedging":
                        buyOver[1] += netShares;
                        break;
                    case "Foreign_Investor":
                    case "Foreign_Dealer_Self":
                        buyOver[2] += netShares;
                        break;
                    default:
                        break;
                }
            }

            if (buyOverByDate.isEmpty()) {
                throw new IOException("FinMind returned no investor buy sell rows for stock: " + stockNum);
            }

            DateTimeFormatter minguoFormatter = DateTimeFormatter.ofPattern("yyy/MM/dd");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), "UTF-8"))) {
                for (Map.Entry<LocalDate, long[]> entry : buyOverByDate.descendingMap().entrySet()) {
                    long[] buyOver = entry.getValue();
                    writer.write(String.format("%s , %s , %s , %s",
                            minguoFormatter.format(MinguoDate.from(entry.getKey())),
                            convertSharesToLots(buyOver[0]),
                            convertSharesToLots(buyOver[1]),
                            convertSharesToLots(buyOver[2])));
                    writer.newLine();
                }
            }
            return;
        }
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
    private boolean isBuyOverHistoryInsufficient(String stockNum, Integer months) {
        try {
            Set<BuyOverVO> buyOverHistoryData = collectBuyOverHistoryData(stockNum);
            if (buyOverHistoryData.isEmpty()) {
                return true;
            }
            LocalDate firstNeededDate = LocalDate.now().minusMonths(months - 1).withDayOfMonth(1);
            LocalDate earliestDate = buyOverHistoryData.stream()
                    .map(BuyOverVO::getDataDate)
                    .filter(Objects::nonNull)
                    .min(LocalDate::compareTo)
                    .orElse(LocalDate.MAX);
            return earliestDate.isAfter(firstNeededDate.plusMonths(1));
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isValidFinMindInvestorRow(JSONObject row) {
        return row.has("date")
                && row.has("name")
                && row.has("buy")
                && row.has("sell");
    }

    private long convertSharesToLots(long shareQty) {
        return BigDecimal.valueOf(shareQty)
                .divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP)
                .longValue();
    }

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

    private List<IncreasePeriodVO> analyzeIncreaseFile (String stockNum) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(String.format(INCREASE_PATH, stockNum)));
        sc.useDelimiter("\n");
        List<StockVO> dataList = new LinkedList<>();
        while (sc.hasNext()) {  //returns a boolean value
            try {
                String val = sc.next();
                if (val.contains("Date") || val.contains("null")) {
                    continue;
                }
                String[] valArr = val.split(",");
                if (valArr.length <= 4) {
                    continue;
                }
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                LocalDate dataDate = LocalDate.parse(valArr[0].trim(),formatter);
                String priceString = valArr[4].trim();
                BigDecimal closePrice = new BigDecimal(priceString).setScale(2, RoundingMode.HALF_UP);
                if (closePrice.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                StockVO stockVO = new StockVO();
                stockVO.setStockPriceDate(dataDate);
                stockVO.setStockPrice(closePrice);
                dataList.add(stockVO);
            } catch (NumberFormatException | DateTimeParseException ne) {
                ne.printStackTrace();
            }
        }
        sc.close();

        int counter = 1;
        //5天內上漲10%的區間
        List<IncreasePeriodVO> increasePeriodVOList = new LinkedList<>();
        for (int i = 0; i < dataList.size() - 4; i++) {
            BigDecimal increaseRate = countIncRate(dataList.get(i).getStockPrice(), dataList.get(i + 4).getStockPrice());
            if (increaseRate.compareTo(BigDecimal.valueOf(15)) > 0) {
                //拿到起始日期與結束日期
                IncreasePeriodVO increaseVO = new IncreasePeriodVO();
                increaseVO.setItemNumber(counter);
                increaseVO.setStartDate(dataList.get(i).getStockPriceDate());
                increaseVO.setEndDate(dataList.get(i + 4).getStockPriceDate());
                increaseVO.setIncreaseRate(increaseRate);
                increasePeriodVOList.add(increaseVO);
                ++counter;
            }
        }
        return increasePeriodVOList;
    }

    private void downloadIncreaseFile(String stockNum , Integer months) throws IOException, InterruptedException {
        String filePath = String.format(INCREASE_PATH, stockNum);
        int rowCount = 0;
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months - 1).withDayOfMonth(1);
        JSONObject originData = new JSONObject(readJsonUrlWithRetry(
                String.format(FINMIND_STOCK_PRICE_URL, stockNum, startDate, endDate)));
        JSONArray data = originData.optJSONArray("data");
        if (originData.optInt("status") != 200 || data == null) {
            throw new IOException("FinMind returned no price data for stock: " + stockNum);
        }

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), "UTF-8"))) {
            writer.write("Date,Open,High,Low,Close,Adj Close,Volume");
            writer.newLine();

            for (int i = 0; i < data.length(); i++) {
                JSONObject row = data.getJSONObject(i);
                if (!isValidFinMindPriceRow(row)) {
                    continue;
                }
                writer.write(String.join(",",
                        row.getString("date"),
                        row.get("open").toString(),
                        row.get("max").toString(),
                        row.get("min").toString(),
                        row.get("close").toString(),
                        row.get("close").toString(),
                        row.get("Trading_Volume").toString()));
                writer.newLine();
                rowCount++;
            }
        }

        if (rowCount == 0) {
            throw new IOException("FinMind returned no price rows for stock: " + stockNum);
        }
    }

    private boolean isValidFinMindPriceRow(JSONObject row) {
        return row.has("date")
                && row.has("open")
                && row.has("max")
                && row.has("min")
                && row.has("close")
                && row.has("Trading_Volume");
    }

    private String readJsonUrlWithRetry(String urlStr) throws IOException, InterruptedException {
        IOException lastException = null;
        for (int tryCount = 1; tryCount <= 3; tryCount++) {
            try {
                String response = readUrl(urlStr).trim();
                if (response.startsWith("{")) {
                    return response;
                }
                lastException = new IOException("Non JSON response: " +
                        response.substring(0, Math.min(120, response.length())));
            } catch (IOException e) {
                lastException = e;
            }
            Thread.currentThread().sleep(tryCount * 1500L);
        }
        throw lastException;
    }

    private String readUrl(String urlStr) throws IOException {
        SSLHelper.init();
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(15000);
        con.setRequestProperty("Accept", "application/json, text/plain, */*");
        con.setRequestProperty("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8");
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        if (urlStr.contains("twse.com.tw")) {
            con.setRequestProperty("Referer", "https://www.twse.com.tw/zh/trading/historical/stock-day.html");
        }
        String finMindToken = System.getProperty("finmind.token", System.getenv("FINMIND_TOKEN"));
        if (finMindToken != null && !finMindToken.trim().isEmpty()) {
            con.setRequestProperty("Authorization", "Bearer " + finMindToken.trim());
        }
        StringBuilder response = new StringBuilder();
        InputStream inputStream = con.getResponseCode() >= 400 ? con.getErrorStream() : con.getInputStream();
        if (inputStream == null) {
            throw new IOException("Empty response from: " + urlStr);
        }
        try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        }
        return response.toString();
    }

    private Set<BuyOverVO> collectBuyOverHistoryData(String stockNum) throws FileNotFoundException {
        Set<BuyOverVO> buyOverHistoryData = new HashSet<>();
        try (Scanner sc = new Scanner(new File(String.format(BUYOVER_PATH, stockNum)))) {
            sc.useDelimiter("\n");
            while (sc.hasNext()){
                try {
                    String val = sc.next();
                    String[] valArr = val.split(",");
                    if (valArr.length < 4) {
                        continue;
                    }

                    BuyOverVO buyOverVO = new BuyOverVO();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyy/MM/dd");
                    LocalDate adDate = LocalDate.parse(valArr[0].trim(), formatter).plusYears(1911);

                    buyOverVO.setDataDate(adDate);
                    buyOverVO.setInvTruQty(Long.parseLong(valArr[1].trim()));
                    buyOverVO.setDealerQty(Long.parseLong(valArr[2].trim()));
                    buyOverVO.setForeignQty(Long.parseLong(valArr[3].trim()));
                    buyOverHistoryData.add(buyOverVO);
                } catch (NumberFormatException | DateTimeParseException ne) {
                    ne.printStackTrace();
                }
            }
        }
        return buyOverHistoryData;
    }

    //比較2個bigDecimal
    private static BigDecimal countIncRate(BigDecimal baseNum, BigDecimal IncreaseNum) {
        if (baseNum == null || IncreaseNum == null || baseNum.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal twoNumDiff = IncreaseNum.subtract(baseNum);
        return twoNumDiff.multiply(BigDecimal.valueOf(100)).divide(baseNum, 2, RoundingMode.HALF_UP);
    }

}
