import UIKit
import PhoenixShared

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate {

    let mocks = MockDIBuilder().apply {
        $0.contentModel = ContentView_Previews.mockModel
        $0.initModel = InitView_Previews.mockModel
        $0.homeModel = HomeView_Previews.mockModel
        $0.receiveModel = ReceiveView_Previews.mockModel
        $0.scanModel = ScanView_Previews.mockModel
        $0.restoreWalletModel = RestoreWalletView_Previews.mockModel
        $0.configurationModel = ConfigurationView_Previews.mockModel
        $0.displayConfigurationModel = DisplayConfigurationView_Previews.mockModel
        $0.electrumConfigurationModel = ElectrumConfigurationView_Previews.mockModel
        $0.channelsConfigurationModel = ChannelsConfigurationView_Previews.mockModel
    }

    let di: DI

    override init() {
        setenv("CFNETWORK_DIAGNOSTICS", "3", 1);

        di = DI(
            DiIosKt.phoenixDI()
//            mocks.di()
        )
    }

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {

        UITableView.appearance().backgroundColor = .clear
        UITableView.appearance().separatorStyle = .none
        UITableViewCell.appearance().backgroundColor = .clear

        // Override point for customization after application launch.
        #if DEBUG
            var injectionBundlePath = "/Applications/InjectionIII.app/Contents/Resources"
            #if targetEnvironment(macCatalyst)
                injectionBundlePath = "\(injectionBundlePath)/macOSInjection.bundle"
            #elseif os(iOS)
                injectionBundlePath = "\(injectionBundlePath)/iOSInjection.bundle"
            #elseif os(tvOS)
                injectionBundlePath = "\(injectionBundlePath)/tvOSInjection.bundle"
            #endif
            Bundle(path: injectionBundlePath)?.load()
        #endif

        return true
    }

    // MARK: UISceneSession Lifecycle

    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        // Called when a new scene session is being created.
        // Use this method to select a configuration to create the new scene with.
        return UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
    }

    func application(_ application: UIApplication, didDiscardSceneSessions sceneSessions: Set<UISceneSession>) {
        // Called when the user discards a scene session.
        // If any sessions were discarded while the application was not running, this will be called shortly after application:didFinishLaunchingWithOptions.
        // Use this method to release any resources that were specific to the discarded scenes, as they will not return.
    }


}

