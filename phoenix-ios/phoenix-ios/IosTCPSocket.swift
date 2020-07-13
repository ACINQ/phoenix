import Foundation
import Network
import PhoenixShared

class IosTCPSocket: TCPSocket {

    let connection: NWConnection

    init(_ connection: NWConnection) {
        self.connection = connection
    }

    func close() {
        connection.cancel()
    }

    func receive(min: Int32, max: Int32, completionHandler: @escaping (TCPSocketResult<KotlinByteArray>?, Error?) -> ()) {
        connection.receive(minimumIncompleteLength: Int(min), maximumLength: Int(max)) { data, context, isComplete, error in
            if let error = error {
                completionHandler(TCPSocketResultFailure(error: error.toIOException()), nil)
            } else if let data = data {
                let bytes = DarwinUtilsKt.toByteArray(data)
                completionHandler(TCPSocketResultSuccess(result: bytes), nil)
            } else if isComplete {
                completionHandler(TCPSocketResultFailure(error: TCPSocketIOException.ConnectionClosed()), nil)
            } else {
                completionHandler(TCPSocketResultFailure(error: TCPSocketIOException.Unknown(message: nil)), nil)
            }
        }
    }

    func send(bytes: KotlinByteArray?, flush: Bool, completionHandler: @escaping (TCPSocketResult<KotlinUnit>?, Error?) -> ()) {
        let pinned = bytes?.kotlinPin()
        let data = pinned?.toData()
        connection.send(content: data, isComplete: flush, completion: .contentProcessed { error in
            pinned?.unpin()
            completionHandler(error.map { TCPSocketResultFailure(error: $0.toIOException()) } ?? TCPSocketResultSuccess(result: KotlinUnit()), nil)
        })
    }

    class Builder : TCPSocketBuilder {
        func connect(host: String, port: Int32, completionHandler: @escaping (TCPSocketResult<TCPSocket>?, Error?) -> Void) {
            let connection = NWConnection(host: .init(host), port: .init(integerLiteral: UInt16(port)), using: .tcp)
            connection.stateUpdateHandler = {
                switch $0 {
                case .ready:
                    completionHandler(TCPSocketResultSuccess(result: IosTCPSocket(connection)), nil)
                    connection.stateUpdateHandler = nil
                case .failed(let error), .waiting(let error):
                    completionHandler(TCPSocketResultFailure(error: error.toIOException()), nil)
                    connection.stateUpdateHandler = nil
                default: break
                }
            }
            connection.start(queue: .main)
        }
    }

}

extension NWError {
    func toIOException() -> TCPSocketIOException {
        switch self {
        case .posix(let code):
            switch code {
            case POSIXError.ECONNREFUSED:
                return TCPSocketIOException.ConnectionRefused()
            case POSIXError.ECONNRESET:
                return TCPSocketIOException.ConnectionClosed()
            default:
                return TCPSocketIOException.Unknown(message: debugDescription)
            }
        default:
            return TCPSocketIOException.Unknown(message: debugDescription)
        }
    }
}
