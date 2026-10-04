package com.linerobot.crawler;

import com.linerobot.tools.RequestSender;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 目前處置股分析。
 *
 * <p>本類別只篩選目前仍在處置期間的四碼普通股，並以歷史處置資料的
 * 「同制度、同處置次數、同處置天數」統計，搭配最新日動能產生透明的 0~100 分排名。
 * 分數是研究用統計排序，不代表投資報酬保證。</p>
 */
@Component
public class DispositionCrawler {

    private static final String TWSE_DISPOSITION =
            "https://www.twse.com.tw/rwd/zh/announcement/punish";
    private static final String TPEX_DISPOSITION =
            "https://www.tpex.org.tw/www/zh-tw/bulletin/disposal";
    private static final String TWSE_STOCK_DAY =
            "https://www.twse.com.tw/rwd/zh/afterTrading/STOCK_DAY";
    private static final String TPEX_STOCK_DAY =
            "https://www.tpex.org.tw/www/zh-tw/afterTrading/tradingStock";
    private static final String TWSE_HOLIDAY_SCHEDULE =
            "https://www.twse.com.tw/rwd/zh/holidaySchedule/holidaySchedule";

    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    private static final LocalDate NEW_RULE_DATE = LocalDate.of(2026, 8, 10);
    private static final long CACHE_TTL_SECONDS = 900L;
    private static final Pattern ROC_DATE_PATTERN =
            Pattern.compile("(\\d{2,3})/(\\d{1,2})/(\\d{1,2})");
    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("^\\d{4}$");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");

    private static final double[][] NEW_FIRST = {
            {0.552, 0.0105}, {0.554, 0.0118}, {0.540, 0.0053}, {0.541, 0.0121},
            {0.678, 0.0216}
    };
    private static final double[][] NEW_SECOND = {
            {0.419, -0.0148}, {0.595, 0.0124}, {0.525, 0.0010}, {0.625, 0.0068},
            {0.579, 0.0089}
    };

    private final RequestSender requestSender;
    private final Object cacheLock = new Object();
    private final Map<Integer, Set<LocalDate>> marketHolidayCache = new HashMap<>();
    private volatile AnalysisCache analysisCache;

    public DispositionCrawler(RequestSender requestSender) {
        this.requestSender = requestSender;
    }

    /** 回傳目前處置股的模型排名，最多 10 檔。 */
    public String getDispositionAnalysis() {
        LocalDate today = LocalDate.now(TAIPEI_ZONE);
        AnalysisCache cached = analysisCache;
        if (cached != null && cached.date.equals(today)
                && cached.createdAt.plusSeconds(CACHE_TTL_SECONDS).isAfter(java.time.Instant.now())) {
            return cached.result;
        }

        synchronized (cacheLock) {
            cached = analysisCache;
            if (cached != null && cached.date.equals(today)
                    && cached.createdAt.plusSeconds(CACHE_TTL_SECONDS).isAfter(java.time.Instant.now())) {
                return cached.result;
            }
            try {
                String result = buildAnalysis(today);
                analysisCache = new AnalysisCache(today, result);
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return "處置股分析目前無法取得資料，請稍後再試。";
            }
        }
    }

    private String buildAnalysis(LocalDate today) throws IOException {
        List<DispositionEvent> events = getCurrentEvents(today);
        if (events.isEmpty()) {
            return "目前查無仍在處置期間的四碼普通股。\n資料日：" + today;
        }

        Map<String, List<PricePoint>> priceCache = new HashMap<>();
        List<RankedStock> rankedStocks = new ArrayList<>();
        for (DispositionEvent event : events) {
            List<PricePoint> prices = getPriceHistory(event, today, priceCache);
            rankedStocks.add(score(event, prices, today));
        }
        rankedStocks.sort(Comparator.comparingDouble(RankedStock::getScore).reversed()
                .thenComparing(Comparator.comparingDouble(RankedStock::getProbability).reversed())
                .thenComparing(RankedStock::getCode));

        StringBuilder result = new StringBuilder();
        result.append("處置股分析（新制隔日統計模型排名）\n")
                .append("資料日：").append(today).append("｜目前新制處置：")
                .append(events.size()).append(" 檔\n")
                .append("評分：新制歷史同類上漲機率70%＋新制歷史平均漲跌20%＋近期動能10%\n")
                .append("僅供研究參考，不代表隔日一定上漲或構成投資建議。\n\n");

        int limit = Math.min(10, rankedStocks.size());
        for (int i = 0; i < limit; i++) {
            RankedStock stock = rankedStocks.get(i);
            result.append(i + 1).append(". ").append(stock.code).append(" ")
                    .append(stock.name).append("｜分數 ")
                    .append(DECIMAL_FORMAT.format(stock.score)).append("\n")
                    .append("   ").append(stock.market).append(" ")
                    .append(stock.kind).append(" 第").append(stock.dayNumber)
                    .append("天/").append(stock.regimeDays).append("天");
            if (stock.dayNumber > stock.modelDay) {
                result.append("（以第").append(stock.modelDay).append("天模型）");
            }
            result.append("｜歷史上漲 ").append(formatPercent(stock.probability, false))
                    .append("｜歷史均值 ").append(formatPercent(stock.averageReturn, true)).append("\n")
                    .append("   最新日動能 ").append(formatPercentOrUnknown(stock.latestReturn))
                    .append("｜模型判定：").append(stock.signal).append("\n");
        }
        if (rankedStocks.size() > limit) {
            result.append("\n其餘 ").append(rankedStocks.size() - limit).append(" 檔未列出。");
        }
        return result.toString().trim();
    }

