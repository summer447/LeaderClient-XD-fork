package unfair.util

import java.util.function.BooleanSupplier
import java.util.function.LongSupplier
import kotlin.math.max

class Cold {
    // Last time in milliseconds
    private var lastMs: Long

    // Current time in milliseconds
    private var time: Long = 0

    // Flag to track if the timer has been checked for completion
    private var checkedFinish = false

    /**
     * Constructs an AdvancedTimer object with a specified cooldown duration.
     * 
     * @param lasts The duration of the cooldown in milliseconds
     */
    constructor(lasts: Long) {
        this.lastMs = lasts
    }

    constructor() {
        lastMs = System.currentTimeMillis()
    }

    // Resets the timer to the current time and clears the checkedFinish flag
    fun start() {
        reset()
        checkedFinish = false
    }

    /**
     * Checks if the timer has finished for the first time.
     * 
     * @return true if the timer has finished for the first time, false otherwise
     */
    fun firstFinish(): Boolean {
        return checkAndSetFinish { System.currentTimeMillis() >= (time + lastMs) }
    }

    /**
     * Sets the cooldown time to a specified duration.
     * 
     * @param time The duration of the cooldown in milliseconds
     */
    fun setCooldown(time: Long) {
        this.lastMs = time
    }

    /**
     * Checks if the timer has finished.
     * 
     * @return true if the timer has finished, false otherwise
     */
    fun hasFinished(): Boolean {
        return isElapsed(time + lastMs) { System.currentTimeMillis() }
    }

    /**
     * Checks if the timer has finished with an additional delay.
     * 
     * @param delay The additional delay in milliseconds
     * @return true if the timer has finished with the delay, false otherwise
     */
    fun finished(delay: Long): Boolean {
        return isElapsed(time) { System.currentTimeMillis() - delay }
    }

    /**
     * Checks if a delay has been completed.
     * 
     * @param l The delay duration in milliseconds
     * @return true if the delay has been completed, false otherwise
     */
    fun isDelayComplete(l: Long): Boolean {
        return isElapsed(lastMs) { System.currentTimeMillis() - l }
    }

    /**
     * Checks if a specified time has been reached.
     * 
     * @param currentTime The specified time in milliseconds
     * @return true if the specified time has been reached, false otherwise
     */
    fun reached(currentTime: Long): Boolean {
        return isElapsed(time) { max(0L, System.currentTimeMillis() - currentTime) }
    }

    // Resets the timer to the current time
    fun reset() {
        this.time = System.currentTimeMillis()
    }

    // Gets the elapsed time since the timer was started or reset
    fun getTime(): Long {
        return max(0L, System.currentTimeMillis() - time)
    }

    fun getCum(hentai: Long): Boolean {
        return getTime() - lastMs >= hentai
    }

    /**
     * Checks if a specified time has elapsed.
     * 
     * @param owo   The specified time in milliseconds
     * @param reset Indicates whether to reset the timer after the time has elapsed
     * @return true if the specified time has elapsed, false otherwise
     */
    fun hasTimeElapsed(owo: Long, reset: Boolean): Boolean {
        if (getTime() >= owo) {
            if (reset) {
                reset()
            }
            return true
        }
        return false
    }

    // Private method to check if a specified time has elapsed
    private fun checkAndSetFinish(condition: BooleanSupplier): Boolean {
        if (condition.asBoolean && !checkedFinish) {
            checkedFinish = true
            return true
        }
        return false
    }

    // Private method to check if a specified time has elapsed
    private fun isElapsed(targetTime: Long, currentTimeSupplier: LongSupplier): Boolean {
        return currentTimeSupplier.asLong >= targetTime
    }
}