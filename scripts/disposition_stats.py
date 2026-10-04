#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
處置股統計：處置期間第 N 個交易日的上漲機率與平均漲跌幅。

資料來源（證交所 / 櫃買中心官方公開資料）
  上市處置清單   https://www.twse.com.tw/rwd/zh/announcement/punish
  上櫃處置清單   https://www.tpex.org.tw/www/zh-tw/bulletin/disposal
  上市個股日成交 https://www.twse.com.tw/rwd/zh/afterTrading/STOCK_DAY
  上櫃個股日成交 https://www.tpex.org.tw/www/zh-tw/afterTrading/tradingStock

計算方式
  * 只統計 4 碼普通股（排除權證、可轉債、ETF）。
  * 第 k 天漲跌幅 = 漲跌價差 / (收盤價 - 漲跌價差)，也就是相對前一日參考價，除權息日不會失真；
    「不比價(X)」或無成交的日子視為缺值。
  * 累積漲跌幅 = 第 1 ~ k 天日報酬連乘，基準是處置前一個交易日收盤。
  * 115/08/10 起處置新制（處置期間 10 -> 5 個營業日、改約每 2 分鐘撮合），新舊制分開統計；
    處置期間跨越 115/08/10、被交易所中途縮短的案件直接排除。

用法
  python scripts/disposition_stats.py                  # 預設 2024-01-01 ~ 今天
  python scripts/disposition_stats.py --start 2025-01-01
  python scripts/disposition_stats.py --list-only      # 只抓處置清單、看件數
