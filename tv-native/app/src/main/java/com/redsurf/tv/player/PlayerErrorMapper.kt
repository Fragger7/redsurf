package com.redsurf.tv.player

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource

/**
 * PLAYER_ENGINEERING_BRIEF.md §11 (user directive, 2026-09-16): a single source of truth for
 * turning an ExoPlayer error into something a family member watching TV can actually understand -
 * never a raw error code or exception class name as the primary text. Called by exactly one place
 * ([PlayerController]'s error handling, brief §4.4); no error copy gets written ad hoc anywhere
 * else. Retry orchestration (attempt counts, backoff delays) lives in the controller, not here -
 * this object only ever answers "what does the user see," never "should we retry."
 */
data class PlayerErrorPresentation(
    val message: String,
    val isRetrying: Boolean,
    val isTerminal: Boolean,
)

object PlayerErrorMapper {
    /** [isTerminal] is the controller's classification (brief §4.4's retryable/non-retryable
     * split, and whether the backoff ladder is exhausted) - this function only maps that decision
     * to copy, it never makes it. */
    fun present(error: PlaybackException, isTerminal: Boolean): PlayerErrorPresentation {
        val httpStatus = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        val message = when {
            !isTerminal -> "Reconnecting…"
            httpStatus == 403 -> "This channel isn't available right now"
            httpStatus == 884 -> "This channel is temporarily locked by the provider"
            httpStatus == 404 -> "Channel not available"
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                "This channel's format isn't supported"
            else -> "Can't reach this channel — try another"
        }
        return PlayerErrorPresentation(message = message, isRetrying = !isTerminal, isTerminal = isTerminal)
    }

    /** A retry in flight with no (or not yet classified) underlying [PlaybackException] to map -
     * the 456 race's single delayed retry, and the stall watchdog's first strike. */
    fun reconnecting(): PlayerErrorPresentation =
        PlayerErrorPresentation(message = "Reconnecting…", isRetrying = true, isTerminal = false)

    /** §2.5 - AV1 is the only track and this device has no hardware decoder for it. Distinct from
     * [present] since it's never an ExoPlayer error at all (the stream "succeeds" and stutters). */
    fun avOneUnsupported(): PlayerErrorPresentation =
        PlayerErrorPresentation(
            message = "This device can't play this channel's video format",
            isRetrying = false,
            isTerminal = true,
        )

    /** §4.5 - the stall watchdog's second strike within 60s; give up rather than loop forever. */
    fun stalled(): PlayerErrorPresentation =
        PlayerErrorPresentation(
            message = "Having trouble with this channel — try another",
            isRetrying = false,
            isTerminal = true,
        )
}
