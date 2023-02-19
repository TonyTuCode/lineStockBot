package com.linerobot.handler;

import org.springframework.stereotype.Component;

@Component
public class MenuCode {

    /** MENU */
    public static final int MENU = 999;

    /** 籌碼日報 */
    public static final int DAILY_REPORT = 1;

    /** 籌碼指定日報 */
    public static final int HIS_DAY_REPORT = 2;

    /** 漲幅勝大盤 */
    public static final int STRONGER_THAN_WTX = 3;

    public String getMenu (){
        StringBuilder menu = new StringBuilder();
        menu.append("指令表(不分大小寫)");
        menu.append("\n===============");
        menu.append("\nmenu: 菜單 ");
        menu.append("\nday : 籌碼日報");
        menu.append("\nday+yyyyMMdd: 籌碼指定日報");
        menu.append("\nstrong+2~5: x日勝大盤10%股");
        return menu.toString();
    }





}
