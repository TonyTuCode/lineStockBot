package com.linerobot.crawler;

import com.linerobot.tools.Convertor;
import com.linerobot.tools.RequestSender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuyOverAnalyzeCrawlerTest {

    private final BuySellCrawler buySellCrawler = new BuySellCrawler(new Convertor(), new RequestSender());

    private final DominatorCrawler dominatorCrawler = new DominatorCrawler();

    private final BuyOverAnalyzeCrawler buyOverAnalyzeCrawler = new BuyOverAnalyzeCrawler(buySellCrawler, dominatorCrawler);

//    @Test
    void refreshBuyOverAnalyzeCallsRemoteAndPrintsResult() throws Exception {
        String result = buyOverAnalyzeCrawler.refreshBuyOverAnalyze();

        printResult("buy over analyze", result);
        assertThat(result).isNotBlank();
    }

//    @Test
    void getBuyOverAnalyzeResultReadsResultFileAndPrintsResult() {
        String result = buyOverAnalyzeCrawler.getBuyOverAnalyzeResult();

        printResult("buy over analyze result file", result);
        assertThat(result).isNotBlank();
    }

    private void printResult(String title, String result) {
        System.out.println("============================================================");
        System.out.println(title);
        System.out.println(result);
        System.out.println("============================================================");
    }
}
