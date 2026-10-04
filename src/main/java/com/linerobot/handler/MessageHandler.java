package com.linerobot.handler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.linerobot.crawler.BuyOverAnalyzeCrawler;
import com.linerobot.crawler.BuySellCrawler;
import com.linerobot.crawler.DispositionCrawler;
import com.linerobot.crawler.DominatorCrawler;
import com.linerobot.crawler.StrongCrawler;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

@Service
public class MessageHandler {

    private static final String LINE_MSG_API = "https://api.line.me/v2/bot/message/reply";

    private BuySellCrawler buySellCrawler;

    private StrongCrawler strongCrawler;

    private DominatorCrawler dominatorCrawler;

    private BuyOverAnalyzeCrawler buyOverAnalyzeCrawler;

    private DispositionCrawler dispositionCrawler;

    private MenuCode menuCode;

    public MessageHandler (BuySellCrawler buySellCrawler, StrongCrawler strongCrawler, DominatorCrawler dominatorCrawler,
                           BuyOverAnalyzeCrawler buyOverAnalyzeCrawler, DispositionCrawler dispositionCrawler, MenuCode menuCode){
        this.buySellCrawler = buySellCrawler;
        this.strongCrawler = strongCrawler;
        this.dominatorCrawler = dominatorCrawler;
        this.buyOverAnalyzeCrawler = buyOverAnalyzeCrawler;
        this.dispositionCrawler = dispositionCrawler;
        this.menuCode = menuCode;
    }


    @Value("${line.bot.token}")
    private String LINE_TOKEN;

    public void doAction(JSONObject event) throws IOException {
        String token = event.getString("replyToken");
        String evenText = event.getJSONObject("message").getString("text").trim().toLowerCase();
        switch (eventAnalyzer(evenText)) {
            case MenuCode.MENU:
                sendLinePlatform(text(token, menuCode.getMenu()));
                break;
            case MenuCode.NEW_MENU:
                sendLinePlatform(textNewMenu(token));
                break;
            case MenuCode.BUY_OVER_MENU:
                sendLinePlatform(textBuyMenu(token));
                break;
            case MenuCode.DAILY_REPORT:
                sendLinePlatform(text(token, buySellCrawler.getBuySellOver("")));
                break;
            case MenuCode.HIS_DAY_REPORT:
                String date = evenText.substring(3, evenText.length());
                sendLinePlatform(text(token, buySellCrawler.getBuySellOver(date)));
                break;
            case MenuCode.STRONGER_THAN_WTX:
                String days = evenText.substring(6, evenText.length());
                sendLinePlatform(text(token, strongCrawler.getRiseTop(Integer.valueOf(days))));
                break;
            case MenuCode.FOREIGN_BUY:
                sendLinePlatform(text(token, buySellCrawler.getBuyOverStockTop(1)));
                break;
            case MenuCode.INV_TRU_BUY:
                sendLinePlatform(text(token, buySellCrawler.getBuyOverStockTop(2)));
                break;
            case MenuCode.FOREIGN_INV_TOGETHER_BUY:
                sendLinePlatform(text(token, buySellCrawler.getBuyOverStockTop(3)));
                break;
            case MenuCode.BUY_OVER_ANALYZE:
                sendLinePlatform(text(token, buyOverAnalyzeCrawler.getBuyOverAnalyzeResult()));
                break;
            case MenuCode.SELL_OVER_MENU:
                sendLinePlatform(textSellMenu(token));
                break;
            case MenuCode.FOREIGN_SELL:
                sendLinePlatform(text(token, buySellCrawler.getSellOverStockTop(1)));
                break;
            case MenuCode.INV_TRU_SELL:
                sendLinePlatform(text(token, buySellCrawler.getSellOverStockTop(2)));
                break;
            case MenuCode.FOREIGN_INV_TOGETHER_SELL:
                sendLinePlatform(text(token, buySellCrawler.getSellOverStockTop(3)));
                break;
            case MenuCode.DISPOSITION_ANALYZE:
                sendLinePlatform(text(token, dispositionCrawler.getDispositionAnalysis()));
                break;
            case MenuCode.DOMINATOR:
                String stockNum = evenText.substring(4, evenText.length());
                String analyzeResult = "";
                try {
                    analyzeResult = dominatorCrawler.dominateCrawlingAndAnalyze(stockNum);
                } catch (Exception e){
                    e.printStackTrace();
                    analyzeResult = "系統錯誤，請重新確認後再試";
                }
                sendLinePlatform(text(token, analyzeResult));
        }
    }

