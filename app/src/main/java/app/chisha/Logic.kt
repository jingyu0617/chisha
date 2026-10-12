package app.chisha

import kotlin.math.roundToInt

/**
 * 不依赖安卓的纯逻辑：App 界面和后台提醒共用，也方便写自动化测试。
 */
object Logic {
    const val DAY = 86_400_000L

    fun round2(v: Double) = (v * 100).roundToInt() / 100.0

    private var seq = 0
    fun newId(prefix: String) = prefix + System.currentTimeMillis().toString(36) + (seq++ % 1296).toString(36) + (0..1295).random().toString(36)

    /** 把名字加进购物清单（同类合并），返回新数据和新加了几样 */
    fun addToShop(d: AppData, names: List<String>, dish: String?, now: Long = System.currentTimeMillis()): Pair<AppData, Int> {
        var n = 0
        val list = d.shop.toMutableList()
        for (raw in names) {
            val name = raw.trim()
            if (name.isEmpty()) continue
            val idx = list.indexOfFirst { !it.done && K.sameThing(it.name, name) }
            if (idx >= 0) {
                val ex = list[idx]
                if (dish != null && dish !in ex.forDishes) list[idx] = ex.copy(forDishes = ex.forDishes + dish)
            } else {
                list += ShopItem(newId("p"), name, K.guessCat(name), listOfNotNull(dish), false, now)
                n++
            }
        }
        return d.copy(shop = list) to n
    }

    /**
     * 消耗品按天扣量：从上次扣到现在过了几整天就扣几天的量；
     * 剩 5 天以内且本轮还没进过清单的，自动加进购物清单。
     * 返回新数据，以及是否有变化。
     */
    fun applyConsumption(d: AppData, now: Long = System.currentTimeMillis()): Pair<AppData, Boolean> {
        var changed = false
        val toShop = mutableListOf<String>()
        val inv = d.inv.map { it0 ->
            var it = it0
            val dose = it.dose
            val per = it.perDay
            if (dose != null && per != null && dose > 0 && per > 0) {
                val since = it.autoAt
                if (since == null) {
                    it = it.copy(autoAt = now); changed = true
                } else {
                    val days = ((now - since) / DAY).toInt()
                    if (days >= 1) {
                        it = it.copy(qty = round2((it.qty - days * dose * per).coerceAtLeast(0.0)), autoAt = since + days * DAY)
                        changed = true
                    }
                }
                val left = K.supplyDays(it) ?: 99
                if (left <= 5 && !it.reordered) {
                    toShop += it.name
                    it = it.copy(reordered = true); changed = true
                }
            }
            it
        }
        var out = d.copy(inv = inv)
        if (toShop.isNotEmpty()) out = addToShop(out, toShop, "快用完了", now).first
        return out to changed
    }

    /** 今天该提醒的事（给系统通知用） */
    data class Reminder(val title: String, val lines: List<String>)

    fun reminders(d: AppData): Reminder? {
        val expiring = d.inv.filter { it.dose == null && (K.daysLeft(it.exp) ?: 99) in 0..1 }
        val expired = d.inv.filter { it.dose == null && (K.daysLeft(it.exp) ?: 99) < 0 }
        val low = d.inv.filter { (K.supplyDays(it) ?: 99) <= 3 }
        val coffee = d.inv.filter { K.coffeeFresh(it.roast)?.second == K.Level.BAD }
        val lines = buildList {
            if (expiring.isNotEmpty()) add("快到期：" + expiring.joinToString("、") { "${it.name}（${K.expInfo(K.daysLeft(it.exp))!!.first}）" })
            if (low.isNotEmpty()) add("快用完：" + low.joinToString("、") { "${it.name}（还能用${K.supplyDays(it)}天）" })
            if (expired.isNotEmpty()) add("已过期：" + expired.joinToString("、") { it.name })
            if (coffee.isNotEmpty()) add("咖啡豆风味在下降：" + coffee.joinToString("、") { it.name })
        }
        if (lines.isEmpty()) return null
        val title = when {
            expiring.isNotEmpty() -> "${expiring.size} 样食材快到期，今天先吃掉"
            low.isNotEmpty() -> "${low.first().name} 快用完了"
            expired.isNotEmpty() -> "冰箱里有 ${expired.size} 样过期了"
            else -> "咖啡豆该喝完了"
        }
        return Reminder(title, lines)
    }
}
