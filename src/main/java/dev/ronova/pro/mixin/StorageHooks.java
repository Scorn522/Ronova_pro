package dev.ronova.pro.mixin;

import dev.ronova.pro.RecoverySources;
import java.io.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class StorageHooks {
    private StorageHooks() { }
    @Mixin(NbtIo.class)
    public abstract static class Output {
        @Redirect(method="readCompressed(Ljava/io/File;)Lnet/minecraft/nbt/CompoundTag;",
            at=@At(value="NEW",target="(Ljava/io/File;)Ljava/io/FileInputStream;"))
        private static FileInputStream openInput(File file)throws FileNotFoundException { return RecoverySources.openInput(file); }
        @Redirect(method="readCompressed(Ljava/io/File;)Lnet/minecraft/nbt/CompoundTag;",
            at=@At(value="INVOKE",target="Lnet/minecraft/nbt/NbtIo;readCompressed(Ljava/io/InputStream;)Lnet/minecraft/nbt/CompoundTag;"))
        private static CompoundTag readInput(InputStream stream)throws IOException { return RecoverySources.readInput(stream); }
        @Inject(method="readCompressed(Ljava/io/File;)Lnet/minecraft/nbt/CompoundTag;",at=@At("RETURN"))
        private static void read(File file,CallbackInfoReturnable<CompoundTag> callback) {
            if(callback.getReturnValue()!=null)RecoverySources.readFile(file);
        }
        @Inject(method="writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/OutputStream;)V",at=@At("HEAD"))
        private static void output(CompoundTag image,OutputStream stream,CallbackInfo callback) {
            RecoverySources.outputObserved(image);
        }
        @Redirect(method="writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/File;)V",
            at=@At(value="NEW",target="(Ljava/io/File;)Ljava/io/FileOutputStream;"))
        private static FileOutputStream open(File file,CompoundTag image,File input)throws FileNotFoundException {
            return RecoverySources.open(image,file);
        }
        @Redirect(method="writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/File;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/OutputStream;)V"))
        private static void write(CompoundTag image,OutputStream output)throws IOException {
            RecoverySources.write(image,output);
        }
    }
    @Mixin(SavedData.class)
    public abstract static class Holder {
        @Redirect(method="save(Ljava/io/File;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/File;)V"))
        private void write(CompoundTag image,File file)throws IOException {
            RecoverySources.savedData((SavedData)(Object)this,image,file);
        }
    }
    @Mixin(net.minecraft.world.level.storage.DimensionDataStorage.class)
    public abstract static class Cache implements RecoverySources.CacheOwner {
        @Shadow @Final private File dataFolder;
        @Shadow @Final private java.util.Map<String,SavedData> cache;
        @Override public final java.util.Map<String,SavedData> pro$cacheView() { return cache; }
        @Redirect(method="readTagFromDisk",at=@At(value="NEW",target="(Ljava/io/File;)Ljava/io/FileInputStream;"))
        private FileInputStream sourceInput(File file)throws FileNotFoundException { return RecoverySources.openInput(file); }
        @Redirect(method="readTagFromDisk",at=@At(value="NEW",target="(Ljava/io/InputStream;I)Ljava/io/PushbackInputStream;"))
        private PushbackInputStream sourcePushback(InputStream input,int size) { return RecoverySources.pushback(input,size); }
        @Redirect(method="readTagFromDisk",at=@At(value="INVOKE",target="Lnet/minecraft/nbt/NbtIo;readCompressed(Ljava/io/InputStream;)Lnet/minecraft/nbt/CompoundTag;"))
        private CompoundTag sourceRead(InputStream input)throws IOException { return RecoverySources.readInput(input); }
        @Inject(method="get(Ljava/util/function/Function;Ljava/lang/String;)Lnet/minecraft/world/level/saveddata/SavedData;",at=@At("RETURN"))
        private void observed(java.util.function.Function<CompoundTag,SavedData> reader,String name,CallbackInfoReturnable<SavedData> callback) {
            RecoverySources.cacheSeen((net.minecraft.world.level.storage.DimensionDataStorage)(Object)this,dataFolder,cache,name,callback.getReturnValue());
        }
        @Redirect(method={"set(Ljava/lang/String;Lnet/minecraft/world/level/saveddata/SavedData;)V",
                "get(Ljava/util/function/Function;Ljava/lang/String;)Lnet/minecraft/world/level/saveddata/SavedData;"},
            at=@At(value="INVOKE",target="Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
        private Object put(java.util.Map<Object,Object> cache,Object key,Object value) {
            return RecoverySources.cachePut((net.minecraft.world.level.storage.DimensionDataStorage)(Object)this,dataFolder,cache,key,value);
        }
    }
}
