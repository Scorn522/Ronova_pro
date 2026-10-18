package dev.ronova.pro.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Decodes the existing group intent stream without replaying any resource or save effect. */
public final class GroupLineage {
    public record Group(UUID id,UUID operation,long generation,Set<String> aliases,String mode,String carrier,String transition){}
    private GroupLineage(){}
    private static final class State{
        UUID id,operation;long generation;Set<String> aliases;String mode,root,carrier,transition="LEGACY_UNBOUND";
        Group freeze(){return new Group(id,operation,generation,Set.copyOf(aliases),mode,carrier,transition);}
    }
    public static Map<UUID,Group> read(List<String> history)throws IOException{
        Map<UUID,State> states=new LinkedHashMap<>();Map<UUID,String> transitions=new HashMap<>();
        try{
            for(String line:history)if(line.startsWith("GROUP/")){
                String[] row=line.split("\t",-1);if(!row[0].equals("GROUP/1")||row.length<3)throw new IllegalArgumentException("GROUP_VERSION_OR_SHAPE");
                UUID id=UUID.fromString(row[2]);State state=states.get(id);
                if(row[1].equals("STOP")&&(row.length==6||row.length==7)){
                    TreeSet<String> aliases=new TreeSet<>();for(String alias:row[5].split(",",-1)){ModCarriers.validId(alias);aliases.add(alias);}
                    String names=String.join(",",aliases);if(!names.equals(row[5])||!UUID.nameUUIDFromBytes(("ronova-mod-group/1|"+names).getBytes(StandardCharsets.UTF_8)).equals(id))throw new IllegalArgumentException("GROUP_IDENTITY");
                    if(state!=null){if(!line.equals(state.root))throw new IllegalArgumentException("GROUP_ROOT_CHANGED");continue;}
                    state=new State();state.id=id;state.operation=UUID.fromString(row[3]);state.generation=Long.parseLong(row[4]);
                    if(state.generation<1)throw new IllegalArgumentException("GROUP_GENERATION");
                    state.aliases=Set.copyOf(aliases);state.mode=mode(row.length==7?row[6]:"default");state.root=line;states.put(id,state);continue;
                }
                if(state==null)throw new IllegalArgumentException("GROUP_FACT_WITHOUT_ROOT");
                switch(row[1]){
                    case "RETURN"->{if(row.length!=4)throw new IllegalArgumentException("GROUP_RETURN");state.mode=mode(row[3]);}
                    case "MEMBER","BLOCK"->{if(row.length!=4)throw new IllegalArgumentException("GROUP_MEMBER");UUID.fromString(row[3]);}
                    case "CARRIER"->{
                        if(row.length!=4||!ModCarriers.entries(row[3]).keySet().equals(state.aliases)||state.carrier!=null&&!state.carrier.equals(row[3]))throw new IllegalArgumentException("GROUP_CARRIER_CHANGED");
                        state.carrier=row[3];state.transition="BOUND";
                    }
                    case "HANDOFF"->{
                        if(row.length!=7||!Set.of("upgrade","replace","uninstall").contains(row[4]))throw new IllegalArgumentException("GROUP_HANDOFF");
                        UUID transition=UUID.fromString(row[3]);String previous=transitions.putIfAbsent(transition,line);
                        if(previous!=null){if(!previous.equals(line))throw new IllegalArgumentException("GROUP_HANDOFF_ID_REUSED");continue;}
                        if(!Objects.equals(state.carrier,row[5].equals("-")?null:row[5]))throw new IllegalArgumentException("GROUP_HANDOFF_PREDECESSOR_CHANGED");
                        Set<String> aliases=ModCarriers.entries(row[6]).keySet();
                        if(row[4].equals("uninstall")?!aliases.isEmpty():!aliases.equals(state.aliases))throw new IllegalArgumentException("GROUP_HANDOFF_ALIAS_SCOPE");
                        state.carrier=row[6];state.transition=row[4].toUpperCase(Locale.ROOT)+":"+transition;
                    }
                    default->throw new IllegalArgumentException("GROUP_HISTORY_SHAPE");
                }
            }
        }catch(RuntimeException invalid){throw new IOException("GROUP_HISTORY_NO_REPLAY",invalid);}
        Map<UUID,Group> result=new LinkedHashMap<>();states.forEach((id,state)->result.put(id,state.freeze()));return Collections.unmodifiableMap(result);
    }
    public static String mode(String value)throws IOException{
        if(!Set.of("default","null","empty","uuid-fixed","uuid-each","invalid-id").contains(value))throw new IOException("INVALID_RETURN_POLICY:"+value);return value;
    }
}
