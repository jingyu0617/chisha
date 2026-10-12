package app.chisha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
