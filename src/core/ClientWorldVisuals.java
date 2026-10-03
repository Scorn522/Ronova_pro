package dev.ronova.pro;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.ronova.pro.mixin.ClientWorldHooks;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;

/** Draw-time isolation of the actual portal clock and animated block-atlas pixels. */
public final class ClientWorldVisuals {
    private static final Map<BufferBuilder,List<Range>> building=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static final Map<BufferBuilder.RenderedBuffer,List<Range>> rendered=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static final Map<VertexBuffer,Mesh> meshes=new WeakIdentityMap<>();
    private static final Map<SpriteContents,Map<Integer,Upload>> uploads=new IdentityHashMap<>();
    private static final Map<TextureAtlas,List<TextureAtlasSprite>> atlasSprites=new WeakIdentityMap<>();
    private static final Set<Frame> frames=Collections.newSetFromMap(new IdentityHashMap<>());
    private static DrawContext drawing;
    private record Source(Level level,BlockPos position) {}
    private record Range(int first,int end,Source source) {}
    private record UV(float lowU,float highU,float lowV,float highV) {}
    private record Quad(Source source,UV uv) {}
    private record Mesh(Quad[] quads,int[] order) {}
    private record DrawContext(Frame frame,boolean portal) {}
    private ClientWorldVisuals() {}

    /** A frame belongs to one current object/position selection, never to a transport packet. */
    public static final class Frame implements AutoCloseable {
        private final WeakReference<Object> owner;
        private final BlockPos position;
        private final Map<TextureAtlasSprite,FrozenSprite> sprites=new IdentityHashMap<>();
        private final Map<RenderType,RenderType> types=new IdentityHashMap<>();
        private Float shaderTime;
        private boolean closed;
        Frame(Object owner){this.owner=new WeakReference<>(owner);position=owner instanceof BlockPos pos?pos:null;frames.add(this);}
        boolean isOpen(){return !closed;}
        private boolean current(){Object object=position==null?owner.get():position;return !closed&&object!=null&&ClientPresence.currentVisualFrame(object,this);}
        private FrozenSprite sprite(TextureAtlasSprite sprite,int texture){
            FrozenSprite existing=sprites.get(sprite);
            if(existing!=null)return existing;
            Map<Integer,Upload> locations=uploads.get(sprite.contents());
            Upload source=locations==null?null:locations.get(texture);
            if(source==null||source.x!=sprite.getX()||source.y!=sprite.getY())return null;
            FrozenSprite frozen=source.snapshot(sprite,texture);
            frozen.users++;sprites.put(sprite,frozen);return frozen;
        }
        private RenderType type(RenderType original){
            return types.computeIfAbsent(original,type->new FrozenType(type,this));
        }
        @Override public void close(){
            if(closed)return;closed=true;
            for(FrozenSprite sprite:sprites.values())sprite.release();
            sprites.clear();types.clear();frames.remove(this);
        }
        private void clearAtlas(Set<SpriteContents> contents){
            sprites.entrySet().removeIf(entry->{if(!contents.contains(entry.getKey().contents()))return false;entry.getValue().release();return true;});
        }
    }
    private static final class FrozenType extends RenderType {
        private final RenderType original;private final Frame frame;
        FrozenType(RenderType original,Frame frame){
            super("ronova_world_frame",original.format(),original.mode(),original.bufferSize(),original.affectsCrumbling(),false,original::setupRenderState,original::clearRenderState);
            this.original=original;this.frame=frame;
        }
        @Override public Optional<RenderType> outline(){return original.outline().map(frame::type);}
        @Override public boolean isOutline(){return original.isOutline();}
        @Override public void end(BufferBuilder builder,VertexSorting sorting){
            DrawContext previous=drawing;
            drawing=frame.current()?new DrawContext(frame,original==RenderType.endPortal()||original==RenderType.endGateway()):null;
            try{original.end(builder,sorting);}finally{drawing=previous;}
        }
    }
    public static VertexConsumer objectBuffer(MultiBufferSource source,RenderType type,Object object){
        Frame frame=ClientPresence.worldVisualFrame(object);
        return source.getBuffer(frame==null?type:frame.type(type));
    }
    /** Immediate first-person fire is submitted outside MultiBufferSource's RenderType batches. */
    public static void objectDraw(Object object,Runnable draw){
        Frame frame=ClientPresence.worldVisualFrame(object);DrawContext previous=drawing;
        drawing=frame==null?null:new DrawContext(frame,false);
        try{draw.run();}finally{drawing=previous;}
    }
    public static float shaderTime(float current){
        DrawContext context=drawing;
        if(context==null||!context.portal||!context.frame.current())return current;
        if(context.frame.shaderTime==null)context.frame.shaderTime=current;
        return context.frame.shaderTime;
    }

