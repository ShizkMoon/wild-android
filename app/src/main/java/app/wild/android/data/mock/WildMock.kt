package app.wild.android.data.mock

/**
 * Stage 3 界面复刻期的桩数据源（spec：「数据源先用 mock/桩数据驱动界面」）。
 * 结构与 spec 附录 D 的数据模型一一对应；Stage 4 由真实 Repository 替换。
 */
data class MockNovel(val aid: Int, val title: String, val author: String, val status: String = "连载中")

data class MockHomeBlock(val title: String, val novels: List<MockNovel>)

data class MockChapter(val cid: Int, val title: String)

data class MockVolume(val title: String, val chapters: List<MockChapter>)

data class MockNovelInfo(
    val aid: Int,
    val title: String,
    val author: String,
    val status: String,
    val finUpdate: String,
    val isAnimated: Boolean,
    val tags: List<String>,
    val introduce: String,
    val commentCount: Int = 0,
)

data class MockBookcase(val id: Int, val title: String)

data class MockHistory(
    val novelId: Int,
    val novelName: String,
    val author: String,
    val chapterTitle: String,
    val lastReadAt: Long,
    val progressPage: Int,
)

data class MockSearchHistory(val searchType: String, val searchKey: String)

data class MockReview(val uname: String, val time: String, val content: String)

data class MockDownload(
    val novelId: Int,
    val novelName: String,
    val author: String,
    val status: Int, // 0 等待 / 1 完成 / 2 失败 / 3 删除中
    val downloadedChapters: Int,
    val chosenChapters: Int,
)

object WildMock {

    val novels: List<MockNovel> = listOf(
        MockNovel(1, "龙娘七七七埋藏的宝藏", "凤乃一真", "已完结"),
        MockNovel(2, "刀剑神域", "川原砾", "连载中"),
        MockNovel(3, "为美好的世界献上祝福！", "晓夏目", "已完结"),
        MockNovel(4, "Re:从零开始的异世界生活", "长月达平", "连载中"),
        MockNovel(5, "凉宫春日的忧郁", "谷川流", "已完结"),
        MockNovel(6, "无头骑士异闻录 DuRaRaRa!!", "成田良悟", "已完结"),
        MockNovel(7, "青春猪头少年不会梦到兔女郎学姐", "鸭志田一", "连载中"),
        MockNovel(8, "魔法禁书目录", "镰池和马", "连载中"),
        MockNovel(9, "狼与香辛料", "支仓冻砂", "已完结"),
        MockNovel(10, "我的青春恋爱物语果然有问题", "渡航", "已完结"),
        MockNovel(11, "间谍教室", "竹町", "连载中"),
        MockNovel(12, "欢迎来到实力至上主义的教室", "衣笠彰梧", "连载中"),
        MockNovel(13, "约会大作战", "橘公司", "已完结"),
        MockNovel(14, "NO GAME NO LIFE 游戏人生", "榎宫祐", "连载中"),
        MockNovel(15, "欢迎来到异世界桑拿", "秋山祯一", "连载中"),
        MockNovel(16, "魔王学院的不适任者", "秋", "连载中"),
        MockNovel(17, "关于我转生变成史莱姆这档事", "伏濑", "连载中"),
        MockNovel(18, "盾之勇者成名录", "アネコユサギ", "连载中"),
        MockNovel(19, "幼女战记", "カルロ・ゼン", "连载中"),
        MockNovel(20, "灰与幻想的格林姆迦尔", "十文字青", "已完结"),
        MockNovel(21, "龙王的工作！", "白鸟士郎", "已完结"),
        MockNovel(22, "樱花庄的宠物女孩", "鸭志田一", "已完结"),
        MockNovel(23, "加速世界", "川原砾", "已完结"),
        MockNovel(24, "灼眼的夏娜", "高桥弥七郎", "已完结"),
    )

    val homeBlocks: List<MockHomeBlock> = listOf(
        MockHomeBlock("本站推荐", novels.take(6)),
        MockHomeBlock("热门排行", novels.drop(4).take(6)),
        MockHomeBlock("最新上架", novels.drop(9).take(6)),
        MockHomeBlock("完结精选", novels.drop(15).take(6)),
    )

