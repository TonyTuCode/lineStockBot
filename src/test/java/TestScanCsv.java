import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.linerobot.tools.SSLHelper;
import com.linerobot.vo.StockVO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class TestScanCsv {

    private static BigDecimal countIncRate(BigDecimal baseNum, BigDecimal IncreaseNum) {
        BigDecimal twoNumDiff = IncreaseNum.subtract(baseNum);
        BigDecimal increasePercent = twoNumDiff.multiply(new BigDecimal(100).divide(baseNum, 2, RoundingMode.HALF_UP));
        return increasePercent;
    }

    public static void main(String[] args) throws IOException, InvalidFormatException {

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

        /**

        String url = "https://stock.wearn.com/netbuy.asp?year=112&month=05&kind=2303";

        try {
            SSLHelper helper = new SSLHelper();
            Document doc = helper.getSSLConn(url).get();
            Elements tbody = doc.select("tbody").select("tr");
            for (Element e : tbody){
                System.out.println(e.text());
            }
            Elements rows = tbody.select("tr");

        } catch (IOException e) {
            e.printStackTrace();
        }

        */
    }
}
