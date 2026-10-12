package com.seagull.nivod.data

/** 列表卡片 */
data class Video(
    val id: String,
    val title: String,
    val cover: String,
    val sub: String = "",
    val badge: String = "",
    val score: String = ""
)

/** 一条播放线路（站点每个分集给 12 条） */
data class Line(val from: String, val name: String, val url: String)

/** 分集 */
data class Episode(val slug: String, val label: String)

/** 详情页 */
data class Detail(
    val id: String,
    val title: String,
    val cover: String,
    val score: String = "",
    val year: String = "",
    val region: String = "",
    val types: String = "",
    val lang: String = "",
    val reso: String = "",
    val director: String = "",
    val actors: String = "",
    val update: String = "",
    val desc: String = "",
    val episodes: List<Episode> = emptyList()
)

/** 站点频道（/class.html?channel=） */
enum class Channel(val label: String, val slug: String) {
    MOVIE("电影", "movie"),
    TV("剧集", "tv"),
    SHOW("综艺", "show"),
    ANIME("动漫", "anime"),
    DOC("纪录片", "doc")
}

/** 地区筛选（/filter.html?region=），slug 取自站点实际链接 */
enum class Region(val label: String, val slug: String) {
    ALL("全部", ""),
    CN("大陆", "cn"),
    HK("香港", "hk"),
    TW("台湾", "tw"),
    JP("日本", "jp"),
    KR("韩国", "kr"),
    SG_MY("新马", "sg-my"),
    TH("泰国", "th"),
    WEST("欧美", "west")
}

/** 类型筛选（/filter.html?showtype=），slug 取自站点实际链接 */
enum class ShowType(val label: String, val slug: String) {
    ALL("全部", ""),
    AI_QING("爱情", "ai-qing"),
    DONG_HUA("动画", "dong-hua"),
    DONG_ZUO("动作", "dong-zuo"),
    FAN_ZUI("犯罪", "fan-zui"),
    GE_WU("歌舞", "ge-wu"),
    JING_DIAN("经典", "jing-dian"),
    JING_SONG("惊悚", "jing-song"),
    JU_QING("剧情", "ju-qing"),
    KE_HUAN("科幻", "ke-huan"),
    KONG_BU("恐怖", "kong-bu"),
    MAO_XIAN("冒险", "mao-xian"),
    QI_HUAN("奇幻", "qi-huan"),
    TONG_XING("同性", "tong-xing"),
    WANG_LUO("网络", "wang-luo"),
    XI_JU("喜剧", "xi-ju"),
    XUAN_YI("悬疑", "xuan-yi"),
    ZAI_NAN("灾难", "zai-nan"),
    ZHAN_ZHENG("战争", "zhan-zheng")
}
