package unfair.util

import net.minecraft.client.Minecraft
import net.minecraft.potion.Potion
import net.minecraft.util.BlockPos
import net.minecraft.util.MathHelper
import unfair.Unfair
import unfair.management.RotationState
import unfair.module.modules.combat.TargetStrafe
import kotlin.math.*

object MoveUtil {
    private val mc: Minecraft = Minecraft.getMinecraft()

    @JvmStatic
    val isForwardPressed: Boolean
        get() {
            if (mc.gameSettings.keyBindForward.isKeyDown != mc.gameSettings.keyBindBack.isKeyDown) return true
            return mc.gameSettings.keyBindLeft.isKeyDown != mc.gameSettings.keyBindRight.isKeyDown
        }

    @JvmStatic
    val forwardValue: Int
        get() {
            var forwardValue = 0
            if (mc.gameSettings.keyBindForward.isKeyDown) {
                ++forwardValue
            }
            if (mc.gameSettings.keyBindBack.isKeyDown) {
                --forwardValue
            }
            return forwardValue
        }

    @JvmStatic
    val leftValue: Int
        get() {
            var leftValue = 0
            if (mc.gameSettings.keyBindLeft.isKeyDown) {
                ++leftValue
            }
            if (mc.gameSettings.keyBindRight.isKeyDown) {
                --leftValue
            }
            return leftValue
        }

    @JvmStatic
    val moveYaw: Float
        get() = adjustYaw(
            if (RotationState.isActived()) RotationState.getSmoothedYaw() else mc.thePlayer.rotationYaw,
            mc.thePlayer.movementInput.moveForward,
            mc.thePlayer.movementInput.moveStrafe
        )

    @JvmStatic
    fun adjustYaw(yaw: Float, forward: Float, strafe: Float): Float {
        var yaw = yaw
        val targetStrafe = Unfair.moduleManager!!.modules.get(TargetStrafe::class.java) as TargetStrafe
        if (targetStrafe.isEnabled()) {
            if (!java.lang.Float.isNaN(targetStrafe.targetYaw)) {
                return targetStrafe.targetYaw
            }
        }
        if (forward < 0.0f) {
            yaw += 180.0f
        }
        if (strafe != 0.0f) {
            val multiplier = if (forward == 0.0f) 1.0f else 0.5f * sign(forward)
            yaw += -90.0f * multiplier * sign(strafe)
        }
        return MathHelper.wrapAngleTo180_float(yaw)
    }

