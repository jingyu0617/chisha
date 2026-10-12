package app.chisha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    @Test fun `西红柿和番茄算同一样`() {
        assertTrue(K.matchItem("西红柿", "番茄 2个"))
        assertTrue(K.matchItem("番茄", "西红柿"))
    }

    @Test fun `洋葱不会被当成葱`() {
        assertFalse(K.matchItem("葱", "洋葱 半个"))
        assertFalse(K.matchItem("洋葱", "小葱 2根"))
    }

    @Test fun `鸡蛋不会被当成鸡肉`() {
        assertFalse(K.matchItem("鸡蛋", "鸡腿 2个"))
        assertFalse(K.matchItem("鸡腿", "鸡蛋 3个"))
    }

    @Test fun `番茄酱不算番茄`() {
        assertFalse(K.matchItem("西红柿", "番茄酱 2勺"))
    }

    @Test fun `五花肉能做需要猪肉的菜`() {
        assertTrue(K.matchItem("五花肉", "猪肉 200g"))
    }

    @Test fun `解析用量`() {
        assertEquals(500.0 to "g", K.parseAmt("五花肉 500g"))
        assertEquals(2.0 to "个", K.parseAmt("鸡蛋两个"))
        assertEquals(1.5 to "斤", K.parseAmt("猪肉 1-2斤"))
        assertEquals(1.0 to "L", K.parseAmt("牛奶 1L"))
        assertNull(K.parseAmt("盐 少许"))
    }

    @Test fun `按单位换算建议用量`() {
        val item = InvItem("1", "五花肉", "肉禽", 1.0, "kg", "冷冻")
        val r = Recipe("r", "小炒肉", "荤菜", ingredients = listOf("五花肉"), amounts = listOf("五花肉 200g"), steps = listOf("炒"))
        assertEquals(0.2, K.suggestUse(item, r), 1e-9)
    }

    @Test fun `蛋白粉还能用几天`() {
        val p = InvItem("1", "蛋白粉", "补剂冲饮", 900.0, "g", "常温", dose = 30.0, perDay = 1.0)
        assertEquals(30, K.supplyDays(p))
    }

    @Test fun `消耗品按天扣量并在快用完时进清单`() {
        val now = 1_800_000_000_000L
        val p = InvItem("1", "蛋白粉", "补剂冲饮", 200.0, "g", "常温", dose = 30.0, perDay = 1.0, autoAt = now - 3 * Logic.DAY)
        val (d, changed) = Logic.applyConsumption(AppData(inv = listOf(p)), now)
        assertTrue(changed)
        assertEquals(110.0, d.inv[0].qty, 1e-9)          // 扣了 3 天 × 30g
        assertEquals(now, d.inv[0].autoAt)
        assertTrue(d.inv[0].reordered)                   // 只够 3 天了
        assertEquals(listOf("蛋白粉"), d.shop.map { it.name })
        // 再跑一次不会重复加进清单
        val (d2, _) = Logic.applyConsumption(d, now)
        assertEquals(1, d2.shop.size)
    }

    @Test fun `清单里同类合并`() {
        val (d, n) = Logic.addToShop(AppData(), listOf("西红柿"), "番茄炒蛋")
        val (d2, n2) = Logic.addToShop(d, listOf("番茄"), "番茄蛋汤")
        assertEquals(1, n); assertEquals(0, n2)
        assertEquals(listOf("番茄炒蛋", "番茄蛋汤"), d2.shop[0].forDishes)
    }

    @Test fun `自动分类`() {
        assertEquals("补剂冲饮", K.guessCat("蛋白粉"))
        assertEquals("补剂冲饮", K.guessCat("咖啡豆"))
        assertEquals("蛋奶豆", K.guessCat("鸡蛋"))
        assertEquals("蔬菜", K.guessCat("油麦菜"))
        assertEquals("肉禽", K.guessCat("鸡腿"))
    }

    @Test fun `调料判断`() {
        assertTrue(K.isSeason("小香葱"))
        assertTrue(K.isSeason("生抽"))
        assertFalse(K.isSeason("洋葱"))
        assertFalse(K.isSeason("油麦菜"))
    }

    @Test fun `做菜不会用到蛋白粉`() {
        val inv = listOf(InvItem("1", "蛋白粉", "补剂冲饮", 900.0, "g", "常温", dose = 30.0, perDay = 1.0))
        assertTrue(liveInv(inv).isEmpty())
    }
}
