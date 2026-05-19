package com.linerobot.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import com.linerobot.tools.Convertor;
import com.linerobot.tools.RequestSender;
import org.junit.jupiter.api.Test;

class BuySellCrawlerTest {

    private final BuySellCrawler buySellCrawler = new BuySellCrawler(new Convertor(), new RequestSender());

//    @Test
    void getBuyOverStockTopForeignBuyCallsRemoteAndPrintsResult() {
        String result = buySellCrawler.getBuyOverStockTop(1);

        printResult("case 1: foreign buy", result);
        assertThat(result).isNotBlank();
    }

//    @Test
    void getBuyOverStockTopInvestmentTrustBuyCallsRemoteAndPrintsResult() {
        String result = buySellCrawler.getBuyOverStockTop(2);

        printResult("case 2: investment trust buy", result);
        assertThat(result).isNotBlank();
    }

//    @Test
    void getBuyOverStockTopForeignAndInvestmentTrustBuyCallsRemoteAndPrintsResult() {
        String result = buySellCrawler.getBuyOverStockTop(3);

        printResult("case 3: foreign and investment trust buy", result);
        assertThat(result).isNotBlank();
    }

    private void printResult(String title, String result) {
        System.out.println("============================================================");
        System.out.println(title);
        System.out.println(result);
        System.out.println("============================================================");
    }
}
