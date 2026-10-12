package app.chisha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeTextTest {

    private val standard = """
【菜名】番茄炒蛋
【分类】素菜
【难度】1
【分钟】10
【辣】否
【含肉】否
【蛋白质克】12
【热量千卡】240
【来源】https://www.bilibili.com/video/BV1xx
【简介】酸甜下饭。
【原料】
- 西红柿
- 鸡蛋
【用量】
- 西红柿 2个
- 鸡蛋 3个
【做法】
1. 西红柿切块
2. 鸡蛋炒熟盛出
3. 炒西红柿出汁后倒回鸡蛋
【小贴士】
- 加一点糖更好吃
""".trimIndent()

    @Test fun `标准模板全部字段`() {
        val p = RecipeText.parse(standard)
        assertEquals(1, p.size)
        val r = p[0].recipe
        assertTrue(p[0].ok)
        assertEquals("番茄炒蛋", r.name)
        assertEquals("素菜", r.cat)
        assertEquals(1, r.diff)
        assertEquals(10, r.minutes)
        assertFalse(r.spicy); assertFalse(r.meat)
        assertEquals(12, r.protein); assertEquals(240, r.kcal)
        assertEquals("https://www.bilibili.com/video/BV1xx", r.source)
        assertEquals(listOf("西红柿", "鸡蛋"), r.ingredients)
        assertEquals(listOf("西红柿 2个", "鸡蛋 3个"), r.amounts)
        assertEquals(3, r.steps.size)
        assertEquals("西红柿切块", r.steps[0])
        assertEquals(listOf("加一点糖更好吃"), r.tips)
    }

    @Test fun `多道菜用分隔线`() {
        val p = RecipeText.parse(standard + "\n---\n" + standard.replace("番茄炒蛋", "西红柿炒蛋"))
        assertEquals(listOf("番茄炒蛋", "西红柿炒蛋"), p.map { it.recipe.name })
    }

    @Test fun `没有分隔线也能按菜名切开`() {
        val p = RecipeText.parse(standard + "\n" + standard.replace("番茄炒蛋", "第二道"))
        assertEquals(2, p.size)
    }

    @Test fun `宽松写法：冒号、代码框、别名、各种序号`() {
        val t = """
```
菜名：辣椒炒肉
类别: 荤
时间：约半小时
辣度：微辣
食材：五花肉 300g、青椒 4个、蒜 3瓣
步骤：
① 五花肉切片
第二步：煸出油
(3) 下青椒大火炒
注意：青椒要先干煸
```
""".trimIndent()
        val p = RecipeText.parse(t)
        assertEquals(1, p.size)
        val r = p[0].recipe
        assertEquals("辣椒炒肉", r.name)
        assertEquals("荤菜", r.cat)
        assertEquals(30, r.minutes)
        assertTrue(r.spicy)
        assertTrue(r.meat)
        assertEquals(listOf("五花肉", "青椒", "蒜"), r.ingredients)          // 原料带数量：拆开
        assertEquals(listOf("五花肉 300g", "青椒 4个", "蒜 3瓣"), r.amounts)
        assertEquals(listOf("五花肉切片", "煸出油", "下青椒大火炒"), r.steps)
        assertEquals(listOf("青椒要先干煸"), r.tips)
    }

    @Test fun `缺做法会标成有问题`() {
        val p = RecipeText.parse("【菜名】空菜\n【原料】\n- 土豆")
        assertFalse(p[0].ok)
        assertTrue(p[0].errors.any { it.contains("做法") })
    }

    @Test fun `没写分类会猜并提醒`() {
        val p = RecipeText.parse("【菜名】清蒸鲈鱼\n【原料】\n- 鲈鱼\n【做法】\n1. 蒸 8 分钟")
        assertEquals("水产", p[0].recipe.cat)
        assertTrue(p[0].catGuessed)
        assertTrue(p[0].warnings.any { it.contains("分类") })
    }

    @Test fun `难度和时间的各种写法`() {
        assertEquals(3, RecipeText.parseDiff("★★★"))
        assertEquals(2, RecipeText.parseDiff("简单"))
        assertEquals(90, RecipeText.parseMinutes("1小时30分钟"))
        assertEquals(60, RecipeText.parseMinutes("一小时"))
        assertNull(RecipeText.parseMinutes("看情况"))
    }

    @Test fun `导出再识别内容不变`() {
        val r = RecipeText.parse(standard)[0].recipe
        val again = RecipeText.parse(RecipeText.format(r))[0].recipe
        assertEquals(r.copy(id = ""), again.copy(id = ""))
    }

    @Test fun `不是菜谱的文字识别不出东西`() {
        val p = RecipeText.parse("今天天气不错，晚上吃什么好呢？")
        assertTrue(p.isEmpty() || p.all { !it.ok })
    }
}
