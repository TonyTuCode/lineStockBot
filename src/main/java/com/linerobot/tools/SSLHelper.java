package com.linerobot.tools;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Component
public class SSLHelper {

	private static String USER_AGENT = "Mozilla/4.0 (compatible; MSIE 9.0; Windows NT 5.0)";

	public static void init() {
		try {
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new X509TrustManager[] {
				new X509TrustManager() {
				public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
				public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
				public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
			} }, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
		}
		
	}
	
	public Connection getSSLConn(String url) {
		Connection connect = Jsoup.connect(url).userAgent(USER_AGENT);
		connect.header("Accept","text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
		connect.header("Accept-Encoding", "gzip, deflate, sdch");
		connect.header("Accept-Language", "zh-TW,zh;q=0.8");
//		connect.timeout(2000);
		connect.ignoreHttpErrors(true);
		return connect;
	}
	

}
