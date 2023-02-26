import com.linerobot.crawler.CrawlingBuySell;
import com.linerobot.crawler.CrawlingStrong;
import com.linerobot.handler.MenuCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TestCrawling {

    @Test
    public void testCrawlingFunc() {
        CrawlingBuySell cbs = new CrawlingBuySell();
//        System.out.println(cbs.getBuySellOver("20230217"));
        System.out.println(cbs.getBuyOverStockTop("20230216"));
    }

//    @Test
    public void testCrawlingStrong() throws IOException {
        CrawlingStrong cs = new CrawlingStrong();
        System.out.println(cs.getRiseTop(3));
    }

//    @Test
    public void testMenu(){
        MenuCode m = new MenuCode();
        System.out.println(m.getMenu());
    }



}
