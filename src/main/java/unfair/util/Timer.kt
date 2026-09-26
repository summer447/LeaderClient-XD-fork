package unfair.util

import kotlin.math.pow

class Timer(var updates: Float) {
    @JvmField
    var last: Long = 0
    var cached: Float = 0f

    @Suppress("DuplicatedCode")
    fun getValueFloat(begin: Float, end: Float, type: Int): Float {
        if (this.cached == end) {
            return this.cached
        } else {
            var t = (System.currentTimeMillis() - this.last).toFloat() / this.updates
            when (type) {
                1 -> t = if (t < 0.5f) 4.0f * t * t * t else (t - 1.0f) * (2.0f * t - 2.0f) * (2.0f * t - 2.0f) + 1.0f
                2 -> t = (1.0 - (1.0f - t).toDouble().pow(5.0)).toFloat()
                3 -> t = this.bounce(t)
                4 -> t = quadInOut(t)
            }

            var value = begin + t * (end - begin)
            if ((end > begin && value > end) || (end < begin && value < end)) {
                value = end
            }

            if (value == end) {
                this.cached = value
            }

            return value
        }
    }

    fun getValueInt(begin: Int, end: Int, type: Int): Int {
        return Math.round(this.getValueFloat(begin.toFloat(), end.toFloat(), type))
    }

    fun start() {
        this.cached = 0.0f
        this.last = System.currentTimeMillis()
    }

    private fun bounce(t: Float): Float {
        var t = t
        val i: Float
        val i2 = 7.5625
        val i3 = 2.75
        if (t.toDouble() < 1.0 / i3) {
            i = (i2 * t.toDouble() * t.toDouble()).toFloat()
        } else if (t.toDouble() < 2.0 / i3) {
            i = (i2 * ((t.toDouble() - 1.5 / i3).toFloat().also { t = it }).toDouble() * t.toDouble() + 0.75).toFloat()
        } else if (t.toDouble() < 2.5 / i3) {
            i = (i2 * ((t.toDouble() - 2.25 / i3).toFloat()
                .also { t = it }).toDouble() * t.toDouble() + 0.9375).toFloat()
        } else {
            i = (i2 * ((t.toDouble() - 2.625 / i3).toFloat()
                .also { t = it }).toDouble() * t.toDouble() + 0.984375).toFloat()
        }

        return i
    }

    fun quadInOut(t: Float): Float {
        if (t < 0.5f) {
            return 2 * t * t
        } else {
            return -1 + (4 - 2 * t) * t
        }
    }
}
