package com.linerobot.tools;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Component
public class RequestSender {

    public String postRequester(String url,Map<String,String> paramMap) throws IOException {
        String returnMessage = "";
        SSLHelper.init();
            //用於拼接多組參數
            List<String> paramList = new LinkedList<>();
            for (Map.Entry<String, String> entry : paramMap.entrySet()){
                paramList.add(entry.getKey() + "=" + entry.getValue());
            }
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setDoOutput(true);
            con.setRequestMethod("POST");
            con.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded; charset=utf-8" );
            OutputStream outputStream = con.getOutputStream();
            outputStream.write(String.join("&", paramList).getBytes());
            if (con.getResponseCode() == HttpURLConnection.HTTP_OK){
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(),"UTF-8"));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                returnMessage = response.toString();
            }
        return returnMessage;
    }

    public String getRequester (String url) throws IOException {
        String returnMessage = "";
        SSLHelper.init();
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("GET");
        if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(),"UTF-8"));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            returnMessage = response.toString();
        }
        return returnMessage;
    }



}
