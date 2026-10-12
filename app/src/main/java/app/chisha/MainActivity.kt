package app.chisha

import android.os.Bundle
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.chisha.ui.ChishaApp
import app.chisha.ui.ChishaTheme
import app.chisha.ui.Skin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            val data by vm.data.collectAsStateWithLifecycle()
            val loaded by vm.loaded.collectAsStateWithLifecycle()
            // 自动测试用：am start --es skin apple 直接切到苹果外观
            val forced = intent?.getStringExtra("skin")
            LaunchedEffect(loaded) { if (loaded && forced != null) vm.setSkin(forced) }
            // 系统通知：第一次打开时问一次权限，并安排每天的检查
            val askPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            LaunchedEffect(loaded) {
                if (!loaded) return@LaunchedEffect
                Reminders.ensureChannel(this@MainActivity)
                if (data.settings.notify) Reminders.schedule(this@MainActivity)
                if (forced == null && !data.settings.askedNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vm.markAskedNotif()
                    askPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            // 每次回到 App：消耗品按天扣量、快用完的进清单
            LifecycleResumeEffect(Unit) {
                vm.tick()
                onPauseOrDispose { }
            }
            ChishaTheme(if (data.settings.skin == "apple") Skin.APPLE else Skin.ANDROID) {
                ChishaApp(vm)
            }
        }
    }
}
