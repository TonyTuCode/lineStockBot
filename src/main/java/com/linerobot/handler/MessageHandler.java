package com.linerobot.handler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.linerobot.crawler.CrawlingBuySell;
import com.linerobot.crawler.CrawlingStrong;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    private CrawlingBuySell crawlingBuySell;

    private CrawlingStrong crawlingStrong;

    private MenuCode menuCode;

    public MessageHandler (CrawlingBuySell crawlingBuySell,CrawlingStrong crawlingStrong,MenuCode menuCode){
        this.crawlingBuySell = crawlingBuySell;
        this.crawlingStrong = crawlingStrong;
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
                sendLinePlatform(text(token, crawlingBuySell.getBuySellOver("")));
                break;
            case MenuCode.HIS_DAY_REPORT:
                String date = evenText.substring(3, evenText.length());
                sendLinePlatform(text(token, crawlingBuySell.getBuySellOver(date)));
                break;
            case MenuCode.STRONGER_THAN_WTX:
                String days = evenText.substring(6, evenText.length());
                sendLinePlatform(text(token, crawlingStrong.getRiseTop(Integer.valueOf(days))));
                break;
            case MenuCode.FOREIGN_BUY:
                sendLinePlatform(text(token, crawlingBuySell.getBuyOverStockTop(1)));
                break;
            case MenuCode.INV_TRU_BUY:
                sendLinePlatform(text(token, crawlingBuySell.getBuyOverStockTop(2)));
                break;
            case MenuCode.FOREIGN_INV_TOGETHER_BUY:
                sendLinePlatform(text(token, crawlingBuySell.getBuyOverStockTop(3)));
                break;
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

    /**
     * 回傳新版菜單 TODO 待整合
     * @param replyToken
     * @return JSONObject
     */
    private  JSONObject textNewMenu(String replyToken){
        Map commandAndWord = new TreeMap();
        commandAndWord.put("buymenu", "買超選單");
        commandAndWord.put("day", "每日籌碼");
        commandAndWord.put("strong3","3日勝大盤");
        return this.menuConvertor("常用指令表",commandAndWord,replyToken);
    }

    /**
     * 回傳買超菜單
     * @param replyToken
     * @return JSONObject
     */
    private JSONObject textBuyMenu(String replyToken){
        Map commandAndWord = new TreeMap();
        commandAndWord.put("foreignbuy","外資3日買超");
        commandAndWord.put("invtrubuy","投信3日買超");
        commandAndWord.put("togetherbuy", "土洋合攻3日買超");
        return this.menuConvertor("買超指令表",commandAndWord,replyToken);
    }


    private JSONObject menuConvertor (String title, Map<String,String> commandAndWord, String replyToken) {
        JSONObject body = new JSONObject();
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        JSONObject template = new JSONObject();
        JSONArray actions = new JSONArray();
        commandAndWord.forEach( (command,word) -> {
            actions.put(new JSONObject().put("type", "message").put("text", command).put("label", word));
        });

        template.put("text", title);
        template.put("type", "buttons");
        template.put("actions", actions);
        message.put("template", template);
        message.put("altText", title);
        message.put("type", "template");
        //放入回傳訊息
        messages.put(message);
        //放入reply token
        body.put("replyToken", replyToken);
        body.put("messages", messages);
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
