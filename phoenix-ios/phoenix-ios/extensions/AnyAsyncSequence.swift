/// AsyncSequence is currently lacking type erasure.
/// Long term, the goal appears to be changes to the language to support this,
/// as hinted at here:
/// https://forums.swift.org/t/anyasyncsequence/50828
///
/// In the meantime, we still need a way to use it which isn't a giant pain in the butt.
/// So we have this workaround for now.
///
/// Inspired by:
/// - https://github.com/vsanthanam/AnyAsyncSequence
/// With improvements from:
/// - https://github.com/profusion/AnyAsyncSequence
///

import Foundation

@available(macOS 10.15, iOS 13.0, watchOS 6.0, tvOS 13.0, *)
public extension AsyncSequence {

	 /// Create a type erased version of this sequence
	 /// - Returns: The sequence, wrapped in an `AnyAsyncSequence`
	 func eraseToAnyAsyncSequence() -> AnyAsyncSequence<Element> {
		  AnyAsyncSequence(self)
	 }

	 /// Create a type erased version of this sequence
	 /// - Returns: The sequence, wrapped in an `AnyAsyncThrowingSequence`
	 func eraseToAnyAsyncThrowingSequence() -> AnyAsyncThrowingSequence<Element> {
		  AnyAsyncThrowingSequence(self)
	 }
}

/// A type erased `AsyncSequence`
///
/// This type allows you to create APIs that return an `AsyncSequence` that allows consumers to iterate over the
/// sequence, without exposing the sequence's underlying type.
/// Typically, you wouldn't actually initialize this type yourself, but instead create one using the
/// `.eraseToAnyAsyncSequence()` operator also provided with this package.
@available(macOS 10.15, iOS 13.0, watchOS 6.0, tvOS 13.0, *)
public struct AnyAsyncSequence<Element>: AsyncSequence {
	 // MARK: - Initializers

	 /// Create an `AnyAsyncSequence` from an `AsyncSequence` conforming type
	 /// - Parameter sequence: The `AnySequence` type you wish to erase
	 public init<T: AsyncSequence>(_ sequence: T) where T.Element == Element {
		  makeAsyncIteratorClosure = { AnyAsyncIterator(sequence.makeAsyncIterator()) }
	 }

	 // MARK: - API

	 public struct AnyAsyncIterator: AsyncIteratorProtocol {
		  private let nextClosure: () async -> Element?

		  public init<T: AsyncIteratorProtocol>(_ iterator: T) where T.Element == Element {
				var iterator = iterator
				nextClosure = { try? await iterator.next() }
		  }

		  public func next() async -> Element? {
				await nextClosure()
		  }
	 }

	 // MARK: - AsyncSequence

	 public typealias Element = Element

	 public typealias AsyncIterator = AnyAsyncIterator

	 public func makeAsyncIterator() -> AsyncIterator {
		  AnyAsyncIterator(makeAsyncIteratorClosure())
	 }

	 private let makeAsyncIteratorClosure: () -> AsyncIterator
}

/// A type erased `AsyncThrowingSequence`
///
/// This type allows you to create APIs that return an `AsyncThrowingSequence` that allows consumers to iterate over the
/// sequence, without exposing the sequence's underlying type.
/// Typically, you wouldn't actually initialize this type yourself, but instead create one using the
/// `.eraseToAnyAsyncThrowingSequence()` operator also provided with this package.
@available(macOS 10.15, iOS 13.0, watchOS 6.0, tvOS 13.0, *)
public struct AnyAsyncThrowingSequence<Element>: AsyncSequence {
	 // MARK: - Initializers

	 /// Create an `AnyAsyncSequence` from an `AsyncSequence` conforming type
	 /// - Parameter sequence: The `AnySequence` type you wish to erase
	 public init<T: AsyncSequence>(_ sequence: T) where T.Element == Element {
		  makeAsyncIteratorClosure = { AnyAsyncIterator(sequence.makeAsyncIterator()) }
	 }

	 // MARK: - API

	 public struct AnyAsyncIterator: AsyncIteratorProtocol {
		  private let nextClosure: () async throws -> Element?

		  public init<T: AsyncIteratorProtocol>(_ iterator: T) where T.Element == Element {
				var iterator = iterator
				nextClosure = { try await iterator.next() }
		  }

		  public func next() async throws -> Element? {
				try await nextClosure()
		  }
	 }

	 // MARK: - AsyncSequence

	 public typealias Element = Element

	 public typealias AsyncIterator = AnyAsyncIterator

	 public func makeAsyncIterator() -> AsyncIterator {
		  AnyAsyncIterator(makeAsyncIteratorClosure())
	 }

	 private let makeAsyncIteratorClosure: () -> AsyncIterator
}
