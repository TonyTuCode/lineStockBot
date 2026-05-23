package com.linerobot.crawler;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
public class BuySellCrawler {

	private Convertor convertor;

	private RequestSender requestSender;

	public BuySellCrawler(Convertor convertor, RequestSender requestSender) {
		this.convertor = convertor;
		this.requestSender = requestSender;
	}

	private static final String STOCK_DAILY = "https://www.twse.com.tw/rwd/zh/fund/BFI82U?Date=";

	private static final String FOREIGN_BUY_OVER = "https://www.twse.com.tw/rwd/zh/fund/TWT38U";

	private static final String INV_TRU_BUY_OVER = "https://www.twse.com.tw/rwd/zh/fund/TWT44U";

	private static final String CACHE_ZONE = "Asia/Taipei";

	private static final Duration BUY_OVER_CACHE_TTL = Duration.ofHours(23);

	private final Object buyOverCacheLock = new Object();

	private volatile List<StockVO> foreignBuyOverCache = Collections.emptyList();

	private volatile List<StockVO> invTruBuyOverCache = Collections.emptyList();

	private volatile Instant foreignBuyOverCacheCreatedAt;

	private volatile Instant invTruBuyOverCacheCreatedAt;


	/**
	 * get 法人連續買超>3日
	 * @Param juridicalPerson 1:外資 2:投信 3:土洋合力
	 * @return returnMessage
	 */
	public String getBuyOverStockTop(int juridicalPerson) {
		StringBuffer returnMessage = new StringBuffer();
		List<StockVO> comparedList = new ArrayList<>();
		String buyOverSource = "";
		try {
			switch (juridicalPerson) {
				case 1:
					comparedList = this.getForeignBuyOver();
					buyOverSource = "外";
					returnMessage.append("外資3日連續買超股:\n");
					break;
				case 2:
					comparedList = this.getInvTruBuyOver();
					buyOverSource = "投";
					returnMessage.append("投信3日連續買超股:\n");
					break;
				case 3:
					List<StockVO> foreignList = this.getForeignBuyOver();
					List<StockVO> invTruList = this.getInvTruBuyOver();
					returnMessage.append("外資3日連續買超股:\n");
					appendBuyOverStockList(returnMessage, foreignList, "外");
					returnMessage.append("\n投信3日連續買超股:\n");
					appendBuyOverStockList(returnMessage, invTruList, "投");
					returnMessage.append("\n");
					returnMessage.append("土洋合攻3日連續買超股:\n");
					appendTogetherBuyOverStock(returnMessage, foreignList, invTruList);
					return returnMessage.toString();
			}

			//將結果印出
			appendBuyOverStockList(returnMessage, comparedList, buyOverSource);

		} catch (Exception e) {
			e.printStackTrace();
		}
		return returnMessage.toString();
	}

	//格式化買超輸出
	private String formatBuyOverStock(StockVO stockVO, String buyOverSource) {
		return stockVO.getStockID() + " " + stockVO.getStockName()
				+ " " + buyOverSource + ": " + getBuyOverQty(stockVO) + "張\n";
	}

	private void appendBuyOverStockList(StringBuffer returnMessage, List<StockVO> stockList, String buyOverSource) {
		for (StockVO stockVO : stockList) {
			returnMessage.append(formatBuyOverStock(stockVO, buyOverSource));
		}
	}

	private void appendTogetherBuyOverStock(StringBuffer returnMessage, List<StockVO> foreignList, List<StockVO> invTruList) {
		Map<String, StockVO> invTruMap = new HashMap<>();
		for (StockVO stockVO : invTruList) {
			invTruMap.put(stockVO.getStockID(), stockVO);
		}

		for (StockVO foreignStock : foreignList) {
			StockVO invTruStock = invTruMap.get(foreignStock.getStockID());
			if (invTruStock == null) {
				continue;
			}

			returnMessage.append(foreignStock.getStockID())
					.append(" ")
					.append(foreignStock.getStockName())
					.append(" 外: ")
					.append(getBuyOverQty(foreignStock))
					.append("張 投: ")
					.append(getBuyOverQty(invTruStock))
					.append("張\n");
		}
	}

	private Long getBuyOverQty(StockVO stockVO) {
		return stockVO.getBuyOverQty() == null ? 0L : stockVO.getBuyOverQty();
	}

