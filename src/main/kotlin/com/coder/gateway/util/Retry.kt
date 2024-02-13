package com.coder.gateway.util

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.ssh.SshException
import com.jetbrains.gateway.ssh.deploy.DeployException
import kotlinx.coroutines.delay
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

fun unwrap(ex: Exception): Throwable {
    var cause = ex.cause
    while(cause?.cause != null) {
        cause = cause.cause
    }
    return cause ?: ex
}

/**
 * Similar to Intellij's except it adds two new arguments: onCountdown (for
 * displaying the time until the next try) and retryIf (to limit which
 * exceptions can be retried).
 *
 * Exceptions that cannot be retried will be thrown.
 *
 * onException and onCountdown will be called immediately on retryable failures.
 * onCountdown will also be called every second until the next try with the time
 * left until that next try (the last interval might be less than one second if
 * the total delay is not divisible by one second).
 *
 * Some other differences:
 * - onException gives you the time until the next try (intended to be logged
 *   with the error).
 * - Infinite tries.
 * - SshException is unwrapped.
 *
 * It is otherwise identical.
 */
suspend fun <T> suspendingRetryWithExponentialBackOff(
    initialDelayMs: Long = TimeUnit.SECONDS.toMillis(5),
    backOffLimitMs: Long = TimeUnit.MINUTES.toMillis(3),
    backOffFactor: Int = 2,
    backOffJitter: Double = 0.1,
    retryIf: (e: Throwable) -> Boolean,
    onException: (attempt: Int, nextMs: Long, e: Throwable) -> Unit,
    onCountdown: (remaining: Long) -> Unit,
    action: suspend (attempt: Int) -> T
): T {
    val random = Random()
    var delayMs = initialDelayMs
    for (attempt in 1..Int.MAX_VALUE) {
      try {
          return action(attempt)
      }
      catch (originalEx: Exception) {
          // SshException can happen due to anything from a timeout to being
          // canceled so unwrap to find out.
          val unwrappedEx = if (originalEx is SshException) unwrap(originalEx) else originalEx
          if (!retryIf(unwrappedEx)) {
              throw unwrappedEx
          }
          onException(attempt, delayMs, unwrappedEx)
          var remainingMs = delayMs
          while (remainingMs > 0) {
              onCountdown(remainingMs)
              val next = min(remainingMs, TimeUnit.SECONDS.toMillis(1))
              remainingMs -= next
              delay(next)
          }
          delayMs = min(delayMs * backOffFactor, backOffLimitMs) + (random.nextGaussian() * delayMs * backOffJitter).toLong()
      }
    }
    error("Should never be reached")
}

/**
 * Convert a millisecond duration into a human-readable string.
 *
 * < 1 second: "now"
 * 1 second: "in one second"
 * > 1 second: "in <duration> seconds"
 */
fun humanizeDuration(durationMs: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    return if (seconds < 1) "now" else "in $seconds second${if (seconds > 1) "s" else ""}"
}

/**
 * When the worker upload times out Gateway just says it failed. Even the root
 * cause (IllegalStateException) is useless.  The error also includes a very
 * long useless tmp path.  Return true if the error looks like this timeout.
 */
fun isWorkerTimeout(e: Throwable): Boolean {
    return e is DeployException && e.message.contains("Worker binary deploy failed")
}

/**
 * Return true if the exception is some kind of cancellation.
 */
fun isCancellation(e: Throwable): Boolean {
    return e is InterruptedException
            || e is CancellationException
            || e is ProcessCanceledException
}
