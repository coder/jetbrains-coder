package com.coder.gateway.sdk

import kotlinx.coroutines.delay
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Similar to Intellij's except it gives you the next delay, does not do its own
 * logging, updates periodically (for counting down), and runs forever.
 */
suspend fun <T> suspendingRetryWithExponentialBackOff(
    initialDelayMs: Long = TimeUnit.SECONDS.toMillis(5),
    backOffLimitMs: Long = TimeUnit.MINUTES.toMillis(3),
    backOffFactor: Int = 2,
    backOffJitter: Double = 0.1,
    update: (attempt: Int, remainingMs: Long, e: Exception) -> Unit,
    action: suspend (attempt: Int) -> T
): T {
    val random = Random()
    var delayMs = initialDelayMs
    for (attempt in 1..Int.MAX_VALUE) {
      try {
          return action(attempt)
      }
      catch (e: Exception) {
          var remainingMs = delayMs
          while (remainingMs > 0) {
              update(attempt, remainingMs, e)
              val next = min(remainingMs, TimeUnit.SECONDS.toMillis(1))
              remainingMs -= next
              delay(next)
          }
          delayMs = min(delayMs * backOffFactor, backOffLimitMs) + (random.nextGaussian() * delayMs * backOffJitter).toLong()
      }
    }
    error("Should never be reached")
}
