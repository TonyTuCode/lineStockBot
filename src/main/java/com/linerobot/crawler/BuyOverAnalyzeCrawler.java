package com.linerobot.crawler;

import com.linerobot.vo.StockVO;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BuyOverAnalyzeCrawler {

    private static final String CACHE_ZONE = "Asia/Taipei";

    private static final String STOCK_FILE_DIR = "./stockFiles";

    private static final String BUY_OVER_ANALYZE_RESULT_PATH = "./stockFiles/BuyOverAnalyzeResult.txt";

    private static final long ANALYZE_REQUEST_INTERVAL_MS = 500L;

    private final Object analyzeLock = new Object();

    private final BuySellCrawler buySellCrawler;

    private final DominatorCrawler dominatorCrawler;

    public BuyOverAnalyzeCrawler(BuySellCrawler buySellCrawler, DominatorCrawler dominatorCrawler) {
        this.buySellCrawler = buySellCrawler;
        this.dominatorCrawler = dominatorCrawler;
    }

    @Scheduled(cron = "0 30 17 * * *", zone = CACHE_ZONE)
    public void refreshBuyOverAnalyzeBySchedule() {
        try {
            refreshBuyOverAnalyze();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getBuyOverAnalyzeResult() {
        try {
            String cachedResult = readBuyOverAnalyzeResult();
            if (cachedResult != null) {
                return cachedResult;
            }
            System.out.println("買超綜合分析快取不存在，立即重新產生一次。");
            return refreshBuyOverAnalyze();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
            return "買超綜合分析更新中斷，請稍後再試。";
        } catch (IOException e) {
            e.printStackTrace();
            return "買超綜合分析尚未產生，且即時更新失敗，請稍後再試。";
        }
    }

    public String refreshBuyOverAnalyze() throws IOException, InterruptedException {
        synchronized (analyzeLock) {
            buySellCrawler.refreshBuyOverCache();
            List<StockVO> foreignList = buySellCrawler.getForeignBuyOverStocks();
            List<StockVO> invTruList = buySellCrawler.getInvTruBuyOverStocks();
            Map<String, StockVO> analyzeTargets = mergeAnalyzeTargets(foreignList, invTruList);

            StringBuilder result = new StringBuilder();
            boolean firstAnalyze = true;
            for (StockVO stockVO : analyzeTargets.values()) {
                if (!firstAnalyze) {
                    Thread.currentThread().sleep(ANALYZE_REQUEST_INTERVAL_MS);
                }
                result.append(buildStockAnalyzeLine(stockVO)).append("\n");
                firstAnalyze = false;
            }

            String analyzeResult = result.toString().trim();
            writeAnalyzeResult(analyzeResult);
            return analyzeResult;
        }
    }

    private Map<String, StockVO> mergeAnalyzeTargets(List<StockVO> foreignList, List<StockVO> invTruList) {
        Map<String, StockVO> analyzeTargets = new LinkedHashMap<>();
        appendAnalyzeTargets(analyzeTargets, foreignList);
        appendAnalyzeTargets(analyzeTargets, invTruList);
        return analyzeTargets;
    }

    private void appendAnalyzeTargets(Map<String, StockVO> analyzeTargets, List<StockVO> stockList) {
        for (StockVO stockVO : stockList) {
            analyzeTargets.putIfAbsent(stockVO.getStockID(), stockVO);
        }
    }

    private String buildStockAnalyzeLine(StockVO stockVO) {
        try {
            return stockVO.getStockID() + " "
                    + stockVO.getStockName() + " "
                    + dominatorCrawler.dominateCrawlingAndControlCount(stockVO.getStockID());
        } catch (Exception e) {
            e.printStackTrace();
            return stockVO.getStockID() + " " + stockVO.getStockName() + " 主力分析失敗";
        }
    }

    private void writeAnalyzeResult(String analyzeResult) throws IOException {
        Files.createDirectories(Paths.get(STOCK_FILE_DIR));
        Files.write(Paths.get(BUY_OVER_ANALYZE_RESULT_PATH), analyzeResult.getBytes(StandardCharsets.UTF_8));
    }

    private String readBuyOverAnalyzeResult() throws IOException {
        Path resultPath = Paths.get(BUY_OVER_ANALYZE_RESULT_PATH);
        if (!Files.isRegularFile(resultPath)) {
            return null;
        }

        String cachedResult = new String(Files.readAllBytes(resultPath), StandardCharsets.UTF_8);
        if (cachedResult.trim().isEmpty()) {
            return null;
        }
        return cachedResult;
    }
}