    private List<DispositionEvent> getCurrentEvents(LocalDate today) throws IOException {
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth previousMonth = currentMonth.minusMonths(1);
        Map<String, DispositionEvent> events = new LinkedHashMap<>();
        loadTwseEvents(previousMonth, events);
        loadTwseEvents(currentMonth, events);
        loadTpexEvents(previousMonth, events);
        loadTpexEvents(currentMonth, events);

        Map<String, DispositionEvent> activeByCode = new LinkedHashMap<>();
        for (DispositionEvent event : events.values()) {
            if (STOCK_CODE_PATTERN.matcher(event.code).matches()
                    && !event.start.isBefore(NEW_RULE_DATE)
                    && !event.start.isAfter(today) && !event.end.isBefore(today)
                    && !"其他".equals(event.kind)) {
                String stockKey = event.market + "|" + event.code;
                DispositionEvent existing = activeByCode.get(stockKey);
                if (existing == null || isMoreRecentDisposition(event, existing)) {
                    activeByCode.put(stockKey, event);
                }
            }
        }
        return new ArrayList<>(activeByCode.values());
    }

    private boolean isMoreRecentDisposition(DispositionEvent candidate, DispositionEvent existing) {
        if (!candidate.start.equals(existing.start)) {
            return candidate.start.isAfter(existing.start);
        }
        return "第二次".equals(candidate.kind) && "第一次".equals(existing.kind);
    }

    private void loadTwseEvents(YearMonth month, Map<String, DispositionEvent> events) throws IOException {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        String url = TWSE_DISPOSITION + "?startDate=" + formatDate(start)
                + "&endDate=" + formatDate(end) + "&response=json";
        String response = requestSender.getRequester(url);
        if (response == null || response.trim().isEmpty()) {
            return;
        }
        JSONObject root = new JSONObject(response);
        JSONArray rows = root.optJSONArray("data");
        JSONArray fields = root.optJSONArray("fields");
        if (rows == null || fields == null) {
            return;
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONArray row = rows.optJSONArray(i);
            if (row == null) {
                continue;
            }
            addEvent(events, "上市", fields, row, "處置起迄時間", 6);
        }
    }

    private void loadTpexEvents(YearMonth month, Map<String, DispositionEvent> events) throws IOException {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        String url = TPEX_DISPOSITION + "?startDate=" + toRocDate(start)
                + "&endDate=" + toRocDate(end) + "&response=json";
        String response = requestSender.getRequester(url);
        if (response == null || response.trim().isEmpty()) {
            return;
        }
        JSONObject root = new JSONObject(response);
        JSONArray tables = root.optJSONArray("tables");
        if (tables == null) {
            return;
        }
        for (int t = 0; t < tables.length(); t++) {
            JSONObject table = tables.optJSONObject(t);
            if (table == null) {
                continue;
            }
            JSONArray fields = table.optJSONArray("fields");
            JSONArray rows = table.optJSONArray("data");
            if (fields == null || rows == null) {
                continue;
            }
            for (int i = 0; i < rows.length(); i++) {
                JSONArray row = rows.optJSONArray(i);
                if (row != null) {
                    addEvent(events, "上櫃", fields, row, "處置起訖時間", 5);
                }
            }
        }
    }

    private void addEvent(Map<String, DispositionEvent> events, String market,
                          JSONArray fields, JSONArray row, String periodField, int fallbackIndex) {
        String code = fieldValue(fields, row, "證券代號", 2).trim();
        String period = fieldValue(fields, row, periodField, fallbackIndex);
        List<LocalDate> dates = parseDates(period);
        if (!STOCK_CODE_PATTERN.matcher(code).matches() || dates.size() < 2) {
            return;
        }
        String measure = fieldValue(fields, row, "處置措施", 7).trim();
        String kind = classify(measure);
        String name = fieldValue(fields, row, "證券名稱", 3)
                .replaceAll("\\(.*?\\)", "").trim();
        String key = market + "|" + code + "|" + dates.get(0) + "|" + dates.get(1) + "|" + kind;
        events.putIfAbsent(key, new DispositionEvent(market, code, name, dates.get(0), dates.get(1), kind));
    }

