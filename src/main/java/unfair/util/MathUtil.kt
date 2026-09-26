package unfair.util

import net.minecraft.util.Vec3
import java.math.BigDecimal
import java.math.MathContext
import java.security.SecureRandom
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

object MathUtil {
    val PI: Float = Math.PI.toFloat()
    val TO_DEGREES: Float = 180.0f / PI

    fun interpolate(partialTicks: Float, old: Double, current: Double): Double {
        return old + (current - old) * partialTicks
    }

    fun interpolateVec3(old: Vec3, current: Vec3, partialTicks: Float): Vec3 {
        return Vec3(
            old.xCoord + (current.xCoord - old.xCoord) * partialTicks,
            old.yCoord + (current.yCoord - old.yCoord) * partialTicks,
            old.zCoord + (current.zCoord - old.zCoord) * partialTicks
        )
    }

    fun interpolateDouble(old: Double, current: Double, partialTicks: Float): Double {
        return old + (current - old) * partialTicks
    }

    @JvmStatic
    fun normalize(value: Float, min: Float, max: Float): Float {
        return (value - min) / (max - min)
    }

    fun roundToHalf(d: Double): Double {
        return Math.round(d * 2) / 2.0
    }

    fun incValue(`val`: Double, inc: Double): Double {
        val one = 1.0 / inc
        return Math.round(`val` * one) / one
    }

    fun roundToDecimalPlace(value: Double, inc: Double): Double {
        val halfOfInc = inc / 2.0
        val floored = StrictMath.floor(value / inc) * inc
        if (value >= floored + halfOfInc) {
            return BigDecimal(StrictMath.ceil(value / inc) * inc, MathContext.DECIMAL64).stripTrailingZeros().toDouble()
        }
        return BigDecimal(floored, MathContext.DECIMAL64).stripTrailingZeros().toDouble()
    }

    @JvmStatic
    fun interpolate(old: Double, now: Double, partialTicks: Float): Double {
        return old + (now - old) * partialTicks
    }

    fun interpolate(oldValue: Double, newValue: Double, interpolationValue: Double): Double {
        return oldValue + (newValue - oldValue) * interpolationValue
    }

    fun interpolate(old: Float, now: Float, partialTicks: Float): Float {
        return old + (now - old) * partialTicks
    }

    @JvmStatic
    fun interpolate(end: Vec3, start: Vec3, multiple: Float): Vec3 {
        return Vec3(
            interpolate(end.xCoord, start.xCoord, multiple),
            interpolate(end.yCoord, start.yCoord, multiple),
            interpolate(end.zCoord, start.zCoord, multiple)
        )
    }

    fun clampValue(value: Float, floor: Float, cap: Float): Float {
        if (value < floor) return floor
        return min(value, cap)
    }

    fun getRandom(min: Int, max: Int): Int {
        var min = min
        var max = max
        if (min == max) return min
        if (min > max) {
            val d = min
            min = max
            max = d
        }
        return ThreadLocalRandom.current().nextInt(min, max)
    }

    fun getRandom(min: Double, max: Double): Double {
        var min = min
        var max = max
        if (min == max) return min
        if (min > max) {
            val d = min
            min = max
            max = d
        }
        return ThreadLocalRandom.current().nextDouble(min, max)
    }

    fun nextSecureFloat(origin: Double, bound: Double): Float {
        if (origin == bound) return origin.toFloat()
        val secureRandom = SecureRandom()
        val difference = (bound - origin).toFloat()
        return (origin + secureRandom.nextFloat() * difference).toFloat()
    }

    fun calculateGaussianValue(x: Float, sigma: Float): Float {
        val PI = Math.PI
        val output = 1.0 / sqrt(2.0 * PI * (sigma * sigma))
        return (output * exp(-(x * x) / (2.0 * (sigma * sigma)))).toFloat()
    }

    fun lerp(pct: Double, start: Double, end: Double): Double {
        return start + pct * (end - start)
    }

    fun lerp(min: Float, max: Float, delta: Float): Float {
        return min + (max - min) * delta
    }

    fun nextInt(min: Int, max: Int): Int {
        if (min == max || max - min <= 0.0) return min
        return (min + ((max - min) * Math.random())).toInt()
    }

    fun nextDouble(min: Double, max: Double): Double {
        if (min == max || max - min <= 0.0) return min
        return min + ((max - min) * Math.random())
    }

    fun nextFloat(startInclusive: Float, endInclusive: Float): Float {
        if (startInclusive == endInclusive || endInclusive - startInclusive <= 0f) return startInclusive
        return (startInclusive + ((endInclusive - startInclusive) * Math.random())).toFloat()
    }

    fun inBetween(min: Double, max: Double, value: Double): Boolean {
        return value >= min && value <= max
    }

    fun wrappedDifference(number1: Double, number2: Double): Double {
        return min(
            abs(number1 - number2),
            min(
                abs(number1 - 360) - abs(number2 - 0),
                abs(number2 - 360) - abs(number1 - 0)
            )
        )
    }
}