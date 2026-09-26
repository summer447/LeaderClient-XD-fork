package unfair.util

import kotlin.math.pow

class DecelerateAnimation : Animation {
    constructor(ms: Int, endPoint: Double) : super(ms, endPoint)

    constructor(ms: Int, endPoint: Double, direction: Direction?) : super(ms, endPoint, direction)

    override fun getOutput(): Double {
        if (direction == Direction.FORWARDS) {
            if (isDone) return endPoint
            return (endPoint * (1 - (1 - linearOutput).pow(2.0)))
        } else {
            if (isDone) return 0.0
            return (endPoint * (1 - linearOutput).pow(2.0))
        }
    }
}