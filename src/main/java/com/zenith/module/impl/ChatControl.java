package com.zenith.module.impl;

import com.zenith.Proxy;
import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.event.module.ChatControlExecuteEvent;
import com.zenith.event.module.ClientBotTick;
import com.zenith.event.proxy.chat.WhisperChatEvent;
import com.zenith.feature.pathfinder.Baritone;
import com.zenith.feature.world.World;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.module.Module;
import com.zenith.util.Timer;
import com.zenith.util.Timers;
import com.zenith.util.math.MathHelper;
import lombok.Getter;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandSignedPacket;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Shared.*;

public class ChatControl extends Module {
    private Instant lastReply = Instant.EPOCH;
    private static final UUID NULL_UUID = new UUID(0, 0);
    @Getter private UUID controller = NULL_UUID;
    private final Timer controllerTimer = Timers.tickTimer();

    @Override
    public void subscribeEvents() {
        EVENT_BUS.subscribe(
            this,
            of(WhisperChatEvent.class, this::onWhisperReceived),
            of(ClientBotTick.Starting.class, this::onBotTickStarting),
            of(ClientBotTick.class, this::onBotTick)
        );
    }

    private void onBotTick(ClientBotTick event) {
        if (controllerTimer.tick(500)) {
            if (!hasController()) {
                controller = NULL_UUID;
            }
        }
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.chatControl.enabled;
    }

    public boolean hasController() {
        return !controller.equals(NULL_UUID) && CACHE.getEntityCache().get(controller) != null;
    }

    private boolean isSenderTheController(GameProfile sender) {
        return controller.equals(sender.getId());
    }

    private void onBotTickStarting(ClientBotTick.Starting event) {
        controller = NULL_UUID;
    }

    private void onWhisperReceived(WhisperChatEvent event) {
        try {
            onWhisperReceived0(event);
        } catch (Exception e) {
            error("Error handling whisper", e);
        }
    }

