package unfair.util

import net.minecraft.block.BlockAir
import net.minecraft.client.Minecraft
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.EnumCreatureAttribute
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.potion.Potion
import net.minecraft.stats.AchievementList
import net.minecraft.stats.StatList
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.DamageSource
import net.minecraft.util.MathHelper
import net.minecraftforge.common.ForgeHooks
import unfair.Unfair
import unfair.module.modules.movement.KeepSprint
import unfair.util.RotationUtil.distanceToEntity
import kotlin.math.pow

object PlayerUtil {
    private val mc: Minecraft = Minecraft.getMinecraft()

    @JvmStatic
    val isJumping: Boolean
        get() = mc.currentScreen == null && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.keyCode)

    @JvmStatic
    val isSneaking: Boolean
        get() = mc.currentScreen == null && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.keyCode)

    @JvmStatic
    val isMovingLeft: Boolean
        get() = mc.currentScreen == null && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindLeft.keyCode)

    @JvmStatic
    val isMovingRight: Boolean
        get() = mc.currentScreen == null && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindRight.keyCode)

    @JvmStatic
    val isAttacking: Boolean
        get() = mc.currentScreen == null && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.keyCode)

    @JvmStatic
    val isUsingItem: Boolean
        get() = mc.currentScreen == null && KeyBindUtil.isKeyDown(mc.gameSettings.keyBindUseItem.keyCode)

    @JvmStatic
    fun canFly(fallThreshold: Float): Boolean {
        if (!mc.thePlayer.capabilities.allowFlying && !mc.thePlayer.capabilities.disableDamage) {
            val jumpEffect = mc.thePlayer.getActivePotionEffect(Potion.jump)
            val jumpBoost = if (jumpEffect != null) (jumpEffect.amplifier + 1).toFloat() else 0.0f
            var fallDistance = mc.thePlayer.fallDistance
            if (mc.thePlayer.motionY < -0.67 || !isAirBelow) {
                fallDistance -= mc.thePlayer.motionY.toFloat()
            }
            return MathHelper.ceiling_float_int(fallDistance - fallThreshold - jumpBoost) > 0
        } else {
            return false
        }
    }

    @JvmStatic
    fun canFly(checkHeight: Int): Boolean {
        if (!mc.thePlayer.capabilities.allowFlying && !mc.thePlayer.capabilities.disableDamage) {
            val playerY = MathHelper.floor_double(mc.thePlayer.posY)
            for (offset in 0..checkHeight) {
                val currentY = playerY - offset
                if (currentY < 0) {
                    break
                }
                val block =
                    mc.theWorld.getBlockState(BlockPos(mc.thePlayer.posX, currentY.toDouble(), mc.thePlayer.posZ))
                        .block
                if (block !is BlockAir) {
                    return false
                }
            }
            return true
        } else {
            return false
        }
    }

    @JvmStatic
    val isInWater: Boolean
        get() = checkInWater(mc.thePlayer.entityBoundingBox.expand(-1.0E-6, 0.0, -1.0E-6))

    @JvmStatic
    fun checkInWater(boundingBox: AxisAlignedBB): Boolean {
        if (!mc.thePlayer.isInWater && !mc.thePlayer.isInLava) {
            val minY = MathHelper.floor_double(boundingBox.minY)
            if (minY < 0) {
                return true
            } else {
                val minX = MathHelper.floor_double(boundingBox.minX)
                val maxX = MathHelper.floor_double(boundingBox.maxX + 1.0)
                val minZ = MathHelper.floor_double(boundingBox.minZ)
                val maxZ = MathHelper.floor_double(boundingBox.maxZ + 1.0)
                for (x in minX..<maxX) {
                    for (z in minZ..<maxZ) {
                        for (y in minY downTo 0) {
                            if (!BlockUtil.isReplaceable(BlockPos(x, y, z))) {
                                return false
                            }
                        }
                    }
                }
                return true
            }
        } else {
            return false
        }
    }

    @JvmStatic
    fun canMove(x: Double, z: Double): Boolean {
        return canMove(x, z, -1.0)
    }

    @JvmStatic
    fun canMove(x: Double, z: Double, y: Double): Boolean {
        val boundingBox = mc.thePlayer.entityBoundingBox.offset(x, y, z)
        return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, boundingBox).isEmpty()
    }

    @JvmStatic
    val isAirBelow: Boolean
        get() {
            val axisAlignedBB = mc.thePlayer.entityBoundingBox.offset(0.0, -1.0, 0.0)
            return !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, axisAlignedBB).isEmpty()
        }

    @JvmStatic
    val isAirAbove: Boolean
        get() {
            val axisAlignedBB = mc.thePlayer.entityBoundingBox.offset(0.0, 1.0, 0.0)
            return !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, axisAlignedBB).isEmpty()
        }

    @JvmStatic
    fun canReach(blockPos: BlockPos, reach: Double): Boolean {
        return isBlockWithinReach(
            blockPos,
            mc.thePlayer.posX,
            mc.thePlayer.posY + mc.thePlayer.getEyeHeight().toDouble(),
            mc.thePlayer.posZ,
            reach
        )
    }

    @JvmStatic
    fun isBlockWithinReach(blockPos: BlockPos, x: Double, y: Double, z: Double, reach: Double): Boolean {
        return blockPos.distanceSqToCenter(x, y, z) < reach.pow(2.0)
    }

    @JvmStatic
    fun attackEntity(target: Entity) {
        if (ForgeHooks.onPlayerAttackTarget(mc.thePlayer, target)) {
            if (target.canAttackWithItem() && !target.hitByEntity(mc.thePlayer)) {
                var baseDamage =
                    mc.thePlayer.getEntityAttribute(SharedMonsterAttributes.attackDamage).attributeValue.toFloat()
                val enchantmentBonus = EnchantmentHelper.getModifierForCreature(
                    mc.thePlayer.heldItem,
                    if (target is EntityLivingBase) target.creatureAttribute else EnumCreatureAttribute.UNDEFINED
                )
                var knockbackLevel = EnchantmentHelper.getKnockbackModifier(mc.thePlayer)
                if (mc.thePlayer.isSprinting) {
                    ++knockbackLevel
                }
                if (baseDamage > 0.0f || enchantmentBonus > 0.0f) {
                    val isCritical =
                        mc.thePlayer.fallDistance > 0.0f && !mc.thePlayer.onGround && !mc.thePlayer.isOnLadder && !mc.thePlayer.isInWater && !mc.thePlayer.isPotionActive(
                            Potion.blindness
                        ) && mc.thePlayer.ridingEntity == null
                    if (isCritical && baseDamage > 0.0f) {
                        baseDamage *= 1.5f
                    }
                    baseDamage += enchantmentBonus
                    var isFireAspectApplied = false
                    val fireAspectLevel = EnchantmentHelper.getFireAspectModifier(mc.thePlayer)
                    if (target is EntityLivingBase && fireAspectLevel > 0 && !target.isBurning) {
                        isFireAspectApplied = true
                        target.setFire(1)
                    }
                    val originalMotionX = target.motionX
                    val originalMotionY = target.motionY
                    val originalMotionZ = target.motionZ
                    if (target.attackEntityFrom(DamageSource.causePlayerDamage(mc.thePlayer), baseDamage)) {
                        if (knockbackLevel > 0) {
                            target.addVelocity(
                                (-MathHelper.sin(mc.thePlayer.rotationYaw * Math.PI.toFloat() / 180.0f) * knockbackLevel.toFloat() * 0.5f).toDouble(),
                                0.1,
                                (MathHelper.cos(mc.thePlayer.rotationYaw * Math.PI.toFloat() / 180.0f) * knockbackLevel.toFloat() * 0.5f).toDouble()
                            )
                            val keepSprint = Unfair.moduleManager!!.modules.get(KeepSprint::class.java) as KeepSprint
                            if (keepSprint.isEnabled()
                                && (!keepSprint.groundOnly.value || mc.thePlayer.onGround)
                                && (!keepSprint.reachOnly.value || !(distanceToEntity(target) <= 3.0))
                            ) {
                                mc.thePlayer.motionX *= 0.6 + 0.4 * (1.0 - keepSprint.slowdown.value
                                    .toDouble() / 100.0)
                                mc.thePlayer.motionZ *= 0.6 + 0.4 * (1.0 - keepSprint.slowdown.value
                                    .toDouble() / 100.0)
                            } else {
                                mc.thePlayer.motionX *= 0.6
                                mc.thePlayer.motionZ *= 0.6
                                mc.thePlayer.isSprinting = false
                            }
                        }
                        if (target is EntityPlayerMP && target.velocityChanged) {
                            target.playerNetServerHandler.sendPacket(S12PacketEntityVelocity(target))
                            target.velocityChanged = false
                            target.motionX = originalMotionX
                            target.motionY = originalMotionY
                            target.motionZ = originalMotionZ
                        }
                        if (isCritical) {
                            mc.thePlayer.onCriticalHit(target)
                        }
                        if (enchantmentBonus > 0.0f) {
                            mc.thePlayer.onEnchantmentCritical(target)
                        }
                        if (baseDamage >= 18.0f) {
                            mc.thePlayer.triggerAchievement(AchievementList.overkill)
                        }
                        mc.thePlayer.setLastAttacker(target)
                        if (target is EntityLivingBase) {
                            EnchantmentHelper.applyThornEnchantments(target, mc.thePlayer)
                        }
                        EnchantmentHelper.applyArthropodEnchantments(mc.thePlayer, target)
                        if (target is EntityLivingBase) {
                            mc.thePlayer.addStat(StatList.damageDealtStat, Math.round(baseDamage * 10.0f))
                            if (fireAspectLevel > 0) {
                                target.setFire(fireAspectLevel * 4)
                            }
                        }
                        mc.thePlayer.addExhaustion(0.3f)
                    } else if (isFireAspectApplied) {
                        target.extinguish()
                    }
                }
            }
        }
    }
}
