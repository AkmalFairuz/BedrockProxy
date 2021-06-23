package org.akmalfairuz.bedrockproxy;

import com.nukkitx.protocol.bedrock.BedrockPong;
import com.nukkitx.protocol.bedrock.BedrockServer;
import com.nukkitx.protocol.bedrock.v440.Bedrock_v440;
import lombok.Getter;
import org.akmalfairuz.bedrockproxy.server.ServerHandler;
import org.akmalfairuz.bedrockproxy.utils.Log;
import org.akmalfairuz.bedrockproxy.vanilla.BedrockData;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class ProxyServer {
    @Getter
    private InetSocketAddress address;

    public ArrayList<Player> players = new ArrayList<>();

    public ProxyServer(InetSocketAddress address) {
        this.address = address;
    }

    public void start() {
        InetSocketAddress bindAddress = new InetSocketAddress("0.0.0.0", 19132);
        BedrockServer server = new BedrockServer(bindAddress);

        server.setHandler(new ServerHandler(this));

        server.bind().join();

        Log.info("Proxy running on " + address);

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                tick();
            }
        };
        Timer timer = new Timer("main");
        timer.scheduleAtFixedRate(timerTask, 50, 50);
    }

    public void tick() {
        // TODO
    }
}
