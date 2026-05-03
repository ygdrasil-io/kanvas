package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * class TokenTracker {
 * public:
 *     /**
 *      * Gets the token one beyond the last token that has been flushed.
 *      * This represents the ID for the *current* batch of work being recorded.
 *      */
 *     Token nextFlushToken() const { return fCurrentFlushToken.next(); }
 *
 *     /**
 *      * Gets the token that was *just issued*. This represents the ID of the
 *      * flush that was most recently completed.
 *      */
 *     Token currentFlushToken() const { return fCurrentFlushToken; }
 *
 *     /**
 *      * Gets the next draw token.
 *      */
 *     Token nextDrawToken() const { return fCurrentDrawToken.next(); }
 *
 * private:
 *     // Only these classes get to increment the token counters
 *     friend class ::GrOpFlushState;               // Ganesh
 *     friend class ::TestingUploadTarget;          // ``
 *     friend class skgpu::graphite::RecorderPriv;  // Graphite
 *
 *     Token issueDrawToken() { return ++fCurrentDrawToken; }
 *     Token issueFlushToken() { return ++fCurrentFlushToken; }
 *
 *     Token fCurrentDrawToken = Token::InvalidToken();
 *     Token fCurrentFlushToken = Token::InvalidToken();
 * }
 * ```
 */
public data class TokenTracker public constructor(
  /**
   * C++ original:
   * ```cpp
   * Token fCurrentDrawToken
   * ```
   */
  private var fCurrentDrawToken: Token,
  /**
   * C++ original:
   * ```cpp
   * Token fCurrentFlushToken
   * ```
   */
  private var fCurrentFlushToken: Token,
) {
  /**
   * C++ original:
   * ```cpp
   * Token nextFlushToken() const { return fCurrentFlushToken.next(); }
   * ```
   */
  public fun nextFlushToken(): Token {
    TODO("Implement nextFlushToken")
  }

  /**
   * C++ original:
   * ```cpp
   * Token currentFlushToken() const { return fCurrentFlushToken; }
   * ```
   */
  public fun currentFlushToken(): Token {
    TODO("Implement currentFlushToken")
  }

  /**
   * C++ original:
   * ```cpp
   * Token nextDrawToken() const { return fCurrentDrawToken.next(); }
   * ```
   */
  public fun nextDrawToken(): Token {
    TODO("Implement nextDrawToken")
  }

  /**
   * C++ original:
   * ```cpp
   * Token issueDrawToken() { return ++fCurrentDrawToken; }
   * ```
   */
  private fun issueDrawToken(): Token {
    TODO("Implement issueDrawToken")
  }

  /**
   * C++ original:
   * ```cpp
   * Token issueFlushToken() { return ++fCurrentFlushToken; }
   * ```
   */
  private fun issueFlushToken(): Token {
    TODO("Implement issueFlushToken")
  }
}
