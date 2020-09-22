//
// Created by Salomon BRYS on 24/08/2020.
// Copyright (c) 2020 Acinq. All rights reserved.
//

import SwiftUI
import PhoenixShared

struct TransactionView : View {

    let transaction: PhoenixShared.Transaction

    let close: () -> Void

    var body: some View {
        ZStack {
            VStack {
                HStack {
                    Spacer()
                    Button {
                        close()
                    } label: {
                        Image("ic_cross")
                                .resizable()
                                .frame(width: 30, height: 30)
                    }
                    .padding(32)
                }
                Spacer()
            }

            VStack {
                switch (transaction.status) {
                case .success:
                    Image(vector: "ic_payment_success_static")
                            .renderingMode(.template)
                            .resizable()
                            .frame(width: 100, height: 100)
                            .foregroundColor(.appGreen)
                    VStack {
                        Text(transaction.amountSat < 0 ? "SENT" : "RECEIVED")
                                .font(Font.title2.bold())
                        Text(transaction.timestamp.formatDateMS().uppercased())
                                .font(Font.title2)
                    }
                            .padding()
                case .pending:
                    Image(vector: "ic_send")
                            .resizable()
                            .frame(width: 100, height: 100)
                    Text("PENDING")
                            .font(Font.title2.bold())
                                .padding()
                case .failure:
                    Image(vector: "ic_cross")
                            .renderingMode(.template)
                            .resizable()
                            .frame(width: 100, height: 100)
                            .foregroundColor(.appRed)
                    VStack {
                        Text("PAYMENT ")
                                .font(Font.title2)
                        +
                        Text("FAILED")
                                .font(Font.title2.bold())
                        Text("NO FUND HAS BEEN SENT")
                                .font(Font.title2)
                    }
                            .padding()
                default:
                    EmptyView()
                }

                HStack(alignment: .bottom) {
                    Text(Int64((Double(abs(transaction.amountSat)) / 1000.0).rounded()).formatNumber())
                            .font(.largeTitle)
                    Text("sat")
                            .font(.title3)
                            .padding(.bottom, 4)
                            .foregroundColor(.gray)
                }
                        .padding()
            }
        }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.appBackground)
    }
}

class TransactionView_Previews : PreviewProvider {
    static var previews: some View {
        TransactionView(transaction: mockSpendFailedTransaction, close: {})
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
