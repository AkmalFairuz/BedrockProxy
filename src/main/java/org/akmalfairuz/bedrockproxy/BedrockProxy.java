package org.akmalfairuz.bedrockproxy;

import org.akmalfairuz.bedrockproxy.utils.Log;
import org.akmalfairuz.bedrockproxy.vanilla.BedrockData;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class BedrockProxy {

    public static void main(String[] args) {
        Log.info("Loading bedrock data...");
        BedrockData.loadItemEntries();
        BedrockData.loadBiomeDefinitions();
        BedrockData.loadEntityIdentifiers();

        ProxyServer server = new ProxyServer(new InetSocketAddress("0.0.0.0", 19132));
        server.start();
    }
}
