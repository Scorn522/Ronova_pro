package dev.ronova.pro.agent;

import com.sun.tools.attach.VirtualMachine;
import java.nio.file.*;

/**
 * Zero-argument bootstrap helper.
 *
 * The game process launches this helper as a short-lived child; the helper attaches the agent to that exact
 * process and exits. Attaching from a separate process is what makes the player's launch line free of any
 * Ronova-specific JVM argument, including the self-attach switch a same-process attach would need.
 *
 * It never touches a launcher profile or a system-wide setting, serves one process, and exits after the attach
 * attempt. On failure it writes one diagnostic line to the caller's log standard error and returns non-zero;
 * a missing backend is reported, never hidden.
 *
 * Arguments: <pid> <agentJar> <bootstrapJar>
 */
public final class AttachMain {
    private AttachMain() { }

    public static void main(String[] arguments) {
        if(arguments.length<3) {
            System.err.println("RONOVA_ATTACH_USAGE_REQUIRED");
            System.exit(2);
        }
        String pid=arguments[0],agent=arguments[1],target=arguments[2];
        try {
            Path agentJar=Path.of(agent).toAbsolutePath().normalize();
            if(!Files.isReadable(agentJar)) { System.err.println("RONOVA_ATTACH_AGENT_UNREADABLE");System.exit(3); }
            // The argument is either a bootstrap jar path or an already encoded premain argument.
            String premain;
            if(target.startsWith("base64:"))premain=target;
            else {
                Path bootstrapJar=Path.of(target).toAbsolutePath().normalize();
                if(!Files.isReadable(bootstrapJar)) { System.err.println("RONOVA_ATTACH_BOOTSTRAP_UNREADABLE");System.exit(4); }
                // The agent's premain argument carries the bootstrap path; the game process never sees a JVM flag.
                premain="base64:"+java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(bootstrapJar.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            VirtualMachine machine=VirtualMachine.attach(pid);
            try { machine.loadAgent(agentJar.toString(),premain); }
            finally { machine.detach(); }
            System.out.println("RONOVA_ATTACH_OK");
            System.exit(0);
        } catch(Throwable unavailable) {
            System.err.println("RONOVA_ATTACH_FAILED:"+unavailable.getClass().getSimpleName()+":"+unavailable.getMessage());
            System.exit(1);
        }
    }
}
