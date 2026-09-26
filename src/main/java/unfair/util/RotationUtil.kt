package unfair.util

import net.minecraft.client.Minecraft
import net.minecraft.entity.Entity
import net.minecraft.util.*
import unfair.mixin.IAccessorEntity
import kotlin.math.*

object RotationUtil {
    private val mc: Minecraft = Minecraft.getMinecraft()

    @JvmStatic
    fun wrapAngleDiff(angle: Float, target: Float): Float {
        return target + MathHelper.wrapAngleTo180_float(angle - target)
    }

    @JvmStatic
    fun hasVisiblePoint(boundingBox: AxisAlignedBB): Boolean {
        val eyePos = mc.thePlayer.getPositionEyes(1.0f)
        val centerX = (boundingBox.minX + boundingBox.maxX) / 2.0
        val centerZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0
        val height = boundingBox.maxY - boundingBox.minY
        val yRatios = doubleArrayOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9)

        for (ratio in yRatios) {
            val targetY = boundingBox.minY + ratio * height
            val targetPoint = Vec3(centerX, targetY, centerZ)
            if (mc.theWorld.rayTraceBlocks(eyePos, targetPoint) == null) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun clampAngle(angle: Float, maxAngle: Float): Float {
        var angle = angle
        var maxAngle = maxAngle
        maxAngle = max(0.0f, min(180.0f, maxAngle))
        if (angle > maxAngle) {
            angle = maxAngle
        } else if (angle < -maxAngle) {
            angle = -maxAngle
        }
        return angle
    }

    fun smoothAngle(angle: Float, smoothFactor: Float): Float {
        return angle * (0.5f + 0.5f * (1.0f - max(0.0f, min(1.0f, smoothFactor + RandomUtil.nextFloat(-0.1f, 0.1f)))))
    }

    @JvmStatic
    fun quantizeAngle(angle: Float): Float {
        return (angle.toDouble() - angle.toDouble() % 0.0096).toFloat()
    }

    @JvmStatic
    fun getRotationsTo(
        targetX: Double,
        targetY: Double,
        targetZ: Double,
        currentYaw: Float,
        currentPitch: Float
    ): FloatArray {
        return getRotations(targetX, targetY, targetZ, currentYaw, currentPitch, 180.0f, 0.0f)
    }

    @JvmStatic
    fun getRotationsToBox(
        boundingBox: AxisAlignedBB,
        yaw: Float,
        pitch: Float,
        maxAngle: Float,
        smoothFactor: Float
    ): FloatArray {
        val eyePos = mc.thePlayer.getPositionEyes(1.0f)
        val centerX = (boundingBox.minX + boundingBox.maxX) / 2.0
        val centerZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0
        val height = boundingBox.maxY - boundingBox.minY
        val yRatios = doubleArrayOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9)
        var bestPoint: Vec3? = null
        var bestDistSq = Double.Companion.MAX_VALUE

        for (ratio in yRatios) {
            val targetY = boundingBox.minY + ratio * height
            val targetPoint = Vec3(centerX, targetY, centerZ)
            if (mc.theWorld.rayTraceBlocks(eyePos, targetPoint) == null) {
                val dx = centerX - eyePos.xCoord
                val dy = targetY - eyePos.yCoord
                val dz = centerZ - eyePos.zCoord
                val distSq = dx * dx + dy * dy + dz * dz
                if (distSq < bestDistSq) {
                    bestDistSq = distSq
                    bestPoint = targetPoint
                }
            }
        }

        if (bestPoint != null) {
            val deltaX = bestPoint.xCoord - eyePos.xCoord
            val deltaY = bestPoint.yCoord - eyePos.yCoord
            val deltaZ = bestPoint.zCoord - eyePos.zCoord
            return getRotations(deltaX, deltaY, deltaZ, yaw, pitch, maxAngle, smoothFactor)
        }

        val minTargetY = boundingBox.minY + 0.05 * height
        val maxTargetY = boundingBox.minY + 0.75 * height
        val deltaX = centerX - eyePos.xCoord
        val deltaY =
            if (eyePos.yCoord >= maxTargetY) maxTargetY - eyePos.yCoord else (if (eyePos.yCoord <= minTargetY) minTargetY - eyePos.yCoord else 0.0)
        val deltaZ = centerZ - eyePos.zCoord
        return getRotations(deltaX, deltaY, deltaZ, yaw, pitch, maxAngle, smoothFactor)
    }

