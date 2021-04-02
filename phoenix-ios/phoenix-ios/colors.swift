//
// Created by Salomon BRYS on 24/08/2020.
// Copyright (c) 2020 Acinq. All rights reserved.
//

import SwiftUI

extension Color {
	
	// See Colors.xcassets for RGB values.
	// The assets catalog allows us to customize the values for light vs dark modes.
	
	static var appAccent: Color {
		get {
			if AppDelegate.get().business.chain.isTestnet() {
				return Color("appAccentBlue")
			} else {
				return Color("appAccentGreen")
			}
		}
	}
	
	static var appPositive: Color = Color("appAccentGreen")
	static let appNegative = Color("appNegative")
	static let appWarn = Color("appWarn")
	
	static let buttonFill = Color("buttonFill")
	static let primaryBackground = Color("primaryBackground")
	static let primaryForeground = Color("primaryForeground")
	static let borderColor = Color("borderColor")
	static let mutedBackground = Color("mutedBackground")
}
