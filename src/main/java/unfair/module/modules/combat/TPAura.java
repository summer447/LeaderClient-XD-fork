package unfair.module.modules.combat;

import com.google.common.base.CaseFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings.GameType;
import unfair.event.EventManager;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.AttackEvent;
import unfair.events.MoveInputEvent;
import unfair.events.PacketEvent;
import unfair.events.UpdateEvent;
import unfair.management.RotationState;
import unfair.mixin.IAccessorPlayerControllerMP;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.FloatProperty;
import unfair.property.properties.IntProperty;
import unfair.property.properties.ModeProperty;
import unfair.util.ChatUtil;
import unfair.util.MoveUtil;
import unfair.util.PacketUtil;
import unfair.util.PlayerUtil;
import unfair.util.RotationUtil;
import unfair.util.TeamUtil;
import unfair.util.TimerUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

/**
 * 架构移植自 LiquidBounce ModuleTpAura（Immediate / AStar）。
 * 核心不是「传过去打一下立刻回来」，而是 desync 状态机：
 * 1. travel() 发送填充包 + 落点包，服务器认为你站在目标处
 * 2. Stick 期间所有自发移动包被重写到 desync 位置，攻击循环正常出手
 * 3. 收到 S08 说明服务器拒绝（setback），立即终止整个流程 —— 这才是防踢关键
 */