    /** Keep the real source position while the original Forge tessellator and its callbacks run. */
    public static void block(VertexConsumer vertices,BlockAndTintGetter view,BlockPos position,Runnable tessellate){
        Level level=view instanceof Level direct?direct:view instanceof RenderChunkRegion region?((ClientWorldHooks.ChunkRegion)region).pro$level():null;
        if(!(vertices instanceof BufferBuilder builder)||level==null){tessellate.run();return;}
        int first=((ClientWorldHooks.BuilderLabels)builder).pro$vertices();
        try{tessellate.run();}finally{
            int end=((ClientWorldHooks.BuilderLabels)builder).pro$vertices();
            if(end>first){
                synchronized(building){building.computeIfAbsent(builder,ignored->new ArrayList<>()).add(new Range(first,end,new Source(level,position.immutable())));}
            }
        }
    }
    public static void stored(BufferBuilder builder,BufferBuilder.RenderedBuffer buffer){
        if(buffer.drawState().indexOnly())return;
        List<Range> ranges=building.remove(builder);
        if(ranges!=null)rendered.put(buffer,List.copyOf(ranges));
    }
    public static void discard(BufferBuilder builder){building.remove(builder);}
    public static void released(BufferBuilder.RenderedBuffer buffer){rendered.remove(buffer);}
    public static void closed(VertexBuffer buffer){meshes.remove(buffer);}
    public static void uploaded(VertexBuffer buffer,BufferBuilder.RenderedBuffer upload){
        if(buffer.isInvalid()){rendered.remove(upload);return;}
        BufferBuilder.DrawState state=upload.drawState();
        if(state.mode()!=VertexFormat.Mode.QUADS||state.indexCount()%6!=0){meshes.remove(buffer);rendered.remove(upload);return;}
        Mesh previous=meshes.get(buffer);
        Quad[] quads;
        if(state.indexOnly()){
            if(previous==null)return;
            quads=previous.quads;
        }else{
            int count=state.vertexCount()/4;
            if(state.vertexCount()%4!=0){meshes.remove(buffer);rendered.remove(upload);return;}
            quads=new Quad[count];Source[] sources=new Source[count];
            List<Range> ranges=rendered.remove(upload);
            if(ranges!=null)for(Range range:ranges){
                // Nested actual tessellations finish first and keep their more specific positions.
                int low=(range.first+3)/4,high=Math.min(count,range.end/4);
                for(int quad=low;quad<high;quad++)if(sources[quad]==null)sources[quad]=range.source;
            }
            int uvOffset=-1;
            List<VertexFormatElement> elements=state.format().getElements();
            for(int i=0;i<elements.size();i++){
                VertexFormatElement element=elements.get(i);
                if(element.getUsage()==VertexFormatElement.Usage.UV&&element.getIndex()==0&&element.getType()==VertexFormatElement.Type.FLOAT&&element.getCount()==2){uvOffset=state.format().getOffset(i);break;}
            }
            ByteBuffer bytes=upload.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());int stride=state.format().getVertexSize();
            for(int quad=0;quad<count;quad++){
                UV uv=null;
                if(uvOffset>=0){
                    float lowU=Float.POSITIVE_INFINITY,highU=Float.NEGATIVE_INFINITY,lowV=Float.POSITIVE_INFINITY,highV=Float.NEGATIVE_INFINITY;
                    for(int vertex=0;vertex<4;vertex++){
                        int offset=(quad*4+vertex)*stride+uvOffset;float u=bytes.getFloat(offset),v=bytes.getFloat(offset+4);
                        lowU=Math.min(lowU,u);highU=Math.max(highU,u);lowV=Math.min(lowV,v);highV=Math.max(highV,v);
                    }
                    uv=new UV(lowU,highU,lowV,highV);
                }
                quads[quad]=new Quad(sources[quad],uv);
            }
        }
        int[] order=new int[state.indexCount()/6];
        if(state.sequentialIndex()){
            if(order.length!=quads.length){meshes.remove(buffer);return;}
            for(int i=0;i<order.length;i++)order[i]=i;
        }else{
            ByteBuffer indices=upload.indexBuffer().duplicate().order(ByteOrder.nativeOrder());int size=state.indexType().bytes;
            for(int i=0;i<order.length;i++){
                int index=index(indices,i*6*size,size),quad=index/4;
                if(quad<0||quad>=quads.length){meshes.remove(buffer);return;}
                for(int corner=1;corner<6;corner++)if(index(indices,(i*6+corner)*size,size)/4!=quad){meshes.remove(buffer);return;}
                order[i]=quad;
            }
        }
        meshes.put(buffer,new Mesh(quads,order));
    }
    private static int index(ByteBuffer bytes,int offset,int size){return size==2?Short.toUnsignedInt(bytes.getShort(offset)):bytes.getInt(offset);}

    /** The stock uploader passes either the source frame or its exact CPU interpolation image. */
    public static void spriteUploaded(SpriteContents contents,int x,int y,int sourceX,int sourceY,NativeImage[] images){
        RenderSystem.assertOnRenderThreadOrInit();
        int texture=GlStateManager._getInteger(32873); // GL_TEXTURE_BINDING_2D
        uploads.computeIfAbsent(contents,ignored->new HashMap<>()).put(texture,new Upload(x,y,sourceX,sourceY,contents.width(),contents.height(),images,contents.byMipLevel.length));
    }
    public static void atlasUploaded(TextureAtlas atlas){
        List<TextureAtlasSprite> animated=new ArrayList<>();
        for(var location:atlas.getTextureLocations()){
            TextureAtlasSprite sprite=atlas.getSprite(location);SpriteContents contents=sprite.contents();
            NativeImage original=contents.getOriginalImage();
            if(original.getWidth()!=contents.width()||original.getHeight()!=contents.height())animated.add(sprite);
        }
        atlasSprites.put(atlas,List.copyOf(animated));
    }
    public static void atlasCleared(TextureAtlas atlas){
        List<TextureAtlasSprite> old=atlasSprites.remove(atlas);
        Set<SpriteContents> contents=Collections.newSetFromMap(new IdentityHashMap<>());
        if(old!=null)for(TextureAtlasSprite sprite:old)contents.add(sprite.contents());
        int texture=atlas.getId();
        var entries=uploads.entrySet().iterator();
        while(entries.hasNext()){
            var entry=entries.next();
            if(entry.getValue().remove(texture)!=null)contents.add(entry.getKey());
            if(entry.getValue().isEmpty())entries.remove();
        }
        for(Frame frame:new ArrayList<>(frames))frame.clearAtlas(contents);
    }
    private static final class Upload {
        final int x,y,sourceX,sourceY,width,height,levels;final NativeImage[] images;
        FrozenSprite cached;
        Upload(int x,int y,int sourceX,int sourceY,int width,int height,NativeImage[] images,int levels){
            this.x=x;this.y=y;this.sourceX=sourceX;this.sourceY=sourceY;this.width=width;this.height=height;this.images=images;this.levels=Math.min(levels,images.length);
        }
        FrozenSprite snapshot(TextureAtlasSprite sprite,int texture){
            if(cached==null||cached.closed)cached=new FrozenSprite(sprite,texture,this);
            return cached;
        }
        void upload(){
            for(int level=0;level<levels;level++){
                int w=width>>level,h=height>>level;if(w<1||h<1)break;
                images[level].upload(level,x>>level,y>>level,sourceX>>level,sourceY>>level,w,h,levels>1,false);
            }
        }
    }
    private static final class FrozenSprite {
        final TextureAtlasSprite sprite;final int texture;final NativeImage[] images;
        int users;boolean closed;
        FrozenSprite(TextureAtlasSprite sprite,int texture,Upload source){
            this.sprite=sprite;this.texture=texture;List<NativeImage> copies=new ArrayList<>();
            try{
                for(int level=0;level<source.levels;level++){
                    int w=source.width>>level,h=source.height>>level;if(w<1||h<1)break;
                    NativeImage copy=new NativeImage(source.images[level].format(),w,h,false);copies.add(copy);
                    int sx=source.sourceX>>level,sy=source.sourceY>>level;
                    NativeImage original=source.images[level];
                    if(sx<0||sy<0||sx>original.getWidth()-w||sy>original.getHeight()-h)throw new IllegalStateException("SPRITE_FRAME_OUTSIDE_ACTUAL_IMAGE");
                    long from=((ClientWorldHooks.PixelImage)(Object)original).pro$pixels(),to=((ClientWorldHooks.PixelImage)(Object)copy).pro$pixels();
                    if(from==0||to==0)throw new IllegalStateException("SPRITE_FRAME_IMAGE_CLOSED");
                    int components=original.format().components();long row=(long)w*components;
                    for(int y=0;y<h;y++)org.lwjgl.system.MemoryUtil.memCopy(from+((long)(sy+y)*original.getWidth()+sx)*components,to+(long)y*row,row);
                }
                images=copies.toArray(NativeImage[]::new);
            }catch(RuntimeException|Error failure){copies.forEach(NativeImage::close);throw failure;}
        }
        void upload(){
            for(int level=0;level<images.length;level++)images[level].upload(level,sprite.getX()>>level,sprite.getY()>>level,0,0,images[level].getWidth(),images[level].getHeight(),images.length>1,false);
        }
        void release(){if(--users==0&&!closed){closed=true;for(NativeImage image:images)image.close();}}
    }

    public static void draw(VertexBuffer buffer,int mode,int count,int type){
        Mesh mesh=meshes.get(buffer);
        if(mesh==null||mesh.order.length*6!=count||!ClientPresence.worldVisualsActive()){RenderSystem.drawElements(mode,count,type);return;}
        var texture=Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        if(!(texture instanceof TextureAtlas atlas)||RenderSystem.getShaderTexture(0)!=atlas.getId()){RenderSystem.drawElements(mode,count,type);return;}
        List<TextureAtlasSprite> sprites=atlasSprites.get(atlas);
        if(sprites==null||sprites.isEmpty()){RenderSystem.drawElements(mode,count,type);return;}
        DrawContext context=drawing;Frame object=context!=null&&context.frame.current()?context.frame:null;
        int bytes=type==5123?2:type==5125?4:0;
        if(bytes==0){RenderSystem.drawElements(mode,count,type);return;}
        int first=0;FrozenSprite previous=null;
        for(int i=0;i<mesh.order.length;i++){
            Quad quad=mesh.quads[mesh.order[i]];Frame frame=object;
            if(frame==null&&quad.source!=null)frame=ClientPresence.terrainVisualFrame(quad.source.level,quad.source.position);
            FrozenSprite frozen=null;
            if(frame!=null&&quad.uv!=null){
                for(TextureAtlasSprite sprite:sprites)if(contains(sprite,quad.uv)){frozen=frame.sprite(sprite,atlas.getId());break;}
            }
            if(i==0)previous=frozen;
            else if(frozen!=previous){drawRange(mode,type,bytes,first,i,previous);first=i;previous=frozen;}
        }
        drawRange(mode,type,bytes,first,mesh.order.length,previous);
    }
    private static boolean contains(TextureAtlasSprite sprite,UV uv){
        return uv.lowU>=sprite.getU0()-0.000001F&&uv.highU<=sprite.getU1()+0.000001F&&uv.lowV>=sprite.getV0()-0.000001F&&uv.highV<=sprite.getV1()+0.000001F;
    }
    /** Only this index range sees the saved pixels; the live atlas is restored before the next draw. */
    private static void drawRange(int mode,int type,int bytes,int first,int end,FrozenSprite frozen){
        if(end<=first)return;
        if(frozen==null){GlStateManager._drawElements(mode,(end-first)*6,type,(long)first*6*bytes);return;}
        Map<Integer,Upload> locations=uploads.get(frozen.sprite.contents());Upload live=locations==null?null:locations.get(frozen.texture);
        if(live==null||frozen.closed){GlStateManager._drawElements(mode,(end-first)*6,type,(long)first*6*bytes);return;}
        int active=GlStateManager._getInteger(34016);GlStateManager._activeTexture(33984);
        int bound=GlStateManager._getInteger(32873);GlStateManager._bindTexture(frozen.texture);
        int min=org.lwjgl.opengl.GL11.glGetTexParameteri(3553,10241),mag=org.lwjgl.opengl.GL11.glGetTexParameteri(3553,10240);
        int row=GlStateManager._getInteger(3314),skipX=GlStateManager._getInteger(3316),skipY=GlStateManager._getInteger(3315),alignment=GlStateManager._getInteger(3317);
        try{
            frozen.upload();
            GlStateManager._texParameter(3553,10241,min);GlStateManager._texParameter(3553,10240,mag);
            GlStateManager._drawElements(mode,(end-first)*6,type,(long)first*6*bytes);
        }
        finally{
            try{live.upload();}finally{
                GlStateManager._texParameter(3553,10241,min);GlStateManager._texParameter(3553,10240,mag);
                GlStateManager._pixelStore(3314,row);GlStateManager._pixelStore(3316,skipX);GlStateManager._pixelStore(3315,skipY);GlStateManager._pixelStore(3317,alignment);
                GlStateManager._bindTexture(bound);GlStateManager._activeTexture(active);
            }
        }
    }
    static void clearFrames(){for(Frame frame:new ArrayList<>(frames))frame.close();drawing=null;}
    static void pruneFrames(){for(Frame frame:new ArrayList<>(frames))if(!frame.current())frame.close();}
    static Object[] controlObjects(){return new Object[]{building,rendered,meshes,uploads,atlasSprites,frames};}
}
