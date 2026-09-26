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

enum class Direction {
    FORWARDS,
    BACKWARDS;

    fun opposite(): Direction {
        if (this == FORWARDS) {
            return BACKWARDS
        } else return FORWARDS
    }


    fun forwards(): Boolean {
        return this == FORWARDS
    }

    fun backwards(): Boolean {
        return this == BACKWARDS
    }
}
