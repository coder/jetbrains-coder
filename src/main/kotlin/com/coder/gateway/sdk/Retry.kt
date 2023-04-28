package com.coder.gateway.sdk

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.ssh.SshException
import kotlinx.coroutines.delay
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

fun unwrap(ex: Exception): Throwable? {
    var cause = ex.cause
    while(cause?.cause != null) {
        cause = cause.cause
    }
    return cause ?: ex
}

/**
 * Similar to Intellij's except it gives you the next delay, logs differently,
 * updates periodically (for counting down), runs forever, and takes a
 * predicate for determining whether we should retry.
 *
 * The update will have a boolean to indicate whether it is the first update (so
 * things like duplicate logs can be avoided).  If remaining is null then no
 * more retries will be attempted.
 *
 * If an exception related to canceling is received then return null.
 */
suspend fun <T> suspendingRetryWithExponentialBackOff(
    initialDelayMs: Long = TimeUnit.SECONDS.toMillis(5),
    backOffLimitMs: Long = TimeUnit.MINUTES.toMillis(3),
    backOffFactor: Int = 2,
    backOffJitter: Double = 0.1,
    label: String,
    logger: Logger,
    predicate: (e: Throwable?) -> Boolean,
    update: (attempt: Int, e: Throwable?, remaining: String?) -> Unit,
    action: suspend (attempt: Int) -> T?
): T? {
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
          when (unwrappedEx) {
              is InterruptedException,
              is CancellationException,
              is ProcessCanceledException -> {
                  logger.info("Retrying $label canceled due to ${unwrappedEx.javaClass}")
                  return null
              }
          }
          if (!predicate(unwrappedEx)) {
              logger.error("Failed to $label (attempt $attempt; will not retry)", originalEx)
              update(attempt, unwrappedEx, null)
              return null
          }
          logger.error("Failed to $label (attempt $attempt; will retry in $delayMs ms)", originalEx)
          var remainingMs = delayMs
          while (remainingMs > 0) {
              val remainingS = TimeUnit.MILLISECONDS.toSeconds(remainingMs)
              val remaining = if (remainingS < 1) "now" else "in $remainingS second${if (remainingS > 1) "s" else ""}"
              update(attempt, unwrappedEx, remaining)
              val next = min(remainingMs, TimeUnit.SECONDS.toMillis(1))
              remainingMs -= next
              delay(next)
          }
          delayMs = min(delayMs * backOffFactor, backOffLimitMs) + (random.nextGaussian() * delayMs * backOffJitter).toLong()
      }
    }
    error("Should never be reached")
}
