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
			return Color(UIColor.appAccent) // see below: extension UIColor
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
	static let textFieldBorder = Color("textFieldBorder")
}

extension UIColor {
	
	static var appAccent: UIColor {
		if BusinessManager.isTestnet {
			return UIColor(named: "appAccentBlue")!
		} else {
			return UIColor(named: "appAccentGreen")!
		}
	}
	
	static var primaryBackground: UIColor {
		return UIColor(named: "primaryBackground")!
	}
	
	/// Note that UITraitCollection.current may be incorrect if accessed from a background thread.
	///
	func htmlString(_ traitCollection: UITraitCollection) -> String {
		
		let adaptedColor = self.resolvedColor(with: traitCollection)
		
		var r: CGFloat = 0
		var g: CGFloat = 0
		var b: CGFloat = 0
		var a: CGFloat = 0
		adaptedColor.getRed(&r, green: &g, blue: &b, alpha: &a)
		
		let ir = Int(r * 255.0)
		let ig = Int(g * 255.0)
		let ib = Int(b * 255.0)
		let sa = String(format: "%.2f", a)
		
		return "rgb(\(ir), \(ig), \(ib), \(sa))"
	}
}
