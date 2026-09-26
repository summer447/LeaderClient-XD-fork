package unfair.module.modules.combat;

import com.google.common.base.CaseFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import unfair.Unfair;
import unfair.enums.BlinkModules;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.AttackEvent;
import unfair.events.TickEvent;
import unfair.module.Module;
import unfair.property.properties.*;
import unfair.util.ItemUtil;
import unfair.util.KeyBindUtil;
import unfair.util.PacketUtil;
import unfair.util.TimerUtil;

public class BlockHit extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    public BlockHit() {
        super("BlockHit",false, false);
    }
    private final ModeProperty mode = new ModeProperty("Mode",0,new String[]{"Helper","Auto","Lag"});
    // 绕过：格挡时允许 NoSlow 生效
    public final BooleanProperty allowNoSlow = new BooleanProperty("Allow NoSlow", true);
    // 绕过：强制第一人称格挡视觉（即使服务端状态未同步）
    public final BooleanProperty visualBlocking = new BooleanProperty("Visual Blocking", true);

    private final IntProperty stopTime = new IntProperty("Stop Ticks",2,1,5, () -> this.mode.getValue() == 0);
    private final ModeProperty autoMode = new ModeProperty("Auto Mode",0,new String[]{"Spam","Hold"},() -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 0);
    private final ModeProperty autoBlockTime = new ModeProperty("AutoBlock Time",0, new String[]{"Delay","HurtTime","Sag","Smart"},() -> this.mode.getValue() == 1);
    private final IntProperty smartBlockTick = new IntProperty("Smart Block Ticks",2,1,5, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 3);
    private final IntProperty blockDelay = new IntProperty("Block Delay",100,0,1000, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 0);
    private final IntProperty holdTick = new IntProperty("Hold Ticks",2,2,5, () -> this.mode.getValue() == 1 && this.autoMode.getValue() == 1  && this.autoBlockTime.getValue() == 0);
    private final IntProperty minHurtTime = new IntProperty("Min HurtTime",10,1,10, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 1);
    private final IntProperty maxHurtTime = new IntProperty("Max HurtTime",10,1,10, () -> this.mode.getValue() == 1 && this.autoBlockTime.getValue() == 1);
    private final IntProperty delayPacketTick = new IntProperty("Delay Packet Ticks",2,1,10, () -> this.mode.getValue() == 2);
    private final IntProperty blockTick = new IntProperty("Block Ticks",3,1,5, () -> this.mode.getValue() == 2 );
    private final IntProperty startHurtTime = new IntProperty("Start HurtTime",6,1,10, () -> this.mode.getValue() == 2 );
    private final PercentProperty chance = new PercentProperty("Block Hit Chance",50,()-> this.mode.getValue() == 1);
    private final BooleanProperty smart = new BooleanProperty("Smart",true,() -> this.mode.getValue() == 1);
    private final BooleanProperty autoBlockRange = new BooleanProperty("AutoBlock Range",true,() -> this.mode.getValue() == 1);
    private final FloatProperty range = new FloatProperty("Range",3.0f,1f,4f,() -> autoBlockRange.getValue() && mode.getValue() == 1);
    private int holdTicks,stopTick;

    private boolean startBlocking;
    private boolean attacking;
    private int attackTicks;
    private int sagTicks = 0;
    private int blockTicks = 0;
    private int blinkTicks = 0;
    private boolean canBlock = false;
    private int getBlockTicks = 0;
    private EntityLivingBase target;
    private TimerUtil timer = new TimerUtil();

    // 模块正在驱动格挡（按下 use 键）期间为 true
    private boolean blocking;

    /** 供 NoSlow / 渲染层查询：模块是否正在格挡 */
    public boolean isBlocking() {
        return this.isEnabled() && this.blocking && ItemUtil.isHoldingSword();
    }

    /** 绕过：按 Ping 延长格挡保持 tick，保证服务器先收到格挡再松开，最多补偿 2 tick */
    private int getLatencyTicks() {
        try {
            int ping = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()).getResponseTime();
            return ping <= 0 ? 0 : Math.max(0, Math.min(2, (int) Math.ceil(ping / 50.0)));
        } catch (Exception ignored) {
            return 0;
        }
    }
    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (event.getType() == EventType.PRE) {
            if (this.mode.getValue() == 0) {
                if (mc.gameSettings.keyBindAttack.isKeyDown()) {
                    if (mc.thePlayer.isBlocking()) {
                        startBlocking = true;
                        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                    }
                }
                if (startBlocking) stopTick++;
                if (stopTick == 2) {
                    KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
                }
                if (stopTick > stopTime.getValue()) {
                    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                    startBlocking = false;
                    stopTick = 0;
                }
            }
            if (this.mode.getValue() == 1) {
                if (target == null) return;
                if (attacking) {
                    attackTicks++;
                }
                if (attackTicks > 5) {
                    reset();
                    target = null;
                    return;
                }
                if (Math.random() > chance.getValue()){
                    reset();
                    return;
                }
                if (autoBlockRange.getValue() && mc.thePlayer.getDistanceToEntity(target) >= range.getValue()){
                    reset();
                    return;
                }
                if (smart.getValue() && target.hurtTime == 0){
                    reset();
                    return;
                }
                if (attacking && ItemUtil.isHoldingSword()) {
                    if (autoBlockTime.getValue() == 0) {
                        if (timer.hasTimeElapsed(blockDelay.getValue().longValue())) {
                            if (this.autoMode.getValue() == 0) {
                                this.blocking = true;
                                KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindUseItem.getKeyCode());
                                timer.reset();
                                reset();
                            }
                            if (this.autoMode.getValue() == 1) {
                                startBlocking = true;
                            }
                            if (startBlocking) {
                                this.blocking = true;
                                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                                holdTicks++;
                            }
                            if (holdTicks > holdTick.getValue() + this.getLatencyTicks()) {
                                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                                startBlocking = false;
                                holdTicks = 0;
                                this.blocking = false;
                                timer.reset();
                            }
                        }
                    }
                    if (autoBlockTime.getValue() == 1) {
                        if (mc.thePlayer.hurtTime >= minHurtTime.getValue() && mc.thePlayer.hurtTime <= maxHurtTime.getValue()) {
                            this.blocking = true;
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                            startBlocking = true;
                        } else if (startBlocking) {
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                            startBlocking = false;
                            this.blocking = false;
                        }
                    }
                    if (autoBlockTime.getValue() == 2){
                        if (sagTicks < 10) {
                            this.blocking = true;
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                            sagTicks++;
                        }
                        if (sagTicks >= 10){
                            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                            sagTicks = 0;
                            this.blocking = false;
                        }
                    }
                    if (autoBlockTime.getValue() == 3){
                        if(mc.thePlayer.hurtTime <= 2){
                            canBlock = true;
                        }
                        if (canBlock){
                            getBlockTicks++;
                            this.blocking = true;
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                        }
                        if (getBlockTicks > smartBlockTick.getValue() + this.getLatencyTicks()){
                            canBlock = false;
                            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                            getBlockTicks = 0;
                            this.blocking = false;
                        }
                    }
                }
            }
            if (this.mode.getValue() == 2){
                if (mc.thePlayer.hurtTime == startHurtTime.getValue()){
                    blockTicks = 1;
                    blinkTicks = 1;
                    Unfair.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                }
                if (blockTicks >= 1){
                    this.blocking = true;
                    KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                    blockTicks++;
                }
                if (blinkTicks >= 1){
                    blinkTicks++;
                }
                if (blinkTicks > delayPacketTick.getValue()){
                    Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    blinkTicks = 0;
                }
                if (blockTicks > blockTick.getValue() + this.getLatencyTicks()){
                    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                    blockTicks = 0;
                    this.blocking = false;
                }
            }
        }
    }
    private void reset(){
        attacking = canBlock = false;
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        holdTicks = sagTicks = getBlockTicks = 0;
        this.blocking = false;
        timer.reset();
    }
    private void startBlock(ItemStack itemStack) {
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
    }

    @EventTarget
    public void onAttack(AttackEvent event){
        if (this.isEnabled() && ItemUtil.isHoldingSword()){
            attacking = true;
            attackTicks = 0;
            target = (EntityLivingBase) event.getTarget();
            if (autoBlockTime.getValue() == 3){
                if (mc.thePlayer.hurtTime == 0)canBlock = true;
            }
        }
    }

    @Override
    public void onDisabled() {
        reset();
        target = null;
        attacking = false;
        attackTicks = 0;
        startBlocking = false;
        blockTicks = 0;
        blinkTicks = 0;
        this.blocking = false;
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
    }
    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}
