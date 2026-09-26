package unfair.module.modules.render;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.Render3DEvent;
import unfair.events.TickEvent;
import unfair.mixin.IAccessorRenderManager;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.FloatProperty;
import unfair.property.properties.IntProperty;
import unfair.property.properties.PercentProperty;
import unfair.util.RenderUtil;

import java.util.List;

/**
 * 移植自 Expo FireBallPredict。
 * 预测真实火球 / 玩家手持火焰弹的弹道命中方块，并在落点渲染高亮。
 */
public class FireBallPredict extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty realFireballs = new BooleanProperty("Real Fireballs", true);
    public final BooleanProperty heldFireCharges = new BooleanProperty("Held Fire Charges", true);
    public final IntProperty renderRadius = new IntProperty("Render Radius", 2, 1, 2);
    public final FloatProperty predictRange = new FloatProperty("Predict Range", 100.0F, 16.0F, 200.0F);
    public final PercentProperty opacity = new PercentProperty("Opacity", 50);

    private BlockPos impactPos;
    private int impactColor;

    public FireBallPredict() {
        super("FireBallPredict", false);
    }

    // ================= 预测 =================

    /** 按火球飞行距离生成红→黄→绿的颜色 */
    private int distanceColor(double distance) {
        if (distance <= 8.0D) {
            return 0xFF0000;
        }
        if (distance >= 48.0D) {
            return 0x00FF00;
        }
        if (distance <= 24.0D) {
            float t = (float) ((distance - 8.0D) / 16.0D);
            return packColor(255, Math.round(255.0F * t), 0);
        }
        float t = (float) ((distance - 24.0D) / 24.0D);
        return packColor(Math.round(255.0F * (1.0F - t)), 255, 0);
    }

    private int packColor(int r, int g, int b) {
        return r << 16 | g << 8 | b;
    }

    /** 追踪世界中真实火球的落点，取距离本地玩家最近的一个 */
    private Impact findRealFireball() {
        Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        Impact closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Object object : mc.theWorld.loadedEntityList) {
            Entity entity = (Entity) object;
            if (!(entity instanceof EntityFireball) || entity instanceof EntityWitherSkull) {
                continue;
            }
            EntityFireball fireball = (EntityFireball) entity;
            double motionSq = fireball.motionX * fireball.motionX
                    + fireball.motionY * fireball.motionY
                    + fireball.motionZ * fireball.motionZ;
            if (motionSq < 1.0E-4D) {
                continue;
            }

            Vec3 start = new Vec3(fireball.posX, fireball.posY, fireball.posZ);
            Vec3 direction = new Vec3(fireball.motionX, fireball.motionY, fireball.motionZ).normalize();
            Vec3 end = start.addVector(
                    direction.xCoord * this.predictRange.getValue(),
                    direction.yCoord * this.predictRange.getValue(),
                    direction.zCoord * this.predictRange.getValue()
            );
            MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(start, end, false, true, false);
            if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
                continue;
            }

            Vec3 center = new Vec3(
                    hit.getBlockPos().getX() + 0.5D,
                    hit.getBlockPos().getY() + 0.5D,
                    hit.getBlockPos().getZ() + 0.5D
            );
            double distSq = playerPos.squareDistanceTo(center);
            if (distSq < closestDist) {
                closestDist = distSq;
                closest = new Impact(hit.getBlockPos(), this.distanceColor(start.distanceTo(hit.hitVec)));
            }
        }
        return closest;
    }

    /** 追踪手持火焰弹玩家的视线落点，黄色标记 */
    private Impact findHeldFireCharge() {
        Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        Impact closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Object object : mc.theWorld.playerEntities) {
            EntityPlayer otherPlayer = (EntityPlayer) object;
            ItemStack held = otherPlayer.getHeldItem();
            if (held == null || held.getItem() != Items.fire_charge) {
                continue;
            }

            Vec3 eyes = otherPlayer.getPositionEyes(1.0F);
            Vec3 look = otherPlayer.getLook(1.0F);
            Vec3 end = eyes.addVector(
                    look.xCoord * this.predictRange.getValue(),
                    look.yCoord * this.predictRange.getValue(),
                    look.zCoord * this.predictRange.getValue()
            );
            MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eyes, end, false, true, false);
            if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
                continue;
            }

            Vec3 center = new Vec3(
                    hit.getBlockPos().getX() + 0.5D,
                    hit.getBlockPos().getY() + 0.5D,
                    hit.getBlockPos().getZ() + 0.5D
            );
            double distSq = playerPos.squareDistanceTo(center);
            if (distSq < closestDist) {
                closestDist = distSq;
                closest = new Impact(hit.getBlockPos(), 0xFFFF00);
            }
        }
        return closest;
    }

    private boolean isFullBlock(BlockPos pos) {
        if (mc.theWorld.isAirBlock(pos)) {
            return false;
        }
        return mc.theWorld.getBlockState(pos).getBlock().isFullCube();
    }

    // ================= 事件 =================

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        Impact impact = null;
        if (this.realFireballs.getValue()) {
            impact = this.findRealFireball();
        }
        if (impact == null && this.heldFireCharges.getValue()) {
            impact = this.findHeldFireCharge();
        }

        if (impact == null) {
            this.impactPos = null;
            this.impactColor = 0;
        } else {
            this.impactPos = impact.pos;
            this.impactColor = impact.color;
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.theWorld == null || mc.thePlayer == null || this.impactPos == null) {
            return;
        }

        // 过红的颜色统一转橙并提高不透明度
        int r = this.impactColor >> 16 & 0xFF;
        int g = this.impactColor >> 8 & 0xFF;
        int b = this.impactColor & 0xFF;
        boolean orange = r > 200 && g < 96 && b < 96;
        if (orange) {
            r = 0xFF;
            g = 0xC0;
            b = 0x00;
        }

        int outlineAlpha = this.scaleAlpha(255, orange);
        int fillAlpha = this.scaleAlpha(orange ? 70 : 42, orange);
        if (outlineAlpha <= 0 && fillAlpha <= 0) {
            return;
        }

        int radius = Math.max(1, Math.min(2, this.renderRadius.getValue()));
        RenderUtil.enableRenderState();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = this.impactPos.add(x, y, z);
                    if (!this.isFullBlock(pos)) {
                        continue;
                    }
                    if (fillAlpha > 0) {
                        this.drawFilled(pos, r, g, b, fillAlpha);
                    }
                    if (outlineAlpha > 0) {
                        RenderUtil.drawBlockBoundingBox(pos, 1.0D, r, g, b, outlineAlpha, 1.5F);
                    }
                }
            }
        }
        // 落点方块本身：白色加粗描边
        if (outlineAlpha > 0) {
            RenderUtil.drawBlockBoundingBox(this.impactPos, 1.0D, 255, 255, 255, outlineAlpha, 2.2F);
        }
        RenderUtil.disableRenderState();
    }

    private void drawFilled(BlockPos pos, int r, int g, int b, int alpha) {
        AxisAlignedBB box = new AxisAlignedBB(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D
        ).offset(
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
        );
        RenderUtil.drawAxisAlignedBB(box, true, alpha << 24 | r << 16 | g << 8 | b);
    }

    /** 按 Opacity 设置缩放 alpha，橙色高亮额外 ×1.4（上限 100%） */
    private int scaleAlpha(int alpha, boolean orange) {
        double percent = this.opacity.getValue() / 100.0D;
        if (orange && percent > 0.0D) {
            percent = Math.min(1.0D, percent * 1.4D);
        }
        return Math.max(0, Math.min(alpha, (int) (alpha * percent)));
    }

    @Override
    public void onDisabled() {
        this.impactPos = null;
        this.impactColor = 0;
    }

    private static final class Impact {
        final BlockPos pos;
        final int color;

        Impact(BlockPos pos, int color) {
            this.pos = pos;
            this.color = color;
        }
    }
}
