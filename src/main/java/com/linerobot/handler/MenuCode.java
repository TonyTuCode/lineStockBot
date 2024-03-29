package com.linerobot.handler;

import org.springframework.stereotype.Component;

@Component
public class MenuCode {

    /** MENU */
    public static final int MENU = 999;

    /** 新菜單 */
    public static final int NEW_MENU = 998;

    /** 買超菜單 */
    public static final int BUY_OVER_MENU = 997;

    /** 籌碼日報 */
    public static final int DAILY_REPORT = 1;

    /** 籌碼指定日報 */
    public static final int HIS_DAY_REPORT = 2;

    /** 漲幅勝大盤 */
    public static final int STRONGER_THAN_WTX = 3;

    /** 外資買超*/
    public static final int FOREIGN_BUY = 4;

    /** 投信買超*/
    public static final int INV_TRU_BUY = 5;

    /** 土洋合力買超*/
    public static final int FOREIGN_INV_TOGETHER_BUY = 6;

    /** 認養股分析*/
    public static final int DOMINATOR = 7;

    public String getMenu (){
        StringBuilder menu = new StringBuilder();
        menu.append("指令表(不分大小寫)");
        menu.append("\n===============");
        menu.append("\nmenu: 菜單 ");
        menu.append("\nnewmenu: 新版菜單 ");
        menu.append("\nday : 籌碼日報");
        menu.append("\nday+yyyyMMdd: 籌碼指定日報");
        menu.append("\nstrong+2~5: x日勝大盤10%股");
        menu.append("\nforeignbuy: 外資3日買超");
        menu.append("\ninvtrubuy: 投信3日買超");
        menu.append("\ntogetherbuy: 土洋合攻3日買超");
        menu.append("\nctrl+股號: 認養股分析");
        return menu.toString();
    }





}
