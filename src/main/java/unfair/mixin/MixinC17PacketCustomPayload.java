package unfair.mixin;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unfair.module.modules.misc.ClientID;

import java.util.ArrayList;
import java.util.List;

@Mixin(C17PacketCustomPayload.class)
public abstract class MixinC17PacketCustomPayload {
    @Shadow private String channel;
    @Shadow private PacketBuffer data;

    @Inject(method = "<init>(Ljava/lang/String;Lnet/minecraft/network/PacketBuffer;)V", at = @At("RETURN"))
    private void onConstruct(String channel, PacketBuffer data, CallbackInfo ci) {
        if ("MC|Brand".equals(this.channel)) {
            if (ClientID.isEnabled) {
                PacketBuffer newBuffer = new PacketBuffer(Unpooled.buffer());
                if (ClientID.mode.getValue() == 0) {
                    newBuffer.writeString("vanilla");
                } else if (ClientID.mode.getValue() == 1) {
                    newBuffer.writeString("lunarclient:v2.22.23-2630");
                } else if (ClientID.mode.getValue() == 2) {
                    newBuffer.writeString(ClientID.text.getValue());
                } else newBuffer.writeString("unknown");
                this.data = newBuffer;
            }
        }
        //if (ClientID.isEnabled && !ClientID.modlist.getValue()) {
        if (ClientID.isEnabled) {
            if ("REGISTER".equals(this.channel)) {
                try {
                    this.data.readerIndex(0);
                    String originalChannels = this.data.readStringFromBuffer(32767);
                    String[] channels = originalChannels.split("\0");
                    List<String> filteredChannels = new ArrayList<>();
                    for (String channel1 : channels) {
                        if (!channel1.startsWith("FML") && !channel1.startsWith("FORGE")) {
                            filteredChannels.add(channel1);
                        }
                    }
                    String newChannels = String.join("\0", filteredChannels);
                    PacketBuffer newBuffer = new PacketBuffer(Unpooled.buffer());
                    newBuffer.writeString(newChannels);
                    this.data = newBuffer;
                } catch (Exception ignored) {}
            }
        }
    }
}
