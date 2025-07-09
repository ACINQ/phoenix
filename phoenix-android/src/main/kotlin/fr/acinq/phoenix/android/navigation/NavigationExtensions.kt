/*
 * Copyright 2025 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.android.navigation

import android.content.Intent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.android.BusinessRepo
import fr.acinq.phoenix.android.navController
import fr.acinq.phoenix.android.utils.logger
import io.ktor.http.encodeURLParameter

/** Navigates to Home and pops everything from the backstack up to Home. This effectively resets the nav stack. */
fun NavController.popToHome() {
    val navController = this
    navigate(Screen.Home.route) {
        popUpTo(navController.graph.id) { inclusive = true }
    }
}

/**
 * This extension of the regular composable method checks that the business is started when attempting to navigate to [route].
 *
 * If not, it redirects to the startup view. Thanks to the `next` parameter in the startup route, this will in turn redirect the user
 * to [route] once the business is started.
 *
 * @param deepLinkPrefix an optional prefix if this route supports deeplinks
 */
fun NavGraphBuilder.businessComposable(
    route: String,
    deepLinkPrefix: String = "",
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(route = route, arguments = arguments, deepLinks = deepLinks) { entry ->
        val log = logger("Navigation")
        val business by BusinessRepo.activeBusiness.collectAsState()

        if (business == null) {

            // in case it's a deeplink we need to look into the intent and apply some optional prefix
            // for example, when deep-linking to the send view, the link is "scanview:lnbc1....."
            val next = if (deepLinks.isNotEmpty()) {
                @Suppress("DEPRECATION")
                val intent = try {
                    entry.arguments?.getParcelable<Intent>(NavController.KEY_DEEP_LINK_INTENT)
                } catch (e: Exception) {
                    null
                }
                intent?.data?.let { "$deepLinkPrefix${it.toString()}" } ?: route
            } else route

            log.info("accessing route=$route but business not found, navigating to [startup] then to [${next ?: route}]")
            val navController = navController
            navController.navigate("${Screen.Startup.route}?next=${next.encodeURLParameter()}") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        } else {
            content(entry)
        }
    }
}

fun navigateToPaymentDetails(navController: NavController, id: UUID, isFromEvent: Boolean) {
    try {
        navController.navigate("${Screen.PaymentDetails.route}?id=${id}&fromEvent=${isFromEvent}")
    } catch (_: Exception) { }
}