    val tagGroups: List<Pair<String, List<String>>> = listOf(
        "题材" to listOf("奇幻", "科幻", "校园", "冒险", "恋爱", "推理", "战斗", "后宫"),
        "文库" to listOf("电击文库", "富士见文库", "角川文库", "GA文库", "MF文库J", "HJ文库"),
        "更新" to listOf("本月更新", "本周更新", "今日更新"),
    )

    val toplistSorts: List<Pair<String, String>> = listOf(
        "更新" to "lastupdate", "发布" to "postdate", "总访问" to "allvisit",
        "总推荐" to "allvote", "总收藏" to "goodnum", "日访问" to "dayvisit",
        "日推荐" to "dayvote", "月访问" to "monthvisit", "月推荐" to "monthvote",
        "周访问" to "weekvisit", "周推荐" to "weekvote", "字数" to "size", "动画" to "anime",
    )

    val bookcases: List<MockBookcase> = listOf(
        MockBookcase(0, "默认书架"),
        MockBookcase(1, "追更中"),
        MockBookcase(2, "已完结"),
        MockBookcase(3, "想读"),
    )

    val histories: List<MockHistory> = listOf(
        MockHistory(2, "刀剑神域", "川原砾", "第十二卷 Alicization Rising 第六章", 1726900000000, 12),
        MockHistory(7, "青春猪头少年不会梦到兔女郎学姐", "鸭志田一", "第三卷 第一章", 1726810000000, 5),
        MockHistory(9, "狼与香辛料", "支仓冻砂", "第五卷 间幕", 1726600000000, 3),
        MockHistory(14, "NO GAME NO LIFE 游戏人生", "榎宫祐", "第一卷 序章", 1726400000000, 8),
    )

    val searchHistories: List<MockSearchHistory> = listOf(
        MockSearchHistory("articlename", "刀剑神域"),
        MockSearchHistory("author", "川原砾"),
        MockSearchHistory("articlename", "狼与香辛料"),
        MockSearchHistory("author", "鸭志田一"),
        MockSearchHistory("articlename", "凉宫春日"),
    )

    val reviews: List<MockReview> = listOf(
        MockReview("书客小白", "2026-09-20 21:34", "这卷的反转真的很惊艳，主角的成长线写得比前几卷扎实多了。"),
        MockReview("夜读人", "2026-09-19 23:02", "翻译质量不错，就是更新有点慢，等下一卷。"),
        MockReview("青空", "2026-09-18 12:47", "插图还原度好评！文库版就是比 WEB 版读起来舒服。"),
        MockReview("匿名用户", "2026-09-17 08:15", "推荐给喜欢慢节奏种田流的读者，前几卷确实劝退但后面真香。"),
        MockReview("旧书虫", "2026-09-15 19:55", "十年老粉表示这本值得收藏，可惜一直没出动画第二季。"),
    )

    val downloads: List<MockDownload> = listOf(
        MockDownload(2, "刀剑神域", "川原砾", 1, 48, 48),
        MockDownload(3, "为美好的世界献上祝福！", "晓夏目", 2, 12, 30),
        MockDownload(9, "狼与香辛料", "支仓冻砂", 0, 3, 24),
    )

    fun novelInfo(aid: Int): MockNovelInfo {
        val n = novels.firstOrNull { it.aid == aid } ?: novels.first()
        return MockNovelInfo(
            aid = n.aid,
            title = n.title,
            author = n.author,
            status = n.status,
            finUpdate = "2026-09-01",
            isAnimated = n.aid % 3 == 0,
            tags = listOf("奇幻", "冒险", "电击文库"),
            introduce = "<p>${n.title}是${n.author}创作的轻小说。故事讲述了主人公在一连串奇遇中被卷入巨大阴谋，与伙伴们并肩战斗、逐渐成长的故事。</p><p>本作品曾获得电击文库大奖，累计发行量突破百万册，并多次改编为动画、漫画与游戏。</p>",
            commentCount = 12 + n.aid,
        )
    }

