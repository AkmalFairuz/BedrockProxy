package org.akmalfairuz.bedrockproxy.server;

import com.nukkitx.protocol.bedrock.BedrockClient;
import com.nukkitx.protocol.bedrock.BedrockPong;
import com.nukkitx.protocol.bedrock.BedrockServerEventHandler;
import com.nukkitx.protocol.bedrock.BedrockServerSession;
import com.nukkitx.protocol.bedrock.packet.DisconnectPacket;
import com.nukkitx.protocol.bedrock.v440.Bedrock_v440;
import org.akmalfairuz.bedrockproxy.Player;
import org.akmalfairuz.bedrockproxy.ProxyServer;
import org.akmalfairuz.bedrockproxy.utils.Log;

import java.net.InetSocketAddress;

public class ServerHandler implements BedrockServerEventHandler {

    private ProxyServer proxy;

    public ServerHandler(ProxyServer proxyServer) {
        proxy = proxyServer;
    }

    @Override
    public boolean onConnectionRequest(InetSocketAddress address) {
        return true;
    }

    @Override
    public BedrockPong onQuery(InetSocketAddress address) {
        BedrockPong pong = new BedrockPong();
        pong.setEdition("MCPE");
        pong.setMotd("BedrockProxy");
        pong.setPlayerCount(proxy.players.size());
        pong.setMaximumPlayerCount(10);
        pong.setGameType("Survival");
        pong.setVersion("1.17.0");
        pong.setProtocolVersion(Bedrock_v440.V440_CODEC.getProtocolVersion());
        pong.setIpv4Port(19132);
        pong.setIpv6Port(19132);
        pong.setNintendoLimited(false);
        pong.setSubMotd("BedrockProxy");
        return pong;
    }

    public DisconnectPacket createDisconnect(String message){
        DisconnectPacket dc = new DisconnectPacket();
        dc.setKickMessage(message);
        dc.setMessageSkipped(false);
        return dc;
    }

    @Override
    public void onSessionCreation(BedrockServerSession serverSession) {
        if(proxy.players.size() > 10) {
            serverSession.sendPacketImmediately(createDisconnect("Proxy full!"));
            serverSession.disconnect();
            return;
        }
        Player player = new Player(serverSession, this, proxy);
        serverSession.addDisconnectHandler((reason) -> {
            if(player.getServerSession() != null) {
                player.getServerSession().disconnect();
                player.setConnectedToServer(false);
            }
            player.close();
        });
        serverSession.setBatchHandler(new ClientBatchHandler(serverSession, player));
        Log.info(serverSession.getAddress() + " joined");
    }
}