package com.linerobot.crawler;

import com.linerobot.tools.RequestSender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真實 API 整合測試：不使用 mock，會直接查詢證交所與櫃買中心資料。
 * 執行：mvn -Dtest=DispositionCrawlerTest test
 */
class DispositionCrawlerTest {

    private final DispositionCrawler dispositionCrawler =
            new DispositionCrawler(new RequestSender());

    @Test
    void getDispositionAnalysisCallsOfficialApisAndReturnsAnalysis() {
        String result = dispositionCrawler.getDispositionAnalysis();

        printResult(result);

        assertThat(result).isNotBlank();
        assertThat(result.contains("處置股分析")
                || result.contains("目前查無仍在處置期間的四碼普通股"))
                .as("處置股分析應回傳正常分析結果或目前無處置股，不應回傳 API 錯誤訊息")
                .isTrue();
    }

    private void printResult(String result) {
        System.out.println("============================================================");
        System.out.println("disposition analysis - real API");
        System.out.println(result);
        System.out.println("============================================================");
    }
}
