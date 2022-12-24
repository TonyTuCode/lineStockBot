import com.linerobot.crawler.CrawlingBuySell;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TestCrawling {

    @Test
    public void testCrawlingFunc() throws IOException {
        CrawlingBuySell cbs = new CrawlingBuySell();
        System.out.println(cbs.getBuySellOver());
    }
}
