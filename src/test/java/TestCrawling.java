import com.linerobot.LineEchoRobotApplication;
import com.linerobot.crawler.BuySellCrawler;
import com.linerobot.crawler.DominatorCrawler;
import com.linerobot.crawler.StrongCrawler;
import com.linerobot.handler.MenuCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.time.LocalDate;

@SpringBootTest(classes = LineEchoRobotApplication.class)
public class TestCrawling {

    @Autowired
    BuySellCrawler buySellCrawler;

    @Autowired
    StrongCrawler strongCrawler;

    @Autowired
    DominatorCrawler dominatorCrawler;



//    @Test
    public void testCrawlingFunc() {
        System.out.println(buySellCrawler.getBuyOverStockTop(1));
        System.out.println(buySellCrawler.getBuyOverStockTop(3));
        System.out.println(buySellCrawler.getBuySellOver(""));
    }

//    @Test
    public void testCrawlingStrong() throws IOException {
        System.out.println(strongCrawler.getRiseTop(3));
    }

//    @Test
    public void testMenu(){
        MenuCode m = new MenuCode();
        System.out.println(m.getMenu());
    }

//    @Test
    public void testWeek(){
        System.out.println(LocalDate.now().getDayOfWeek().getValue());
    }

    @Test
    public void testDominate(){
        try {
            System.out.println(dominatorCrawler.dominateCrawlingAndAnalyze("2303"));
        } catch (Exception e){
            e.printStackTrace();
        }
    }



}
