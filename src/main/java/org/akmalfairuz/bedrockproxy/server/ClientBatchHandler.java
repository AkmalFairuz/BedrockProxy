package org.akmalfairuz.bedrockproxy.server;

import com.nukkitx.math.vector.Vector2f;
import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.nbt.*;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.BedrockSession;
import com.nukkitx.protocol.bedrock.data.*;
import com.nukkitx.protocol.bedrock.data.inventory.ItemData;
import com.nukkitx.protocol.bedrock.handler.BatchHandler;
import com.nukkitx.protocol.bedrock.packet.*;
import com.nukkitx.protocol.bedrock.v440.Bedrock_v440;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import org.akmalfairuz.bedrockproxy.Player;
import org.akmalfairuz.bedrockproxy.utils.Log;
import org.akmalfairuz.bedrockproxy.vanilla.BedrockData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

public class ClientBatchHandler implements BatchHandler {

    @Getter
    private BedrockSession session;

    private int entityId;

    @Getter
    private final Player player;

    public ClientBatchHandler(BedrockSession session, Player player) {
        this.session = session;
        this.player = player;
        player.setClientBatchHandler(this);
    }

    @Override
    public void handle(BedrockSession bedrockSession, ByteBuf byteBuf, Collection<BedrockPacket> collection) {
        collection.forEach((pk) -> {
            try {
                if (!handlePacket(pk)) {
                    if (player.isConnectedToServer()) {
                        player.getServerSession().sendPacketImmediately(pk);
                    }
                }
            } catch (Exception e) {
                player.sendMessage("§cError handling " + pk.getClass().getSimpleName() + " from client, Message: " + e.getMessage() + " " + e.getClass().getName());
            }
        });
    }

