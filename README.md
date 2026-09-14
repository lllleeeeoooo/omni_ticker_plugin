# Omni Ticker — A股实时行情 JetBrains 插件

在 JetBrains IDE（IDEA / PyCharm / WebStorm / GoLand / CLion 等全系）中查看 A 股实时行情：

- **工具窗口**：自选股表格（代码/名称/现价/涨跌幅/涨跌额/最高/最低/今开/昨收/成交额），红涨绿跌，点击列头排序
- **详情面板**：选中股票显示买卖五档、最高/最低/今开/昨收、成交额、换手率、振幅、PE/PB、总市值、涨跌停
- **状态栏**：上证/深成/创业板指（可单独开关）+ 自选个股逐条展示（最多 6 条），红涨绿跌可切换为默认白色
- **设置**（Settings → Omni Ticker）：沪深创独立显示开关、个股展示开关、个股名称全称/拼音首字母缩写（茅台→mt）、字体颜色红涨绿跌/白色
- **自选股管理**：搜索添加（代码 / 名称 / 拼音前缀）、删除，配置持久化，无需登录
- **数据源**：腾讯行情为主源（实测单请求返回全量字段含五档/PE/PB/市值，快），腾讯不可用时自动降级新浪（hq.sinajs.cn）；内置熔断、限频、多级刷新节奏（盘中 3s / 失焦 30s / 休市省电 60s）

## 构建

```bash
# 首次构建会在 gradle.properties 指定的 JDK（本机为 Android Studio 自带 JBR 17）下
# 解析 local() 依赖：本机 WebStorm 2025.2.1 作为 IntelliJ Platform SDK
./gradlew.bat buildPlugin        # 产出 build/distributions/*.zip
./gradlew.bat test               # 单元测试（数据源解析器 + 交易时段）
./gradlew.bat runIde             # 启动开发版 IDE 调试插件
```

构建环境要点（见 `gradle.properties` / `build.gradle.kts`）：

| 项 | 值 |
|---|---|
| Gradle | 9.5.0（wrapper 已包含）|
| Kotlin | 2.1.20 |
| IntelliJ Platform Gradle Plugin | `org.jetbrains.intellij.platform` 2.18.1 |
| Platform SDK | `local()` → 本机 WebStorm 2025.2.1（build 252）|
| `since-build` | 252（2025.2），`until-build` 不限 |
| JDK | Android Studio 自带 JBR 17 |

## 安装

1. 执行 `./gradlew.bat buildPlugin`
2. IDE 中 `File → Settings → Plugins → ⚙ → Install Plugin from Disk`，选择
   `build/distributions/omni-ticker-*.zip`
3. 右侧边栏打开 **Omni Ticker** 工具窗口（或 `Tools → 立即刷新 A 股行情`）

## 结构

```
src/main/kotlin/com/omniticker/
├── model/     行情/五档/搜索模型，市场推导
├── data/      数据源接口 + 东方财富/腾讯 Provider + 解析器 + 降级聚合 + 熔断
├── service/   QuoteManager(调度/缓存/订阅)、MarketCalendar、WatchlistState、PluginSettings
├── ui/        工具窗口、表格、渲染器、详情面板、搜索弹窗、颜色
├── statusbar/ 状态栏 widget
├── settings/  设置页
└── util/      HTTP 客户端、格式化
```

## 数据源说明

免费非官方接口，请注意限频（插件默认节奏已克制）。已实测校准：

- **腾讯主源** `qt.gtimg.cn`（GBK，`~` 分隔，一次返回五档/PE/PB/市值/涨跌停/指数）
- **新浪备源** `hq.sinajs.cn`（需 HTTPS + finance.sina.com.cn Referer；实测 2026 返回 UTF-8，
  34 段 CSV 格式，含五档/成交量/成交额，无换手/PE/市值等高级字段）
- **搜索**：腾讯 `smartbox.gtimg.cn/s3`（`v_hint` 格式，含 `\uXXXX` 转义，代码/名称/拼音）