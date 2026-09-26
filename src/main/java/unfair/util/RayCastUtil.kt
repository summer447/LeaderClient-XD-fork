package unfair.util

import com.google.common.base.Predicate
import net.minecraft.client.Minecraft
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.*
import net.minecraft.util.MovingObjectPosition.MovingObjectType
import unfair.util.RotationUtil.RotationVec
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

object RayCastUtil {
    private val mc: Minecraft = Minecraft.getMinecraft()

    private fun wrapAngle(angle: Float): Float {
        var angle = angle
        angle %= 360.0f
        if (angle >= 180.0f) {
            angle -= 360.0f
        }

        if (angle < -180.0f) {
            angle += 360.0f
        }

        return angle
    }

    private fun wrappedDifference(angle1: Float, angle2: Float): Float {
        return abs(wrapAngle(angle1 - angle2))
    }

    private fun getVectorForRotation(pitch: Float, yaw: Float): Vec3 {
        val f = MathHelper.cos(-yaw * 0.017453292f - 3.1415927f)
        val f1 = MathHelper.sin(-yaw * 0.017453292f - 3.1415927f)
        val f2 = -MathHelper.cos(-pitch * 0.017453292f)
        val f3 = MathHelper.sin(-pitch * 0.017453292f)
        return Vec3((f1 * f2).toDouble(), f3.toDouble(), (f * f2).toDouble())
    }

    @JvmStatic
    fun rayCast(rotation: RotationVec, distance: Double): RayCastResult? {
        return rayCast(rotation, distance, 0.0f)
    }

    fun inView(entity: Entity): Boolean {
        val rotation = calculateRotationToEntity(entity)
        val renderDistance = 16 * mc.gameSettings.renderDistanceChunks
        if (!(entity.getDistanceToEntity(mc.thePlayer).toDouble() > 100.0) && entity is EntityPlayer) {
            val boundingBox = entity.getEntityBoundingBox()
            val var10000 = boundingBox.maxX - boundingBox.minX

            var yOffset = 1.0
            while (yOffset >= -1.0) {
                var xOffset = 1.0
                while (xOffset >= -1.0) {
                    var zOffset = 1.0
                    while (zOffset >= -1.0) {
                        val scanX = entity.posX + (boundingBox.maxX - boundingBox.minX) * xOffset
                        val scanY = entity.posY + (boundingBox.maxY - boundingBox.minY) * yOffset
                        val scanZ = entity.posZ + (boundingBox.maxZ - boundingBox.minZ) * zOffset
                        val scanRotation = calculateRotationTo(scanX, scanY, scanZ)
                        val result = rayCast(scanRotation, renderDistance.toDouble(), 0.2f)
                        if (result != null && result.typeOfHit == RayCastResult.Type.ENTITY) {
                            return true
                        }
                        --zOffset
                    }
                    --xOffset
                }
                yOffset -= 0.5
            }

            return false
        } else {
            val result = rayCast(rotation, renderDistance.toDouble(), 0.3f)
            return result != null && result.typeOfHit == RayCastResult.Type.ENTITY
        }
    }

    fun rayCast(rotation: RotationVec, distance: Double, expandSize: Float): RayCastResult? {
        return rayCast(rotation, distance, expandSize, mc.thePlayer)
    }

    fun rayCast(rotation: RotationVec, distance: Double, expandSize: Float, sourceEntity: Entity?): RayCastResult? {
        if (sourceEntity != null && mc.theWorld != null) {
            val partialTicks = 1.0f
            val blockHit = rayTraceCustom(sourceEntity, rotation.x, rotation.y, distance)
            var maxDistance = distance
            val eyePos = sourceEntity.getPositionEyes(partialTicks)
            if (blockHit != null && blockHit.typeOfHit == MovingObjectType.BLOCK) {
                maxDistance = blockHit.hitVec.distanceTo(eyePos)
            }

            val lookVec = getVectorForRotation(rotation.y, rotation.x)
            val endPos =
                eyePos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance)
            var hitEntity: Entity? = null
            var hitVec: Vec3? = null
            val predicate = Predicate { obj: Entity? -> obj!!.canBeCollidedWith() }
            val entities = mc.theWorld.getEntitiesInAABBexcluding(
                sourceEntity,
                sourceEntity.getEntityBoundingBox()
                    .addCoord(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance)
                    .expand(1.0, 1.0, 1.0),
                predicate
            )
            var currentDistance = maxDistance
            val var18: MutableIterator<*> = entities.iterator()

            while (true) {
                if (!var18.hasNext()) {
                    if (hitEntity != null && (currentDistance < maxDistance || blockHit == null)) {
                        return RayCastResult(hitEntity, hitVec)
                    }

                    if (blockHit != null) {
                        return RayCastResult(blockHit.hitVec, blockHit.sideHit, blockHit.getBlockPos())
                    }
                    break
                }

                val entity = var18.next() as Entity
                val entityExpand = entity.getCollisionBorderSize() + expandSize
                val expandedBB = entity.getEntityBoundingBox()
                    .expand(entityExpand.toDouble(), entityExpand.toDouble(), entityExpand.toDouble())
                val entityHit = expandedBB.calculateIntercept(eyePos, endPos)
                if (expandedBB.isVecInside(eyePos)) {
                    if (currentDistance >= 0.0) {
                        hitEntity = entity
                        hitVec = if (entityHit == null) eyePos else entityHit.hitVec
                        currentDistance = 0.0
                    }
                } else if (entityHit != null) {
                    val distanceToHit = eyePos.distanceTo(entityHit.hitVec)
                    if (distanceToHit < currentDistance || currentDistance == 0.0) {
                        hitEntity = entity
                        hitVec = entityHit.hitVec
                        currentDistance = distanceToHit
                    }
                }
            }
        }

