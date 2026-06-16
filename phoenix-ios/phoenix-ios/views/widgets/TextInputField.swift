//
//  TextInputField.swift
//  phoenix-ios
//
//  Created by Dominique Padiou on 12/06/2026.
//  Copyright © 2026 Acinq. All rights reserved.
//

import SwiftUI
import PhoenixShared

struct TextInputField: View {
	let label: String
	let placeholder: String
	@Binding var text: String
	var isDisabled: Bool

	enum LayoutStyle {
		case horizontal
		case vertical
	}
	var layout: LayoutStyle = .horizontal
	var keyboardType: UIKeyboardType = .alphabet

	var body: some View {

		let layout = layout == .horizontal
			? AnyLayout(HStackLayout(alignment: .firstTextBaseline, spacing: 16))
			: AnyLayout(VStackLayout(alignment: .leading, spacing: 8))

		layout {
			Text(label)
				.font(.subheadline)

			HStack {
				TextField(placeholder, text: $text)
					.keyboardType(keyboardType)
					.disableAutocorrection(true)
					.autocapitalization(.none)
					.disabled(isDisabled)
			}
			.padding([.top, .bottom], 8)
			.padding(.leading, 12)
			.padding(.trailing, 8)
			.background(
				RoundedRectangle(cornerRadius: 12)
					.fill(Color.textFieldBorder.opacity(0.1))
			)
			.overlay(
				RoundedRectangle(cornerRadius: 12)
					.stroke(Color.textFieldBorder.opacity(0.5), lineWidth: 1)
			)
		}.frame(maxWidth: .infinity)
	}
}
