package com.linerobot.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DominatorCrawlerTest {

    private final DominatorCrawler dominatorCrawler = new DominatorCrawler();

    @Test
    void getBuyOverStockTopForeignBuyCallsRemoteAndPrintsResult() {
        String result = dominatorCrawler.dominateCrawlingAndAnalyze("2317");
        System.out.println(result);
        assertThat(result).isNotBlank();
    }
}