        return null
    }

    private fun rayTraceCustom(entity: Entity, yaw: Float, pitch: Float, distance: Double): MovingObjectPosition? {
        val eyePos = entity.getPositionEyes(1.0f)
        val lookVec = getVectorForRotation(pitch, yaw)
        val targetPos =
            eyePos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance)
        return entity.worldObj.rayTraceBlocks(eyePos, targetPos)
    }

    fun overBlock(rotation: RotationVec, side: EnumFacing?, pos: BlockPos?, checkSide: Boolean): Boolean {
        val hit = rayCast(rotation, 4.5)
        if (hit != null && hit.hitVec != null) {
            return hit.blockPos != null && hit.blockPos == pos && (!checkSide || hit.sideHit == side)
        } else {
            return false
        }
    }

    fun calculateRotationToEntity(entity: Entity): RotationVec {
        val eyePos = mc.thePlayer.getPositionEyes(1.0f)
        val entityPos = Vec3(entity.posX, entity.posY + entity.getEyeHeight().toDouble(), entity.posZ)
        val deltaX = entityPos.xCoord - eyePos.xCoord
        val deltaY = entityPos.yCoord - eyePos.yCoord
        val deltaZ = entityPos.zCoord - eyePos.zCoord
        val horizontalDist = sqrt(deltaX * deltaX + deltaZ * deltaZ)
        val yaw = (atan2(deltaZ, deltaX) * 180.0 / 3.141592653589793).toFloat() - 90.0f
        val pitch = (-(atan2(deltaY, horizontalDist) * 180.0 / 3.141592653589793)).toFloat()
        return RotationVec(yaw, pitch)
    }

    private fun calculateRotationTo(x: Double, y: Double, z: Double): RotationVec {
        val eyePos = mc.thePlayer.getPositionEyes(1.0f)
        val deltaX = x - eyePos.xCoord
        val deltaY = y - eyePos.yCoord
        val deltaZ = z - eyePos.zCoord
        val horizontalDist = sqrt(deltaX * deltaX + deltaZ * deltaZ)
        val yaw = (atan2(deltaZ, deltaX) * 180.0 / 3.141592653589793).toFloat() - 90.0f
        val pitch = (-(atan2(deltaY, horizontalDist) * 180.0 / 3.141592653589793)).toFloat()
        return RotationVec(yaw, pitch)
    }

    private fun getYawToEntity(entity: Entity): Float {
        val eyePos = mc.thePlayer.getPositionEyes(1.0f)
        val entityPos = Vec3(entity.posX, entity.posY + entity.getEyeHeight().toDouble(), entity.posZ)
        val deltaX = entityPos.xCoord - eyePos.xCoord
        val deltaZ = entityPos.zCoord - eyePos.zCoord
        return (atan2(deltaZ, deltaX) * 180.0 / 3.141592653589793).toFloat() - 90.0f
    }

    class RayCastResult {
        var typeOfHit: Type?
        var hitVec: Vec3?
        @JvmField
        var entityHit: Entity? = null
        var sideHit: EnumFacing? = null
        var blockPos: BlockPos? = null

        constructor(hitVec: Vec3?, type: Type?) {
            this.hitVec = hitVec
            this.typeOfHit = type
        }

        constructor(entity: Entity?, hitVec: Vec3?) {
            this.entityHit = entity
            this.hitVec = hitVec
            this.typeOfHit = Type.ENTITY
        }

        constructor(hitVec: Vec3?, sideHit: EnumFacing?, blockPos: BlockPos?) {
            this.hitVec = hitVec
            this.sideHit = sideHit
            this.blockPos = blockPos
            this.typeOfHit = Type.BLOCK
        }

        constructor(hitVec: Vec3?, sideHit: EnumFacing?, type: Type?) {
            this.hitVec = hitVec
            this.sideHit = sideHit
            this.typeOfHit = type
        }

        enum class Type {
            MISS,
            BLOCK,
            ENTITY
        }
    }
}
