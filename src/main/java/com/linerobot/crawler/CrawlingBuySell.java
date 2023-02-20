package com.linerobot.crawler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.linerobot.tools.RequestSender;
import com.linerobot.tools.SSLHelper;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import static java.time.format.DateTimeFormatter.BASIC_ISO_DATE;
@Component
public class CrawlingBuySell {

	private static final String STOCK_DAILY = "https://www.twse.com.tw/fund/BFI82U?response=json&dayDate=";

	private static final String BUY_OVER = "https://www.twse.com.tw/zh/fund/TWT38U";

	/**
	 * get 法人連續買超
	 * @return returnMessage
	 */
	public String getBuyOverStockTop () {
		RequestSender requestSender = new RequestSender();
		String returnMessage = "";
		try {
			Map map = new HashMap();
			map.put("date", "20230216");
			returnMessage = requestSender.postRequester(BUY_OVER, map);
		}catch (Exception e){
			e.printStackTrace();
		}
		return returnMessage;
	}


	/**
	 * get 三大法人買賣超
	 * @param day
	 * @return returnMessage
	*/
	public String getBuySellOver (String day) {
		String returnMessage = "";
		SSLHelper.init();
		day = StringUtils.isBlank(day) ? LocalDate.now().format(BASIC_ISO_DATE) : day;
		RequestSender requestSender = new RequestSender();
		try {
				String response = requestSender.getRequester(STOCK_DAILY+day);
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
				messageCombine.append(day+"籌碼日報");
				messageCombine.append("\n外資買賣超(億): " + foreign);
				messageCombine.append("\n投信買賣超(億): " + sit);
				messageCombine.append("\n自營商買賣超(億): " + dealer);
				messageCombine.append("\n合計(億): " + total);
				returnMessage = messageCombine.toString();

		}catch (Exception e){
			returnMessage = "查無該日資料，請重新確認";
			e.printStackTrace();
		}
		return returnMessage;
	}

	private String[] getArrayByIdx(JSONArray data,int idx){
		String [] arr = data.get(idx).toString()
				.replaceAll("\\[\"", "")
				.replaceAll("\"]", "")
				.split("\",\"");
		return arr;
	}
	private BigDecimal getCalculatedVal(String unParsedStr){
		double doubleVal =Double.parseDouble(unParsedStr.replaceAll(",",""));
		BigDecimal calDecimal = new BigDecimal(doubleVal/100000000).setScale(2, RoundingMode.HALF_UP);
		return calDecimal;
	}

}
