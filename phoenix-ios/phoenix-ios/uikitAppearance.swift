//
// Created by Salomon BRYS on 20/11/2020.
// Copyright (c) 2020 Acinq. All rights reserved.
//

import SwiftUI

func UIKitAppearance() {
	let navBarAppearance = UINavigationBarAppearance()
	navBarAppearance.configureWithOpaqueBackground()
	navBarAppearance.backgroundColor = .primaryBackground
	UINavigationBar.appearance().scrollEdgeAppearance = navBarAppearance
	UINavigationBar.appearance().compactAppearance = navBarAppearance
	UINavigationBar.appearance().standardAppearance = navBarAppearance

	UITableView.appearance().backgroundColor = .clear
	UITableView.appearance().separatorStyle = .none
	UITableViewCell.appearance().backgroundColor = .clear
}
