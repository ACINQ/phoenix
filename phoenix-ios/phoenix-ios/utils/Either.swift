import Foundation

enum Either<A, B>{
	case Left(A)
	case Right(B)
}
