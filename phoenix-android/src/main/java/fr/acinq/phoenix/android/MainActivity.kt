package fr.acinq.phoenix.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.ui.core.setContent
import androidx.ui.foundation.Text
import androidx.ui.foundation.lazy.LazyColumnItems
import androidx.ui.tooling.preview.Preview
import fr.acinq.phoenix.android.utils.bindOnLifecycle

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val controller = (application as PhoenixApplication).phoenix.newLogController()

        controller
            .subscribe {
                setContent {
                    PhoenixAndroidTheme {
                        LogList(it.lines)
                    }
                }
            }
            .bindOnLifecycle(this)

        setContent {
            PhoenixAndroidTheme {
                Text("...")
            }
        }
    }
}

@Composable
fun LogList(logs: List<String>) {
    LazyColumnItems(logs) {
        Text(it)
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    PhoenixAndroidTheme {
        LogList(listOf("fake", "log", "list"))
    }
}