    fun volumes(aid: Int): List<MockVolume> = listOf(
        MockVolume("第一卷", listOf(
            MockChapter(aid * 1000 + 1, "序章"),
            MockChapter(aid * 1000 + 2, "第一章 命运的相遇"),
            MockChapter(aid * 1000 + 3, "第二章 无法回头的选择"),
            MockChapter(aid * 1000 + 4, "第三章 觉醒"),
            MockChapter(aid * 1000 + 5, "终章"),
            MockChapter(aid * 1000 + 6, "后记"),
        )),
        MockVolume("第二卷", listOf(
            MockChapter(aid * 1000 + 7, "序章"),
            MockChapter(aid * 1000 + 8, "第一章 阴影"),
            MockChapter(aid * 1000 + 9, "第二章 交易"),
            MockChapter(aid * 1000 + 10, "第三章 反击"),
            MockChapter(aid * 1000 + 11, "终章"),
        )),
        MockVolume("第三卷", listOf(
            MockChapter(aid * 1000 + 12, "序章"),
            MockChapter(aid * 1000 + 13, "第一章 重逢"),
            MockChapter(aid * 1000 + 14, "第二章 真相"),
        )),
    )

    /** 模拟章节正文：普通段落 + 一个 `<!--image-->` 插图标记（spec F16 解析约定）。 */
    fun chapterContent(): String = buildString {
        val paras = listOf(
            "夜色像一层浸了墨的纱，缓缓罩在这座临海的小城上。街灯次第亮起，将石板路染成温润的橘黄。少年抱着刚从旧书店淘来的厚册子，脚步在巷口顿了顿——前方传来的喧闹声里，混着某种不该属于这个时代的金属鸣响。",
            "他原以为那只是错觉。直到那道影子从屋檐下直坠而下，落在他面前三步远的地方，轻巧得没有一丝声响。月光照亮了来者的侧脸：银色的发，湖蓝色的眼，还有耳际那枚流转着微光的吊坠。",
            "「你看见了。」少女开口，声音比想象中平静，「那么，按照规矩，你有两个选择。」",
            "<!--image-->illustration-1<!--image-->",
            "故事要从三个月前说起。那时他还只是个普通的高中生，每天最大的烦恼是月考和社团经费。直到某个雨夜，他在图书馆的地下书库里翻到一册没有书名的旧书——封皮上只画着一轮缺月，和一行已经褪色的小字：「献给仍在寻找星星的人」。",
            "翻开第一页的瞬间，世界便悄悄换了模样。窗外明明还是熟悉的街道，空气里却多了一种若有似无的香气，像是雪落在松针上的气味。而那本书的内页上，竟一字一句地写着他自己的名字。",
            "他没有立刻逃跑——事后想来，这大概就是所谓命运的开关。他只是好奇地读下去，读到月亮沉入海底，读到守门人的灯一盏盏熄灭，读到那个名字在纸页间渐渐发烫，烫得他几乎要握不住书脊。",
            "后来发生的事，所有人都告诉他那只是高烧时的幻觉。可掌心里留下的那道月牙形浅痕，在之后无数个夜里，都会随着潮起潮落隐隐发光。",
            "而此刻，站在他面前的少女正等着他的回答。他深吸一口气，听见自己说：「在那之前，能不能先告诉我——那本书，到底是什么？」",
            "少女微微一怔，忽然笑了。那是他第一次看见她露出那种表情，像是长夜里忽然亮起的灯。「居然先问这个。」她说，「看来，我这次挑的人，或许真的不一样。」",
        )
        append(paras.joinToString("\n"))
    }

    val accountSections: List<Pair<String, List<Pair<String, String>>>> = listOf(
        "基本信息" to listOf(
            "用户名" to "wild_demo", "昵称" to "夜航西飞", "用户ID" to "102400",
            "等级" to "3", "头衔" to "书虫", "性别" to "保密",
        ),
        "联系方式" to listOf(
            "邮箱" to "demo@example.com", "QQ" to "未设置", "MSN" to "未设置", "网站" to "未设置",
        ),
        "账户信息" to listOf(
            "注册日期" to "2024-03-15", "贡献值" to "120", "经验值" to "864",
            "持有积分" to "532", "好友数量" to "4", "邮件数量" to "0",
            "收藏数量" to "27", "每日推荐" to "3",
        ),
        "个人签名" to listOf(
            "签名" to "书山有路，且行且读。", "描述" to "未设置",
        ),
    )

    fun search(type: String, key: String): List<MockNovel> {
        if (key.isBlank()) return emptyList()
        val byAuthor = type == "author"
        val matched = novels.filter { if (byAuthor) it.author.contains(key) else it.title.contains(key) }
        return if (matched.isEmpty()) novels.take(6) else matched
    }
}
