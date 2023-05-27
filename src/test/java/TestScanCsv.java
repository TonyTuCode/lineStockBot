import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.linerobot.tools.SSLHelper;
import com.linerobot.vo.StockVO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.chrono.MinguoDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;


public class TestScanCsv {

    /**
     * 演算法:
     * 1.先下載股價檔案
     * 2.再下載法人檔案
     * 3.找出上漲區間
     * 4.將法人檔案寫成List<BuyOverVO>
     * 5.用串流過濾上漲區間
     * 6.比對該區間誰的買超最多(該法人counter++)
     * 7.回傳結果
    */

    public static void main(String[] args) throws IOException, InterruptedException {


        long currentTime = getTimestamp8amByBackMonths(0);

        long pastTime = getTimestamp8amByBackMonths(11);

        String rawUrl = "https://query1.finance.yahoo.com/v7/finance/download/%S.TW?period1=%S&period2=%S&interval=1d&events=history&includeAdjustedClose=true";
        String url = String.format(rawUrl,"2330",pastTime,currentTime);

        System.out.println(pastTime);
        System.out.println(currentTime);
        System.out.println(url);

//        String filePath = "D://Test.csv";
//        FileOutputStream fos = new FileOutputStream(filePath,true);
//        BufferedOutputStream bos = new BufferedOutputStream(fos);
//
//        for (int backMonth = 0 ; backMonth < 2; backMonth++){
//            String url = getBeforeMonthUrlByStockNum("2303",backMonth);
//            try {
//                SSLHelper helper = new SSLHelper();
//                Document doc = helper.getSSLConn(url).get();
//                Elements tbody = doc.select("tbody").select("tr");
//                for (int i = 2; i < tbody.size() - 1; i++) {
//                    //清洗資料
//                    String purgedData = tbody.get(i).text()
//                            .replace("+ ", "+")
//                            .replace(",", "");
//                    String[] splitContent = purgedData.split(" ");
//                    String eachRawData = String.join(" , ",splitContent) + "\n";
//                    byte[] bytes = eachRawData.getBytes();
//                    bos.write(bytes);
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            Thread.currentThread().sleep(500);
//        }
//        bos.close();
    }

    //取得by月份網址
    private static String getBeforeMonthUrlByStockNum (String stockNum, Integer backMonth){
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
    private static Long getTimestamp8amByBackMonths (Integer backMonths) {
        LocalDateTime backMonthDateTime = LocalDateTime.now().minusMonths(backMonths);
        LocalDateTime  backMonthDateTimeSet8am=  backMonthDateTime.withHour(8).withMinute(0).withSecond(0).withNano(0);
        Long timestamp =backMonthDateTimeSet8am.toEpochSecond(ZoneOffset.ofHours(8));
        return  timestamp;
    }


    //比較2個bigDecimal
    private static BigDecimal countIncRate(BigDecimal baseNum, BigDecimal IncreaseNum) {
        BigDecimal twoNumDiff = IncreaseNum.subtract(baseNum);
        BigDecimal increasePercent = twoNumDiff.multiply(new BigDecimal(100).divide(baseNum, 2, RoundingMode.HALF_UP));
        return increasePercent;
    }

    private static void downloadIncreaseFile() throws IOException {

        String stockNum = "2330";

        long currentTime = System.currentTimeMillis();
        long ldt = LocalDateTime.now().minusMonths(11)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();


        String url = "https://query1.finance.yahoo.com/v7/finance/download/2330.TW?period1=1651851349&period2=1683387349&interval=1d&events=history&includeAdjustedClose=true";
        downloadFile (url,"D://2330-test.csv");

        Scanner sc = new Scanner(new File("D:\\2303.TW.csv"));
        sc.useDelimiter("\n");
        List<StockVO> dataList = new LinkedList();
        while (sc.hasNext())  //returns a boolean value
        {
            try {
                String val = sc.next();
                if (val.contains("Date") || val.contains("null")) {
                    continue;
                }
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                Date dataDate = sdf.parse(val.split(",")[0]);
                String priceString = val.split(",")[4];
                BigDecimal closePrice = BigDecimal.valueOf(Double.valueOf(priceString)).setScale(2, RoundingMode.HALF_UP);
                ;
                StockVO stockVO = new StockVO();
                stockVO.setStockPriceDate(dataDate);
                stockVO.setStockPrice(closePrice);
                dataList.add(stockVO);
            } catch (ParseException pe) {
                pe.printStackTrace();
            } catch (NumberFormatException ne) {
                ne.printStackTrace();
            }
        }
        sc.close();

        //5天內上漲10%的區間
        int counter = 0;
        for (int i = 0; i < dataList.size() - 4; i++) {
            if ((countIncRate(dataList.get(i).getStockPrice(), dataList.get(i + 4).getStockPrice()).compareTo(new BigDecimal(15))) == 1) {
                System.out.println("事件"+counter+": ");
                System.out.println(dataList.get(i).getStockPriceDate() + "到" + dataList.get(i + 4).getStockPriceDate());
                System.out.println("價格從" + dataList.get(i).getStockPrice() + "漲到" + dataList.get(i + 4).getStockPrice());
                System.out.println("上漲" + countIncRate(dataList.get(i).getStockPrice(), dataList.get(i + 4).getStockPrice()) + "%");
                ++counter;
            }
        }
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
