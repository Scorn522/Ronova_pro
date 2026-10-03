package dev.ronova.pro.agent;

import java.util.Set;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Observes accepted layers, real module reads and actual ASM parser delivery. */
final class CodeSourceBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/CodeSourceBridge";
    static final Set<String> TARGETS=Set.of("java/lang/ModuleLayer","cpw/mods/cl/ModuleClassLoader","org/objectweb/asm/ClassReader",
            "org/objectweb/asm/tree/ClassNode","org/objectweb/asm/ClassWriter","org/spongepowered/asm/mixin/transformer/MixinInfo",
            "org/spongepowered/asm/mixin/transformer/MixinApplicatorStandard","org/spongepowered/asm/mixin/transformer/MixinTargetContext",
            "org/spongepowered/asm/mixin/transformer/ClassInfo","org/spongepowered/asm/mixin/transformer/TargetClassContext",
            "org/spongepowered/asm/mixin/transformer/ClassInfo$Member","org/spongepowered/asm/mixin/transformer/ClassInfo$Method","org/spongepowered/asm/mixin/transformer/ClassInfo$Field",
            "org/spongepowered/asm/service/modlauncher/ModLauncherClassTracker",
            "cpw/mods/modlauncher/TransformerHolder","cpw/mods/modlauncher/TransformingClassLoader",
            "cpw/mods/cl/JarModuleFinder$JarModuleReference","cpw/mods/jarhandling/impl/Jar$JarModuleDataProvider",
            "java/net/URL","java/io/InputStreamReader","org/spongepowered/asm/mixin/transformer/MixinConfig","org/spongepowered/asm/mixin/Mixins");
    static byte[] transform(ClassLoader loader,String name,byte[] bytes){
        if(!TARGETS.contains(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(MethodNode method:node.methods){
            if(resourceOrConfig(name,method)){changed=true;continue;}
            if(name.equals("org/spongepowered/asm/mixin/transformer/MixinTargetContext")&&(method.access&(Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0){
                metadataScope(method,node.superName,false);changed=true;
            }
            if(name.equals("org/spongepowered/asm/mixin/transformer/MixinInfo")&&(method.access&(Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0
                    &&Set.of("validate","parseTargets","readDeclaredTargets","readTargetClasses","getTargetClass","createContextFor").contains(method.name)){
                metadataScope(method,node.superName,true);changed=true;
            }
            if(name.equals("org/spongepowered/asm/mixin/transformer/ClassInfo")){
                if(method.name.equals("addInterface")&&method.desc.equals("(Ljava/lang/String;)V")){
                    for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){
                        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"metadataHeaderChanged","(Ljava/lang/Object;)V",false));method.instructions.insertBefore(at,code);
                    }changed=true;
                }
                if(method.name.equals("findMember")&&method.desc.equals("(Ljava/lang/String;Ljava/lang/String;ILorg/spongepowered/asm/mixin/transformer/ClassInfo$Member$Type;)Lorg/spongepowered/asm/mixin/transformer/ClassInfo$Member;")){
                    for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.ARETURN){
                        int result=method.maxLocals++;InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ASTORE,result));
                        for(int local:new int[]{0,1,2,4,result})code.add(new VarInsnNode(Opcodes.ALOAD,local));
                        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"metadataMemberRead","(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V",false));code.add(new VarInsnNode(Opcodes.ALOAD,result));method.instructions.insertBefore(at,code);
                    }
                    changed=true;
                }
                if((method.access&Opcodes.ACC_STATIC)==0&&Set.of("findInHierarchy","getSuperClass","getSuperName","getInterfaces","getSignature","isInterface","isMixin","isInner","isProbablyStatic","getOuterName","getOuterClass","getNestHost","getNestMembers","findCorrespondingType","hasMixinInHierarchy","hasMixinTargetInHierarchy").contains(method.name)){
                    InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"metadataHeaderRead","(Ljava/lang/Object;)V",false));method.instructions.insert(code);changed=true;
                }
            }
            if(Set.of("org/spongepowered/asm/mixin/transformer/ClassInfo$Method","org/spongepowered/asm/mixin/transformer/ClassInfo$Field").contains(name)&&method.name.equals("<init>")
                    &&method.desc.equals("(Lorg/spongepowered/asm/mixin/transformer/ClassInfo;Lorg/spongepowered/asm/mixin/transformer/ClassInfo$Member;)V")){
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){
                    InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,2));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"metadataMemberCopied","(Ljava/lang/Object;Ljava/lang/Object;)V",false));method.instructions.insertBefore(at,code);
                }changed=true;
            }
            if(Set.of("org/spongepowered/asm/mixin/transformer/ClassInfo$Method","org/spongepowered/asm/mixin/transformer/ClassInfo$Field").contains(name)&&method.name.equals("<init>")
                    &&Set.of("(Lorg/spongepowered/asm/mixin/transformer/ClassInfo;Lorg/objectweb/asm/tree/MethodNode;Z)V","(Lorg/spongepowered/asm/mixin/transformer/ClassInfo;Lorg/objectweb/asm/tree/FieldNode;Z)V").contains(method.desc)){
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){
                    InsnList code=new InsnList();for(int local=0;local<3;local++)code.add(new VarInsnNode(Opcodes.ALOAD,local));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"metadataMemberCreated","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",false));method.instructions.insertBefore(at,code);
                }changed=true;
            }
            if(Set.of("org/spongepowered/asm/mixin/transformer/ClassInfo$Member","org/spongepowered/asm/mixin/transformer/ClassInfo$Method","org/spongepowered/asm/mixin/transformer/ClassInfo$Field").contains(name)
                    &&(method.access&(Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0
                    &&Set.of("getName","getDesc","getOriginalName","getOriginalDesc","getOwner","getImplementor","getFrames","isAccessor","isConformed","isInjected","isStatic","isPrivate","isSynthetic","isUnique","isDecoratedFinal","isDecoratedMutable","matchesFlags").contains(method.name)){
                InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"metadataMemberUsed","(Ljava/lang/Object;)V",false));method.instructions.insert(code);changed=true;
            }
            if(Set.of("org/spongepowered/asm/mixin/transformer/ClassInfo$Member","org/spongepowered/asm/mixin/transformer/ClassInfo$Method","org/spongepowered/asm/mixin/transformer/ClassInfo$Field").contains(name)
                    &&(method.access&(Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0&&Set.of("renameTo","remapTo","setUnique","setDecoratedFinal","conform").contains(method.name)){
                memberMutation(method);changed=true;
            }
            if(name.equals("org/spongepowered/asm/service/modlauncher/ModLauncherClassTracker")&&method.name.equals("registerInvalidClass")&&method.desc.equals("(Ljava/lang/String;)V")){
                trackerRegistration(method);changed=true;continue;
            }
            if(name.equals("org/spongepowered/asm/mixin/transformer/MixinInfo")&&method.name.equals("<init>")
                    &&method.desc.equals("(Lorg/spongepowered/asm/service/IMixinService;Lorg/spongepowered/asm/mixin/transformer/MixinConfig;Ljava/lang/String;Lorg/spongepowered/asm/mixin/transformer/PluginHandle;ZLorg/spongepowered/asm/mixin/transformer/ext/Extensions;)V")){
                mixinRegistration(method);
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){
                    InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ILOAD,5));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"mixinConstructed","(Ljava/lang/Object;Z)V",false));method.instructions.insertBefore(at,code);
                }
                changed=true;
            }
            if(name.equals("org/spongepowered/asm/mixin/transformer/ClassInfo")&&method.name.equals("<init>")
                    &&method.desc.equals("(Lorg/objectweb/asm/tree/ClassNode;)V")){
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){
                    InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,1));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"classInfoCreated","(Ljava/lang/Object;Ljava/lang/Object;)V",false));method.instructions.insertBefore(at,code);
                }
                changed=true;
            }
            if(name.equals("org/spongepowered/asm/mixin/transformer/TargetClassContext")&&method.name.equals("<init>")
                    &&method.desc.equals("(Lorg/spongepowered/asm/mixin/MixinEnvironment;Lorg/spongepowered/asm/mixin/transformer/ext/Extensions;Ljava/lang/String;Ljava/lang/String;Lorg/objectweb/asm/tree/ClassNode;Ljava/util/SortedSet;)V")){
                for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN){
                    InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,6));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"targetSelection","(Ljava/lang/Object;Ljava/lang/Object;)V",false));method.instructions.insertBefore(at,code);
                }
                changed=true;
            }
            if(name.equals("org/spongepowered/asm/mixin/transformer/MixinInfo")&&method.name.equals("createContextFor")&&method.desc.equals("(Lorg/spongepowered/asm/mixin/transformer/TargetClassContext;)Lorg/spongepowered/asm/mixin/transformer/MixinTargetContext;")){
                InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"mixinPrepare","(Ljava/lang/Object;)V",false));method.instructions.insert(code);changed=true;
            }
            boolean layer=name.equals("java/lang/ModuleLayer")&&method.name.startsWith("defineModules")
                    &&(method.desc.endsWith("Ljava/lang/ModuleLayer;")||method.desc.endsWith("Ljava/lang/ModuleLayer$Controller;"));
            boolean read=name.equals("cpw/mods/cl/ModuleClassLoader")&&method.name.equals("getClassBytes")
                    &&method.desc.equals("(Ljava/lang/module/ModuleReader;Ljava/lang/module/ModuleReference;Ljava/lang/String;)[B");
            boolean reader=name.equals("org/objectweb/asm/ClassReader")&&method.name.equals("<init>")&&method.desc.startsWith("([B");
            boolean tree=name.equals("org/objectweb/asm/ClassReader")&&method.name.equals("accept")
                    &&method.desc.equals("(Lorg/objectweb/asm/ClassVisitor;[Lorg/objectweb/asm/Attribute;I)V");
            boolean copy=name.equals("org/objectweb/asm/tree/ClassNode")&&method.name.equals("accept")&&method.desc.equals("(Lorg/objectweb/asm/ClassVisitor;)V");
            boolean output=name.equals("org/objectweb/asm/ClassWriter")&&method.name.equals("toByteArray")&&method.desc.equals("()[B");
            boolean mixin=name.equals("org/spongepowered/asm/mixin/transformer/MixinInfo")&&method.name.equals("loadMixinClass")
                    &&method.desc.equals("(Ljava/lang/String;)Lorg/objectweb/asm/tree/ClassNode;");
            boolean apply=name.equals("org/spongepowered/asm/mixin/transformer/MixinApplicatorStandard")&&method.name.equals("applyMixin")
                    &&method.desc.equals("(Lorg/spongepowered/asm/mixin/transformer/MixinTargetContext;Lorg/spongepowered/asm/mixin/transformer/MixinApplicatorStandard$ApplicatorPass;)V");
            boolean plugin=name.equals("org/spongepowered/asm/mixin/transformer/MixinInfo")&&Set.of("preApply","postApply").contains(method.name)
                    &&method.desc.equals("(Ljava/lang/String;Lorg/objectweb/asm/tree/ClassNode;)V");
            boolean transformer=name.equals("cpw/mods/modlauncher/TransformerHolder")&&method.name.equals("transform")
                    &&method.desc.equals("(Ljava/lang/Object;Lcpw/mods/modlauncher/api/ITransformerVotingContext;)Ljava/lang/Object;");
            boolean vote=name.equals("cpw/mods/modlauncher/TransformerHolder")&&method.name.equals("castVote")
                    &&method.desc.equals("(Lcpw/mods/modlauncher/api/ITransformerVotingContext;)Lcpw/mods/modlauncher/api/TransformerVoteResult;");
            boolean holder=name.equals("cpw/mods/modlauncher/TransformerHolder")&&method.name.equals("<init>")
                    &&method.desc.equals("(Lcpw/mods/modlauncher/api/ITransformer;Lcpw/mods/modlauncher/api/ITransformationService;)V");
            boolean context=name.equals("org/spongepowered/asm/mixin/transformer/MixinTargetContext")&&method.name.equals("<init>")
                    &&method.desc.equals("(Lorg/spongepowered/asm/mixin/transformer/MixinInfo;Lorg/objectweb/asm/tree/ClassNode;Lorg/spongepowered/asm/mixin/transformer/TargetClassContext;)V");
            boolean pipeline=name.equals("cpw/mods/modlauncher/TransformingClassLoader")&&method.name.equals("maybeTransformClassBytes")&&method.desc.equals("([BLjava/lang/String;Ljava/lang/String;)[B");
            if(apply||plugin||transformer||vote){
                if(!vote)wrap(method,apply,plugin);
                if(apply||transformer||vote)skipStopped(method,apply,vote);changed=true;continue;
            }
            if(!layer&&!read&&!reader&&!tree&&!copy&&!output&&!mixin&&!holder&&!context&&!pipeline)continue;
            for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==(layer||read||output||mixin||pipeline?Opcodes.ARETURN:Opcodes.RETURN)){
                InsnList code=new InsnList();
                if(layer){code.add(new InsnNode(Opcodes.DUP));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"layerDefined","(Ljava/lang/Object;)V",false));}
                else if(read){int result=method.maxLocals++;code.add(new VarInsnNode(Opcodes.ASTORE,result));code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,1));code.add(new VarInsnNode(Opcodes.ALOAD,2));code.add(new VarInsnNode(Opcodes.ALOAD,result));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"moduleBytes","(Ljava/lang/ClassLoader;Ljava/lang/Object;Ljava/lang/module/ModuleReference;[B)[B",false));}
                else if(output){code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new InsnNode(Opcodes.SWAP));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"writerBytes","(Ljava/lang/Object;[B)[B",false));}
                else if(mixin){code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new InsnNode(Opcodes.SWAP));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"mixinBytes","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));code.add(new TypeInsnNode(Opcodes.CHECKCAST,"org/objectweb/asm/tree/ClassNode"));}
                else if(context||holder){for(int local=0;local<3;local++)code.add(new VarInsnNode(Opcodes.ALOAD,local));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,context?"mixinContext":"transformerContext","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",false));}
                else if(pipeline){int result=method.maxLocals++;code.add(new VarInsnNode(Opcodes.ASTORE,result));code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,1));code.add(new VarInsnNode(Opcodes.ALOAD,result));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"pipelineBytes","(Ljava/lang/Object;[B[B)[B",false));}
                else {code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,1));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,reader?"classReader":copy?"treeCopied":"classTree","(Ljava/lang/Object;"+(reader?"[B":"Ljava/lang/Object;")+")V",false));}
                method.instructions.insertBefore(at,code);changed=true;
            }
        }
        if(!changed)throw new IllegalStateException("CODE_SOURCE_READ_LAYOUT_UNAVAILABLE:"+name);
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
    private static boolean resourceOrConfig(String name,MethodNode method){
        boolean provider=name.equals("cpw/mods/cl/JarModuleFinder$JarModuleReference")&&method.name.equals("jar")&&method.desc.equals("()Lcpw/mods/jarhandling/SecureJar$ModuleDataProvider;");
        boolean moduleReader=name.equals("cpw/mods/cl/JarModuleFinder$JarModuleReference")&&method.name.equals("open")&&method.desc.equals("()Ljava/lang/module/ModuleReader;");
        boolean locate=name.equals("cpw/mods/jarhandling/impl/Jar$JarModuleDataProvider")&&method.name.equals("findFile")&&method.desc.equals("(Ljava/lang/String;)Ljava/util/Optional;");
        boolean opened=name.equals("cpw/mods/jarhandling/impl/Jar$JarModuleDataProvider")&&method.name.equals("open")&&method.desc.equals("(Ljava/lang/String;)Ljava/util/Optional;");
        boolean resource=name.equals("cpw/mods/cl/ModuleClassLoader")&&method.name.equals("readerToURL")&&method.desc.equals("(Ljava/lang/module/ModuleReader;Ljava/lang/module/ModuleReference;Ljava/lang/String;)Ljava/net/URL;");
        boolean url=name.equals("cpw/mods/cl/ModuleClassLoader")&&method.name.equals("toURL")&&method.desc.equals("(Ljava/util/Optional;)Ljava/net/URL;");
        boolean stream=name.equals("java/net/URL")&&method.name.equals("openStream")&&method.desc.equals("()Ljava/io/InputStream;");
        boolean reader=name.equals("java/io/InputStreamReader")&&method.name.equals("<init>")&&method.desc.startsWith("(Ljava/io/InputStream;");
        boolean config=name.equals("org/spongepowered/asm/mixin/transformer/MixinConfig");
        boolean lookup=config&&((method.name.equals("hasMixinsFor")&&method.desc.equals("(Ljava/lang/String;)Z"))
                ||(method.name.equals("getMixinsFor")&&method.desc.equals("(Ljava/lang/String;)Ljava/util/List;"))
                ||(Set.of("getTargetsSet","getTargets","getUnhandledTargets").contains(method.name)&&method.desc.equals("()Ljava/util/Set;")));
        boolean parse=config&&method.name.equals("create")&&method.desc.equals("(Ljava/lang/String;Lorg/spongepowered/asm/mixin/MixinEnvironment;)Lorg/spongepowered/asm/mixin/transformer/Config;");
        boolean handle=config&&method.name.equals("getHandle")&&method.desc.equals("()Lorg/spongepowered/asm/mixin/transformer/Config;");
        boolean parent=config&&method.name.equals("assignParent")&&method.desc.equals("(Lorg/spongepowered/asm/mixin/transformer/Config;)Z");
        boolean install=name.equals("org/spongepowered/asm/mixin/Mixins")&&method.name.equals("registerConfiguration")&&method.desc.equals("(Lorg/spongepowered/asm/mixin/transformer/Config;)V");
        boolean gate=config&&(method.name.equals("prepare")||method.name.equals("postInitialise")||method.name.equals("onLoad")||method.name.equals("select"));
        if(lookup){
            InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"configurationPrepare","(Ljava/lang/Object;)V",false));method.instructions.insert(code);return true;
        }
        if(parse){
            boolean found=false;
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode call&&call.owner.equals("com/google/gson/Gson")&&call.name.equals("fromJson")&&call.desc.equals("(Ljava/io/Reader;Ljava/lang/Class;)Ljava/lang/Object;")){
                int type=method.maxLocals++,input=method.maxLocals++;InsnList before=new InsnList();before.add(new VarInsnNode(Opcodes.ASTORE,type));before.add(new VarInsnNode(Opcodes.ASTORE,input));before.add(new VarInsnNode(Opcodes.ALOAD,input));before.add(new VarInsnNode(Opcodes.ALOAD,type));method.instructions.insertBefore(call,before);
                InsnList after=new InsnList();after.add(new InsnNode(Opcodes.DUP));after.add(new VarInsnNode(Opcodes.ALOAD,input));after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"configurationParsed","(Ljava/lang/Object;Ljava/lang/Object;)V",false));method.instructions.insert(call,after);found=true;
            }
            if(!found)throw new IllegalStateException("ACTUAL_MIXIN_CONFIG_PARSER_UNAVAILABLE");return true;
        }
        if(gate||install){
            InsnList code=new InsnList();LabelNode allowed=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,0));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,install?"configurationInstalling":"configurationAllowed","(Ljava/lang/Object;)Z",false));code.add(new JumpInsnNode(Opcodes.IFNE,allowed));
            if(Type.getReturnType(method.desc).getSort()==Type.BOOLEAN){code.add(new InsnNode(Opcodes.ICONST_0));code.add(new InsnNode(Opcodes.IRETURN));}else code.add(new InsnNode(Opcodes.RETURN));code.add(allowed);method.instructions.insert(code);return true;
        }
        if(!provider&&!moduleReader&&!locate&&!opened&&!resource&&!url&&!stream&&!reader&&!handle&&!parent)return false;
        for(AbstractInsnNode instruction:method.instructions.toArray()){
            int exit=reader?Opcodes.RETURN:parent?Opcodes.IRETURN:Opcodes.ARETURN;if(instruction.getOpcode()!=exit)continue;
            InsnList code=new InsnList();
            if(reader){code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,1));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"resourceReader","(Ljava/lang/Object;Ljava/lang/Object;)V",false));}
            else if(parent){code.add(new InsnNode(Opcodes.DUP));code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,1));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"configurationParent","(ZLjava/lang/Object;Ljava/lang/Object;)V",false));}
            else {
                code.add(new InsnNode(Opcodes.DUP));code.add(new VarInsnNode(Opcodes.ALOAD,resource?2:0));code.add(new InsnNode(Opcodes.SWAP));
                String hook=provider?"moduleProvider":moduleReader?"moduleReader":locate?"resourceLocated":opened?"providerOpened":resource?"moduleResource":url?"resourceURL":stream?"resourceOpened":"configurationHandle";
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,hook,"(Ljava/lang/Object;Ljava/lang/Object;)V",false));
            }
            method.instructions.insertBefore(instruction,code);
        }
        return true;
    }
    private static void metadataScope(MethodNode method,String superName,boolean mixin){
        int token=method.maxLocals++,failure=method.maxLocals++;Type resultType=Type.getReturnType(method.desc);int result=method.maxLocals;method.maxLocals+=resultType.getSize();
        LabelNode start=new LabelNode(),finish=new LabelNode(),handler=new LabelNode();InsnList prefix=new InsnList();prefix.add(new VarInsnNode(Opcodes.ALOAD,0));
        prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,mixin?"metadataMixinBegin":"metadataContextBegin","(Ljava/lang/Object;)Ljava/lang/Object;",false));prefix.add(new VarInsnNode(Opcodes.ASTORE,token));prefix.add(start);
        if(method.name.equals("<init>")){
            MethodInsnNode parent=null;for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL&&call.owner.equals(superName)&&call.name.equals("<init>")){parent=call;break;}
            if(parent==null)throw new IllegalStateException("ACTUAL_MIXIN_CONTEXT_INITIALIZATION_UNAVAILABLE");method.instructions.insert(parent,prefix);
        }else method.instructions.insert(prefix);
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()>=Opcodes.IRETURN&&at.getOpcode()<=Opcodes.RETURN){
            InsnList code=new InsnList();if(resultType.getSort()!=Type.VOID)code.add(new VarInsnNode(resultType.getOpcode(Opcodes.ISTORE),result));
            code.add(new VarInsnNode(Opcodes.ALOAD,token));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"metadataContextEnd","(Ljava/lang/Object;)V",false));
            if(resultType.getSort()!=Type.VOID)code.add(new VarInsnNode(resultType.getOpcode(Opcodes.ILOAD),result));method.instructions.insertBefore(at,code);
        }
        method.instructions.add(finish);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"metadataContextEnd","(Ljava/lang/Object;)V",false));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,finish,handler,"java/lang/Throwable"));
    }
    private static void memberMutation(MethodNode method){
        int token=method.maxLocals++,failure=method.maxLocals++;Type resultType=Type.getReturnType(method.desc);int result=method.maxLocals;method.maxLocals+=resultType.getSize();
        LabelNode start=new LabelNode(),finish=new LabelNode(),handler=new LabelNode();InsnList prefix=new InsnList();prefix.add(new VarInsnNode(Opcodes.ALOAD,0));
        prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"metadataMemberChangeBegin","(Ljava/lang/Object;)Ljava/lang/Object;",false));prefix.add(new VarInsnNode(Opcodes.ASTORE,token));prefix.add(start);method.instructions.insert(prefix);
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()>=Opcodes.IRETURN&&at.getOpcode()<=Opcodes.RETURN){
            InsnList code=new InsnList();if(resultType.getSort()!=Type.VOID)code.add(new VarInsnNode(resultType.getOpcode(Opcodes.ISTORE),result));
            code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,token));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"metadataMemberChangeEnd","(Ljava/lang/Object;Ljava/lang/Object;)V",false));
            if(resultType.getSort()!=Type.VOID)code.add(new VarInsnNode(resultType.getOpcode(Opcodes.ILOAD),result));method.instructions.insertBefore(at,code);
        }
        method.instructions.add(finish);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"metadataMemberChangeEnd","(Ljava/lang/Object;Ljava/lang/Object;)V",false));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,finish,handler,"java/lang/Throwable"));
    }
    private static void mixinRegistration(MethodNode method){
        for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode call
                &&call.owner.equals("org/spongepowered/asm/service/IClassTracker")&&call.name.equals("registerInvalidClass")&&call.desc.equals("(Ljava/lang/String;)V")){
            int argument=method.maxLocals++,tracker=method.maxLocals++,token=method.maxLocals++,failure=method.maxLocals++;
            LabelNode start=new LabelNode(),finish=new LabelNode(),handler=new LabelNode(),resume=new LabelNode();
            InsnList before=new InsnList();before.add(new VarInsnNode(Opcodes.ASTORE,argument));before.add(new VarInsnNode(Opcodes.ASTORE,tracker));
            before.add(new VarInsnNode(Opcodes.ALOAD,0));before.add(new VarInsnNode(Opcodes.ALOAD,tracker));before.add(new VarInsnNode(Opcodes.ALOAD,argument));
            before.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"mixinRegistrationBegin","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",false));
            before.add(new VarInsnNode(Opcodes.ASTORE,token));before.add(start);before.add(new VarInsnNode(Opcodes.ALOAD,tracker));before.add(new VarInsnNode(Opcodes.ALOAD,argument));method.instructions.insertBefore(at,before);
            InsnList after=new InsnList();after.add(finish);after.add(new VarInsnNode(Opcodes.ALOAD,token));
            after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"mixinRegistrationEnd","(Ljava/lang/Object;)V",false));after.add(new JumpInsnNode(Opcodes.GOTO,resume));
            after.add(handler);after.add(new VarInsnNode(Opcodes.ASTORE,failure));after.add(new VarInsnNode(Opcodes.ALOAD,token));
            after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"mixinRegistrationEnd","(Ljava/lang/Object;)V",false));after.add(new VarInsnNode(Opcodes.ALOAD,failure));after.add(new InsnNode(Opcodes.ATHROW));after.add(resume);method.instructions.insert(at,after);
            method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,finish,handler,"java/lang/Throwable"));
        }
    }
    private static void trackerRegistration(MethodNode method){
        boolean found=false;
        for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode call&&call.owner.equals("java/util/Set")&&call.name.equals("add")&&call.desc.equals("(Ljava/lang/Object;)Z")){
            int value=method.maxLocals++,set=method.maxLocals++,added=method.maxLocals++;
            InsnList before=new InsnList();before.add(new VarInsnNode(Opcodes.ASTORE,value));before.add(new VarInsnNode(Opcodes.ASTORE,set));before.add(new VarInsnNode(Opcodes.ALOAD,set));before.add(new VarInsnNode(Opcodes.ALOAD,value));method.instructions.insertBefore(at,before);
            InsnList after=new InsnList();after.add(new VarInsnNode(Opcodes.ISTORE,added));after.add(new VarInsnNode(Opcodes.ALOAD,0));after.add(new VarInsnNode(Opcodes.ALOAD,set));after.add(new VarInsnNode(Opcodes.ALOAD,value));after.add(new VarInsnNode(Opcodes.ILOAD,added));
            after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"trackerRegistered","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Z)V",false));after.add(new VarInsnNode(Opcodes.ILOAD,added));method.instructions.insert(at,after);found=true;
        }
        if(!found)throw new IllegalStateException("ACTUAL_MIXIN_TRACKER_REGISTRATION_UNAVAILABLE");
    }
    private static void skipStopped(MethodNode method,boolean mixin,boolean vote){
        InsnList code=new InsnList();LabelNode allowed=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,mixin?1:0));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,mixin?"mixinAllowed":"transformerAllowed","(Ljava/lang/Object;)Z",false));code.add(new JumpInsnNode(Opcodes.IFNE,allowed));
        if(mixin)code.add(new InsnNode(Opcodes.RETURN));else {if(vote)code.add(new FieldInsnNode(Opcodes.GETSTATIC,"cpw/mods/modlauncher/api/TransformerVoteResult","NO","Lcpw/mods/modlauncher/api/TransformerVoteResult;"));else code.add(new VarInsnNode(Opcodes.ALOAD,1));code.add(new InsnNode(Opcodes.ARETURN));}
        code.add(allowed);method.instructions.insert(code);
    }
    private static InsnList target(boolean apply,boolean plugin){
        InsnList code=new InsnList();
        if(apply){code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new FieldInsnNode(Opcodes.GETFIELD,"org/spongepowered/asm/mixin/transformer/MixinApplicatorStandard","targetClass","Lorg/objectweb/asm/tree/ClassNode;"));}
        else code.add(new VarInsnNode(Opcodes.ALOAD,plugin?2:1));return code;
    }
    private static void wrap(MethodNode method,boolean apply,boolean plugin){
        int token=method.maxLocals++,result=method.maxLocals++,failure=method.maxLocals++;
        LabelNode start=new LabelNode(),finish=new LabelNode(),handler=new LabelNode();InsnList prefix=new InsnList();
        if(apply){prefix.add(target(true,false));prefix.add(new VarInsnNode(Opcodes.ALOAD,1));}
        else {prefix.add(new VarInsnNode(Opcodes.ALOAD,0));prefix.add(target(false,plugin));}
        prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,apply?"mixinBegin":plugin?"pluginBegin":"transformerBegin","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));
        prefix.add(new VarInsnNode(Opcodes.ASTORE,token));prefix.add(start);method.instructions.insert(prefix);
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.RETURN||at.getOpcode()==Opcodes.ARETURN){
            InsnList code=new InsnList();if(at.getOpcode()==Opcodes.ARETURN)code.add(new VarInsnNode(Opcodes.ASTORE,result));
            code.add(new VarInsnNode(Opcodes.ALOAD,token));if(at.getOpcode()==Opcodes.ARETURN)code.add(new VarInsnNode(Opcodes.ALOAD,result));else code.add(target(apply,plugin));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"codeEnd","(Ljava/lang/Object;Ljava/lang/Object;)V",false));
            if(at.getOpcode()==Opcodes.ARETURN)code.add(new VarInsnNode(Opcodes.ALOAD,result));method.instructions.insertBefore(at,code);
        }
        method.instructions.add(finish);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));method.instructions.add(target(apply,plugin));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"codeEnd","(Ljava/lang/Object;Ljava/lang/Object;)V",false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,finish,handler,"java/lang/Throwable"));
    }
}
