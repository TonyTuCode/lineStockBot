package com.linerobot.crawler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import static java.time.format.DateTimeFormatter.BASIC_ISO_DATE;
@Component
public class CrawlingBuySell {

	private static final String STOCK_DAILY = "https://www.twse.com.tw/fund/BFI82U?response=json&dayDate=";

	/**
	 * get 三大法人買賣超
	 * @param day
	 * @return returnMessage
	*/
	public String getBuySellOver (String day) {
		String returnMessage = "";
		SSLHelper.init();
		day = StringUtils.isBlank(day) ? LocalDate.now().format(BASIC_ISO_DATE) : day;
		System.out.println(day);
		try {
			HttpURLConnection con = (HttpURLConnection) new URL(STOCK_DAILY + day).openConnection();
			con.setRequestMethod("GET");
			int responseCode = con.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) { // success
				BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
				String inputLine;
				StringBuffer response = new StringBuffer();

				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
				in.close();

				//拚成JSON資料
				JSONArray dataJArr = new JSONObject("{\"responseData\":" + response + "}")
						.getJSONObject("responseData")
						.getJSONArray("data");
				//自營商
				BigDecimal dealer = getCalculatedVal(getArrayByIdx(dataJArr, 0)[3]).add(getCalculatedVal(getArrayByIdx(dataJArr, 1)[3]));
				//外資
				BigDecimal foreign = getCalculatedVal(getArrayByIdx(dataJArr, 3)[3]);
				//投信
				BigDecimal sit = getCalculatedVal(getArrayByIdx(dataJArr, 2)[3]);
				//合計
				BigDecimal total = getCalculatedVal(getArrayByIdx(dataJArr, 4)[3]);

				StringBuilder messageCombine = new StringBuilder();
				messageCombine.append("外資買賣超(億): " + foreign);
				messageCombine.append("\n投信買賣超(億): " + sit);
				messageCombine.append("\n自營商買賣超(億): " + dealer);
				messageCombine.append("\n合計(億): " + total);
				returnMessage = messageCombine.toString();

			} else {
				returnMessage = "請求異常，請確認網站問題";
			}
		}catch (Exception e){
			returnMessage = "發生錯誤，請稍後再試";
			e.printStackTrace();
		}
		return returnMessage;
	}

	public String[] getArrayByIdx(JSONArray data,int idx){
		String [] arr = data.get(idx).toString()
				.replaceAll("\\[\"", "")
				.replaceAll("\"]", "")
				.split("\",\"");
		return arr;
	}
	public BigDecimal getCalculatedVal(String unParsedStr){
		double doubleVal =Double.parseDouble(unParsedStr.replaceAll(",",""));
		BigDecimal calDecimal = new BigDecimal(doubleVal/100000000).setScale(2, RoundingMode.HALF_UP);
		return calDecimal;
	}

}
