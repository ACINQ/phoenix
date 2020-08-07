import PhoenixShared

struct DI {

    private let di: Kodein_diDI

    init(_ di: Kodein_diDI) { self.di = di }

    func instance<T : AnyObject>(of: T.Type, params: [AnyClass]) -> T {
        DiIosKt.directInstance(di, of: of, params: params) as! T
    }

}
