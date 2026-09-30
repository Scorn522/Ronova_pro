package dev.ronova.pro.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps Connection's protocol transition and write path on its channel event loop. */
@Mixin(Connection.class)
public abstract class ConnectionHooks {
    @Shadow private Channel channel;

    /** Mapped 1.20.1/Forge 47.4.22 target: sendPacket(Packet, PacketSendListener), SRG m_129520_. */
    @Shadow private void sendPacket(Packet<?> packet,PacketSendListener listener) { throw new AssertionError(); }

    @Inject(method="sendPacket(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at=@At("HEAD"),cancellable=true)
    private void ronova$serializeProtocolSend(Packet<?> packet,PacketSendListener listener,CallbackInfo callback) {
        Channel current=channel;
        if(current!=null&&!current.eventLoop().inEventLoop()) {
            current.eventLoop().execute(()->sendPacket(packet,listener));
            callback.cancel();
        }
    }
}
