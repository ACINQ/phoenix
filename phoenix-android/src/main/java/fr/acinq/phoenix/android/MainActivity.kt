package fr.acinq.phoenix.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.unit.dp
import androidx.ui.tooling.preview.Devices
import androidx.ui.tooling.preview.Preview
import fr.acinq.phoenix.android.mvi.MVIView
import fr.acinq.phoenix.android.mvi.AppView
import fr.acinq.phoenix.android.mvi.CF
import fr.acinq.phoenix.android.mvi.MockView
import fr.acinq.phoenix.android.utils.logger
import fr.acinq.phoenix.ctrl.Initialization
import java.security.SecureRandom

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppView { InitializationView() }
        }
    }
}

@Composable
fun InitializationView() {
    val logger = logger()

    PhoenixAndroidTheme {
        MVIView(CF::initialization) { model, postIntent ->
            Column {
                Text(model.toString())

                Box(
                    Modifier.padding(10.dp)
                ) {
                    when (model) {
                        is Initialization.Model.Ready -> {
                            Button(
                                onClick = {
                                    logger.info { "Clicked!" }
                                    val entropy = ByteArray(32)
                                    SecureRandom().nextBytes(entropy)
                                    postIntent(Initialization.Intent.GenerateWallet(entropy))
                                }
                            ) {
                                Text("Create Wallet!")
                            }
                        }
                        is Initialization.Model.GeneratedWallet -> {
                            Text("TODO!")
                        }
                    }
                }
            }
        }
    }
}

val MockModelInitialization = Initialization.Model.Ready

@Preview(device = Devices.PIXEL_3)
@Composable
fun DefaultPreview() {
    MockView { InitializationView() }
}
