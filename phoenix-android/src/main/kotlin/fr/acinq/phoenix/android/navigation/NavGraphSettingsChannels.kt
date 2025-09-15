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

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import fr.acinq.phoenix.android.AppViewModel
import fr.acinq.phoenix.android.settings.MutualCloseView
import fr.acinq.phoenix.android.settings.channels.ChannelDetailsView
import fr.acinq.phoenix.android.settings.channels.ChannelsView
import fr.acinq.phoenix.android.settings.channels.ImportChannelsData
import fr.acinq.phoenix.android.settings.channels.SpendFromChannelAddress

fun NavGraphBuilder.channelsNavGraph(navController: NavController, appViewModel: AppViewModel) {

    businessComposable(Screen.BusinessNavGraph.Channels.route, appViewModel) { _, _, business ->
        ChannelsView(
            business = business,
            onBackClick = {
                navController.navigate(Screen.BusinessNavGraph.Settings.route) {
                    popUpTo(Screen.BusinessNavGraph.Settings.route) { inclusive = true }
                }
            },
            onChannelClick = { navController.navigate("${Screen.BusinessNavGraph.ChannelDetails.route}?id=$it") },
            onImportChannelsDataClick = { navController.navigate(Screen.BusinessNavGraph.ImportChannelsData.route) },
            onSpendFromChannelBalance = { navController.navigate(Screen.BusinessNavGraph.SpendChannelAddress.route) },
        )
    }

    businessComposable(
        route = "${Screen.BusinessNavGraph.ChannelDetails.route}?id={id}",
        appViewModel = appViewModel,
        arguments = listOf(navArgument("id") { type = NavType.StringType })
    ) { backStackEntry, _, business ->
        val channelId = backStackEntry.arguments?.getString("id")
        ChannelDetailsView(business = business, onBackClick = { navController.popBackStack() }, channelId = channelId)
    }

    businessComposable(Screen.BusinessNavGraph.ImportChannelsData.route, appViewModel) { _, _, business ->
        ImportChannelsData(business = business, onBackClick = { navController.popBackStack() })
    }

    businessComposable(Screen.BusinessNavGraph.SpendChannelAddress.route, appViewModel) { _, _, business ->
        SpendFromChannelAddress(business = business, onBackClick = { navController.popBackStack() })
    }

    businessComposable(Screen.BusinessNavGraph.MutualClose.route, appViewModel) { _, walletId, business ->
        MutualCloseView(walletId = walletId, business = business, onBackClick = { navController.popBackStack() })
    }
}