"""
import argparse
import csv
import json
import os
import re
import statistics
import sys
import tempfile
import threading
import time
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from datetime import date, datetime, timedelta

NEW_RULE_DATE = date(2026, 8, 10)  # 處置新制實施日 (115/08/10)
OLD_RULE = "舊制(10天)"
NEW_RULE = "新制(5天)"
CACHE_DIR = os.path.join(tempfile.gettempdir(), "linestockbot_disposition_cache")
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "output")
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
ROC_DATE = re.compile(r"(\d{2,3})/(\d{1,2})/(\d{1,2})")
COMMON_STOCK = re.compile(r"^\d{4}$")
MAX_ABS_RETURN = 0.5  # 超過 ±50% 視為資料異常（台股漲跌幅限制 10%）


def log(msg):
    print(msg, file=sys.stderr, flush=True)


def roc_to_date(text):
    m = ROC_DATE.search(str(text))
    if not m:
        return None
    y, mo, d = (int(g) for g in m.groups())
    return date(y + 1911, mo, d)


def to_roc(d):
    return f"{d.year - 1911}/{d.month:02d}/{d.day:02d}"


def next_month(d):
    return date(d.year + (d.month == 12), d.month % 12 + 1, 1)


def month_starts(start, end):
    cur = date(start.year, start.month, 1)
    while cur <= end:
        yield cur
        cur = next_month(cur)


def parse_number(text):
    """'1,234.50' / '+0.55' / ' 0.00' -> float；'X0.00'(不比價)、'--'、空字串 -> None"""
    s = str(text).strip().replace(",", "")
    if not s or s.startswith("X") or set(s) <= {"-"}:
        return None
    try:
        return float(s.replace("+", ""))
    except ValueError:
        return None


class Client:
    """單一主機的 HTTP client：固定最小請求間隔 + 失敗退避重試，避免被證交所/櫃買封鎖 IP。"""

    def __init__(self, name, min_interval):
        self.name = name
        self.min_interval = min_interval
        self.requests = 0
        self._lock = threading.Lock()
        self._last = 0.0

    def get_json(self, url, retries=6):
        for attempt in range(1, retries + 1):
            with self._lock:
                wait = self._last + self.min_interval - time.monotonic()
                if wait > 0:
                    time.sleep(wait)
                self._last = time.monotonic()
                self.requests += 1
            try:
                req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
                with urllib.request.urlopen(req, timeout=30) as resp:
                    return json.loads(resp.read().decode("utf-8"))
            except Exception as exc:  # 逾時、被擋(回 HTML)、5xx 都走退避重試
                backoff = 15 * attempt
                log(f"[{self.name}] 第 {attempt} 次失敗：{type(exc).__name__}: {exc}；{backoff}s 後重試")
                time.sleep(backoff)
        raise RuntimeError(f"[{self.name}] 重試 {retries} 次仍失敗：{url}")


def cached(client, url, key, cacheable):
    """只快取「已結束的月份」，當月資料每次重抓。"""
    path = os.path.join(CACHE_DIR, key + ".json")
    if cacheable and os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    data = client.get_json(url)
    if cacheable:
        os.makedirs(CACHE_DIR, exist_ok=True)
        tmp = path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
        os.replace(tmp, path)
    return data


# ---------------------------------------------------------------- 處置清單

def classify(measure):
    if "第一次" in measure:
        return "第一次"
    if "第二次" in measure or "再次" in measure:
        return "第二次"
    return "其他"


def pick(fields, row, names, default_idx):
    for name in names:
        if name in fields:
            return str(row[fields.index(name)])
    return str(row[default_idx]) if default_idx < len(row) else ""


def add_event(events, market, fields, row, period_names, period_idx):
    code = pick(fields, row, ["證券代號"], 2).strip()
    period = pick(fields, row, period_names, period_idx)
    dates = [date(int(y) + 1911, int(m), int(d)) for y, m, d in ROC_DATE.findall(period)]
    if not code or len(dates) < 2:
        return
    measure = pick(fields, row, ["處置措施"], 7).strip()
    name = re.sub(r"\(.*?\)", "", pick(fields, row, ["證券名稱"], 3)).strip()
    ev = {
        "market": market,
        "code": code,
        "name": name,
        "announced": roc_to_date(pick(fields, row, ["公布日期"], 1)),
        "start": dates[0],
        "end": dates[1],
        "measure": measure,
        "kind": classify(measure),
    }
    events.setdefault((market, code, ev["start"], ev["end"], ev["kind"]), ev)


def fetch_dispositions(twse, tpex, start, end, today):
    events = {}
    for m in month_starts(start, end):
        m_end = min(end, next_month(m) - timedelta(days=1))
        cacheable = next_month(m) <= today
        span = f"{m:%Y%m%d}_{m_end:%Y%m%d}"

        url = "https://www.twse.com.tw/rwd/zh/announcement/punish?" + urllib.parse.urlencode(
            {"startDate": f"{m:%Y%m%d}", "endDate": f"{m_end:%Y%m%d}", "response": "json"})
        data = cached(twse, url, f"twse_punish_{span}", cacheable)
        fields = [str(f).strip() for f in data.get("fields") or []]
        for row in data.get("data") or []:
            add_event(events, "上市", fields, row, ["處置起迄時間", "處置起訖時間"], 6)

        url = "https://www.tpex.org.tw/www/zh-tw/bulletin/disposal?" + urllib.parse.urlencode(
            {"startDate": to_roc(m), "endDate": to_roc(m_end), "response": "json"})
        data = cached(tpex, url, f"tpex_disposal_{span}", cacheable)
        for table in data.get("tables") or []:
            fields = [str(f).strip() for f in table.get("fields") or []]
            for row in table.get("data") or []:
                add_event(events, "上櫃", fields, row, ["處置起訖時間", "處置起迄時間"], 5)
        log(f"處置清單 {m:%Y-%m} 完成，累計 {len(events)} 筆")
    return [ev for ev in events.values() if start <= ev["start"] <= end]


# ---------------------------------------------------------------- 日成交

def fetch_month_prices(clients, market, code, month, today):
    """回傳 {日期: (收盤價, 漲跌價差)}"""
    cacheable = next_month(month) <= today
    if market == "上市":
        url = "https://www.twse.com.tw/rwd/zh/afterTrading/STOCK_DAY?" + urllib.parse.urlencode(
            {"date": f"{month:%Y%m%d}", "stockNo": code, "response": "json"})
        data = cached(clients["上市"], url, f"twse_day_{code}_{month:%Y%m}", cacheable)
        rows = data.get("data") or [] if data.get("stat") == "OK" else []
    else:
        url = "https://www.tpex.org.tw/www/zh-tw/afterTrading/tradingStock?" + urllib.parse.urlencode(
            {"code": code, "date": f"{month:%Y/%m/%d}", "response": "json"})
        data = cached(clients["上櫃"], url, f"tpex_day_{code}_{month:%Y%m}", cacheable)
        tables = data.get("tables") or [{}]
        rows = tables[0].get("data") or []
    prices = {}
    for row in rows:
        d = roc_to_date(row[0])
        if d and len(row) > 7:
            prices[d] = (parse_number(row[6]), parse_number(row[7]))  # 收盤價, 漲跌價差
    return prices


def fetch_all_prices(clients, events, today):
    tasks = sorted({(ev["market"], ev["code"], m)
                    for ev in events
                    for m in month_starts(ev["start"], min(ev["end"], today))})
    prices = defaultdict(dict)
    lock = threading.Lock()
    done = [0]
    log(f"需抓取 {len(tasks)} 個「個股 x 月份」日成交（已快取者不會重抓）")

    def worker(items):
        for market, code, month in items:
            try:
                data = fetch_month_prices(clients, market, code, month, today)
            except Exception as exc:
                log(f"放棄 {market} {code} {month:%Y-%m}：{exc}")
                data = {}
            with lock:
                prices[(market, code)].update(data)
                done[0] += 1
                if done[0] % 50 == 0 or done[0] == len(tasks):
                    log(f"日成交進度 {done[0]}/{len(tasks)}")

    threads = [threading.Thread(target=worker, args=([t for t in tasks if t[0] == mk],))
               for mk in ("上市", "上櫃")]
    for th in threads:
        th.start()
    for th in threads:
        th.join()
    return prices


# ---------------------------------------------------------------- 統計

def regime_of(ev):
    if ev["start"] >= NEW_RULE_DATE:
        return NEW_RULE
    if ev["end"] < NEW_RULE_DATE:
        return OLD_RULE
    return None  # 跨新制實施日、被中途縮短 -> 排除


def event_returns(ev, prices, today, counters):
    """回傳處置期間每個交易日的日報酬 list（缺值為 None）"""
    series = prices.get((ev["market"], ev["code"]), {})
    days = sorted(d for d in series if ev["start"] <= d <= min(ev["end"], today))
    returns = []
    for d in days:
        close, chg = series[d]
        r = None
        if close is not None and chg is not None and close - chg > 0:
            r = chg / (close - chg)
            if abs(r) > MAX_ABS_RETURN:
                counters["日報酬異常(>50%)捨棄"] += 1
                r = None
        if r is None:
            counters["缺值日(不比價/無成交/異常)"] += 1
        returns.append(r)
    return days, returns


def cumulative(returns):
    out, acc = [], 1.0
    for r in returns:
        acc = None if acc is None or r is None else acc * (1 + r)
        out.append(None if acc is None else acc - 1)
    return out


def describe(values):
    vals = [v for v in values if v is not None]
    if not vals:
        return None
    n = len(vals)
    return {
        "n": n,
        "up": sum(v > 0 for v in vals) / n,
        "down": sum(v < 0 for v in vals) / n,
        "mean": statistics.fmean(vals),
        "median": statistics.median(vals),
    }


def pct(x, signed=True):
    return f"{x * 100:+.2f}%" if signed else f"{x * 100:.1f}%"


def main():
    parser = argparse.ArgumentParser(description="處置股第 N 天上漲機率/平均漲跌幅統計")
    parser.add_argument("--start", default="2024-01-01", help="處置起日下限 (YYYY-MM-DD)")
    parser.add_argument("--end", default=None, help="處置起日上限 (YYYY-MM-DD)，預設今天")
    parser.add_argument("--list-only", action="store_true", help="只抓處置清單並統計件數")
    parser.add_argument("--twse-interval", type=float, default=3.0, help="證交所請求間隔秒數")
    parser.add_argument("--tpex-interval", type=float, default=2.0, help="櫃買中心請求間隔秒數")
    parser.add_argument("--min-samples", type=int, default=5, help="樣本數少於此值的天數不列出")
    args = parser.parse_args()

    today = date.today()
    start = datetime.strptime(args.start, "%Y-%m-%d").date()
    end = datetime.strptime(args.end, "%Y-%m-%d").date() if args.end else today
    clients = {"上市": Client("TWSE", args.twse_interval), "上櫃": Client("TPEx", args.tpex_interval)}

    all_events = fetch_dispositions(clients["上市"], clients["上櫃"], start, end, today)
    stocks = [ev for ev in all_events if COMMON_STOCK.match(ev["code"])]
    log(f"處置案件 {len(all_events)} 筆，其中 4 碼普通股 {len(stocks)} 筆")

    counters = Counter()
    counters["非普通股(權證/CB/ETF)排除"] = len(all_events) - len(stocks)
    measures = Counter(ev["measure"] for ev in stocks)
    by_bucket = Counter((ev["start"].year, ev["market"], ev["kind"], regime_of(ev) or "跨新制排除")
                        for ev in stocks)

    if args.list_only:
        print("處置措施原始值：", dict(measures))
        for key in sorted(by_bucket, key=str):
            print(key, by_bucket[key])
        return

    prices = fetch_all_prices(clients, stocks, today)

    groups = defaultdict(list)  # (regime, kind) -> [(ev, returns, cumulative)]
    day_counts = defaultdict(Counter)
    for ev in stocks:
        regime = regime_of(ev)
        if regime is None:
            counters["處置期間跨 115/08/10 新制(中途縮短)排除"] += 1
            continue
        if ev["kind"] == "其他":
            counters[f"處置措施無法分類排除({ev['measure']})"] += 1
            continue
        days, returns = event_returns(ev, prices, today, counters)
        if not days:
            counters["查無日成交排除"] += 1
            continue
        if ev["end"] <= today:
            day_counts[regime][len(days)] += 1
        item = (ev, days, returns, cumulative(returns))
        groups[(regime, ev["kind"])].append(item)
        groups[(regime, "全部")].append(item)

    os.makedirs(OUTPUT_DIR, exist_ok=True)
    report, summary_rows = [], []
    report.append(f"統計區間：處置起日 {start} ~ {end}（資料抓取日 {today}）")
    report.append(f"API 請求數：TWSE {clients['上市'].requests}、TPEx {clients['上櫃'].requests}")
    report.append("排除/缺值：" + "、".join(f"{k} {v}" for k, v in counters.items() if v))
    for regime in (OLD_RULE, NEW_RULE):
        report.append(f"{regime} 已結束案件的處置交易日數分布：{dict(sorted(day_counts[regime].items()))}")

    for regime in (OLD_RULE, NEW_RULE):
        for kind in ("全部", "第一次", "第二次"):
            items = groups.get((regime, kind), [])
            if not items:
                continue
            markets = Counter(ev["market"] for ev, *_ in items)
            first = min(ev["start"] for ev, *_ in items)
            last = max(ev["start"] for ev, *_ in items)
            report.append("")
            report.append(f"【{regime}｜{kind}處置】{len(items)} 件（上市 {markets['上市']} / 上櫃 {markets['上櫃']}），"
                          f"處置起日 {first} ~ {last}")
            report.append("天數 | 樣本 | 上漲機率 | 下跌機率 | 平均漲跌 | 中位數 | 累積上漲機率 | 累積平均漲跌")
            max_len = max(len(r) for _, _, r, _ in items)
            for k in range(max_len):
                daily = describe([r[k] if k < len(r) else None for _, _, r, _ in items])
                cum = describe([c[k] if k < len(c) else None for _, _, _, c in items])
                if not daily or daily["n"] < args.min_samples:
                    continue
                cum_text = f"{pct(cum['up'], False)} | {pct(cum['mean'])}" if cum else "- | -"
                report.append(f"第{k + 1:>2}天 | {daily['n']:>4} | {pct(daily['up'], False):>6} | "
                              f"{pct(daily['down'], False):>6} | {pct(daily['mean']):>7} | "
                              f"{pct(daily['median']):>7} | {cum_text}")
                summary_rows.append({
                    "制度": regime, "處置別": kind, "第幾天": k + 1, "樣本數": daily["n"],
                    "上漲機率": round(daily["up"], 4), "下跌機率": round(daily["down"], 4),
                    "平均漲跌幅": round(daily["mean"], 5), "中位數漲跌幅": round(daily["median"], 5),
                    "累積樣本數": cum["n"] if cum else 0,
                    "累積上漲機率": round(cum["up"], 4) if cum else "",
                    "累積平均漲跌幅": round(cum["mean"], 5) if cum else "",
                })

    text = "\n".join(report)
    print(text)
    with open(os.path.join(OUTPUT_DIR, "disposition_report.txt"), "w", encoding="utf-8") as f:
        f.write(text + "\n")
    with open(os.path.join(OUTPUT_DIR, "disposition_summary.csv"), "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(summary_rows[0].keys()) if summary_rows else ["制度"])
        writer.writeheader()
        writer.writerows(summary_rows)
    with open(os.path.join(OUTPUT_DIR, "disposition_events.csv"), "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["制度", "處置別", "市場", "代號", "名稱", "公布日", "處置起日", "處置迄日",
                         "交易日數", "每日漲跌幅(依序)", "期間累積漲跌幅"])
        for (regime, kind), items in sorted(groups.items()):
            if kind == "全部":
                continue
            for ev, days, returns, cum in items:
                writer.writerow([regime, kind, ev["market"], ev["code"], ev["name"], ev["announced"],
                                 ev["start"], ev["end"], len(days),
                                 " ".join("NA" if r is None else f"{r * 100:+.2f}%" for r in returns),
                                 "NA" if cum[-1] is None else f"{cum[-1] * 100:+.2f}%"])
    log(f"輸出檔案：{OUTPUT_DIR}")


if __name__ == "__main__":
    main()
