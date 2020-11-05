package fr.acinq.phoenix.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.onCommit
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ContextAmbient
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.unit.dp
import androidx.ui.tooling.preview.Devices
import androidx.ui.tooling.preview.Preview
import fr.acinq.phoenix.android.mvi.mvi
import fr.acinq.phoenix.android.mvi.View
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.ctrl.Init
import fr.acinq.phoenix.ctrl.InitController
import org.kodein.di.DIAware
import org.kodein.di.DIProperty
import org.kodein.di.android.di
import org.kodein.di.instance
import java.util.logging.Logger
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class MainActivity : AppCompatActivity(), DIAware {
    override val di by di()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PhoenixAndroidTheme {
                mvi<InitController>().View { m, i ->
                    InitView(m, i)
                }
            }
        }
    }
}

@Composable
fun InitView(model: Init.Model, postIntent: (Init.Intent) -> Unit) {
    val logger = logger()

    onCommit(model) { logger.info { "New model: $model" } }

    Column {
        Text(model.toString())

        Box(
            Modifier.padding(10.dp)
        ) {
            when (model) {
                is Init.Model.Initialization -> {
                    Button(onClick = { postIntent(Init.Intent.CreateWallet) }) {
                        Text("Create Wallet!")
                    }
                }
                is Init.Model.Creating -> {
                    Text("TODO!")
                }
            }
        }
    }
}

@Preview(device = Devices.PIXEL_3)
@Composable
fun DefaultPreview() {
    InitView(Init.Model.Initialization) {}
}