    private List<PricePoint> getPriceHistory(DispositionEvent event, LocalDate today,
                                              Map<String, List<PricePoint>> priceCache) throws IOException {
        YearMonth month = YearMonth.from(today);
        YearMonth previousMonth = month.minusMonths(1);
        String cacheKey = event.market + "|" + event.code;
        List<PricePoint> prices = priceCache.get(cacheKey);
        if (prices != null) {
            return prices;
        }
        prices = new ArrayList<>();
        prices.addAll(loadPrices(event.market, event.code, previousMonth));
        prices.addAll(loadPrices(event.market, event.code, month));
        prices.sort(Comparator.comparing(price -> price.date));
        priceCache.put(cacheKey, prices);
        return prices;
    }

    private List<PricePoint> loadPrices(String market, String code, YearMonth month) throws IOException {
        String url;
        if ("上市".equals(market)) {
            url = TWSE_STOCK_DAY + "?date=" + formatDate(month.atDay(1))
                    + "&stockNo=" + code + "&response=json";
        } else {
            url = TPEX_STOCK_DAY + "?code=" + code + "&date="
                    + toRocDate(month.atDay(1)) + "&response=json";
        }
        String response = requestSender.getRequester(url);
        if (response == null || response.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<PricePoint> prices = new ArrayList<>();
        JSONObject root = new JSONObject(response);
        JSONArray rows;
        if ("上市".equals(market)) {
            rows = root.optJSONArray("data");
        } else {
            JSONArray tables = root.optJSONArray("tables");
            JSONObject firstTable = tables == null ? null : tables.optJSONObject(0);
            rows = firstTable == null ? null : firstTable.optJSONArray("data");
        }
        if (rows == null) {
            return prices;
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONArray row = rows.optJSONArray(i);
            if (row == null || row.length() < 8) {
                continue;
            }
            LocalDate date = parseDate(row.optString(0));
            Double close = parseDouble(row.optString(6));
            Double change = parseDouble(row.optString(7));
            if (date != null && close != null && change != null && close - change > 0) {
                prices.add(new PricePoint(date, change / (close - change)));
            }
        }
        return prices;
    }

    private RankedStock score(DispositionEvent event, List<PricePoint> prices, LocalDate today) throws IOException {
        int dayNumber = countTradingPriceDays(event.start, today, prices);
        int regimeDays = businessDays(event.start, event.end);
        double[][] model = selectNewModel("第一次".equals(event.kind));
        int modelDay = Math.min(dayNumber, model.length);
        int modelIndex = Math.max(0, modelDay - 1);
        double probability = model[modelIndex][0];
        double averageReturn = model[modelIndex][1];

        List<PricePoint> usable = new ArrayList<>();
        for (PricePoint price : prices) {
            if (!price.date.isAfter(today) && !price.date.isBefore(event.start)) {
                usable.add(price);
            }
        }
        Double latestReturn = usable.isEmpty() ? null : usable.get(usable.size() - 1).dailyReturn;
        double momentum = latestReturn == null ? 0.5 : clamp((latestReturn + 0.10) / 0.20, 0, 1);
        double returnComponent = clamp((averageReturn + 0.05) / 0.10, 0, 1);
        double score = probability * 70 + returnComponent * 20 + momentum * 10;
        String signal = score >= 65 ? "偏多" : score >= 55 ? "中性偏多" : "觀察";
        return new RankedStock(event, dayNumber, regimeDays, modelDay, probability, averageReturn,
                latestReturn, score, signal);
    }

    private double[][] selectNewModel(boolean firstDisposition) {
        return firstDisposition ? NEW_FIRST : NEW_SECOND;
    }

    private int countTradingPriceDays(LocalDate start, LocalDate end, List<PricePoint> prices)
            throws IOException {
        int count = 0;
        for (PricePoint price : prices) {
            if (!price.date.isBefore(start) && !price.date.isAfter(end)) {
                count++;
            }
        }
        return count == 0 ? businessDays(start, end) : count;
    }

    private int businessDays(LocalDate start, LocalDate end) throws IOException {
        int count = 0;
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            if (isTradingDay(cursor)) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return Math.max(1, count);
    }

    private boolean isTradingDay(LocalDate date) throws IOException {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY
                && day != DayOfWeek.SUNDAY
                && !getMarketHolidays(date.getYear()).contains(date);
    }

    private Set<LocalDate> getMarketHolidays(int year) throws IOException {
        synchronized (cacheLock) {
            Set<LocalDate> cachedHolidays = marketHolidayCache.get(year);
            if (cachedHolidays != null) {
                return cachedHolidays;
            }

            String url = TWSE_HOLIDAY_SCHEDULE + "?queryYear=" + year + "&response=json";
            String response = requestSender.getRequester(url);
            Set<LocalDate> holidays = new HashSet<>();
            if (response != null && !response.trim().isEmpty()) {
                JSONObject root = new JSONObject(response);
                JSONArray rows = root.optJSONArray("data");
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONArray row = rows.optJSONArray(i);
                        if (row == null || row.length() == 0) {
                            continue;
                        }
                        try {
                            holidays.add(LocalDate.parse(row.optString(0)));
                        } catch (Exception ignored) {
                            // 忽略無法解析的休市日期，其他日期仍可繼續統計。
                        }
                    }
                }
            }
            Set<LocalDate> immutableHolidays = Collections.unmodifiableSet(holidays);
            marketHolidayCache.put(year, immutableHolidays);
            return immutableHolidays;
        }
    }

    private String fieldValue(JSONArray fields, JSONArray row, String name, int fallbackIndex) {
        for (int i = 0; i < fields.length(); i++) {
            if (name.equals(fields.optString(i).trim()) && i < row.length()) {
                return row.optString(i);
            }
        }
        return fallbackIndex < row.length() ? row.optString(fallbackIndex) : "";
    }

    private List<LocalDate> parseDates(String text) {
        List<LocalDate> dates = new ArrayList<>();
        Matcher matcher = ROC_DATE_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            dates.add(LocalDate.of(Integer.parseInt(matcher.group(1)) + 1911,
                    Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))));
        }
        return dates;
    }

    private LocalDate parseDate(String text) {
        List<LocalDate> dates = parseDates(text);
        return dates.isEmpty() ? null : dates.get(0);
    }

    private String formatDate(LocalDate date) {
        return String.format("%04d%02d%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    private String toRocDate(LocalDate date) {
        return String.format("%03d/%02d/%02d", date.getYear() - 1911,
                date.getMonthValue(), date.getDayOfMonth());
    }

    private String classify(String measure) {
        if (measure.contains("第一次")) {
            return "第一次";
        }
        if (measure.contains("第二次") || measure.contains("再次")) {
            return "第二次";
        }
        return "其他";
    }

    private Double parseDouble(String text) {
        if (text == null || text.trim().isEmpty() || text.trim().startsWith("X")) {
            return null;
        }
        try {
            return Double.parseDouble(text.replace(",", "").replace("+", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String formatPercent(double value, boolean signed) {
        return String.format("%s%.2f%%", signed && value >= 0 ? "+" : "", value * 100);
    }

    private String formatPercentOrUnknown(Double value) {
        return value == null ? "無資料" : formatPercent(value, true);
    }

    private static class AnalysisCache {
        private final LocalDate date;
        private final java.time.Instant createdAt;
        private final String result;

        private AnalysisCache(LocalDate date, String result) {
            this.date = date;
            this.createdAt = java.time.Instant.now();
            this.result = result;
        }
    }

    private static class DispositionEvent {
        private final String market;
        private final String code;
        private final String name;
        private final LocalDate start;
        private final LocalDate end;
        private final String kind;

        private DispositionEvent(String market, String code, String name,
                                 LocalDate start, LocalDate end, String kind) {
            this.market = market;
            this.code = code;
            this.name = name;
            this.start = start;
            this.end = end;
            this.kind = kind;
        }
    }

    private static class PricePoint {
        private final LocalDate date;
        private final double dailyReturn;

        private PricePoint(LocalDate date, double dailyReturn) {
            this.date = date;
            this.dailyReturn = dailyReturn;
        }
    }

    private static class RankedStock {
        private final String code;
        private final String name;
        private final String market;
        private final String kind;
        private final int dayNumber;
        private final int regimeDays;
        private final int modelDay;
        private final double probability;
        private final double averageReturn;
        private final Double latestReturn;
        private final double score;
        private final String signal;

        private RankedStock(DispositionEvent event, int dayNumber, int regimeDays, int modelDay,
                            double probability, double averageReturn, Double latestReturn,
                            double score, String signal) {
            this.code = event.code;
            this.name = event.name;
            this.market = event.market;
            this.kind = event.kind;
            this.dayNumber = dayNumber;
            this.regimeDays = regimeDays;
            this.modelDay = modelDay;
            this.probability = probability;
            this.averageReturn = averageReturn;
            this.latestReturn = latestReturn;
            this.score = score;
            this.signal = signal;
        }

        private double getScore() {
            return score;
        }

        private double getProbability() {
            return probability;
        }

        private String getCode() {
            return code;
        }
    }
}
