package dev.ronova.pro.agent;

import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Channel/codec dispatch, real packet queues and the actual Netty write operation. */
final class NetworkBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/NetworkBridge";
    private static final String INSTANCE="net/minecraftforge/network/NetworkInstance",REGISTRY="net/minecraftforge/network/NetworkRegistry";
    private static final String SIMPLE="net/minecraftforge/network/simple/SimpleChannel",CODEC="net/minecraftforge/network/simple/IndexedMessageCodec";
    private static final String HANDLER=CODEC+"$MessageHandler",EVENT="net/minecraftforge/network/event/EventNetworkChannel";
    private static final String CONNECTION="net/minecraft/network/Connection",HOLDER=CONNECTION+"$PacketHolder",ENCODER="net/minecraft/network/PacketEncoder";
    private static final String NETTY="io/netty/channel/AbstractChannelHandlerContext";
    private static final String WRITE=NETTY+"$WriteTask",BYTE_ENCODER="io/netty/handler/codec/MessageToByteEncoder";
    static final Set<String> TARGETS=Set.of(INSTANCE,REGISTRY,SIMPLE,CODEC,HANDLER,EVENT,CONNECTION,HOLDER,ENCODER,NETTY,WRITE,BYTE_ENCODER);
    private NetworkBoundary(){}
    static byte[] transform(ClassLoader loader,String name,byte[] bytes){
        if(!TARGETS.contains(name))return null;ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(MethodNode method:node.methods){
            if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)continue;
            Type[] args=Type.getArgumentTypes(method.desc);int[] vars=locals(method,args);Type result=Type.getReturnType(method.desc);
            if(method.name.equals("<init>")){
                if(name.equals(INSTANCE)&&args.length==4){returns(method,code-> {code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));call(code,"channelCreated","(Ljava/lang/Object;Ljava/lang/Object;)V");});changed=true;}
                else if((name.equals(SIMPLE)||name.equals(EVENT)||name.equals(CODEC))&&args.length>0&&args[0].getDescriptor().equals("L"+INSTANCE+";")){
                    returns(method,code->{code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));call(code,"wrapperCreated","(Ljava/lang/Object;Ljava/lang/Object;)V");});changed=true;
                }else if(name.equals(HANDLER)&&args.length==7){
                    returns(method,code->{code.add(new VarInsnNode(Opcodes.ALOAD,0));for(int i:new int[]{0,2,3,4,5})code.add(new VarInsnNode(Opcodes.ALOAD,vars[i]));call(code,"handlerCreated","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");});changed=true;
                }else if(name.equals(HOLDER)&&args.length>0){
                    returns(method,code->{code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));call(code,"holderCreated","(Ljava/lang/Object;Ljava/lang/Object;)V");});changed=true;
                }else if(name.equals(CONNECTION)){
                    FieldNode queue=node.fields.stream().filter(field->field.desc.equals("Ljava/util/Queue;")&&(field.access&Opcodes.ACC_STATIC)==0).findFirst().orElse(null);
                    if(queue==null)throw new IllegalStateException("CONNECTION_PACKET_QUEUE_LAYOUT_UNAVAILABLE");
                    returns(method,code->{code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new FieldInsnNode(Opcodes.GETFIELD,name,queue.name,queue.desc));code.add(new LdcInsnNode(queue.name));call(code,"connectionCreated","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V");});changed=true;
                }continue;
            }
            if(name.equals(REGISTRY)&&method.name.equals("createInstance")&&result.getDescriptor().equals("L"+INSTANCE+";")){
                returns(method,code->{code.add(new InsnNode(Opcodes.DUP));call(code,"routePublished","(Ljava/lang/Object;)V");});changed=true;
            }
            if(name.equals(INSTANCE)&&Set.of("dispatch","dispatchGatherLogin","dispatchLoginPacket","dispatchEvent","addListener","addGatherListener","registerObject").contains(method.name)){
                InsnList guard=gate(0,"channelAllowed");if(result.getSort()==Type.BOOLEAN){guard.add(new InsnNode(Opcodes.ICONST_1));guard.add(new InsnNode(Opcodes.IRETURN));}else guard.add(new InsnNode(Opcodes.RETURN));endGate(guard);method.instructions.insert(guard);
                if(method.name.startsWith("dispatch"))scope(method,0);changed=true;
            }
            if(name.equals(SIMPLE)&&Set.of("send","sendTo","sendToServer","reply","toVanillaPacket","toBuffer").contains(method.name)&&args.length>0){
                int message=method.name.equals("send")?vars[1]:vars[0];InsnList guard=new InsnList();LabelNode allowed=new LabelNode();
                guard.add(new VarInsnNode(Opcodes.ALOAD,0));guard.add(new VarInsnNode(Opcodes.ALOAD,message));call(guard,"messageAllowed","(Ljava/lang/Object;Ljava/lang/Object;)Z");guard.add(new JumpInsnNode(Opcodes.IFNE,allowed));
                if(result.getSort()!=Type.VOID)guard.add(new InsnNode(Opcodes.ACONST_NULL));guard.add(new InsnNode(result.getSort()==Type.VOID?Opcodes.RETURN:Opcodes.ARETURN));guard.add(allowed);
                method.instructions.insert(guard);
                if(method.name.equals("toVanillaPacket")||method.name.equals("toBuffer")){
                    int output=method.maxLocals++;String hook=method.name.equals("toBuffer")?"bufferProduced":"packetProduced";
                    returns(method,code->{code.add(new InsnNode(Opcodes.DUP));code.add(new VarInsnNode(Opcodes.ASTORE,output));code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,message));code.add(new VarInsnNode(Opcodes.ALOAD,output));call(code,hook,"(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");});
                }changed=true;
            }
            if(name.equals(CODEC)&&(method.name.equals("tryDecode")||method.name.equals("tryEncode"))){
                int owner=vars[method.name.equals("tryDecode")?3:2];InsnList guard=gate(owner,"codecAllowed");
                if(method.name.equals("tryDecode")){guard.add(new VarInsnNode(Opcodes.ALOAD,vars[1]));call(guard,"skippedDecode","(Ljava/lang/Object;)V");guard.add(new InsnNode(Opcodes.RETURN));}
                else{guard.add(new LdcInsnNode(Integer.MIN_VALUE));guard.add(new InsnNode(Opcodes.IRETURN));}endGate(guard);method.instructions.insert(guard);scope(method,owner);changed=true;
            }
            if(name.equals(CONNECTION)&&result.getSort()==Type.VOID){
                int packet=-1;for(int i=0;i<args.length;i++)if(args[i].getDescriptor().equals("Lnet/minecraft/network/protocol/Packet;")){packet=vars[i];break;}
                if(packet>=0){InsnList guard=gate(packet,"packetAllowed");guard.add(new InsnNode(Opcodes.RETURN));endGate(guard);method.instructions.insert(guard);changed=true;}
            }
            if(name.equals(ENCODER)&&args.length==3&&args[1].getDescriptor().equals("Lnet/minecraft/network/protocol/Packet;")&&result.getSort()==Type.VOID){
                returns(method,code->{code.add(new VarInsnNode(Opcodes.ALOAD,vars[1]));code.add(new VarInsnNode(Opcodes.ALOAD,vars[2]));call(code,"packetEncoded","(Ljava/lang/Object;Ljava/lang/Object;)V");});changed=true;
            }
            if(name.equals(NETTY)&&method.name.equals("write")&&args.length==3&&args[0].getDescriptor().equals("Ljava/lang/Object;")&&args[1].getSort()==Type.BOOLEAN&&args[2].getDescriptor().equals("Lio/netty/channel/ChannelPromise;")){
                if(result.getSort()!=Type.VOID)throw new IllegalStateException("NETTY_WRITE_RETURN_LAYOUT_UNAVAILABLE:"+method.desc);
                InsnList guard=gate(vars[0],"outboundAllowed");guard.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"io/netty/util/ReferenceCountUtil","release","(Ljava/lang/Object;)Z",false));guard.add(new InsnNode(Opcodes.POP));
                guard.add(new VarInsnNode(Opcodes.ALOAD,vars[2]));guard.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,"io/netty/channel/ChannelPromise","trySuccess","()Z",true));guard.add(new InsnNode(Opcodes.POP));
                guard.add(new InsnNode(Opcodes.RETURN));endGate(guard);method.instructions.insert(guard);changed=true;
            }
            if(name.equals(NETTY)&&Set.of("invokeWrite","invokeWriteAndFlush").contains(method.name)&&args.length==2&&args[0].getDescriptor().equals("Ljava/lang/Object;")&&args[1].getDescriptor().equals("Lio/netty/channel/ChannelPromise;")&&(method.access&Opcodes.ACC_STATIC)==0){
                InsnList guard=gate(vars[0],"outboundAllowed");guard.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"io/netty/util/ReferenceCountUtil","release","(Ljava/lang/Object;)Z",false));guard.add(new InsnNode(Opcodes.POP));
                guard.add(new VarInsnNode(Opcodes.ALOAD,vars[1]));guard.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,"io/netty/channel/ChannelPromise","trySuccess","()Z",true));guard.add(new InsnNode(Opcodes.POP));
                if(method.name.equals("invokeWriteAndFlush")){guard.add(new VarInsnNode(Opcodes.ALOAD,0));guard.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,name,"invokeFlush","()V",false));}
                guard.add(new InsnNode(Opcodes.RETURN));endGate(guard);method.instructions.insert(guard);changed=true;
            }
            if(name.equals(BYTE_ENCODER)&&method.name.equals("write")&&args.length==3){
                for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode call&&call.name.equals("encode")&&call.desc.equals("(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;Lio/netty/buffer/ByteBuf;)V")){
                    int buffer=method.maxLocals++;InsnList retain=new InsnList();retain.add(new InsnNode(Opcodes.DUP));retain.add(new VarInsnNode(Opcodes.ASTORE,buffer));method.instructions.insertBefore(at,retain);
                    InsnList encoded=new InsnList();encoded.add(new VarInsnNode(Opcodes.ALOAD,buffer));call(encoded,"encodedBuffer","(Ljava/lang/Object;)V");method.instructions.insert(at,encoded);
                }scope(method,vars[1],"encoderEnter");changed=true;
            }
            if(name.equals(WRITE)&&method.name.equals("init")&&args.length==5){
                returns(method,code->{for(int i:new int[]{0,1,2,3})code.add(new VarInsnNode(Opcodes.ALOAD,vars[i]));call(code,"writeCreated","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");});changed=true;
            }
            if(name.equals(WRITE)&&method.name.equals("run")&&method.desc.equals("()V")){scope(method,0,"writeEnter");changed=true;}
            if(name.equals(WRITE)&&method.name.equals("recycle")&&method.desc.equals("()V")){
                InsnList retired=new InsnList();retired.add(new VarInsnNode(Opcodes.ALOAD,0));call(retired,"writeRetired","(Ljava/lang/Object;)V");method.instructions.insert(retired);changed=true;
            }
        }
        if(!changed)throw new IllegalStateException("NETWORK_BOUNDARY_LAYOUT_UNAVAILABLE:"+name);
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
    private static int[] locals(MethodNode method,Type[] args){int[] result=new int[args.length];int at=(method.access&Opcodes.ACC_STATIC)==0?1:0;for(int i=0;i<args.length;i++){result[i]=at;at+=args[i].getSize();}return result;}
    private static void call(InsnList code,String name,String desc){code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,name,desc,false));}
    private static InsnList gate(int value,String operation){InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,value));call(code,operation,"(Ljava/lang/Object;)Z");code.add(new JumpInsnNode(Opcodes.IFNE,new LabelNode()));return code;}
    private static void endGate(InsnList code){for(AbstractInsnNode at:code.toArray())if(at instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.IFNE){code.add(jump.label);return;}}
    private static void returns(MethodNode method,java.util.function.Consumer<InsnList> callback){
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()>=Opcodes.IRETURN&&at.getOpcode()<=Opcodes.RETURN){InsnList code=new InsnList();callback.accept(code);method.instructions.insertBefore(at,code);}
    }
    private static void scope(MethodNode method,int owner){
        scope(method,owner,"enter");
    }
    private static void scope(MethodNode method,int owner,String operation){
        int token=method.maxLocals++,error=method.maxLocals++;LabelNode start=new LabelNode(),end=new LabelNode(),failed=new LabelNode();
        InsnList enter=new InsnList();enter.add(new VarInsnNode(Opcodes.ALOAD,owner));call(enter,operation,"(Ljava/lang/Object;)Ljava/lang/Object;");enter.add(new VarInsnNode(Opcodes.ASTORE,token));enter.add(start);method.instructions.insert(enter);
        returns(method,code->{code.add(new VarInsnNode(Opcodes.ALOAD,token));call(code,"exit","(Ljava/lang/Object;)V");});
        method.instructions.add(end);method.instructions.add(failed);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,error));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"exit","(Ljava/lang/Object;)V",false));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,error));method.instructions.add(new InsnNode(Opcodes.ATHROW));method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,failed,null));
    }
}
