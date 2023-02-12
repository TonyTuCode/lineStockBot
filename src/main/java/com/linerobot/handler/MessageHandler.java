package com.linerobot.handler;

import java.io.IOException;

import com.linerobot.crawler.CrawlingBuySell;
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

    @Autowired
    private CrawlingBuySell crawlingBuySell;

    @Value("${line.bot.token}")
    private String LINE_TOKEN;

    public void doAction(JSONObject event) {
        String token = event.getString("replyToken");
        switch (eventAnalyzer(event)){
            case MessageCode.DAILY_REPORT:
                sendLinePlatform(text(token, crawlingBuySell.getBuySellOver("")));
                break;
            case MessageCode.HIS_DAY_REPORT:
                String date = event.getJSONObject("message").getString("text").substring(3, 11);
                sendLinePlatform(text(token, crawlingBuySell.getBuySellOver(date)));
                break;
        }
    }

    //分析event
    private int eventAnalyzer(JSONObject event){
        String eventText = event.getJSONObject("message")
                .getString("text").toLowerCase(); //統一轉小寫方便辨識
        if (eventText.equals("day")) {
            return MessageCode.DAILY_REPORT;
        }
        else if (eventText.matches("day{1}[0-9]{8}")) {
            return MessageCode.HIS_DAY_REPORT;
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
