# 多品类支持规划：A股 / 美股 / 虚拟币

> 状态：设计草案（2026-09）。先定模块划分，具体实现按 P1–P5 阶段推进。

## 目标

一个插件内支持三个资产品类：

- **A股 / 指数**（现状）
- **美股**
- **虚拟币**

工具窗口面板与设置页均以 **Tab** 区分品类；数据源、自选股列表、刷新策略按品类独立；详情 / 分时 / 状态栏按品类差异化展示。

---

## 1. 模型层 `src/main/kotlin/com/omniticker/model/`

| 路径 | 改动 |
|---|---|
| `AssetClass.kt`（新增） | `enum AssetClass { A_SHARE, US, CRYPTO }`，与交易所语义解耦 |
| `Market.kt` | 保留 A股交易所前缀语义（SH/SZ/BJ 六位代码派生），**不承担品类概念** |
| `Quote.kt` | 加 `assetClass: AssetClass`；品类差异化可空字段：美股 `preCloseMarket`（盘前/盘后价）、虚拟币 `high24h/low24h/amount24h`；A股独有字段（PE/PB/涨跌停/五档）币股留空 |
| `QuoteRequest.kt` / `SearchHit.kt` | 携带 `assetClass`，用于数据源与 Tab 路由 |

**原则**：共用 `Quote` 主模型（price/change/amount/…），品类差异走可空字段，不建多套模型。

## 2. 数据源层 `src/main/kotlin/com/omniticker/data/`

```
data/
 ├─ QuoteProvider            现有接口，按 assetClass 路由实现
 ├─ AShare/                  TencentProvider · SinaProvider（现状不动）
 ├─ US/                      USProvider：腾讯 us（qt.gtimg.cn/q=usAAPL，免 key）；备选接口待定
 ├─ Crypto/                  CryptoProvider：Binance 公共行情（GET /api/v3/ticker/24hr + depth，免 key）
 └─ MinuteProvider           扩展为按品类：
      A股  → 腾讯 minute 接口（现状）
      美股 → 腾讯 us 分钟/K线接口
      币   → Binance klines(1m)  → 归纳为分钟序列（价格+量）
```

`AggregatingQuoteSource.fetchQuotes` 改为按 `assetClass` 分组路由到各自 Provider。

## 3. 调度与存储 `src/main/kotlin/com/omniticker/service/`

- `QuoteManager`：缓存按品类分区 `Map<AssetClass, ConcurrentHashMap<String, Quote>>`；刷新周期可分品类（币 10s / A股 30s / 美股 60s 建议值）；品类独立启用/停用开关。
- `WatchlistState`：存储分区（`assetClass -> codes`），A股现有结构向后兼容迁移（旧 key 归入 A_SHARE）。

## 4. 工具窗口面板 `src/main/kotlin/com/omniticker/ui/`（Tab 化）

```
QuoteToolWindowFactory
 └─ JBTabbedPane
      ├─ Tab「A股」     MarketPanel(assetClass = A_SHARE)   现状逻辑迁入
      ├─ Tab「美股」    MarketPanel(assetClass = US)         新增
      └─ Tab「虚拟币」  MarketPanel(assetClass = CRYPTO)     新增

MarketPanel（新组件，复用现有件）：
 ├─ 输入行（A股：代码/名称/拼音搜索；美股：symbol/名称；币：符号/别名）
 ├─ JTable（复用 QuoteTableModel / QuoteCellRenderer；品类列可配置）
 ├─ QuoteDetailPanel（品类字段切换：A股 涨跌停·PE·PB / 美股 盘前盘后 / 币 24h高低·量）
 └─ MinuteChartPanel（数据接口不变，喂对应品类的分钟序列）
```

- 布局管理：每 Tab 独立 `MarketPanel`（各自表格 + 详情 + 分时图 + 自选操作）。
- `toggleVisibility` 状态栏点击仍整体切换工具窗口，Tab 定位到被点击品类段。

## 5. 设置页 `src/main/kotlin/com/omniticker/settings/`（Tab 化）

`OmniTickerConfigurable` → `JBTabbedPane`：

| Tab | 项 |
|---|---|
| 通用 | 状态栏显隐 / 展示段、全局刷新开关 |
| A股 | 现有项 |
| 美股 | 启用开关、自选、刷新间隔 |
| 虚拟币 | 启用开关、自选、刷新间隔、默认币种列表 |

配置存储沿用 `PluginSettings`（PropertiesState，按品类 key 分区）。

## 6. 状态栏 `src/main/kotlin/com/omniticker/statusbar/`

- 每品类可配置是否在状态栏显示摘要段（如 `BTC 67,200 +1.8%`）；全局红涨绿跌配色不变。
- 点击状态栏 → 打开工具窗口并切换到对应品类 Tab。

## 7. 任务拆解（P1–P5 实现顺序）

| 阶段 | 内容 | 关键产出 |
|---|---|---|
| **P1 基建** | `AssetClass` + `Quote.assetClass` + `QuoteRequest` 路由；`Market` 兼容 | 改模型，A股全流程不回归 |
| **P2 单一新品类试点** | **虚拟币先行**（Binance 免 key、接口稳）：`CryptoProvider` + `MarketPanel` 接入 + Tab 雏形 + Watchlist 分区 | 币种行情可用，验证架构 |
| **P3 美股** | `USProvider`（腾讯 us）+ 搜索 + 分时 | 美股行情可用 |
| **P4 设置与状态栏** | 设置 Tab 化 + 分品类刷新 + 状态栏多段摘要 | 配置与展示完整 |
| **P5 打磨** | 详情字段差异化、币种深度 / 美股盘前、图表细节、文案 | 全品类可用 |

> 建议从 **P2 虚拟币先行**：无 API-key、数据免费稳定，最快验证分层架构是否成立。

---

## 决策记录

- 品类语义独立于交易所前缀（`AssetClass` ≠ `Market`），避免 A股 6 位代码派生逻辑污染美股/币。
- 不引入第三方图表库（分时图手绘已定型）；不为此功能引入新的运行时依赖（Binance/腾讯均为免费公共接口）。
- 币种/美股数据源优先无 key 公共接口，避免为用户增加配置门槛。