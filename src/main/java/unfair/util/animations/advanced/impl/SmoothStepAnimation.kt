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
package unfair.util.animations.advanced.impl

import unfair.util.animations.advanced.Animation
import unfair.util.animations.advanced.Direction
import kotlin.math.pow

class SmoothStepAnimation : Animation {
    constructor(ms: Int, endPoint: Double) : super(ms, endPoint)

    constructor(ms: Int, endPoint: Double, direction: Direction) : super(ms, endPoint, direction)

    override fun getEquation(x: Double): Double {
        return -2 * x.pow(3.0) + (3 * x.pow(2.0))
    }
}
