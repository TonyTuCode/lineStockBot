package com.linerobot.crawler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;

import com.linerobot.tools.RequestSender;
import com.linerobot.tools.SSLHelper;
import com.linerobot.vo.StockVO;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static java.time.format.DateTimeFormatter.BASIC_ISO_DATE;
@Component
public class CrawlingBuySell {

	private static final String STOCK_DAILY = "https://www.twse.com.tw/fund/BFI82U?response=json&dayDate=";

	private static final String BUY_OVER = "https://www.twse.com.tw/zh/fund/TWT38U";

	/**
	 * get 法人連續買超>3日
	 * @return returnMessage
	 */
	public String getBuyOverStockTop () {
		RequestSender requestSender = new RequestSender();
		StringBuffer returnMessage = new StringBuffer();
		Map<Integer,List<StockVO>> collectStockList = new TreeMap<>();
		try {
			Map<String, String> map = new HashMap();
			int collectDataDays = 0;
			int minusDay = 0;
			// 當天+往回抓2天買超股票 TODO 之後改為變數形式
			while (collectDataDays < 2){
				String dayBack =LocalDate.now().minusDays(minusDay).format(BASIC_ISO_DATE);
				map.put("date", dayBack);
				String response = requestSender.postRequester(BUY_OVER, map);
				JSONObject originData = new JSONObject(response);
				//用response裡的"stat":"OK" 分辨是否有拿到資料
				if (("OK").equals(originData.getString("stat"))){
					//建一個List存每天的資料
					List<StockVO> stockListByDay = new ArrayList<>();
					for (int i = 0 ; i <=100 ; i++){
						StockVO vo = new StockVO();
						String[] eachStockBlock = getArrayByIdx(originData.getJSONArray("data"),i);
						vo.setStockID(eachStockBlock[1].trim());
						vo.setStockName(eachStockBlock[2].trim());
						vo.setBuyOverQty(Long.valueOf(eachStockBlock[3].replace(",", "")));
						stockListByDay.add(vo);
					}
					collectStockList.put(collectDataDays,stockListByDay);
					collectDataDays++;
				}
				Thread.currentThread().sleep(1000); //避免請求過於頻繁
				minusDay++;
			}

			List<StockVO> comparedList = new ArrayList<>();
			for (int i = 0; i < collectStockList.size(); i++) {
				if (CollectionUtils.isEmpty(comparedList)){
					comparedList = collectStockList.get(i);
				} else {
					comparedList.retainAll(collectStockList.get(i));
				}
			}

			//組合回傳字串
			returnMessage.append("外資3日連續買超股:\n");
			comparedList.forEach(e->{
				returnMessage.append(e.getStockID() +" "+e.getStockName()+"\n");
			});

		}catch (Exception e){
			e.printStackTrace();
		}
		return returnMessage.toString();
	}


	/**
	 * get 三大法人買賣超
	 * @param day
	 * @return returnMessage
	*/
	public String getBuySellOver (String day) {
		String returnMessage = "";
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
