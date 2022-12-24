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

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import static java.time.format.DateTimeFormatter.BASIC_ISO_DATE;

public class CrawlingBuySell {

	private static final String STOCK_DAILY = "https://www.twse.com.tw/fund/BFI82U?response=json&dayDate=";
	public static void main(String[] args) throws IOException {
		SSLHelper.init();
		String today = LocalDate.now().format(BASIC_ISO_DATE);
		URL obj = new URL(STOCK_DAILY+20221221);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
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

			//撈JSON資料
			JSONArray dataJArr = new JSONObject("{\"responseData\":"+response+"}")
					.getJSONObject("responseData")
					.getJSONArray("data");
			//自營商
			BigDecimal dealer = getCalculatedVal(getArrayByIdx(dataJArr,0)[3]).add(getCalculatedVal(getArrayByIdx(dataJArr,1)[3]));
			//外資
			BigDecimal foreign = getCalculatedVal(getArrayByIdx(dataJArr,3)[3]);
			//投信
			BigDecimal sit = getCalculatedVal(getArrayByIdx(dataJArr,2)[3]);
			//合計
			BigDecimal total = getCalculatedVal(getArrayByIdx(dataJArr,4)[3]);

			System.out.println("外資買賣超(億): "+foreign);
			System.out.println("投信買賣超(億): "+sit);
			System.out.println("自營商買賣超(億): "+dealer);
			System.out.println("合計(億): "+total);


		} else {
			System.out.println("GET request did not work.");
		}


	}
	public static String[] getArrayByIdx(JSONArray data,int idx){
		String [] arr = data.get(idx).toString()
				.replaceAll("\\[\"", "")
				.replaceAll("\"]", "")
				.split("\",\"");
		return arr;
	}
	public static BigDecimal getCalculatedVal(String unParsedStr){
		double doubleVal =Double.parseDouble(unParsedStr.replaceAll(",",""));
		BigDecimal calDecimal = new BigDecimal(doubleVal/100000000).setScale(2, RoundingMode.HALF_UP);
		return calDecimal;
	}

}