    public boolean handlePacket(BedrockPacket packet) {
        if(packet instanceof LoginPacket) {
            if(((LoginPacket) packet).getProtocolVersion() != 440) {
                session.sendPacketImmediately(player.getServerHandler().createDisconnect("Please use Minecraft v1.17.0 (Protocol 440) to join this server."));
                session.disconnect();
                return true;
            }
            player.setLoginPacket((LoginPacket) packet);

            session.setPacketCodec(Bedrock_v440.V440_CODEC);

            PlayStatusPacket status = new PlayStatusPacket();
            status.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
            session.sendPacket(status);

            ResourcePacksInfoPacket resourcePacksInfoPacket = new ResourcePacksInfoPacket();
            resourcePacksInfoPacket.setForcingServerPacksEnabled(false);
            resourcePacksInfoPacket.setScriptingEnabled(false);
            resourcePacksInfoPacket.setForcedToAccept(false);
            session.sendPacket(resourcePacksInfoPacket);
            return true;
        }
        if(packet instanceof ResourcePackClientResponsePacket) {
            if(((ResourcePackClientResponsePacket) packet).getStatus() == ResourcePackClientResponsePacket.Status.HAVE_ALL_PACKS) {
                ResourcePackStackPacket packStackPacket = new ResourcePackStackPacket();
                packStackPacket.setGameVersion("1.17.0");
                packStackPacket.setExperimentsPreviouslyToggled(false);
                packStackPacket.setForcedToAccept(false);
                session.sendPacket(packStackPacket);
                return true;
            }else if(((ResourcePackClientResponsePacket) packet).getStatus() == ResourcePackClientResponsePacket.Status.COMPLETED) {
                sendStartGame();
                sendEmptyChunk();
                spawn();
                return true;
            }
        }
        if(packet instanceof SetLocalPlayerAsInitializedPacket) {
            player.setInitialized(true);
            player.onJoinProxy();
            return true;
        }
        if(packet instanceof PacketViolationWarningPacket) {
            int id = ((PacketViolationWarningPacket) packet).getPacketCauseId();
            Log.info("Packet Violation ID: " + id);
            return true;
        }
        if(packet instanceof TextPacket) {
            return player.handleChat(((TextPacket) packet).getMessage());
        }
        if(packet instanceof CommandRequestPacket) {
            return player.handleChat(((CommandRequestPacket) packet).getCommand());
        }
        if(packet instanceof ModalFormResponsePacket) {
            return player.handleFormResponse((ModalFormResponsePacket) packet);
        }
        if(packet instanceof MovePlayerPacket) {
            player.setPosition(((MovePlayerPacket) packet).getPosition());
        }
        if(player.isConnectedToServer()) {
            ClientPacketRewriter.rewrite(player, packet);
            if(packet instanceof MovePlayerPacket || packet instanceof PlayerActionPacket || packet instanceof AnimatePacket) {
                if (player.getPlayerCheat().isFakeLag() && player.isInitialized()) {
                    player.getFakeLagQueuedPackets().add(packet);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public void sendEmptyChunk() {
        Vector3i position = Vector3i.from(99999, 60, 99999);
        int radius = 0;
        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                LevelChunkPacket data = new LevelChunkPacket();
                data.setChunkX(chunkX + x);
                data.setChunkZ(chunkZ + z);
                data.setSubChunksLength(0);
                data.setData(getEmptyChunkData());
                data.setCachingEnabled(false);
                session.sendPacket(data);
            }
        }
    }

    public void sendStartGame() {
        int entityId = ThreadLocalRandom.current().nextInt(10000, 15000);
        this.entityId = entityId;
        player.setPlayerId(entityId);
        StartGamePacket startGamePacket = new StartGamePacket();
        startGamePacket.setUniqueEntityId(entityId);
        startGamePacket.setRuntimeEntityId(entityId);
        startGamePacket.setPlayerGameType(GameType.SURVIVAL);
        startGamePacket.setPlayerPosition(Vector3f.from(99999, 69, 99999));
        startGamePacket.setRotation(Vector2f.from(1, 1));

        startGamePacket.setSeed(-1);
        startGamePacket.setDimensionId(0);
        startGamePacket.setGeneratorId(1);
        startGamePacket.setLevelGameType(GameType.SURVIVAL);
        startGamePacket.setDifficulty(1);
        startGamePacket.setDefaultSpawn(Vector3i.ZERO);
        startGamePacket.setAchievementsDisabled(true);
        startGamePacket.setCurrentTick(-1);
        startGamePacket.setEduEditionOffers(0);
        startGamePacket.setEduFeaturesEnabled(false);
        startGamePacket.setRainLevel(0);
        startGamePacket.setLightningLevel(0);
        startGamePacket.setMultiplayerGame(true);
        startGamePacket.setBroadcastingToLan(true);
        startGamePacket.setPlatformBroadcastMode(GamePublishSetting.PUBLIC);
        startGamePacket.setXblBroadcastMode(GamePublishSetting.PUBLIC);
        startGamePacket.setCommandsEnabled(true);
        startGamePacket.setTexturePacksRequired(false);
        startGamePacket.setBonusChestEnabled(false);
        startGamePacket.setStartingWithMap(false);
        startGamePacket.setTrustingPlayers(false);
        startGamePacket.setDefaultPlayerPermission(PlayerPermission.MEMBER);
        startGamePacket.setServerChunkTickRange(4);
        startGamePacket.setBehaviorPackLocked(false);
        startGamePacket.setResourcePackLocked(false);
        startGamePacket.setFromLockedWorldTemplate(false);
        startGamePacket.setUsingMsaGamertagsOnly(false);
        startGamePacket.setFromWorldTemplate(false);
        startGamePacket.setWorldTemplateOptionLocked(false);

        String serverName = "BedrockProxy";
        startGamePacket.setLevelId(serverName);
        startGamePacket.setLevelName(serverName);

        startGamePacket.setPremiumWorldTemplateId("00000000-0000-0000-0000-000000000000");

        startGamePacket.setEnchantmentSeed(0);
        startGamePacket.setMultiplayerCorrelationId("");
        startGamePacket.setItemEntries(BedrockData.ITEM_ENTRIES);
        startGamePacket.setVanillaVersion("*");
        startGamePacket.setInventoriesServerAuthoritative(false);
        startGamePacket.setServerEngine("");

        SyncedPlayerMovementSettings settings = new SyncedPlayerMovementSettings();
        settings.setMovementMode(AuthoritativeMovementMode.CLIENT);
        settings.setRewindHistorySize(0);
        settings.setServerAuthoritativeBlockBreaking(false);
        startGamePacket.setPlayerMovementSettings(settings);

        session.sendPacket(startGamePacket);
    }

    public void spawn() {
        BiomeDefinitionListPacket biomeDefinitionListPacket = new BiomeDefinitionListPacket();
        biomeDefinitionListPacket.setDefinitions(BedrockData.BIOMES);
        session.sendPacket(biomeDefinitionListPacket);

        AvailableEntityIdentifiersPacket entityPacket = new AvailableEntityIdentifiersPacket();
        entityPacket.setIdentifiers(BedrockData.ENTITY_IDENTIFIERS);
        session.sendPacket(entityPacket);

        CreativeContentPacket packet = new CreativeContentPacket();
        packet.setContents(new ItemData[0]);
        session.sendPacket(packet);

        PlayStatusPacket playStatusPacket = new PlayStatusPacket();
        playStatusPacket.setStatus(PlayStatusPacket.Status.PLAYER_SPAWN);
        session.sendPacket(playStatusPacket);

        UpdateAttributesPacket attributesPacket = new UpdateAttributesPacket();
        attributesPacket.setRuntimeEntityId(entityId);
        // Default move speed
        // Bedrock clients move very fast by default until they get an attribute packet correcting the speed
        attributesPacket.setAttributes(Collections.singletonList(
                new AttributeData("minecraft:movement", 0.0f, 1024f, 0.1f, 0.1f)));
        session.sendPacket(attributesPacket);

        GameRulesChangedPacket gamerulePacket = new GameRulesChangedPacket();
        gamerulePacket.getGameRules().add(new GameRuleData<>("naturalregeneration", false));
        session.sendPacket(gamerulePacket);
    }

    public byte[] getEmptyChunkData() {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(new byte[258]); // Biomes + Border Size + Extra Data Size

            try (NBTOutputStream stream = NbtUtils.createNetworkWriter(outputStream)) {
                stream.writeTag(NbtMap.EMPTY);
            }

            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new AssertionError("Unable to generate empty level chunk data");
        }
    }
}