    //分析event
    private int eventAnalyzer(String eventText){

        if (eventText.equals("menu")) {
            return MenuCode.MENU;
        }
        if (eventText.equals("newmenu")){
            return MenuCode.NEW_MENU;
        }
        if (eventText.equals("buymenu")){
            return MenuCode.BUY_OVER_MENU;
        }
        if (eventText.equals("day")) {
            return MenuCode.DAILY_REPORT;
        }
        if (eventText.matches("day{1}[0-9]{8}")) {
            return MenuCode.HIS_DAY_REPORT;
        }
        if (eventText.matches("strong{1}[2-5]{1}")){
            return MenuCode.STRONGER_THAN_WTX;
        }
        if (eventText.equals("foreignbuy")){
            return MenuCode.FOREIGN_BUY;
        }
        if (eventText.equals("invtrubuy")){
            return MenuCode.INV_TRU_BUY;
        }
        if (eventText.equals("togetherbuy")){
            return MenuCode.FOREIGN_INV_TOGETHER_BUY;
        }
        if (eventText.equals("buyoveranalyze")){
            return MenuCode.BUY_OVER_ANALYZE;
        }
        if (eventText.equals("sellmenu")){
            return MenuCode.SELL_OVER_MENU;
        }
        if (eventText.equals("foreignsell")){
            return MenuCode.FOREIGN_SELL;
        }
        if (eventText.equals("invtrusell")){
            return MenuCode.INV_TRU_SELL;
        }
        if (eventText.equals("togethersell")){
            return MenuCode.FOREIGN_INV_TOGETHER_SELL;
        }
        if (eventText.equals("disposition") || eventText.equals("處置股分析")){
            return MenuCode.DISPOSITION_ANALYZE;
        }
        if (eventText.matches("ctrl{1}[a-zA-Z0-9]*")){
            return MenuCode.DOMINATOR;
        }

        return 0;
    }

    /**
     * 回傳JSON字串組裝
     * @param replyToken
     * @param text
     * @return JSONObject
     */
    private JSONObject text(String replyToken, String text) {
        JSONObject body = new JSONObject();
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("type", "text");
        //放入回傳訊息
        message.put("text", text);
        messages.put(message);
        //放入reply token
        body.put("replyToken", replyToken);
        body.put("messages", messages);
        return body;
    }

    /** 常用功能主題色(藍) */
    private static final String COLOR_COMMON = "#3B82F6";

    /** 買超主題色(紅,漲) */
    private static final String COLOR_BUY = "#E03131";

    /** 賣超主題色(綠,跌) */
    private static final String COLOR_SELL = "#2F9E44";

    /**
     * 新版整合選單：以 LINE Flex Message 的 carousel 一次呈現常用/買超/賣超，
     * 取代原本 buttons template 的巢狀選單。
     * @param replyToken
     * @return JSONObject
     */
    private JSONObject textNewMenu(String replyToken){
        Map<String, String> common = new LinkedHashMap<>();
        common.put("day", "每日籌碼");
        common.put("strong3", "3日勝大盤");
        common.put("disposition", "處置股分析");
        common.put("menu", "完整指令表");

        JSONArray bubbles = new JSONArray();
        bubbles.put(buildMenuBubble("常用功能", COLOR_COMMON, common));
        bubbles.put(buildMenuBubble("三日買超", COLOR_BUY, buyMenuItems()));
        bubbles.put(buildMenuBubble("三日賣超", COLOR_SELL, sellMenuItems()));

        return flexMenuMessage("功能選單", replyToken, bubbles);
    }

    /**
     * 回傳買超選單(單張 Flex bubble)
     * @param replyToken
     * @return JSONObject
     */
    private JSONObject textBuyMenu(String replyToken){
        JSONArray bubbles = new JSONArray();
        bubbles.put(buildMenuBubble("三日買超", COLOR_BUY, buyMenuItems()));
        return flexMenuMessage("買超選單", replyToken, bubbles);
    }