    private fun findVisiblePointFromHead(eyePos: Vec3?, bb: AxisAlignedBB): Vec3? {
        val headX = (bb.minX + bb.maxX) / 2.0
        val headY = bb.minY + (bb.maxY - bb.minY) * 0.75
        val headZ = (bb.minZ + bb.maxZ) / 2.0
        val step = 0.2
        var xOff = 0.0
        while (xOff <= 0.5) {
            var yOff = 0.0
            while (yOff <= 0.5) {
                var zOff = 0.0
                while (zOff <= 0.5) {
                    val xOffsets = doubleArrayOf(xOff, -xOff)
                    val yOffsets = doubleArrayOf(yOff, -yOff)
                    val zOffsets = doubleArrayOf(zOff, -zOff)
                    for (x in xOffsets) {
                        for (y in yOffsets) {
                            for (z in zOffsets) {
                                val targetX = headX + x
                                val targetY = headY + y
                                val targetZ = headZ + z
                                if (targetX >= bb.minX && targetX <= bb.maxX && targetY >= bb.minY && targetY <= bb.maxY && targetZ >= bb.minZ && targetZ <= bb.maxZ) {
                                    val testPoint = Vec3(targetX, targetY, targetZ)
                                    if (isPointVisible(eyePos, testPoint)) {
                                        return testPoint
                                    }
                                }
                            }
                        }
                    }
                    zOff += step
                }
                yOff += step
            }
            xOff += step
        }
        return null
    }

    private fun isPointVisible(start: Vec3?, end: Vec3?): Boolean {
        val rayTrace = mc.theWorld.rayTraceBlocks(start, end, false, true, false)
        return rayTrace == null
    }

    fun getRotations(
        targetX: Double,
        targetY: Double,
        targetZ: Double,
        currentYaw: Float,
        currentPitch: Float,
        maxAngle: Float,
        smoothFactor: Float
    ): FloatArray {
        val horizontalDistance = sqrt(targetX * targetX + targetZ * targetZ)
        var yawDelta =
            MathHelper.wrapAngleTo180_float((atan2(targetZ, targetX) * 180.0 / Math.PI).toFloat() - 90.0f - currentYaw)
        var pitchDelta = MathHelper.wrapAngleTo180_float(
            (-atan2(
                targetY,
                horizontalDistance
            ) * 180.0 / Math.PI).toFloat() - currentPitch
        )
        yawDelta = if (abs(yawDelta) <= 1.0f) 0.0f else smoothAngle(clampAngle(yawDelta, maxAngle), smoothFactor)
        pitchDelta = if (abs(pitchDelta) <= 1.0f) 0.0f else smoothAngle(clampAngle(pitchDelta, maxAngle), smoothFactor)
        return floatArrayOf(quantizeAngle(currentYaw + yawDelta), quantizeAngle(currentPitch + pitchDelta))
    }

    fun getClosestPointOnBox(point: Vec3, bb: AxisAlignedBB): Vec3 {
        val x = MathHelper.clamp_double(point.xCoord, bb.minX, bb.maxX)
        val y = MathHelper.clamp_double(point.yCoord, bb.minY, bb.maxY)
        val z = MathHelper.clamp_double(point.zCoord, bb.minZ, bb.maxZ)
        return Vec3(x, y, z)
    }

    @JvmStatic
    fun distanceToEntity(entity: Entity): Double {
        val borderSize = entity.getCollisionBorderSize()
        val boundingBox =
            entity.getEntityBoundingBox().expand(borderSize.toDouble(), borderSize.toDouble(), borderSize.toDouble())
        return distanceToBox(boundingBox)
    }

    @JvmStatic
    fun distanceToBox(entity: Entity, point: Vec3): Double {
        val borderSize = entity.getCollisionBorderSize()
        return getDistanceToBox(
            entity.getEntityBoundingBox().expand(borderSize.toDouble(), borderSize.toDouble(), borderSize.toDouble()),
            point
        )
    }

    @JvmStatic
    fun distanceToBox(boundingBox: AxisAlignedBB): Double {
        return getDistanceToBox(boundingBox, mc.thePlayer.getPositionEyes(1.0f))
    }

    fun getDistanceToBox(bb: AxisAlignedBB, point: Vec3): Double {
        if (bb.isVecInside(point)) {
            return 0.0
        }
        val closestPoint = getClosestPointOnBox(point, bb)
        return point.distanceTo(closestPoint)
    }

    @JvmStatic
    fun angleToEntity(entity: Entity): Float {
        val eyePos = mc.thePlayer.getPositionEyes(1.0f)
        val borderSize = entity.getCollisionBorderSize()
        val boundingBox =
            entity.getEntityBoundingBox().expand(borderSize.toDouble(), borderSize.toDouble(), borderSize.toDouble())
        if (boundingBox.isVecInside(eyePos)) {
            return 0.0f
        }
        val deltaX = entity.posX - eyePos.xCoord
        val deltaZ = entity.posZ - eyePos.zCoord
        return abs(
            MathHelper.wrapAngleTo180_float(
                (atan2(
                    deltaZ,
                    deltaX
                ) * 180.0 / Math.PI).toFloat() - 90.0f - mc.thePlayer.rotationYaw
            )
        ) * 2.0f
    }

