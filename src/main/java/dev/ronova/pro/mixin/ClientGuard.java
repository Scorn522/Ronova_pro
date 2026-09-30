package dev.ronova.pro.mixin;

import dev.ronova.pro.ClientPresence;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Consumer guard for the client mirror, driven by the server's actual protection policy.
 *
 * Only entries whose exact identity the policy covers are withheld; the rest of the batch is delivered as sent.
 * The whole packet is cancelled only when every entry in it is covered, which is the case where delivering it
 * would contradict the current policy entirely.
 */
public final class ClientGuard {
    private ClientGuard() { }

    @Mixin(ClientPacketListener.class)
    public abstract static class Removal {
        @Inject(method="handleRemoveEntities",at=@At("HEAD"),cancellable=true)
        private void removal(ClientboundRemoveEntitiesPacket packet,CallbackInfo callback) {
            net.minecraft.client.Minecraft minecraft=net.minecraft.client.Minecraft.getInstance();
            // Let the vanilla ensureRunningOnSameThread call schedule this packet before any read or mutation.
            if(!minecraft.isSameThread())return;
            if(minecraft.getConnection()!=(ClientPacketListener)(Object)this) { callback.cancel();return; }
            IntList ids=packet.getEntityIds();
            if(ids==null||ids.isEmpty())return;
            IntList allowed=null;
            int withheld=0;
            for(int index=0;index<ids.size();index++) {
                int entityId=ids.getInt(index);
                UUID identity=ClientPresence.identityOf(entityId);
                if(identity!=null&&ClientPresence.removalDenied(entityId,identity)) {
                    withheld++;
                    if(allowed==null) {
                        allowed=new IntArrayList(ids.size());
                        for(int kept=0;kept<index;kept++)allowed.add(ids.getInt(kept));
                    }
                    continue;
                }
                if(allowed!=null)allowed.add(entityId);
            }
            if(withheld==0)return;
            if(allowed==null||allowed.isEmpty()) { callback.cancel();return; }
            ((Access.Packet)(Object)packet).pro$entityIds(allowed);
        }
    }
}
