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
    private static final String PACKET="Lnet/minecraft/network/protocol/Packet;",LISTENER="Lnet/minecraft/network/PacketSendListener;",SEND="("+PACKET+LISTENER+")V";
    private static final String NETTY="io/netty/channel/AbstractChannelHandlerContext";
    private static final String WRITE=NETTY+"$WriteTask",BYTE_ENCODER="io/netty/handler/codec/MessageToByteEncoder";
    static final Set<String> TARGETS=Set.of(INSTANCE,REGISTRY,SIMPLE,CODEC,HANDLER,EVENT,CONNECTION,HOLDER,ENCODER,NETTY,WRITE,BYTE_ENCODER);
    private NetworkBoundary(){}
    static byte[] transform(ClassLoader loader,String name,byte[] bytes){
        if(!TARGETS.contains(name))return null;ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        String[] connection=name.equals(CONNECTION)?connectionLayout(node):null;
        String packetField=name.equals(HOLDER)?uniqueField(node,PACKET):null,listenerField=name.equals(HOLDER)?uniqueField(node,LISTENER):null;
        for(MethodNode method:node.methods){
            if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)continue;
            Type[] args=Type.getArgumentTypes(method.desc);int[] vars=locals(method,args);Type result=Type.getReturnType(method.desc);
            if(method.name.equals("<init>")){
                if(name.equals(INSTANCE)&&args.length==4){returns(method,code-> {code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));call(code,"channelCreated","(Ljava/lang/Object;Ljava/lang/Object;)V");});changed=true;}
                else if((name.equals(SIMPLE)||name.equals(EVENT)||name.equals(CODEC))&&args.length>0&&args[0].getDescriptor().equals("L"+INSTANCE+";")){
                    returns(method,code->{code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));call(code,"wrapperCreated","(Ljava/lang/Object;Ljava/lang/Object;)V");});changed=true;
                }else if(name.equals(HANDLER)&&args.length==7){
                    returns(method,code->{code.add(new VarInsnNode(Opcodes.ALOAD,0));for(int i:new int[]{0,2,3,4,5})code.add(new VarInsnNode(Opcodes.ALOAD,vars[i]));call(code,"handlerCreated","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");});changed=true;
                }else if(name.equals(HOLDER)&&method.desc.equals(SEND)){
                    returns(method,code->{code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,vars[0]));code.add(new VarInsnNode(Opcodes.ALOAD,vars[1]));code.add(new LdcInsnNode(packetField));code.add(new LdcInsnNode(listenerField));call(code,"holderCreated","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V");});changed=true;
                }else if(name.equals(CONNECTION)){
                    returns(method,code->{code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new FieldInsnNode(Opcodes.GETFIELD,name,connection[0],"Ljava/util/Queue;"));for(String value:connection)code.add(new LdcInsnNode(value));call(code,"connectionCreated","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");});changed=true;
                }continue;
            }
            if(connection!=null)connectionCalls(method,connection);
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
            if(name.equals(CONNECTION)&&method.name.equals("exceptionCaught")&&method.desc.equals("(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Throwable;)V")){
                InsnList guard=gate(vars[1],"outboundFailureUnhandled");guard.add(new InsnNode(Opcodes.RETURN));endGate(guard);method.instructions.insert(guard);changed=true;
            }
            if(name.equals(ENCODER)&&args.length==3&&args[1].getDescriptor().equals("Lnet/minecraft/network/protocol/Packet;")&&result.getSort()==Type.VOID){
                returns(method,code->{code.add(new VarInsnNode(Opcodes.ALOAD,vars[1]));code.add(new VarInsnNode(Opcodes.ALOAD,vars[2]));call(code,"packetEncoded","(Ljava/lang/Object;Ljava/lang/Object;)V");});changed=true;
            }
            if(name.equals(NETTY)&&method.name.equals("write")&&args.length==3&&args[0].getDescriptor().equals("Ljava/lang/Object;")&&args[1].getSort()==Type.BOOLEAN&&args[2].getDescriptor().equals("Lio/netty/channel/ChannelPromise;")){
                if(result.getSort()!=Type.VOID)throw new IllegalStateException("NETTY_WRITE_RETURN_LAYOUT_UNAVAILABLE:"+method.desc);
                InsnList guard=gate(vars[0],"outboundAllowed");rejectOutbound(method,guard,vars[0],vars[2],vars[1],"flush");
                guard.add(new InsnNode(Opcodes.RETURN));endGate(guard);afterWriteValidation(method,guard);changed=true;
            }
            if(name.equals(NETTY)&&Set.of("invokeWrite","invokeWriteAndFlush").contains(method.name)&&args.length==2&&args[0].getDescriptor().equals("Ljava/lang/Object;")&&args[1].getDescriptor().equals("Lio/netty/channel/ChannelPromise;")&&(method.access&Opcodes.ACC_STATIC)==0){
                InsnList guard=gate(vars[0],"outboundAllowed");rejectOutbound(method,guard,vars[0],vars[1],-1,method.name.equals("invokeWriteAndFlush")?"invokeFlush":null);
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
            if(name.equals(WRITE)&&(method.name.equals("run")||method.name.equals("cancel"))&&method.desc.equals("()V")){scope(method,0,"writeEnter");changed=true;}
            if(name.equals(WRITE)&&method.name.equals("recycle")&&method.desc.equals("()V")){
                writeRecycle(method);
                InsnList retired=new InsnList();retired.add(new VarInsnNode(Opcodes.ALOAD,0));call(retired,"writeRetired","(Ljava/lang/Object;)V");method.instructions.insert(retired);changed=true;
            }
        }
        if(!changed)throw new IllegalStateException("NETWORK_BOUNDARY_LAYOUT_UNAVAILABLE:"+name);
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
    private static String uniqueField(ClassNode node,String descriptor){
        String result=null;for(FieldNode field:node.fields)if((field.access&Opcodes.ACC_STATIC)==0&&field.desc.equals(descriptor)){
            if(result!=null)throw new IllegalStateException("NETWORK_FIELD_LAYOUT_UNAVAILABLE:"+node.name+":"+descriptor);result=field.name;
        }if(result==null)throw new IllegalStateException("NETWORK_FIELD_LAYOUT_UNAVAILABLE:"+node.name+":"+descriptor);return result;
    }
    private static List<AbstractInsnNode> instructions(MethodNode method){return Arrays.stream(method.instructions.toArray()).filter(at->at.getOpcode()>=0).toList();}
    /** Resolve names from the actual queue producer/consumer, including SRG images. */
    private static String[] connectionLayout(ClassNode node){
        String[] layout={uniqueField(node,"Ljava/util/Queue;"),null,null,null,null,null};
        for(MethodNode method:node.methods)if((method.access&Opcodes.ACC_STATIC)==0){
            for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode invoke&&invoke.owner.equals("java/util/Queue")){
                if(invoke.name.equals("add")&&invoke.desc.equals("(Ljava/lang/Object;)Z")&&method.desc.equals(SEND)){
                    if(layout[1]!=null)throw new IllegalStateException("CONNECTION_PACKET_PUBLICATION_AMBIGUOUS");layout[1]=method.name;
                }else if(invoke.name.equals("poll")&&invoke.desc.equals("()Ljava/lang/Object;")&&method.desc.equals("()V")){
                    if(layout[2]!=null)throw new IllegalStateException("CONNECTION_PACKET_CONSUMPTION_AMBIGUOUS");layout[2]=method.name;
                    for(AbstractInsnNode part:method.instructions.toArray()){
                        if(part instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(HOLDER)){
                            if(field.desc.equals(PACKET))layout[4]=field.name;else if(field.desc.equals(LISTENER))layout[5]=field.name;
                        }else if(part instanceof MethodInsnNode send&&send.owner.equals(CONNECTION)&&send.desc.equals(SEND)){
                            if(layout[3]!=null)throw new IllegalStateException("CONNECTION_PACKET_DISPATCH_AMBIGUOUS");layout[3]=send.name;
                        }
                    }
                }
            }
        }
        for(String value:layout)if(value==null)throw new IllegalStateException("CONNECTION_PACKET_QUEUE_LAYOUT_UNAVAILABLE");return layout;
    }
    private static void connectionCalls(MethodNode method,String[] layout){
        if(method.name.equals(layout[1])&&method.desc.equals(SEND)){
            int count=0;for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode invoke&&invoke.owner.equals("java/util/Queue")&&invoke.name.equals("add")&&invoke.desc.equals("(Ljava/lang/Object;)Z")){
                method.instructions.insertBefore(at,new VarInsnNode(Opcodes.ALOAD,0));method.instructions.set(at,new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"packetQueued","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",false));count++;
            }if(count!=1)throw new IllegalStateException("CONNECTION_PACKET_PUBLICATION_MISSING");
        }
        if(method.name.equals(layout[2])&&method.desc.equals("()V")){
            int polls=0,sends=0;List<AbstractInsnNode> code=instructions(method);
            for(int i=0;i<code.size();i++)if(code.get(i) instanceof MethodInsnNode invoke){
                if(invoke.owner.equals("java/util/Queue")&&invoke.name.equals("poll")&&invoke.desc.equals("()Ljava/lang/Object;")){
                    if(i<1||!(code.get(i-1) instanceof FieldInsnNode field)||!field.owner.equals(CONNECTION)||!field.name.equals(layout[0]))throw new IllegalStateException("CONNECTION_PACKET_POLL_OWNER_UNAVAILABLE");
                    method.instructions.insertBefore(invoke,new VarInsnNode(Opcodes.ALOAD,0));method.instructions.set(invoke,new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"packetPoll","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));polls++;
                }else if(invoke.owner.equals(CONNECTION)&&invoke.name.equals(layout[3])&&invoke.desc.equals(SEND)){
                    if(i<5||!(code.get(i-1) instanceof FieldInsnNode listener)||!listener.owner.equals(HOLDER)||!listener.name.equals(layout[5])
                            ||!(code.get(i-2) instanceof VarInsnNode holder)||holder.getOpcode()!=Opcodes.ALOAD
                            ||!(code.get(i-3) instanceof FieldInsnNode packet)||!packet.owner.equals(HOLDER)||!packet.name.equals(layout[4])
                            ||!(code.get(i-4) instanceof VarInsnNode same)||same.getOpcode()!=Opcodes.ALOAD||same.var!=holder.var)throw new IllegalStateException("CONNECTION_PACKET_DISPATCH_PAIR_UNAVAILABLE");
                    method.instructions.insertBefore(invoke,new VarInsnNode(Opcodes.ALOAD,holder.var));method.instructions.set(invoke,new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"packetQueueSend","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",false));sends++;
                }
            }
            if(polls!=1||sends!=1)throw new IllegalStateException("CONNECTION_PACKET_CONSUMPTION_MISSING");
            int token=method.maxLocals++,error=method.maxLocals++;LabelNode start=new LabelNode(),end=new LabelNode(),complete=new LabelNode(),failed=new LabelNode();
            for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN)method.instructions.set(at,new JumpInsnNode(Opcodes.GOTO,complete));
            InsnList enter=new InsnList();enter.add(new VarInsnNode(Opcodes.ALOAD,0));enter.add(new FieldInsnNode(Opcodes.GETFIELD,CONNECTION,layout[0],"Ljava/util/Queue;"));enter.add(new VarInsnNode(Opcodes.ALOAD,0));call(enter,"packetQueueEntering","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");enter.add(new VarInsnNode(Opcodes.ASTORE,token));enter.add(start);method.instructions.insert(enter);
            InsnList tail=new InsnList();tail.add(end);tail.add(complete);tail.add(new VarInsnNode(Opcodes.ALOAD,token));call(tail,"packetQueueExiting","(Ljava/lang/Object;)V");tail.add(new InsnNode(Opcodes.RETURN));
            tail.add(failed);tail.add(new VarInsnNode(Opcodes.ASTORE,error));tail.add(new VarInsnNode(Opcodes.ALOAD,token));call(tail,"packetQueueExiting","(Ljava/lang/Object;)V");tail.add(new VarInsnNode(Opcodes.ALOAD,error));tail.add(new InsnNode(Opcodes.ATHROW));method.instructions.add(tail);method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,failed,null));
        }
        if(method.name.equals(layout[3])&&method.desc.equals(SEND)){
            int count=0;for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode invoke&&invoke.owner.equals("io/netty/channel/EventLoop")&&invoke.name.equals("execute")&&invoke.desc.equals("(Ljava/lang/Runnable;)V")){
                InsnList actual=new InsnList();actual.add(new VarInsnNode(Opcodes.ALOAD,0));actual.add(new VarInsnNode(Opcodes.ALOAD,1));actual.add(new VarInsnNode(Opcodes.ALOAD,2));method.instructions.insertBefore(at,actual);
                method.instructions.set(at,new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"packetDeferred","(Ljava/lang/Object;Ljava/lang/Runnable;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",false));count++;
            }if(count!=1)throw new IllegalStateException("CONNECTION_PACKET_EVENTLOOP_HANDOFF_MISSING");
        }
    }
    private static int[] locals(MethodNode method,Type[] args){int[] result=new int[args.length];int at=(method.access&Opcodes.ACC_STATIC)==0?1:0;for(int i=0;i<args.length;i++){result[i]=at;at+=args[i].getSize();}return result;}
    private static void afterWriteValidation(MethodNode method,InsnList guard){
        var checks=Arrays.stream(method.instructions.toArray()).filter(at->at instanceof MethodInsnNode call&&call.owner.equals(NETTY)
                &&call.name.equals("isNotValidPromise")&&call.desc.equals("(Lio/netty/channel/ChannelPromise;Z)Z")).toList();
        if(checks.size()!=1)throw new IllegalStateException("NETTY_WRITE_PROMISE_VALIDATION_UNAVAILABLE");
        AbstractInsnNode next=checks.get(0).getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();
        if(!(next instanceof JumpInsnNode valid)||valid.getOpcode()!=Opcodes.IFEQ)throw new IllegalStateException("NETTY_WRITE_PROMISE_VALID_BRANCH_UNAVAILABLE");
        // Netty owns null/message checks, cancelled-promise release and foreign or
        // completed-promise rejection. A stopped write may touch its promise and
        // flush earlier messages only after that original validation accepts it.
        method.instructions.insert(valid.label,guard);
    }
    private static void call(InsnList code,String name,String desc){code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,name,desc,false));}
    private static void writeRecycle(MethodNode method){
        var calls=Arrays.stream(method.instructions.toArray()).filter(at->at instanceof MethodInsnNode invoke
                &&invoke.getOpcode()==Opcodes.INVOKEINTERFACE&&invoke.owner.equals("io/netty/util/internal/ObjectPool$Handle")
                &&invoke.name.equals("recycle")&&invoke.desc.equals("(Ljava/lang/Object;)V")).toList();
        if(calls.size()!=1)throw new IllegalStateException("NETTY_WRITE_RECYCLE_CALL_UNAVAILABLE");
        // Keep the original field clearing and the real run/cancel finally. A
        // retired item need not publish itself back into a shared recycler.
        LabelNode allowed=new LabelNode();InsnList gate=new InsnList();gate.add(new VarInsnNode(Opcodes.ALOAD,0));
        call(gate,"writeRecycleAllowed","(Ljava/lang/Object;)Z");gate.add(new JumpInsnNode(Opcodes.IFNE,allowed));
        gate.add(new InsnNode(Opcodes.POP2));gate.add(new InsnNode(Opcodes.RETURN));gate.add(allowed);method.instructions.insertBefore(calls.get(0),gate);
    }
    private static void rejectOutbound(MethodNode method,InsnList code,int message,int promise,int flush,String flushMethod){
        int token=method.maxLocals++,released=method.maxLocals++,failed=method.maxLocals++,flushed=method.maxLocals++;
        code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,message));code.add(new VarInsnNode(Opcodes.ALOAD,promise));
        call(code,"outboundCleanupEnter","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");code.add(new VarInsnNode(Opcodes.ASTORE,token));
        for(int failure:new int[]{released,failed,flushed}){code.add(new InsnNode(Opcodes.ACONST_NULL));code.add(new VarInsnNode(Opcodes.ASTORE,failure));}
        // Each original action is attempted once. A release failure must not strand
        // the promise or prevent the original flush of already accepted messages.
        cleanupStep(method,code,token,0,released,step->{
            step.add(new VarInsnNode(Opcodes.ALOAD,message));step.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"io/netty/util/ReferenceCountUtil","release","(Ljava/lang/Object;)Z",false));step.add(new InsnNode(Opcodes.POP));
        });
        cleanupStep(method,code,token,1,failed,step->{
            step.add(new VarInsnNode(Opcodes.ALOAD,promise));call(step,"outboundRejected","()Ljava/lang/Throwable;");
            step.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,"io/netty/channel/ChannelPromise","tryFailure","(Ljava/lang/Throwable;)Z",true));step.add(new InsnNode(Opcodes.POP));
        });
        if(flushMethod!=null){
            LabelNode noFlush=new LabelNode();if(flush>=0){code.add(new VarInsnNode(Opcodes.ILOAD,flush));code.add(new JumpInsnNode(Opcodes.IFEQ,noFlush));}
            // Preserve context selection/executor semantics: submission uses flush,
            // while an already dispatched WriteTask uses its original invokeFlush.
            cleanupStep(method,code,token,2,flushed,step->{
                step.add(new VarInsnNode(Opcodes.ALOAD,0));step.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,NETTY,flushMethod,flushMethod.equals("flush")?"()Lio/netty/channel/ChannelHandlerContext;":"()V",false));
                if(flushMethod.equals("flush"))step.add(new InsnNode(Opcodes.POP));
            });code.add(noFlush);
        }
        // No throwing operation remains outside the three handlers before exit.
        // The bridge restores its exact cleanup token in finally and combines the
        // failures there, without giving a user callback the token's write policy.
        code.add(new VarInsnNode(Opcodes.ALOAD,token));for(int failure:new int[]{released,failed,flushed})code.add(new VarInsnNode(Opcodes.ALOAD,failure));
        call(code,"outboundCleanupExit","(Ljava/lang/Object;Ljava/lang/Throwable;Ljava/lang/Throwable;Ljava/lang/Throwable;)Ljava/lang/Throwable;");
        LabelNode complete=new LabelNode();code.add(new InsnNode(Opcodes.DUP));code.add(new JumpInsnNode(Opcodes.IFNULL,complete));code.add(new InsnNode(Opcodes.ATHROW));code.add(complete);code.add(new InsnNode(Opcodes.POP));
    }
    private static void cleanupStep(MethodNode method,InsnList code,int token,int phase,int failure,java.util.function.Consumer<InsnList> action){
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),complete=new LabelNode();code.add(start);
        code.add(new VarInsnNode(Opcodes.ALOAD,token));code.add(new InsnNode(Opcodes.ICONST_0+phase));call(code,"outboundCleanupPhase","(Ljava/lang/Object;I)V");action.accept(code);
        code.add(end);code.add(new JumpInsnNode(Opcodes.GOTO,complete));code.add(handler);code.add(new VarInsnNode(Opcodes.ASTORE,failure));code.add(complete);
        // These action-local handlers must precede any original Netty handler;
        // only the merged failure escapes after the exact cleanup scope closes.
        method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
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
