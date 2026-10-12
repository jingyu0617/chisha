package app.chisha

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first

/**
 * 桌面小部件：不用打开 App 就能看到快到期、快用完的东西和要买几样。点一下打开 App。
 */
class ChishaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = Logic.applyConsumption(context.appStore.data.first()).first
        provideContent { GlanceTheme { Content(d) } }
    }

    @Composable
    private fun Content(d: AppData) {
        val expiring = d.inv.filter { it.dose == null && (K.daysLeft(it.exp) ?: 99) <= 3 }.sortedBy { K.daysLeft(it.exp) }
        val low = d.inv.filter { (K.supplyDays(it) ?: 99) <= 7 }
        val shop = d.shop.count { !it.done }
        val c = GlanceTheme.colors
        Column(
            GlanceModifier.fillMaxSize().background(c.widgetBackground).cornerRadius(24.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text("今天吃啥", style = TextStyle(color = c.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(6.dp))
            if (expiring.isEmpty() && low.isEmpty()) {
                Text("冰箱里没有快到期的", style = TextStyle(color = c.onSurface, fontSize = 13.sp))
            }
            expiring.take(3).forEach {
                Text("${it.name} · ${K.expInfo(K.daysLeft(it.exp))?.first ?: ""}", style = TextStyle(color = c.error, fontSize = 13.sp), maxLines = 1)
            }
            low.take(2).forEach {
                Text("${it.name} · 还能用${K.supplyDays(it)}天", style = TextStyle(color = c.onSurface, fontSize = 13.sp), maxLines = 1)
            }
            Spacer(GlanceModifier.height(6.dp))
            Text(if (shop > 0) "购物清单 $shop 样" else "购物清单是空的", style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp))
        }
    }

    companion object {
        suspend fun refresh(ctx: Context) = runCatching { ChishaWidget().updateAll(ctx) }
    }
}

class ChishaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChishaWidget()
}
