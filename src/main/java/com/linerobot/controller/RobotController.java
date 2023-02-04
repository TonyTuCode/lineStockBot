package com.linerobot.controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.tomcat.util.codec.binary.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linerobot.handler.MessageHandler;

@RequestMapping("/robot")
@RestController
public class RobotController {

	@Value("${line.bot.channel-secret}")
	private String LINE_SECRET;
	
	@Autowired
	private MessageHandler messageHandler;

	/** 接收消息的controller */
	@PostMapping("/messaging")
	public ResponseEntity messagingAPI(
			@RequestHeader("X-Line-Signature") String X_Line_Signature,
			@RequestBody String requestBody) throws UnsupportedEncodingException, IOException {
		if (checkFromLine(X_Line_Signature,requestBody)) {
			JSONArray objArr = new JSONObject(requestBody).getJSONArray("events");
			for (int i = 0; i < objArr.length(); i++) {
				if (objArr.getJSONObject(i).getString("type").equals("message")) {
					messageHandler.doAction(objArr.getJSONObject(i));
				}
			}
			return new ResponseEntity<String>("OK", HttpStatus.OK);
		}
		return new ResponseEntity<String>("Not line platform", HttpStatus.BAD_GATEWAY);
	}

	/**
	 * 驗證訊息來源
	 * @param requestBody
	 * @param X_Line_Signature
	 * @return boolean
	 */
	public boolean checkFromLine(String X_Line_Signature,String requestBody) {
		SecretKeySpec key = new SecretKeySpec(LINE_SECRET.getBytes(), "HmacSHA256");
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			byte[] source = requestBody.getBytes("UTF-8");
			String signature = Base64.encodeBase64String(mac.doFinal(source));
			//比對簽名相符
			if (signature.equals(X_Line_Signature)) {
				return true;
			}
		} catch (NoSuchAlgorithmException | InvalidKeyException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return false;
	}
}
