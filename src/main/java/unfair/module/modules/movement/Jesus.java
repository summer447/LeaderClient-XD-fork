package unfair.module.modules.movement;

import net.minecraft.block.BlockLiquid;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import unfair.event.EventTarget;
import unfair.events.LivingUpdateEvent;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.ModeProperty;

public class Jesus extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double SWIM_UP = 0.03999999910593033D;

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Old Prediction", "Solid", "Jump"});
    public final BooleanProperty lava = new BooleanProperty("Lava", false);

    public Jesus() {
        super("Jesus", false);
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!this.isEnabled() || this.mode.getValue() == 1) {
            return;
        }
        if (mc.thePlayer == null || mc.thePlayer.isRiding() || mc.thePlayer.isSneaking()) {
            return;
        }
        if (mc.thePlayer.capabilities.isFlying) {
            return;
        }
        if (this.mode.getValue() == 2) {
            boolean canJump = mc.thePlayer.onGround ? this.isStandingOnLiquid() : this.isTouchingLiquid();
            if (canJump) {
                mc.thePlayer.jump();
            }
            return;
        }
        if (mc.thePlayer.movementInput.jump) {
            return;
        }
        boolean liquid = mc.thePlayer.isInWater() || this.lava.getValue() && mc.thePlayer.isInLava();
        if (!liquid) {
            return;
        }
        double surfaceY = this.findSurfaceY();
        if (Double.isNaN(surfaceY)) {
            mc.thePlayer.motionY += SWIM_UP;
            return;
        }
        if (mc.thePlayer.posY < surfaceY + 0.25D) {
            mc.thePlayer.motionY += SWIM_UP;
        } else if (mc.thePlayer.motionY < 0.0D) {
            mc.thePlayer.motionY += SWIM_UP;
        }
    }

    private double findSurfaceY() {
        AxisAlignedBB bb = mc.thePlayer.getEntityBoundingBox();
        int minX = MathHelper.floor_double(bb.minX);
        int maxX = MathHelper.floor_double(bb.maxX - 1.0E-7D);
        int minZ = MathHelper.floor_double(bb.minZ);
        int maxZ = MathHelper.floor_double(bb.maxZ - 1.0E-7D);
        int startY = MathHelper.floor_double(bb.minY) - 3;
        int endY = MathHelper.floor_double(bb.minY) + 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(0, 0, 0);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = startY; y <= endY; y++) {
                    pos.set(x, y, z);
                    if (!(mc.theWorld.getBlockState(pos).getBlock() instanceof BlockLiquid)) {
                        continue;
                    }
                    pos.set(x, y + 1, z);
                    if (mc.theWorld.getBlockState(pos).getBlock() instanceof BlockLiquid) {
                        continue;
                    }
                    pos.set(x, y, z);
                    int meta = mc.theWorld.getBlockState(pos).getBlock().getMetaFromState(mc.theWorld.getBlockState(pos));
                    if (meta >= 8) {
                        continue;
                    }
                    return y + 1 - BlockLiquid.getLiquidHeightPercent(meta);
                }
            }
        }
        return Double.NaN;
    }

    public boolean isTouchingLiquid() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return false;
        }
        AxisAlignedBB bb = mc.thePlayer.getEntityBoundingBox().contract(0.001D, 0.001D, 0.001D);
        int minX = MathHelper.floor_double(bb.minX);
        int maxX = MathHelper.floor_double(bb.maxX);
        int minY = MathHelper.floor_double(bb.minY);
        int maxY = MathHelper.floor_double(bb.maxY);
        int minZ = MathHelper.floor_double(bb.minZ);
        int maxZ = MathHelper.floor_double(bb.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(0, 0, 0);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    if (mc.theWorld.getBlockState(pos).getBlock() instanceof BlockLiquid) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isStandingOnLiquid() {
        AxisAlignedBB bb = mc.thePlayer.getEntityBoundingBox();
        int minX = MathHelper.floor_double(bb.minX);
        int maxX = MathHelper.floor_double(bb.maxX - 1.0E-7D);
        int minZ = MathHelper.floor_double(bb.minZ);
        int maxZ = MathHelper.floor_double(bb.maxZ - 1.0E-7D);
        int y = MathHelper.floor_double(bb.minY - 0.05D);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(0, 0, 0);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                pos.set(x, y, z);
                if (mc.theWorld.getBlockState(pos).getBlock() instanceof BlockLiquid) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
