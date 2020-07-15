import Foundation
import Network
import PhoenixShared

class IosConnectionBridge: IosTcpSocketConnectionBridge {

    private let connection: NWConnection

    init(_ connection: NWConnection) {
        self.connection = connection
    }

    func send(content: Data?, isComplete: Bool, completion: @escaping (TcpSocketIOException?) -> Void) {
        connection.send(content: content, completion: .contentProcessed { completion($0?.toIOException()) })
    }

    func receive(minimumIncompleteLength: Int32, maximumLength: Int32, completion: @escaping (Data?, KotlinBoolean, TcpSocketIOException?) -> Void) {
        connection.receive(
                minimumIncompleteLength: Int(minimumIncompleteLength),
                maximumLength: Int(maximumLength)
        ) { data, _, isComplete, error in
            completion(data, KotlinBoolean(value: isComplete), error?.toIOException())
        }
    }

    func cancel() {
        connection.cancel()
    }

    class Builder : IosTcpSocketConnectionBridgeBuilder {

        static let shared = Builder()
        private init() {}

        func connect(host: String, port: Int32, completion: @escaping (IosTcpSocketConnectionBridge?, TcpSocketIOException?) -> Void) {
            let connection = NWConnection(host: .init(host), port: .init(integerLiteral: UInt16(port)), using: .tcp)
            connection.stateUpdateHandler = {
                switch $0 {
                case .ready:
                    completion(IosConnectionBridge(connection), nil)
                    connection.stateUpdateHandler = nil
                case .failed(let error), .waiting(let error):
                    completion(nil, error.toIOException())
                    connection.stateUpdateHandler = nil
                default: break
                }
            }
            connection.start(queue: .main)
        }
    }
}

extension NWError {
    func toIOException() -> TcpSocketIOException {
        switch self {
        case .posix(let code):
            switch code {
            case POSIXError.ECONNREFUSED:
                return TcpSocketIOException.ConnectionRefused()
            case POSIXError.ECONNRESET:
                return TcpSocketIOException.ConnectionClosed()
            default:
                return TcpSocketIOException.Unknown(message: debugDescription)
            }
        default:
            return TcpSocketIOException.Unknown(message: debugDescription)
        }
    }
}