    private void onWhisperReceived0(WhisperChatEvent event) {
        if (event.outgoing()
            || !Proxy.getInstance().isConnected()
            || CACHE.getProfileCache().getProfile().getId().equals(event.sender().getProfileId())) return;
        String msg = event.message();
        if (msg.contains(": ")) msg = msg.split(": ")[1];
        String[] msgSplit = msg.split(" ");
        if (msgSplit.length < 1) return;
        String command = msgSplit[0];
        if (!command.startsWith("!")) return;
        command = command.substring(1);
        if (PLAYER_LISTS.getChatControlBlacklist().contains(event.sender().getProfileId())) {
            info("Blocked command from " + event.sender().getName() + ": " + command);
            EVENT_BUS.postAsync(new ChatControlExecuteEvent(event.sender(), "", msgSplit, false));
            return;
        }
        boolean senderInRange = senderInVisualRange(event.sender().getProfile());
        UUID senderUUID = event.sender().getProfileId();
        boolean hasController = hasController();
        boolean senderIsController = isSenderTheController(event.sender().getProfile());
        String response;
        switch (command) {
//            case "load" -> {
//                COMMAND.execute(CommandContext.create("pearl load a", CommandSource.TERMINAL));
//                response = "Loading pearl!";
//            }
            case "help" -> {
                response = "I am using ZenithProxy 3.0! > !where - !follow - !stop - !goto <x> <z> - !come - !mine <block>";
            }
            case "mine" -> {
                if (!senderInRange) {
                    response = "i don't see you :( come find me: !where";
                    break;
                }
                if (hasController && !senderIsController) {
                    response = "i'm busy with someone else :(";
                    break;
                }
                if (msgSplit.length < 2) {
                    response = "Block type required";
                    break;
                }
                String blockInputStr = msgSplit[1];
                Block block = BlockRegistry.REGISTRY.get(blockInputStr.toLowerCase().trim());
                if (block == null) {
                    response = "Invalid block type";
                } else {
                    controller = senderUUID;
                    Baritone.INSTANCE.stop();
                    Baritone.INSTANCE.mine(block);
                    response = "Mining " + blockInputStr;
                }
            }
            case "follow" -> {
                if (!senderInRange) {
                    response = "i don't see you :( come find me: !where";
                    break;
                }
                if (hasController && !senderIsController) {
                    response = "i'm busy with someone else :(";
                    break;
                }
                controller = senderUUID;
                Baritone.INSTANCE.stop();
                Baritone.INSTANCE.follow((e) -> e.getUuid().equals(event.sender().getProfileId()));
                response = "Following you :)";
            }
            case "stop" -> {
                if (!senderInRange) {
                    response = "i don't see you :( come find me: !where";
                    break;
                }
                if (hasController && !senderIsController) {
                    response = "i'm busy with someone else :(";
                    break;
                }
                controller = senderUUID;
                Baritone.INSTANCE.stop();
                response = "Stopped pathing";
            }
            case "goto" -> {
                if (!senderInRange) {
                    response = "i don't see you :( come find me: !where";
                    break;
                }
                if (hasController && !senderIsController) {
                    response = "i'm busy with someone else :(";
                    break;
                }
                if (msgSplit.length < 3) {
                    response = "x and z coordinates required";
                } else {
                    try {
                        int x = MathHelper.clamp(Integer.parseInt(msgSplit[1]), -5000, 5000);
                        int yOrZ = MathHelper.clamp(Integer.parseInt(msgSplit[2]), -5000, 5000);
                        try {
                            int z = MathHelper.clamp(Integer.parseInt(msgSplit[3]), -5000, 5000);
                            Baritone.INSTANCE.stop();
                            Baritone.INSTANCE.pathTo(x, MathHelper.clamp(yOrZ, 0, 120), z);
                            response = "Going to " + x + " " + yOrZ + " " + z;
                            controller = senderUUID;
                            break;
                        } catch (Exception e) {
                            // ignore
                        }
                        Baritone.INSTANCE.stop();
                        Baritone.INSTANCE.pathTo(x, yOrZ);
                        response = "Going to " + x + " " + yOrZ;
                        controller = senderUUID;
                    } catch (Exception e) {
                        response = "x and z coordinates required";
                    }
                }
            }
            case "where" -> {
                BlockPos blockPos = CACHE.getPlayerCache().getThePlayer().blockPos();
                response = blockPos.x() + " " + blockPos.y() + " " + blockPos.z() + " (" + World.getCurrentDimension().name() + ")";
            }
            case "come" -> {
                if (!senderInRange) {
                    response = "i don't see you :( come find me: !where";
                    break;
                }
                if (hasController && !senderIsController) {
                    response = "i'm busy with someone else :(";
                    break;
                }
                EntityPlayer player = (EntityPlayer) CACHE.getEntityCache().get(event.sender().getProfileId());
                if (player == null) {
                    response = "i can't find you :(";
                } else {
                    BlockPos blockPos = player.blockPos();
                    if (!BLOCK_DATA.isAir(World.getBlock(blockPos.below()))) {
                        Baritone.INSTANCE.stop();
                        Baritone.INSTANCE.pathTo(blockPos.x(), blockPos.y(), blockPos.z());
                    } else {
                        Baritone.INSTANCE.stop();
                        Baritone.INSTANCE.pathTo(blockPos.x(), blockPos.z());
                    }
                    controller = senderUUID;
                    response = "Coming!!";
                }
            }
            default -> response = null;
        }
        EVENT_BUS.postAsync(new ChatControlExecuteEvent(event.sender(), response == null ? "" : command, msgSplit, response != null));
        if (response == null) return;
        if (Instant.now().isAfter(lastReply.plus(Duration.ofSeconds(10)))) {
            lastReply = Instant.now();
            info("Responding to " + event.sender().getName() + ": " + response);
            Proxy.getInstance().getClient().sendAsync(new ServerboundChatCommandSignedPacket(
                "w " + event.sender().getName() + " " + response
            ));
        } else {
            info("Silently responding to " + event.sender().getName() + ": " + response);
        }
    }

    private boolean senderInVisualRange(GameProfile sender) {
        return CACHE.getEntityCache().get(sender.getId()) != null;
    }
}
