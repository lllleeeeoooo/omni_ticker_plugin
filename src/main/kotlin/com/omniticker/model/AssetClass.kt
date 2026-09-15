package com.omniticker.model

/**
 * 资产品类：决定数据源路由（QuoteProvider.supportedAssets）与各 Tab/展示形态。
 * 与 [Market]（A股交易所前缀）解耦：Market 只描述代码所属交易所，AssetClass 描述品种大类。
 */
enum class AssetClass {
    /** 沪深交易所证券与指数。 */
    A_SHARE,

    /** 美股。 */
    US,

    /** 虚拟货币。 */
    CRYPTO,
}