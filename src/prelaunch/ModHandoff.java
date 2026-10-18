package dev.ronova.pro.prelaunch;

import dev.ronova.pro.IntentJournal;
import dev.ronova.pro.persistence.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletionException;

/** Offline handoff shares the world's original writer and never replays a save or release. */
final class ModHandoff {
    private ModHandoff(){}
    static RestartTargets.Selection apply(RestartTargets.Selection selected,Map<String,String> requested,Map<String,String> actual)throws IOException{
        if(requested.isEmpty())return selected;
        if(selected.world()==null||selected.realm()==null)throw new IOException("HANDOFF_REQUIRES_EXISTING_SELECTED_WORLD");
        try(IntentJournal journal=new IntentJournal(selected.world().resolve("ronova-pro"))){
            if(!journal.healthy()||!Objects.equals(journal.realm,selected.realm()))throw new IOException("HANDOFF_ORIGINAL_JOURNAL_UNAVAILABLE:"+journal.readOnlyReason());
            Map<UUID,GroupLineage.Group> groups=GroupLineage.read(journal.history());Set<String> matched=new HashSet<>();
            for(var group:groups.values()){
                String action=null;
                for(String id:group.aliases())if(requested.containsKey(id)){
                    matched.add(id);String candidate=requested.get(id);if(action!=null&&!action.equals(candidate))throw new IOException("HANDOFF_SHARED_GROUP_CONFLICT:"+group.aliases());action=candidate;
                }
                if(action==null)continue;
                String next;
                if(action.equals("uninstall")){
                    if(!Collections.disjoint(actual.keySet(),group.aliases()))throw new IOException("UNINSTALL_CARRIER_STILL_PRESENT:"+group.aliases());next="";
                }else next=ModCarriers.selection(actual,group.aliases());
                if(Objects.equals(next,group.carrier()))continue;
                String record="GROUP/1\tHANDOFF\t"+group.id()+"\t"+UUID.randomUUID()+"\t"+action+"\t"+(group.carrier()==null?"-":group.carrier())+"\t"+next;
                try{journal.append(record).join();}catch(CompletionException failure){throw new IOException("HANDOFF_DURABLE_WRITE_FAILED",failure.getCause());}
            }
            if(!matched.containsAll(requested.keySet()))throw new IOException("HANDOFF_GROUP_NOT_IN_ORIGINAL_INTENT:"+requested.keySet().stream().filter(id->!matched.contains(id)).toList());
        }
        return RestartTargets.readWorld(selected.world());
    }
    static void requireBindings(RestartTargets.Selection selected,Map<String,String> actual)throws IOException{
        for(var group:selected.groups().values()){
            if(group.carrier()==null)continue; // Legacy intent remains explicitly unbound in the runtime.
            Map<String,String> expected=ModCarriers.entries(group.carrier());
            if(expected.isEmpty()){
                if(!Collections.disjoint(actual.keySet(),group.aliases()))throw new IOException("UNINSTALLED_GROUP_REAPPEARED_HANDOFF_REQUIRED:"+group.aliases());
            }else if(!ModCarriers.selection(actual,group.aliases()).equals(group.carrier()))
                throw new IOException("MOD_CARRIER_CHANGED_HANDOFF_REQUIRED:"+group.aliases());
        }
    }
}
