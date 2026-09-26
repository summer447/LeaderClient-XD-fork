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

import unfair.util.animations.advanced.impl.SmoothStepAnimation


class ContinualAnimation {
    private var output = 0f
    private var endpoint = 0f


    private var animation: Animation = SmoothStepAnimation(0, 0.0, Direction.BACKWARDS)

    fun animate(destination: Float, ms: Int) {
        output = (endpoint - animation.output).toFloat()
        endpoint = destination
        if (output != (endpoint - destination)) {
            animation = SmoothStepAnimation(ms, (endpoint - output).toDouble(), Direction.BACKWARDS)
        }
    }


    val isDone: Boolean
        get() = output == endpoint || animation.isDone

    fun getOutput(): Float {
        output = (endpoint - animation.output).toFloat()
        return output
    }
}
