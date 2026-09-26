package unfair.util

class TimerUtil {
    private var lastMS = 0L

    fun reset() {
        this.lastMS = System.currentTimeMillis()
    }

    val elapsedTime: Long
        get() = System.currentTimeMillis() - this.lastMS

    fun hasTimeElapsed(ms: Long): Boolean {
        return this.elapsedTime >= ms
    }

    fun setTime() {
        this.lastMS = 0L
    }
}
