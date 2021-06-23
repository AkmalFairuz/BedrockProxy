package org.akmalfairuz.bedrockproxy.server;

import com.nimbusds.jwt.SignedJWT;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.BedrockSession;
import com.nukkitx.protocol.bedrock.data.entity.EntityFlag;
import com.nukkitx.protocol.bedrock.handler.BatchHandler;
import com.nukkitx.protocol.bedrock.packet.*;
import com.nukkitx.protocol.bedrock.util.EncryptionUtils;
import io.netty.buffer.ByteBuf;
import org.akmalfairuz.bedrockproxy.Player;
import org.akmalfairuz.bedrockproxy.utils.Log;

import javax.crypto.SecretKey;
import java.net.URI;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;

public class ServerBatchHandler implements BatchHandler {

    private final BedrockSession session;

    private final Player player;

    public ServerBatchHandler(BedrockSession session, Player player) {
        this.session = session;
        this.player = player;
    }

    @Override
    public void handle(BedrockSession bedrockSession, ByteBuf byteBuf, Collection<BedrockPacket> collection) {
        collection.forEach((pk) -> {
            try {
                if (!handlePacket(pk)) {
                    if (player.isConnectedToServer()) {
                        player.getClientSession().sendPacketImmediately(pk);
                    }
                }
            } catch (Exception e) {
                player.sendMessage("§cError handling " + pk.getClass().getSimpleName() + " from server, Message: " + e.getMessage());
            }
        });
    }

    public boolean handlePacket(BedrockPacket packet) {
        if(packet instanceof ServerToClientHandshakePacket) {
            try {
                SignedJWT saltJwt = SignedJWT.parse(((com.nukkitx.protocol.bedrock.packet.ServerToClientHandshakePacket) packet).getJwt());
                URI x5u = saltJwt.getHeader().getX509CertURL();
                ECPublicKey serverKey = EncryptionUtils.generateKey(x5u.toASCIIString());
                SecretKey key = EncryptionUtils.getSecretKey(
                        player.privateKey,
                        serverKey,
                        Base64.getDecoder().decode(saltJwt.getJWTClaimsSet().getStringClaim("salt"))
                );
                session.enableEncryption(key);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            ClientToServerHandshakePacket clientToServerHandshake = new ClientToServerHandshakePacket();
            session.sendPacket(clientToServerHandshake);
            return true;
        }
        if(packet instanceof ResourcePacksInfoPacket) {
            ResourcePackClientResponsePacket pk = new ResourcePackClientResponsePacket();
            pk.setStatus(ResourcePackClientResponsePacket.Status.HAVE_ALL_PACKS);
            session.sendPacket(pk);
            return true;
        }
        if(packet instanceof ResourcePackStackPacket) {
            ResourcePackClientResponsePacket pk = new ResourcePackClientResponsePacket();
            pk.setStatus(ResourcePackClientResponsePacket.Status.COMPLETED);
            session.sendPacket(pk);
            player.setConnectedToServer(true);
            player.sendMessage("Connected to server!");
            return true;
        }
        if(packet instanceof StartGamePacket) {
            player.setPlayerIdServer((int) ((StartGamePacket) packet).getRuntimeEntityId());
            player.sendMove(((StartGamePacket) packet).getPlayerPosition(), MovePlayerPacket.Mode.TELEPORT);

            RequestChunkRadiusPacket chunkRadiusPacket = new RequestChunkRadiusPacket();
            chunkRadiusPacket.setRadius(8);
            session.sendPacket(chunkRadiusPacket);

            TickSyncPacket tickSyncPacket = new TickSyncPacket();
            tickSyncPacket.setResponseTimestamp(0);
            tickSyncPacket.setRequestTimestamp(0);
            session.sendPacket(tickSyncPacket);

            SetLocalPlayerAsInitializedPacket initializedPacket = new SetLocalPlayerAsInitializedPacket();
            initializedPacket.setRuntimeEntityId(player.getPlayerIdServer());
            session.sendPacket(initializedPacket);

            player.setPlayerFlag(EntityFlag.HAS_GRAVITY, true);
            player.setPlayerFlag(EntityFlag.NO_AI, false);
            return true;
        }
        if(packet instanceof PlayStatusPacket) {
            if(((PlayStatusPacket) packet).getStatus() == PlayStatusPacket.Status.PLAYER_SPAWN) {
                SetLocalPlayerAsInitializedPacket initializedPacket = new SetLocalPlayerAsInitializedPacket();
                initializedPacket.setRuntimeEntityId(player.getPlayerIdServer());
                session.sendPacket(initializedPacket);
            }
            return true;
        }
        if(packet instanceof LoginPacket) {
            PlayStatusPacket status = new PlayStatusPacket();
            status.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
            session.sendPacket(status);
            return true;
        }
        if(packet instanceof BossEventPacket) {
            ((BossEventPacket) packet).setTitle(((BossEventPacket) packet).getTitle() + "\n\n§r§eYou are connected via BedrockProxy!");
        }
        if(packet instanceof DisconnectPacket) {
            session.disconnect();
            return false;
        }
        if(
                packet instanceof AvailableEntityIdentifiersPacket ||
                packet instanceof BiomeDefinitionListPacket ||
                packet instanceof CreativeContentPacket ||
                packet instanceof ItemComponentPacket) {
            return true;
        }
        if(player.isConnectedToServer()) {
            if(packet instanceof MovePlayerPacket) {
                if(((MovePlayerPacket) packet).getRuntimeEntityId() == player.getPlayerIdServer()) {
                    player.setPosition(((MovePlayerPacket) packet).getPosition());
                }
            }
            ServerPacketRewriter.rewrite(player, packet);
        }
        return false;
    }
}