    @JvmStatic
    val directionYaw: Float
        get() {
            if (speed == 0.0) {
                return MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw)
            }
            return MathHelper.wrapAngleTo180_float(
                Math.toDegrees(
                    atan2(
                        mc.thePlayer.motionZ,
                        mc.thePlayer.motionX
                    )
                ).toFloat() - 90.0f
            )
        }

    @JvmStatic
    val baseMoveSpeed: Double
        get() {
            var baseSpeed = 0.28015
            if (speedTime > 0) {
                baseSpeed = 0.28015 * (1.0 + 0.15 * speedLevel.toDouble())
            }
            return baseSpeed
        }

    fun getBaseJumpHigh(speedLevel: Int): Double {
        var jumpHeight = 0.452
        if (speedLevel == 1) {
            jumpHeight = 0.49720000000000003
        } else if (speedLevel >= 2) {
            jumpHeight *= 1.2
        }
        return jumpHeight
    }

    @JvmStatic
    val jumpMotion: Double
        get() {
            var speedLevel = 0
            if (speedTime > 0) {
                speedLevel = MoveUtil.speedLevel
            }
            return getBaseJumpHigh(speedLevel)
        }

    @JvmStatic
    var speed: Double
        get() = getSpeed(mc.thePlayer.motionX, mc.thePlayer.motionZ)
        set(speed) {
            setSpeed(speed, directionYaw)
        }

    fun getSpeed(motionX: Double, motionZ: Double): Double {
        return hypot(motionX, motionZ)
    }

    @JvmStatic
    fun setSpeed(speed: Double, yaw: Float) {
        mc.thePlayer.motionX = -sin(Math.toRadians(yaw.toDouble())) * speed
        mc.thePlayer.motionZ = cos(Math.toRadians(yaw.toDouble())) * speed
    }

    @JvmStatic
    fun addSpeed(speed: Double, yaw: Float) {
        mc.thePlayer.motionX += -sin(Math.toRadians(yaw.toDouble())) * speed
        mc.thePlayer.motionZ += cos(Math.toRadians(yaw.toDouble())) * speed
    }

    @JvmStatic
    val speedLevel: Int
        get() {
            var speedLevel = 0
            if (mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
                speedLevel = (mc.thePlayer.getActivePotionEffect(Potion.moveSpeed)
                    .amplifier + 1)
            }
            return speedLevel
        }

    val speedTime: Int
        get() {
            if (mc.thePlayer.isPotionActive(Potion.moveSpeed)) {
                return mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).duration
            }
            return 0
        }

    val allowedHorizontalDistance: Float
        get() {
            val slipperiness = mc.thePlayer.worldObj.getBlockState(
                BlockPos(
                    MathHelper.floor_double(mc.thePlayer.posX),
                    MathHelper.floor_double(mc.thePlayer.entityBoundingBox.minY) - 1,
                    MathHelper.floor_double(mc.thePlayer.posZ)
                )
            ).block.slipperiness * 0.91f
            return mc.thePlayer.aiMoveSpeed * (0.16277136f / (slipperiness * slipperiness * slipperiness))
        }

    @JvmStatic
    fun predictMovement(): DoubleArray {
        var strafeInput = leftValue.toFloat() * 0.98f
        var forwardInput = forwardValue.toFloat() * 0.98f
        var inputMagnitude = strafeInput * strafeInput + forwardInput * forwardInput
        if (inputMagnitude >= 1.0E-4f) {
            inputMagnitude = MathHelper.sqrt_float(inputMagnitude)
            if (inputMagnitude < 1.0f) {
                inputMagnitude = 1.0f
            }
            inputMagnitude = allowedHorizontalDistance / inputMagnitude
            val sinYaw = MathHelper.sin(mc.thePlayer.rotationYaw * Math.PI.toFloat() / 180.0f)
            val cosYaw = MathHelper.cos(mc.thePlayer.rotationYaw * Math.PI.toFloat() / 180.0f)
            strafeInput *= inputMagnitude
            forwardInput *= inputMagnitude
            return doubleArrayOf(
                (strafeInput * cosYaw - forwardInput * sinYaw).toDouble(),
                (forwardInput * cosYaw + strafeInput * sinYaw).toDouble()
            )
        }
        return doubleArrayOf(0.0, 0.0)
    }

    @JvmStatic
    fun fixStrafe(targetYaw: Float) {
        val angle = MathHelper.wrapAngleTo180_float(
            adjustYaw(
                mc.thePlayer.rotationYaw,
                forwardValue.toFloat(),
                leftValue.toFloat()
            ) - targetYaw + 22.5f
        )
        when ((angle + 180.0f).toInt() / 45 % 8) {
            0 -> {
                mc.thePlayer.movementInput.moveForward = -1.0f
                mc.thePlayer.movementInput.moveStrafe = 0.0f
            }

            1 -> {
                mc.thePlayer.movementInput.moveForward = -1.0f
                mc.thePlayer.movementInput.moveStrafe = 1.0f
            }

            2 -> {
                mc.thePlayer.movementInput.moveForward = 0.0f
                mc.thePlayer.movementInput.moveStrafe = 1.0f
            }

            3 -> {
                mc.thePlayer.movementInput.moveForward = 1.0f
                mc.thePlayer.movementInput.moveStrafe = 1.0f
            }

            4 -> {
                mc.thePlayer.movementInput.moveForward = 1.0f
                mc.thePlayer.movementInput.moveStrafe = 0.0f
            }

            5 -> {
                mc.thePlayer.movementInput.moveForward = 1.0f
                mc.thePlayer.movementInput.moveStrafe = -1.0f
            }

            6 -> {
                mc.thePlayer.movementInput.moveForward = 0.0f
                mc.thePlayer.movementInput.moveStrafe = -1.0f
            }

            7 -> {
                mc.thePlayer.movementInput.moveForward = -1.0f
                mc.thePlayer.movementInput.moveStrafe = -1.0f
            }
        }
        if (mc.thePlayer.movementInput.sneak) {
            mc.thePlayer.movementInput.moveForward *= 0.3f
            mc.thePlayer.movementInput.moveStrafe *= 0.3f
        }
    }
}
