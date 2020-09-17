import SwiftUI
import PhoenixShared

struct ChannelsConfigurationView: MVIView {

    typealias Model = ChannelsConfiguration.Model
    typealias Intent = ChannelsConfiguration.Intent

    @State var sharing: String? = nil

    @State var showingChannel: ChannelsConfiguration.ModelChannel? = nil

    @StateObject var toast = Toast()

    var body: some View {
        mvi { model, intent in
            ZStack {
                VStack {
                    Text("Your node ID: \(model.nodeId)")
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.appBackground)

                    if (model.channels.isEmpty) {
                        Text("You don't have any payment channel!")
                        Spacer()
                    } else {
                        Text("You have \(model.channels.count) payment channels:")
                                .padding()
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.white)
                        HStack {
                            Spacer()
                            Button {
                                sharing = model.json
                            } label: {
                                Image(systemName: "square.and.arrow.up")
                                Text("Share your channel list...")
                            }
                                    .sharing($sharing)
                        }
                                .padding()
                                .frame(maxWidth: .infinity)
                                .background(Color.appBackground)
                        ScrollView {
                            LazyVStack {
                                ForEach(model.channels, id: \.id) { channel in
                                    Button {
                                        withAnimation { showingChannel = channel }
                                    } label: {
                                        HStack {
                                            Image("ic_bullet")
                                                    .resizable()
                                                    .frame(width: 10, height: 10)
                                                    .foregroundColor(channel.isOk ? .appGreen : .appRed)
                                            Text(channel.stateName)
                                                    .foregroundColor(.black)
                                            Spacer()
                                            if let c = channel.commitments {
                                                Text("\(c.first!) sat / \(c.second!) sat")
                                                        .foregroundColor(.black)
                                            }
                                        }
                                                .padding(8)
                                    }
                                    Divider()
                                }
                            }
                                    .padding()
                        }
                    }
                }

                Popup(of: showingChannel) { channel in
                    VStack {
                        ScrollView {
                            Text(channel.json)
                                    .padding()
                                    .frame(maxWidth: .infinity, alignment: .leading)
                        }
                                .frame(height: 300)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.appBackground)

                        HStack {
                            Button {
                                UIPasteboard.general.string = channel.json
                                toast.toast(text: "Copied in pasteboard!")
                            } label: {
                                Image(systemName: "square.on.square")
                                        .resizable()
                                        .scaledToFit()
                                        .frame(width: 22, height: 22)
                            }

                            Divider()
                                    .frame(height: 30)
                                    .padding([.leading, .trailing], 8)

                            Button {
                                sharing = channel.json
                            } label: {
                                Image(systemName: "square.and.arrow.up")
                                        .resizable()
                                        .scaledToFit()
                                        .frame(width: 22, height: 22)
                            }

                            if let txUrl = channel.txUrl {
                                Divider()
                                        .frame(height: 30)
                                        .padding([.leading, .trailing], 8)

                                Button {
                                    if let url = URL(string: txUrl) {
                                        UIApplication.shared.open(url)
                                    }
                                } label: {
                                    Text("Tx")
                                            .font(.title2)
                                }
                            }

                            Spacer()
                            Button("OK") {
                                withAnimation { showingChannel = nil }
                            }
                                    .font(.title2)
                        }
                                .padding()
                    }
                }

                toast.view()
            }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .edgesIgnoringSafeArea(.bottom)
                    .navigationBarTitle("My payment channels", displayMode: .inline)
        }
    }
}

class ChannelsConfigurationView_Previews : PreviewProvider {
    static let mockModel = ChannelsConfiguration.Model(
            nodeId: "03af0ed6052cf28d670665549bc86f4b721c9fdb309d40c58f5811f63966e005d0",
            json: "{}",
            channels: [
                ChannelsConfiguration.ModelChannel(
                        id: "b50bf19d16156de8231f6d3d3fb3dd105ba338de5366d0421b0954b9ceb0d4f8",
                        isOk: true,
                        stateName: "Normal",
                        commitments: KotlinPair(first: 50000, second: 200000),
                        json: "{Everything is normal!}",
                        txUrl: "http://google.com"
                ),
                ChannelsConfiguration.ModelChannel(
                        id: "e5366d0421b0954b9ceb0d4f8b50bf19d16156de8231f6d3d3fb3dd105ba338d",
                        isOk: false,
                        stateName: "Woops",
                        commitments: nil,
                        json: "{Woops!}",
                        txUrl: nil
                )
            ]
    )

    static var previews: some View {
        mockView(ChannelsConfigurationView()) { $0.channelsConfigurationModel = mockModel }
            .previewDevice("iPhone 11")
    }

    #if DEBUG
    @objc class func injected() {
        UIApplication.shared.windows.first?.rootViewController = UIHostingController(rootView: previews)
    }
    #endif
}
