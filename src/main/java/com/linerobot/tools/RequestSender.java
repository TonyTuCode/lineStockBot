package com.linerobot.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class RequestSender {

    public String postRequester(String url,Map<String,String> paramMap) throws IOException {
        String returnMessage = "";
        SSLHelper.init();
            //用於拼接多組參數
            String[] paramBuilderArr = new String[paramMap.size()];
            int arrayIndex = 0;
            for (Map.Entry<String, String> entry : paramMap.entrySet()){
                paramBuilderArr[arrayIndex] = entry.getKey() + "=" + entry.getValue();
                arrayIndex++;
            }
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setDoOutput(true);
            con.setRequestMethod("POST");
            con.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded" );
            OutputStream outputStream = con.getOutputStream();
            outputStream.write(String.join("&", paramBuilderArr).getBytes());
            if (con.getResponseCode() == HttpURLConnection.HTTP_OK){
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
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
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("GET");
        if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
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
