package unfair.util

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.scoreboard.ScorePlayerTeam
import unfair.Unfair
import java.awt.Color
import java.util.stream.Collectors

object TeamUtil {
    private val mc: Minecraft = Minecraft.getMinecraft()
    private val VALID_NAME = Regex("^[A-Za-z0-9_]{3,16}$")

    @JvmStatic
    fun isEntityLoaded(entity: Entity?): Boolean {
        if (entity == null) return false
        return mc.theWorld.loadedEntityList.contains(entity)
    }

    @JvmStatic
    val loadedEntitiesSorted: MutableList<Entity?>
        get() = mc.theWorld.loadedEntityList.stream()
            .sorted { entity1: Entity?, entity2: Entity? ->
                val dist1 =
                    mc.renderManager.getDistanceToCamera(entity1!!.posX, entity1.posY, entity1.posZ)
                val dist2 =
                    mc.renderManager.getDistanceToCamera(entity2!!.posX, entity2.posY, entity2.posZ)
                if (dist1 < dist2) {
                    return@sorted 1
                }
                if (dist1 > dist2) {
                    return@sorted -1
                }
                entity1.uniqueID.toString().compareTo(entity2.uniqueID.toString())
            }.collect(Collectors.toList())

    @JvmStatic
    fun getHealthScore(entityLivingBase: EntityLivingBase): Float {
        return entityLivingBase.health * (20.0f / entityLivingBase.totalArmorValue.toFloat())
    }

    @JvmStatic
    fun stripName(entity: Entity): String {
        return entity.displayName.formattedText.replace("§\\S$".toRegex(), "")
            .replace("(?i)§r".toRegex(), "§f").trim { it <= ' ' }
    }

    @JvmStatic
    fun getTeamColor(player: EntityPlayer, alpha: Float): Color {
        var colorCode = 0
        val playerTeam = player.team as ScorePlayerTeam?
        if (playerTeam != null) {
            val colorPrefix = FontRenderer.getFormatFromString(playerTeam.colorPrefix)
            if (colorPrefix.length >= 2) {
                colorCode = mc.fontRendererObj.getColorCode(colorPrefix.get(1))
            }
        }
        return Color(
            (colorCode shr 16 and 0xFF).toFloat() / 255.0f,
            (colorCode shr 8 and 0xFF).toFloat() / 255.0f,
            (colorCode and 0xFF).toFloat() / 255.0f,
            alpha
        )
    }

    @JvmStatic
    fun isBot(player: EntityPlayer): Boolean {
        if (player === mc.thePlayer) {
            return false
        }
        val name = player.name

        // 乱码名（§k 混淆）只在隐身时判定为 bot
        if (name.startsWith("§k")) {
            return player.isInvisible
        }

        // 正版名只允许 3-16 位字母、数字、下划线，否则必为 NPC
        if (!name.matches(VALID_NAME)) {
            return true
        }

        // 血量 NaN 是 NPC 插件的常见特征
        if (player.health.isNaN() || player.maxHealth.isNaN()) {
            return true
        }

        // 正常玩家实体 ID 是小的正整数，反作弊 bot 常用负数或超大 ID
        if (player.entityId < 0 || player.entityId >= 1000000000) {
            return true
        }

        // 玩家名全服唯一：同世界出现同名实体，说明有 bot 复制了真人数据
        if (mc.theWorld.loadedEntityList.any { it is EntityPlayer && it !== player && it.name == name }) {
            return true
        }

        // 反作弊 bot 会复制真人的名字和皮肤，但 UUID 可能对不上：
        // Tab 列表里按实体 UUID 查不到，说明这个实体是伪造的
        val playerInfo = mc.netHandler.getPlayerInfo(player.uniqueID) ?: return true

        // 以下特征单独都可能误伤真人，改为评分制：命中 >= 2 项才判定
        var score = 0

        // 刚生成：Watchdog bot 会直接刷在目标周围立即发动攻击
        if (player.ticksExisted < 10) {
            score++
        }

        // 无延迟数据（刚进游戏的真人也可能为 0，不能单独定罪）
        if (playerInfo.responseTime < 1) {
            score++
        }

        // Hypixel 上正版玩家 UUID 固定为 version 4（随机），NPC 插件生成的多为 version 2
        if (ServerUtil.isHypixel && player.uniqueID.version() != 4) {
            score++
        }

        // Hypixel Watchdog bot 特征：队伍名为空但颜色前缀为红色
        val playerTeam = playerInfo.playerTeam
        if (playerTeam != null && playerTeam.teamName.isEmpty() && playerTeam.colorPrefix == "§c") {
            score++
        }

        // 隐身玩家在公开战斗中极可疑
        if (player.isInvisible) {
            score++
        }

        return score >= 2
    }

    @JvmStatic
    fun isSameTeam(player: EntityPlayer): Boolean {
        if (player === mc.thePlayer) {
            return true
        }
        val selfInfo = mc.netHandler.getPlayerInfo(mc.thePlayer.uniqueID)
        if (selfInfo == null) {
            return false
        }
        val selfTeam = selfInfo.playerTeam
        if (selfTeam == null) {
            return false
        }
        val targetInfo = mc.netHandler.getPlayerInfo(player.uniqueID)
        if (targetInfo == null) {
            return false
        }
        val targetTeam = targetInfo.playerTeam
        if (targetTeam == null) {
            return false
        }
        return selfTeam.colorPrefix == targetTeam.colorPrefix
    }

    @JvmStatic
    fun hasTeamColor(entity: EntityLivingBase): Boolean {
        if (entity === mc.thePlayer) {
            return true
        }
        val selfInfo = mc.netHandler.getPlayerInfo(mc.thePlayer.getUniqueID())
        if (selfInfo == null) {
            return false
        }
        val selfTeam = selfInfo.playerTeam
        if (selfTeam == null) {
            return false
        }
        if (selfTeam.colorPrefix.length < 2) {
            return false
        }
        val nearestArmorStand = mc.theWorld.findNearestEntityWithinAABB<EntityLivingBase?>(
            EntityArmorStand::class.java,
            entity.entityBoundingBox,
            entity
        )
        if (nearestArmorStand != null) {
            return nearestArmorStand.name.contains(selfTeam.colorPrefix.substring(0, 2))
        }
        return false
    }

    @JvmStatic
    fun isShop(entity: EntityLivingBase): Boolean {
        if (entity === mc.thePlayer) {
            return false
        }
        val armorStand = mc.theWorld.findNearestEntityWithinAABB<EntityLivingBase?>(
            EntityArmorStand::class.java,
            entity.entityBoundingBox,
            entity
        )
        if (armorStand == null) return false
        val displayName = armorStand.name
        if (displayName.contains("RIGHT CLICK")) return true
        if (displayName.contains("ITEM SHOP")) return true
        if (displayName.contains("UPGRADES")) return true
        if (displayName.contains("BANKER")) return true
        return displayName.contains("STREAK POWERS")
    }

    @JvmStatic
    fun isTarget(player: EntityPlayer): Boolean {
        return Unfair.targetManager!!.isFriend(player.getName())
    }
}
