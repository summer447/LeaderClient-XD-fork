package unfair.util

import java.util.*

object RandomUtil {
    private val theRandom = Random()

    @JvmStatic
    fun nextLong(min: Long, max: Long): Long {
        return nextDouble(min.toDouble(), (max + 1L).toDouble()).toLong()
    }

    @JvmStatic
    fun nextFloat(min: Float, max: Float): Float {
        return theRandom.nextFloat() * (max - min) + min
    }

    @JvmStatic
    fun nextDouble(min: Double, max: Double): Double {
        return theRandom.nextDouble() * (max - min) + min
    }

    @JvmStatic
    fun nextInt(min: Int, max: Int): Int {
        return theRandom.nextInt(max - min) + min
    }
}
