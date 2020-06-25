import Foundation
import Network
import Phoenix

class IosSocket: Socket {

    let connection: NWConnection

    init(_ connection: NWConnection) {
        self.connection = connection
        connection.start(queue: .main)
    }

    func close() {
        connection.cancel()
    }

    func receive(min: Int32, max: Int32, completionHandler: @escaping (SocketResult<KotlinByteArray>?, Error?) -> ()) {
        connection.receive(minimumIncompleteLength: Int(min), maximumLength: Int(max)) { data, context, b, error in
            if let error = error {
                completionHandler(SocketResultFailure(error: error.toIOException()), nil)
            } else if let data = data {
                let bytes = DarwinUtilsKt.toByteArray(data)
                completionHandler(SocketResultSuccess(result: bytes), nil)
            } else {
                completionHandler(SocketResultFailure(error: SocketIOException.Unknown(message: nil)), nil)
            }
        }
    }

    func send(bytes: KotlinByteArray?, flush: Bool, completionHandler: @escaping (SocketResult<KotlinUnit>?, Error?) -> ()) {
        let pinned = bytes?.kotlinPin()
        let data = pinned?.toData()
        connection.send(content: data, isComplete: flush, completion: .contentProcessed { error in
            pinned?.unpin()
            completionHandler(error.map { SocketResultFailure(error: $0.toIOException()) } ?? SocketResultSuccess(result: KotlinUnit()), nil)
        })
    }

    class Factory : SocketFactory {
        func createSocket(host: String, port: Int32, onStateChange: @escaping (Socket, SocketState) -> Void) -> Socket {
            let connection = NWConnection(host: .init(host), port: .init(integerLiteral: UInt16(port)), using: .tcp)
            let socket = IosSocket(connection)
            connection.stateUpdateHandler = {
                if let kstate = $0.toKState() {
                    onStateChange(socket, kstate)
                }
            }
            return socket
        }
    }

}

extension NWConnection.State {
    func toKState() -> SocketState? {
        switch self {
        case .ready:
            return SocketState.Ready()
        case .cancelled:
            return SocketState.Closed()
        case .failed(let error), .waiting(let error):
            return SocketState.Error(exception: error.toIOException())
        default:
            return nil
        }
    }
}

extension NWError {
    func toIOException() -> SocketIOException {
        switch self {
        case .posix(let code):
            switch code {
            case POSIXError.ECONNREFUSED:
                return SocketIOException.ConnectionRefused()
            case POSIXError.ECONNRESET:
                return SocketIOException.ConnectionClosed()
            default:
                return SocketIOException.Unknown(message: debugDescription)
            }
        default:
            return SocketIOException.Unknown(message: debugDescription)
        }
    }
}
