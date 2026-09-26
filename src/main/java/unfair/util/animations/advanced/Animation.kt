/*
 * MoonLight Hacked Client
 *
 * A free and open-source hacked client for Minecraft.
 * Developed using Minecraft's resources.
 *
 * Repository: https://github.com/randomguy3725/MoonLight
 *
 * Author(s): [Randumbguy & wxdbie & opZywl & MukjepScarlet & lucas & eonian]
 */
package unfair.util.animations.advanced

import unfair.util.TimerUtil
import kotlin.math.max
import kotlin.math.min

abstract class Animation
@JvmOverloads constructor(
    protected var duration: Int,
    protected var endPoint: Double,
    var direction: Direction = Direction.FORWARDS
) {
    var timerUtil: TimerUtil = TimerUtil()

    fun reset() {
        timerUtil.reset()
    }

    val isDone: Boolean
        get() = timerUtil.hasTimeElapsed(duration.toLong())


    fun setDirection(direction: Direction): Animation {
        if (this.direction != direction) {
            this.direction = direction
            timerUtil.setTime()
        }
        return this
    }

    protected fun correctOutput(): Boolean {
        return false
    }

    val output: Double
        get() {
            if (direction.forwards()) {
                if (this.isDone) {
                    return endPoint
                }

                return getEquation(timerUtil.elapsedTime / duration.toDouble()) * endPoint
            } else {
                if (this.isDone) {
                    return 0.0
                }

                if (correctOutput()) {
                    val revTime =
                        min(duration.toLong(), max(0, duration - timerUtil.elapsedTime))
                            .toDouble()
                    return getEquation(revTime / duration) * endPoint
                }

                return (1 - getEquation(timerUtil.elapsedTime / duration.toDouble())) * endPoint
            }
        }


    //This is where the animation equation should go, for example, a logistic function. Output should range from 0 - 1.
    //This will take the timer's time as an input, x.
    protected abstract fun getEquation(x: Double): Double
}