public class TPAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty range = new FloatProperty("Range", 7.0F, 3.0F, 15.0F);
    public final FloatProperty attackRange = new FloatProperty("Attack Range", 4.2F, 3.0F, 5.0F);
    public final IntProperty cps = new IntProperty("CPS", 12, 1, 20);
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Immediate", "AStar"});
    public final IntProperty stick = new IntProperty("Stick", 10, 1, 20);
    public final IntProperty maximumCost = new IntProperty("Maximum Cost", 150, 50, 400, () -> this.mode.getValue() == 1);
    public final IntProperty tickDistance = new IntProperty("Tick Distance", 3, 1, 7, () -> this.mode.getValue() == 1);
    public final BooleanProperty tpBack = new BooleanProperty("TP Back", true, () -> this.mode.getValue() == 1);
    public final BooleanProperty rotations = new BooleanProperty("Rotations", true);
    public final BooleanProperty attackMoveFix = new BooleanProperty("Attack Move Fix", true);
    public final BooleanProperty teams = new BooleanProperty("Teams", true);
    public final BooleanProperty botCheck = new BooleanProperty("Bot Check", true);

    private enum State { IDLE, STICK }

    private final TimerUtil timer = new TimerUtil();
    private State state = State.IDLE;
    private int stickTicks;
    private Vec3 desync;
    private Vec3 origin;
    private List<Vec3> returnPath;

    public TPAura() {
        super("TPAura", false);
    }

    // ================= 目标选择 =================

    private boolean isValidTarget(EntityPlayer player) {
        if (player == mc.thePlayer || player == mc.thePlayer.ridingEntity || player.ridingEntity != null) {
            return false;
        }
        if (player.isDead || player.deathTime > 0) {
            return false;
        }
        return (!this.teams.getValue() || !TeamUtil.isSameTeam(player)) && (!this.botCheck.getValue() || !TeamUtil.isBot(player));
    }

    private List<EntityPlayer> validTargets() {
        return mc.theWorld.loadedEntityList.stream()
                .filter(entity -> entity instanceof EntityPlayer)
                .map(entity -> (EntityPlayer) entity)
                .filter(this::isValidTarget)
                .collect(Collectors.toList());
    }

    private double boxedDistance(Vec3 from, AxisAlignedBB box) {
        double dx = Math.max(Math.max(box.minX - from.xCoord, from.xCoord - box.maxX), 0.0D);
        double dy = Math.max(Math.max(box.minY - from.yCoord, from.yCoord - box.maxY), 0.0D);
        double dz = Math.max(Math.max(box.minZ - from.zCoord, from.zCoord - box.maxZ), 0.0D);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ================= 发包（travel） =================

    private void sendFull(Vec3 pos) {
        PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                pos.xCoord, pos.yCoord, pos.zCoord, mc.thePlayer.onGround
        ));
    }

    /**
     * LiquidBounce travel：先在当前位置发填充包，把服务器按包计数的速度平均稀释掉，
     * 最后一个 FULL 包跳到落点。
     */
    private void travel(Vec3 destination) {
        Vec3 current = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        double manhattan = Math.abs(destination.xCoord - current.xCoord)
                + Math.abs(destination.yCoord - current.yCoord)
                + Math.abs(destination.zCoord - current.zCoord);
        int fillers = (int) Math.floor(manhattan / 10.0D) - 1;
        for (int i = 0; i < fillers; i++) {
            this.sendFull(current);
        }
        this.sendFull(destination);
        this.desync = destination;
    }

    /** AStar travel：路径按 tickDistance 分块，块内无碰撞就只发一个落点包，否则逐格发送。 */
    private void travelPath(List<Vec3> path) {
        for (int start = 0; start < path.size(); start += this.tickDistance.getValue()) {
            List<Vec3> chunk = path.subList(start, Math.min(path.size(), start + this.tickDistance.getValue()));
            Vec3 first = chunk.get(0);
            Vec3 last = chunk.get(chunk.size() - 1);

            double minX = Math.min(first.xCoord, last.xCoord);
            double minY = Math.min(first.yCoord, last.yCoord);
            double minZ = Math.min(first.zCoord, last.zCoord);
            double maxX = Math.max(first.xCoord, last.xCoord);
            double maxY = Math.max(first.yCoord, last.yCoord);
            double maxZ = Math.max(first.zCoord, last.zCoord);
            AxisAlignedBB box = new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ).expand(0.3D, 0.2D, 0.3D);

            if (!mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box).isEmpty()) {
                for (Vec3 point : chunk) {
                    this.sendFull(point);
                    this.desync = point;
                }
            } else {
                this.sendFull(last);
                this.desync = last;
            }
        }
    }

    // ================= A* 寻路 =================

    private static final int[][] DIRS = new int[][]{
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private boolean free(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return mc.theWorld.getBlockState(pos).getBlock().getCollisionBoundingBox(mc.theWorld, pos, mc.theWorld.getBlockState(pos)) == null;
    }

    private boolean solid(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return mc.theWorld.getBlockState(pos).getBlock().getCollisionBoundingBox(mc.theWorld, pos, mc.theWorld.getBlockState(pos)) != null;
    }

    private static final class PathNode {
        final BlockPos pos;
        final double g;
        final double f;
        final PathNode parent;

        PathNode(BlockPos pos, double g, double f, PathNode parent) {
            this.pos = pos;
            this.g = g;
            this.f = f;
            this.parent = parent;
        }
    }

    private List<Vec3> findPath(EntityPlayer target) {
        BlockPos startPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        BlockPos goalPos = new BlockPos(target.posX, target.posY, target.posZ);

        PriorityQueue<PathNode> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<BlockPos, Double> best = new HashMap<>();
        open.add(new PathNode(startPos, 0.0D, this.heuristic(startPos, goalPos), null));
        best.put(startPos, 0.0D);

        PathNode result = null;
        int iterations = 0;

        while (!open.isEmpty() && iterations++ < this.maximumCost.getValue()) {
            PathNode current = open.poll();
            if (best.getOrDefault(current.pos, Double.MAX_VALUE) < current.g) {
                continue;
            }
            int x = current.pos.getX();
            int y = current.pos.getY();
            int z = current.pos.getZ();

            // 落点 2 格以内即可，attackRange 足够出手
            if (Math.abs(x - goalPos.getX()) + Math.abs(z - goalPos.getZ()) <= 2 && Math.abs(y - goalPos.getY()) <= 1) {
                result = current;
                break;
            }

            for (int[] dir : DIRS) {
                int nx = x + dir[0];
                int nz = z + dir[1];
                BlockNode move = this.stepNode(nx, y, nz, current);
                if (move == null) {
                    continue;
                }
                double ng = current.g + move.cost;
                if (ng < best.getOrDefault(move.pos, Double.MAX_VALUE)) {
                    best.put(move.pos, ng);
                    open.add(new PathNode(move.pos, ng, ng + this.heuristic(move.pos, goalPos), current));
                }
            }
        }

        if (result == null) {
            return null;
        }
        ArrayDeque<Vec3> reversed = new ArrayDeque<>();
        for (PathNode node = result; node != null; node = node.parent) {
            BlockPos p = node.pos;
            reversed.push(new Vec3(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D));
        }
        return new ArrayList<>(reversed);
    }

    private static final class BlockNode {
        final BlockPos pos;
        final double cost;

        BlockNode(BlockPos pos, double cost) {
            this.pos = pos;
            this.cost = cost;
        }
    }

    private BlockNode stepNode(int nx, int y, int nz, PathNode current) {
        int x = current.pos.getX();
        int z = current.pos.getZ();

        // 平地
        if (this.free(nx, y, nz) && this.free(nx, y + 1, nz) && this.solid(nx, y - 1, nz)) {
            return new BlockNode(new BlockPos(nx, y, nz), 1.0D);
        }
        // 上跳一格
        if (this.free(x, y + 2, z) && this.free(nx, y + 1, nz) && this.free(nx, y + 2, nz) && this.solid(nx, y, nz)) {
            return new BlockNode(new BlockPos(nx, y + 1, nz), 1.0D);
        }
        // 下落一格
        if (this.free(nx, y - 1, nz) && this.free(nx, y, nz) && this.solid(nx, y - 2, nz)) {
            return new BlockNode(new BlockPos(nx, y - 1, nz), 1.0D);
        }
        return null;
    }

    private double heuristic(BlockPos from, BlockPos to) {
        return Math.abs(from.getX() - to.getX()) + Math.abs(from.getY() - to.getY()) + Math.abs(from.getZ() - to.getZ());
    }

    // ================= 攻击 =================

    private void attack(EntityPlayer target) {
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        mc.thePlayer.swingItem();
        EventManager.call(new AttackEvent(target));
        PacketUtil.sendPacket(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
        if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
            PlayerUtil.attackEntity(target);
        }
    }

    // ================= 状态机 =================

    private void startTeleport() {
        EntityPlayer target = this.validTargets().stream()
                .filter(p -> mc.thePlayer.getDistanceToEntity(p) <= this.range.getValue())
                .min(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                .orElse(null);
        if (target == null) {
            return;
        }

        this.origin = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        this.returnPath = null;

        if (this.mode.getValue() == 1) {
            List<Vec3> path = this.findPath(target);
            if (path == null || path.size() < 2) {
                return;
            }
            List<Vec3> backwards = new ArrayList<>(path);
            java.util.Collections.reverse(backwards);
            this.returnPath = backwards;
            this.travelPath(path);
        } else {
            this.travel(new Vec3(target.posX, target.posY, target.posZ));
        }

        this.state = State.STICK;
        this.stickTicks = this.stick.getValue();
    }

    private void stickTick(UpdateEvent event) {
        // desync 位置上能打到谁
        EntityPlayer attackTarget = this.validTargets().stream()
                .filter(p -> this.boxedDistance(this.desync, p.getEntityBoundingBox()) <= this.attackRange.getValue())
                .min(Comparator.comparingDouble(p -> this.boxedDistance(this.desync, p.getEntityBoundingBox())))
                .orElse(null);
        if (attackTarget != null) {
            if (this.rotations.getValue() || this.attackMoveFix.getValue()) {
                float[] rots = RotationUtil.getRotationsToBox(
                        attackTarget.getEntityBoundingBox(),
                        event.getYaw(),
                        event.getPitch(),
                        180.0F,
                        0.0F
                );
                if (this.rotations.getValue()) {
                    event.setRotation(rots[0], rots[1], 1);
                }
                // Attack Move Fix：让本 tick 的移动数学基于服务器朝向
                if (this.attackMoveFix.getValue()) {
                    event.setPervRotation(rots[0], 1);
                }
            }

            if (this.timer.hasTimeElapsed(1000L / (long) this.cps.getValue())) {
                this.timer.reset();
                this.attack(attackTarget);
            }
        }

        if (--this.stickTicks <= 0) {
            this.finishTeleport();
        }
    }

    private void finishTeleport() {
        if (this.mode.getValue() == 1 && this.tpBack.getValue() && this.returnPath != null) {
            this.travelPath(this.returnPath);
        } else if (this.mode.getValue() == 0) {
            this.travel(this.origin);
        }
        this.desync = null;
        this.returnPath = null;
        this.state = State.IDLE;
    }

    private void abortBySetback() {
        ChatUtil.INSTANCE.sendRaw("§c[TPAura] Server setback detected - teleport failed!");
        this.timer.reset();
        this.desync = null;
        this.returnPath = null;
        this.state = State.IDLE;
    }

    // ================= 事件 =================

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.state = State.IDLE;
            this.desync = null;
            return;
        }
        if (mc.thePlayer.isRiding()) {
            return;
        }

        if (this.state == State.IDLE) {
            if (this.timer.hasTimeElapsed(1000L / (long) this.cps.getValue())) {
                this.timer.reset();
                this.startTeleport();
            }
        } else {
            this.stickTick(event);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || this.desync == null) {
            return;
        }

        if (event.getType() == EventType.RECEIVE) {
            // 服务器把我们拉回 = 拒绝本次传送，立刻收手，不继续违规就不会踢
            if (event.getPacket() instanceof S08PacketPlayerPosLook) {
                this.abortBySetback();
            }
            return;
        }

        Packet<?> packet = event.getPacket();
        if (!(packet instanceof C03PacketPlayer)) {
            return;
        }
        // desync 期间把自发移动包锚定到 desync 位置，保留原包朝向
        C03PacketPlayer move = (C03PacketPlayer) packet;
        event.setCancelled(true);
        boolean onGround = mc.thePlayer.onGround;
        if (move instanceof C03PacketPlayer.C05PacketPlayerLook) {
            PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C06PacketPlayerPosLook(
                    this.desync.xCoord, this.desync.yCoord, this.desync.zCoord,
                    move.getYaw(), move.getPitch(), onGround
            ));
        } else if (move instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
            PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C06PacketPlayerPosLook(
                    this.desync.xCoord, this.desync.yCoord, this.desync.zCoord,
                    move.getYaw(), move.getPitch(), onGround
            ));
        } else if (move instanceof C03PacketPlayer.C04PacketPlayerPosition) {
            PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                    this.desync.xCoord, this.desync.yCoord, this.desync.zCoord, onGround
            ));
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (!this.isEnabled() || this.state != State.STICK || !this.attackMoveFix.getValue()) {
            return;
        }
        // 静默旋转生效且有移动输入时，按服务器朝向重映射 WASD
        if (RotationState.isActived()
                && RotationState.getPriority() == 1.0F
                && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    @Override
    public void onDisabled() {
        // 关闭模块时若仍在 desync，直接补回程包，避免自发包恢复后触发 setback
        if (this.desync != null) {
            if (this.mode.getValue() == 1 && this.returnPath != null) {
                this.travelPath(this.returnPath);
            } else if (this.origin != null) {
                this.travel(this.origin);
            }
        }
        this.state = State.IDLE;
        this.desync = null;
        this.returnPath = null;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}
