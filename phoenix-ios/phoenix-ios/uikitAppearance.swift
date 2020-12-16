//
// Created by Salomon BRYS on 20/11/2020.
// Copyright (c) 2020 Acinq. All rights reserved.
//

import SwiftUI

func UIKitAppearance() {
    let navigationBar = UINavigationBarAppearance()
    navigationBar.configureWithOpaqueBackground()
    UINavigationBar.appearance().scrollEdgeAppearance = navigationBar
    UINavigationBar.appearance().compactAppearance = navigationBar
    UINavigationBar.appearance().standardAppearance = navigationBar

    UITableView.appearance().backgroundColor = .clear
    UITableView.appearance().separatorStyle = .none
    UITableViewCell.appearance().backgroundColor = .clear
}
