package unfair.util

import net.minecraft.block.*
import net.minecraft.block.material.Material
import net.minecraft.client.Minecraft
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Vec3
import unfair.util.RandomUtil.nextDouble
import unfair.util.RotationUtil.rayTrace
import kotlin.math.max
import kotlin.math.min

object BlockUtil {
    private val mc: Minecraft = Minecraft.getMinecraft()

    @JvmStatic
    fun isReplaceable(blockPos: BlockPos?): Boolean {
        return isReplaceable(mc.theWorld.getBlockState(blockPos).block)
    }

    @JvmStatic
    fun isReplaceable(block: Block): Boolean {
        if (!block.material.isReplaceable) return false
        if (block !is BlockSnow) return true
        return !(block.blockBoundsMaxY > 0.125)
    }

    @JvmStatic
    fun isInteractable(blockPos: BlockPos?): Boolean {
        return isInteractable(mc.theWorld.getBlockState(blockPos).block)
    }

    @JvmStatic
    fun isInteractable(block: Block?): Boolean {
        if (block is BlockContainer) return true
        if (block is BlockWorkbench) return true
        if (block is BlockAnvil) return true
        if (block is BlockBed) return true
        if (block is BlockDoor) {
            if (block.material !== Material.iron) return true
        }
        if (block is BlockTrapDoor) return true
        if (block is BlockFenceGate) return true
        if (block is BlockFence) return true
        if (block is BlockButton) return true
        if (block is BlockLever) return true
        return block is BlockJukebox
    }

    @JvmStatic
    fun isSolid(block: Block?): Boolean {
        if (block is BlockStairs) return false
        if (block is BlockSlab) return false
        if (block is BlockEndPortalFrame) return false
        if (block is BlockEndPortal) return false
        if (block is BlockVine) return false
        if (block is BlockPumpkin) return false
        if (block is BlockCactus) return false
        if (block is BlockBush) return false
        if (block is BlockFalling) return false
        if (block is BlockWeb) return false
        if (block is BlockPane) return false
        if (block is BlockCarpet) return false
        if (block is BlockSnow) return false
        if (block is BlockFence) return false
        if (block is BlockFenceGate) return false
        if (block is BlockWall) return false
        if (block is BlockLadder) return false
        if (block is BlockTorch) return false
        if (block is BlockRedstoneWire) return false
        if (block is BlockRedstoneDiode) return false
        if (block is BlockBasePressurePlate) return false
        if (block is BlockTripWire) return false
        if (block is BlockTripWireHook) return false
        if (block is BlockRailBase) return false
        if (block is BlockSlime) return false
        return block !is BlockTNT
    }

    @JvmStatic
    fun getHitVec(blockPos: BlockPos, enumFacing: EnumFacing, yaw: Float, pitch: Float): Vec3? {
        val movingObjectPosition = rayTrace(yaw, pitch, mc.playerController.blockReachDistance.toDouble(), 1.0f)
        if (movingObjectPosition != null) {
            if (movingObjectPosition.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                if (movingObjectPosition.blockPos == blockPos) {
                    if (movingObjectPosition.sideHit == enumFacing) {
                        return movingObjectPosition.hitVec
                    }
                }
            }
        }
        return getClickVec(blockPos, enumFacing)
    }

    fun getClickVec(blockPos: BlockPos, enumFacing: EnumFacing): Vec3 {
        val block = mc.theWorld.getBlockState(blockPos).block
        val vec3 = Vec3(
            blockPos.x.toDouble() + min(
                max(nextDouble(0.0, 1.0), block.blockBoundsMinX),
                block.blockBoundsMaxX
            ),
            blockPos.y.toDouble() + min(
                max(nextDouble(0.0, 1.0), block.blockBoundsMinY),
                block.blockBoundsMaxY
            ),
            blockPos.z.toDouble() + min(
                max(nextDouble(0.0, 1.0), block.blockBoundsMinZ),
                block.blockBoundsMaxZ
            )
        )
        when (enumFacing) {
            EnumFacing.UP -> {
                return Vec3(vec3.xCoord, blockPos.y.toDouble() + block.blockBoundsMaxY, vec3.zCoord)
            }

            EnumFacing.NORTH -> {
                return Vec3(vec3.xCoord, vec3.yCoord, blockPos.z.toDouble() + block.blockBoundsMinZ)
            }

            EnumFacing.EAST -> {
                return Vec3(blockPos.x.toDouble() + block.blockBoundsMaxX, vec3.yCoord, vec3.zCoord)
            }

            EnumFacing.SOUTH -> {
                return Vec3(vec3.xCoord, vec3.yCoord, blockPos.z.toDouble() + block.blockBoundsMaxZ)
            }

            EnumFacing.WEST -> {}
            else -> {
                return Vec3(vec3.xCoord, blockPos.y.toDouble() + block.blockBoundsMinY, vec3.zCoord)
            }
        }
        return Vec3(blockPos.x.toDouble() + block.blockBoundsMinX, vec3.yCoord, vec3.zCoord)
    }
}
