package com.linerobot.crawler;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import com.linerobot.tools.Convertor;
import com.linerobot.tools.RequestSender;
import com.linerobot.vo.StockVO;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import static java.time.format.DateTimeFormatter.BASIC_ISO_DATE;
@Component
public class CrawlingBuySell {

	private Convertor convertor;

	private RequestSender requestSender;

	public CrawlingBuySell (Convertor convertor,RequestSender requestSender) {
		this.convertor = convertor;
		this.requestSender = requestSender;
	}

	private static final String STOCK_DAILY = "https://www.twse.com.tw/rwd/zh/fund/BFI82U?Date=";

	private static final String FOREIGN_BUY_OVER = "https://www.twse.com.tw/rwd/zh/fund/TWT38U";

	private static final String INV_TRU_BUY_OVER = "https://www.twse.com.tw/rwd/zh/fund/TWT44U";


	/**
	 * get 法人連續買超>3日
	 * @Param juridicalPerson 1:外資 2:投信
	 * @return returnMessage
	 */
	public String getBuyOverStockTop (int juridicalPerson) {
		StringBuffer returnMessage = new StringBuffer();
		String restURL ="";
		try {
		switch (juridicalPerson) {
			case 1:
				restURL = FOREIGN_BUY_OVER;
				returnMessage.append("外資3日連續買超股:\n");
				break;
			case 2:
				restURL = INV_TRU_BUY_OVER;
				returnMessage.append("投信3日連續買超股:\n");
				break;
		}
			List<StockVO> comparedList = this.getBuyOverByURL(restURL);
			comparedList.forEach(e->{
				returnMessage.append(e.getStockID() +" "+e.getStockName()+"\n");
			});

		}catch (Exception e){
			e.printStackTrace();
		}
		return returnMessage.toString();
	}

	//將拿到買超集合方法獨立
	private List<StockVO> getBuyOverByURL(String restURL) throws IOException, InterruptedException {
		Map<Integer,List<StockVO>> collectStockList = new TreeMap<>();
		Map<String, String> map = new HashMap();
		int collectDataDays = 0;
		int minusDay = 0;
		// 當天+往回抓2天買超股票 TODO 之後改為變數形式
		while (collectDataDays <= 2){
			String dayBack =LocalDate.now().minusDays(minusDay).format(BASIC_ISO_DATE);
			map.put("date", dayBack);
			String response = requestSender.postRequester(restURL, map);
			JSONObject originData = new JSONObject(response);
			//用response裡的"stat":"OK" 分辨是否有拿到資料
			if (("OK").equals(originData.getString("stat"))){
				//建一個List存每天的資料
				List<StockVO> stockListByDay = new ArrayList<>();
				for (int i = 0 ; i <=100 ; i++){
					StockVO vo = new StockVO();
					String[] eachStockBlock = convertor.getArrayByIdx(originData.getJSONArray("data"),i);
					vo.setStockID(eachStockBlock[1].trim());
					vo.setStockName(eachStockBlock[2].trim());
					Long buyOverQty = Long.valueOf(eachStockBlock[5].replace(",", ""));
					vo.setBuyOverQty(buyOverQty/1000);
					//>0時才為買超
					if (buyOverQty>0){
						stockListByDay.add(vo);
					}
				}
				collectStockList.put(collectDataDays,stockListByDay);
				collectDataDays++;
			}
			Thread.currentThread().sleep(500); //避免請求過於頻繁
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

		return comparedList;
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
				BigDecimal dealer = new BigDecimal(0);
				//投信
				BigDecimal invTru = new BigDecimal(0);
				//外資
				BigDecimal foreign = new BigDecimal(0);
				//合計
				BigDecimal total = new BigDecimal(0);

				for (int i = 0 ; i <= 5 ; i++){
					BigDecimal amount = convertor.convertStr2CalculatedVal(convertor.getArrayByIdx(dataJArr, i)[3]);
					if (i == 0 || i == 1) {
						dealer = dealer.add(amount);
					} else if (i == 2) {
						invTru = invTru.add(amount);
					} else if (i == 3) {
						foreign = foreign.add(amount);
					} else if (i == 5) {
						total = total.add(amount);
					}
				}

				StringBuilder messageCombine = new StringBuilder();
				messageCombine.append(day+"籌碼日報");
				messageCombine.append("\n外資買賣超(億): " + foreign);
				messageCombine.append("\n投信買賣超(億): " + invTru);
				messageCombine.append("\n自營商買賣超(億): " + dealer);
				messageCombine.append("\n合計(億): " + total);
				returnMessage = messageCombine.toString();

		}catch (Exception e){
			returnMessage = "查無該日資料，請重新確認";
			e.printStackTrace();
		}
		return returnMessage;
	}




}