    /**
     * 回傳賣超選單(單張 Flex bubble)
     * @param replyToken
     * @return JSONObject
     */
    private JSONObject textSellMenu(String replyToken){
        JSONArray bubbles = new JSONArray();
        bubbles.put(buildMenuBubble("三日賣超", COLOR_SELL, sellMenuItems()));
        return flexMenuMessage("賣超選單", replyToken, bubbles);
    }

    private Map<String, String> buyMenuItems(){
        Map<String, String> items = new LinkedHashMap<>();
        items.put("foreignbuy", "外資3日買超");
        items.put("invtrubuy", "投信3日買超");
        items.put("togetherbuy", "土洋合攻3日買超");
        items.put("buyoveranalyze", "買超綜合分析");
        return items;
    }

    private Map<String, String> sellMenuItems(){
        Map<String, String> items = new LinkedHashMap<>();
        items.put("foreignsell", "外資3日賣超");
        items.put("invtrusell", "投信3日賣超");
        items.put("togethersell", "土洋合殺3日賣超");
        return items;
    }

    /**
     * 建立單一分類的 Flex bubble：標題列 + 一組指令按鈕。
     * 每個按鈕為 message action，點擊後送出對應指令字串。
     * @param title 分類標題
     * @param themeColor 主題色(hex)
     * @param commandAndWord key=送出的指令, value=按鈕顯示文字(<=20字)
     * @return bubble 的 JSONObject
     */
    private JSONObject buildMenuBubble(String title, String themeColor, Map<String, String> commandAndWord){
        JSONArray buttonContents = new JSONArray();
        commandAndWord.forEach((command, word) -> {
            JSONObject action = new JSONObject()
                    .put("type", "message")
                    .put("label", word)
                    .put("text", command);
            JSONObject button = new JSONObject()
                    .put("type", "button")
                    .put("style", "primary")
                    .put("color", themeColor)
                    .put("height", "sm")
                    .put("margin", "sm")
                    .put("action", action);
            buttonContents.put(button);
        });

        JSONObject titleText = new JSONObject()
                .put("type", "text")
                .put("text", title)
                .put("weight", "bold")
                .put("size", "lg")
                .put("color", "#FFFFFF");
        JSONObject header = new JSONObject()
                .put("type", "box")
                .put("layout", "vertical")
                .put("backgroundColor", themeColor)
                .put("paddingAll", "12px")
                .put("contents", new JSONArray().put(titleText));

        JSONObject bodyBox = new JSONObject()
                .put("type", "box")
                .put("layout", "vertical")
                .put("spacing", "sm")
                .put("paddingAll", "12px")
                .put("contents", buttonContents);

        return new JSONObject()
                .put("type", "bubble")
                .put("size", "kilo")
                .put("header", header)
                .put("body", bodyBox);
    }

    /**
     * 將 bubble 陣列包成 Flex Message：單張時直接用 bubble，多張時用 carousel。
     * @param altText 替代文字(<=400字，通知列/不支援裝置顯示)
     * @param replyToken
     * @param bubbles bubble 陣列(carousel 上限 12 張)
     * @return 可直接送出的 reply body
     */
    private JSONObject flexMenuMessage(String altText, String replyToken, JSONArray bubbles){
        JSONObject contents;
        if (bubbles.length() == 1) {
            contents = bubbles.getJSONObject(0);
        } else {
            contents = new JSONObject()
                    .put("type", "carousel")
                    .put("contents", bubbles);
        }

        JSONObject message = new JSONObject()
                .put("type", "flex")
                .put("altText", altText)
                .put("contents", contents);

        JSONObject body = new JSONObject();
        body.put("replyToken", replyToken);
        body.put("messages", new JSONArray().put(message));
        return body;
    }

    /**
     * 傳送至line
     * @param json
     */
    private void sendLinePlatform(JSONObject json) {
        //組建Request
        Request request = new Request.Builder()
                .url(LINE_MSG_API)
                //header放入line token令牌
                .header("Authorization", "Bearer {" + LINE_TOKEN + "}")
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json.toString()))
                .build();
        //發出Request
        OkHttpClient client = new OkHttpClient();
        client.newCall(request).enqueue(new Callback() {
            //覆寫成功時的行為
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                System.out.println("line response");
            }
            //覆寫失敗時的行為
            @Override
            public void onFailure(Call call, IOException e) {
                System.err.println(e);
            }
        });
    }
}
