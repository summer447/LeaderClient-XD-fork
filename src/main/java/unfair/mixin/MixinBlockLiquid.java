package unfair.mixin;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unfair.Unfair;
import unfair.module.modules.movement.Jesus;

@SideOnly(Side.CLIENT)
@Mixin({BlockLiquid.class})
public abstract class MixinBlockLiquid {

    /**
     * Jesus Solid / Jump 模式：玩家未接触液体时把液体方块变成整格碰撞箱，可以站在水面上。
     * 只对非 Grim 服务器有效（与原版物理不一致，会被 Grim Simulation 判罚）。
     */
    @Inject(
            method = {"getCollisionBoundingBox"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void onGetCollisionBoundingBox(World world, BlockPos pos, IBlockState state, CallbackInfoReturnable<AxisAlignedBB> cir) {
        if (Unfair.moduleManager == null) {
            return;
        }
        Jesus jesus = (Jesus) Unfair.moduleManager.modules.get(Jesus.class);
        if (jesus == null || !jesus.isEnabled() || jesus.mode.getValue() == 0) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.isSneaking() || mc.thePlayer.isRiding()) {
            return;
        }
        if (mc.thePlayer.capabilities.isFlying || jesus.isTouchingLiquid()) {
            return;
        }
        cir.setReturnValue(new AxisAlignedBB(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D
        ));
    }
}