    @JvmStatic
    fun getYawBetween(x1: Double, z1: Double, x2: Double, z2: Double): Float {
        return MathHelper.wrapAngleTo180_float(
            (atan2(
                z2 - z1,
                x2 - x1
            ) * 180.0 / Math.PI).toFloat() - 90.0f - mc.thePlayer.rotationYaw
        )
    }

    @JvmStatic
    fun rayTrace(yaw: Float, pitch: Float, distance: Double, partialTicks: Float): MovingObjectPosition? {
        val eyePos = mc.thePlayer.getPositionEyes(partialTicks)
        val lookVec = (mc.thePlayer as IAccessorEntity).callGetVectorForRotation(pitch, yaw)
        val targetPos =
            eyePos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance)
        return mc.theWorld.rayTraceBlocks(eyePos, targetPos)
    }

    @JvmStatic
    fun rayTrace(entity: Entity): MovingObjectPosition? {
        val eyePos = mc.thePlayer.getPositionEyes(1.0f)
        val borderSize = entity.getCollisionBorderSize()
        val targetPos = getClosestPointOnBox(
            eyePos,
            entity.getEntityBoundingBox().expand(borderSize.toDouble(), borderSize.toDouble(), borderSize.toDouble())
        )
        return mc.theWorld.rayTraceBlocks(eyePos, targetPos)
    }

    @JvmStatic
    fun rayTrace(boundingBox: AxisAlignedBB, yaw: Float, pitch: Float, distance: Double): MovingObjectPosition? {
        val eyePos = mc.thePlayer.getPositionEyes(1.0f)
        val lookVec = (mc.thePlayer as IAccessorEntity).callGetVectorForRotation(pitch, yaw)
        val targetPos =
            eyePos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance)
        return boundingBox.calculateIntercept(eyePos, targetPos)
    }

    fun getRotations(vec: Vec3): FloatArray {
        return getRotations(vec.xCoord, vec.yCoord, vec.zCoord)
    }

    fun getRotations(blockPos: BlockPos): FloatArray {
        return getRotations(
            blockPos.getX() + 0.5,
            blockPos.getY() + 0.5,
            blockPos.getZ() + 0.5,
            mc.thePlayer.posX,
            mc.thePlayer.posY + mc.thePlayer.getEyeHeight().toDouble(),
            mc.thePlayer.posZ
        )
    }

    @JvmStatic
    fun getRotations(posX: Double, posY: Double, posZ: Double): FloatArray {
        return getRotations(
            posX,
            posY,
            posZ,
            mc.thePlayer.posX,
            mc.thePlayer.posY + mc.thePlayer.getEyeHeight().toDouble(),
            mc.thePlayer.posZ
        )
    }

    fun getRotations(
        rotX: Double,
        rotY: Double,
        rotZ: Double,
        startX: Double,
        startY: Double,
        startZ: Double
    ): FloatArray {
        val x = rotX - startX
        val y = rotY - startY
        val z = rotZ - startZ
        val dist = MathHelper.sqrt_double(x * x + z * z).toDouble()
        val yaw = (atan2(z, x) * 180.0 / Math.PI).toFloat() - 90.0f
        val pitch = (-(atan2(y, dist) * 180.0 / Math.PI)).toFloat()
        return floatArrayOf(yaw, pitch)
    }

    fun getRotations(blockPos: BlockPos, enumFacing: EnumFacing): FloatArray {
        val d = blockPos.getX().toDouble() + 0.5 - mc.thePlayer.posX + enumFacing.getFrontOffsetX().toDouble() * 0.25
        val d2 = blockPos.getZ().toDouble() + 0.5 - mc.thePlayer.posZ + enumFacing.getFrontOffsetZ().toDouble() * 0.25
        val d3 =
            mc.thePlayer.posY + mc.thePlayer.getEyeHeight().toDouble() - blockPos.getY() - enumFacing.getFrontOffsetY()
                .toDouble() * 0.25
        val d4 = MathHelper.sqrt_double(d * d + d2 * d2).toDouble()
        val f = (atan2(d2, d) * 180.0 / Math.PI).toFloat() - 90.0f
        val f2 = (atan2(d3, d4) * 180.0 / Math.PI).toFloat()
        return floatArrayOf(MathHelper.wrapAngleTo180_float(f), f2)
    }

    class RotationVec(@JvmField var x: Float, @JvmField var y: Float) {
        fun add(x: Float, y: Float): RotationVec {
            return RotationVec(this.x + x, this.y + y)
        }
    }
}