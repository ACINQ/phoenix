//
//  ScreenshotProtected.swift
//  Leverages isSecureTextEntry to prevent the content from being captured.
//
//  Created by Dominique Padiou on 01/10/2026.
//  Copyright © 2026 Acinq. All rights reserved.
//

import SwiftUI

struct ScreenshotProtected<Content: View>: UIViewRepresentable {
	@ViewBuilder let content: () -> Content

	func makeUIView(context: Context) -> UITextField {
		let field = UITextField()
		field.isSecureTextEntry = true
		field.isAccessibilityElement = false
		field.delegate = context.coordinator
		field.layoutIfNeeded()

		let host = UIHostingController(rootView: content())
		host.view.backgroundColor = .clear
		host.view.translatesAutoresizingMaskIntoConstraints = false
		(field.subviews.first ?? field).addSubview(host.view)
		NSLayoutConstraint.activate([
			host.view.leadingAnchor.constraint(equalTo: field.leadingAnchor),
			host.view.trailingAnchor.constraint(equalTo: field.trailingAnchor),
			host.view.topAnchor.constraint(equalTo: field.topAnchor),
			host.view.bottomAnchor.constraint(equalTo: field.bottomAnchor),
		])
		context.coordinator.host = host
		return field
	}

	func updateUIView(_ field: UITextField, context: Context) {
		context.coordinator.host?.rootView = content()
	}

	func sizeThatFits(_ proposal: ProposedViewSize, uiView: UITextField, context: Context) -> CGSize? {
		context.coordinator.host?.sizeThatFits(in: CGSize(
			width: proposal.width ?? .greatestFiniteMagnitude,
			height: proposal.height ?? .greatestFiniteMagnitude
		))
	}

	func makeCoordinator() -> Coordinator { Coordinator() }
	final class Coordinator: NSObject, UITextFieldDelegate {
		var host: UIHostingController<Content>?
		func textFieldShouldBeginEditing(_ textField: UITextField) -> Bool { false }
	}}