	public void refreshBuyOverCache() throws IOException, InterruptedException {
		List<StockVO> foreignList = getBuyOverByURL(FOREIGN_BUY_OVER);
		List<StockVO> invTruList = getBuyOverByURL(INV_TRU_BUY_OVER);

		synchronized (buyOverCacheLock) {
			Instant createdAt = Instant.now();
			foreignBuyOverCache = toCacheList(foreignList);
			invTruBuyOverCache = toCacheList(invTruList);
			foreignBuyOverCacheCreatedAt = createdAt;
			invTruBuyOverCacheCreatedAt = createdAt;
		}
	}

	public List<StockVO> getForeignBuyOverStocks() throws IOException, InterruptedException {
		return getForeignBuyOver();
	}

	public List<StockVO> getInvTruBuyOverStocks() throws IOException, InterruptedException {
		return getInvTruBuyOver();
	}

	private List<StockVO> getForeignBuyOver() throws IOException, InterruptedException {
		List<StockVO> cachedList = foreignBuyOverCache;
		if (isCacheAvailable(cachedList, foreignBuyOverCacheCreatedAt)) {
			return toReturnList(cachedList);
		}

		List<StockVO> crawledList = getBuyOverByURL(FOREIGN_BUY_OVER);
		synchronized (buyOverCacheLock) {
			foreignBuyOverCache = toCacheList(crawledList);
			foreignBuyOverCacheCreatedAt = Instant.now();
		}
		return toReturnList(crawledList);
	}

	private List<StockVO> getInvTruBuyOver() throws IOException, InterruptedException {
		List<StockVO> cachedList = invTruBuyOverCache;
		if (isCacheAvailable(cachedList, invTruBuyOverCacheCreatedAt)) {
			return toReturnList(cachedList);
		}

		List<StockVO> crawledList = getBuyOverByURL(INV_TRU_BUY_OVER);
		synchronized (buyOverCacheLock) {
			invTruBuyOverCache = toCacheList(crawledList);
			invTruBuyOverCacheCreatedAt = Instant.now();
		}
		return toReturnList(crawledList);
	}

	private boolean isCacheAvailable(List<StockVO> stockList, Instant createdAt) {
		return stockList != null
				&& createdAt != null
				&& createdAt.plus(BUY_OVER_CACHE_TTL).isAfter(Instant.now());
	}

	private List<StockVO> toCacheList(List<StockVO> stockList) {
		if (stockList == null) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(new ArrayList<>(stockList));
	}

	private List<StockVO> toReturnList(List<StockVO> stockList) {
		if (stockList == null) {
			return new ArrayList<>();
		}
		return new ArrayList<>(stockList);
	}

	//將拿到買超集合方法獨立
	private List<StockVO> getBuyOverByURL(String restURL) throws IOException, InterruptedException {
		Map<Integer,List<StockVO>> collectStockList = new TreeMap<>();
		Map<String, String> map = new HashMap();
		int collectDataDays = 0;
		int minusDay = 0;
		// 當天+往回抓2天買超股票 TODO 之後改為變數形式
		while (collectDataDays <= 3){
			String dayBack =LocalDate.now().minusDays(minusDay).format(BASIC_ISO_DATE);
			map.put("date", dayBack);
			String response = requestSender.postRequester(restURL, map);
			JSONObject originData = new JSONObject(response);
			//用response裡的"stat":"OK" 分辨是否有拿到資料
			if (("OK").equals(originData.getString("stat"))){
				JSONArray sortedData = originData.getJSONArray("data");
				//建一個List存每天的資料(抓前200)
				List<StockVO> stockListByDay = new ArrayList<>();
				int listSize = sortedData.length() < 200 ? sortedData.length() : 200;
				for (int i = 0 ; i < listSize; i++){
					StockVO vo = new StockVO();
					String[] eachStockBlock = convertor.getArrayByIdx(originData.getJSONArray("data"),i);
					vo.setStockID(eachStockBlock[1].trim());
					vo.setStockName(eachStockBlock[2].trim());
					Long buyOverQty = Long.valueOf(eachStockBlock[eachStockBlock.length - 1].replace(",", ""));
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

		//比較集合重複
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
				//實際取到的日期
				String actualDate = new JSONObject("{\"responseData\":" + response + "}")
					.getJSONObject("responseData").getString("date");
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
				messageCombine.append(actualDate+"籌碼日報");
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
