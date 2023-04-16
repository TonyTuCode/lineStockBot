import com.linerobot.LineEchoRobotApplication;
import com.linerobot.crawler.CrawlingBuySell;
import com.linerobot.crawler.CrawlingStrong;
import com.linerobot.handler.MenuCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.time.LocalDate;

@SpringBootTest(classes = LineEchoRobotApplication.class)
public class TestCrawling {

    @Autowired
    CrawlingBuySell crawlingBuySell;

    @Autowired
    CrawlingStrong crawlingStrong;

//    @Test
    public void testCrawlingFunc() {
        System.out.println(crawlingBuySell.getBuyOverStockTop(1));
//        System.out.println(crawlingBuySell.getBuyOverStockTop(2));
//        System.out.println(crawlingBuySell.getBuySellOver(""));
    }

//    @Test
    public void testCrawlingStrong() throws IOException {
        System.out.println(crawlingStrong.getRiseTop(3));
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



}
