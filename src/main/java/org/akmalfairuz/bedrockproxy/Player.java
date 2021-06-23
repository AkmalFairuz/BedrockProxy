package org.akmalfairuz.bedrockproxy;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.protocol.bedrock.BedrockClient;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.BedrockSession;
import com.nukkitx.protocol.bedrock.data.SoundEvent;
import com.nukkitx.protocol.bedrock.data.entity.EntityFlag;
import com.nukkitx.protocol.bedrock.data.entity.EntityFlags;
import com.nukkitx.protocol.bedrock.packet.*;
import com.nukkitx.protocol.bedrock.v440.Bedrock_v440;
import lombok.Getter;
import lombok.Setter;
import org.akmalfairuz.bedrockproxy.auth.LoginPacketGenerator;
import org.akmalfairuz.bedrockproxy.auth.XboxLogin;
import org.akmalfairuz.bedrockproxy.server.ClientBatchHandler;
import org.akmalfairuz.bedrockproxy.server.ServerBatchHandler;
import org.akmalfairuz.bedrockproxy.server.ServerHandler;
import org.akmalfairuz.bedrockproxy.utils.Log;

import java.net.InetSocketAddress;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.*;

public class Player{

    public static final String PROXY_PREFIX = "§a[PROXY]§r ";

    @Getter
    private final ServerHandler serverHandler;

    @Getter
    @Setter
    private ClientBatchHandler clientBatchHandler;

    @Getter
    private final ProxyServer proxyServer;

    @Getter
    @Setter
    private ServerBatchHandler serverBatchHandler;

    @Getter
    private final BedrockSession clientSession;

    @Getter
    private BedrockSession serverSession;

    @Getter
    @Setter
    private int playerId;

    @Getter
    @Setter
    private boolean initialized;

    @Getter
    @Setter
    private boolean connectedToServer;

    @Getter
    private String accessToken;

    @Getter
    @Setter
    private LoginPacket loginPacket;

    public ECPublicKey publicKey;
    public ECPrivateKey privateKey;

    public String username;

    public String xuid;

    public String UUID;

    @Getter
    @Setter
    private int playerIdServer;

    @Getter
    @Setter
    private PlayerCheat playerCheat;

    @Getter
    @Setter
    private Queue<BedrockPacket> fakeLagQueuedPackets = new ArrayDeque<>();

    private TimerTask fakeLagTimer;

    @Getter
    @Setter
    private boolean connectionProcess = false;

    @Getter
    @Setter
    private boolean loginProcess = false;

    @Setter
    @Getter
    private Vector3f position = Vector3f.ZERO;

    public Player(BedrockSession clientSession, ServerHandler serverHandler, ProxyServer proxy) {
        this.serverHandler = serverHandler;
        this.proxyServer = proxy;
        this.clientSession = clientSession;
        this.playerCheat = new PlayerCheat(this);
        proxyServer.players.add(this);
    }

    public void close() {
        Log.info(clientSession.getAddress() + " disconnected");
        proxyServer.players.remove(this);
    }

    public void connectToServer(String address, Integer port) {
        if(isConnectedToServer()) {
            return;
        }
        if(connectionProcess) {
            sendMessage("Connection in process!");
            return;
        }
        connectionProcess = true;
        InetSocketAddress bindAddress = new InetSocketAddress("0.0.0.0", 0);
        BedrockClient future = new BedrockClient(bindAddress);
        future.setRakNetVersion(clientSession.getPacketCodec().getRaknetProtocolVersion());
        future.bind().thenApply((client) -> client).thenAccept(client -> future.connect(new InetSocketAddress(address, port)).whenComplete((session, throwable) -> {
            if (throwable != null) {
                connectionProcess = false;
                sendMessage("Connection to " + address + ":" + port + " failed!");
                return;
            }
            sendMessage("Server responded! Logging in...");
            try {
                session.sendPacket(LoginPacketGenerator.create(this, address, port));
            } catch (Exception e) {
                e.printStackTrace();
                sendMessage("Failed to login");
                connectionProcess = false;
                session.disconnect();
                return;
            }
            session.setPacketCodec(Bedrock_v440.V440_CODEC);
            session.addDisconnectHandler((reason) -> {
                connectionProcess = false;
                if(!clientSession.isClosed()) {
                    sendMessage("§cCan't connect to server reason: " + reason.name());
                }
                this.disconnectedFromServer();
            });
            session.setBatchHandler(new ServerBatchHandler(session, this));
            serverSession = session;
        })).whenComplete((ignore, error) -> {
            if (error != null) {
                error.printStackTrace();
            }
        });
    }

    public void createFakeLagTimer(int delay) {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                if(!clientSession.isClosed()) {
                    if(playerCheat.isFakeLag() && isConnectedToServer() && isInitialized()) {
                        for(BedrockPacket next = fakeLagQueuedPackets.poll(); next != null; next = fakeLagQueuedPackets.poll()) {
                            serverSession.sendPacket(next);
                        }
                    }
                }
            }
        };
        fakeLagTimer = timerTask;
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(timerTask, delay, delay);
    }

    public void sendTip(String msg) {
        TextPacket textPacket = new TextPacket();
        textPacket.setParameters(new ArrayList<>());
        textPacket.setXuid("");
        textPacket.setSourceName("");
        textPacket.setMessage(msg);
        textPacket.setPlatformChatId("");
        textPacket.setType(TextPacket.Type.TIP);
        textPacket.setNeedsTranslation(false);
        clientSession.sendPacket(textPacket);
    }

    public void disconnectedFromServer() {
        this.setServerBatchHandler(null);
        this.setConnectedToServer(false);
        Log.info(clientSession.getAddress() + " Failed to connect: " + serverSession.getAddress());
    }

    public void onJoinProxy() {
        sendMessage("Type /.login to authenticate with your xbox account.");
        setPlayerFlag(EntityFlag.BREATHING, true); // hide bubbles
    }

    public void sendMove(Vector3f vector3f, MovePlayerPacket.Mode mode) {
        MovePlayerPacket packet = new MovePlayerPacket();
        packet.setRuntimeEntityId(playerId);
        packet.setTeleportationCause(MovePlayerPacket.TeleportationCause.UNKNOWN);
        packet.setRotation(vector3f);
        packet.setPosition(vector3f);
        packet.setMode(mode);
        packet.setTick(0);
        packet.setOnGround(false);
        packet.setEntityType(0);
        packet.setRidingRuntimeEntityId(0);
        clientSession.sendPacket(packet);
    }

    public boolean handleChat(String chat) {
        if(chat.startsWith("/.")) {
            String[] args = chat.substring(2).split(" ");
            String cmd = args[0];
            switch (cmd) {
                case "login":
                    if(accessToken != null) {
                        sendMessage("You are logged in with your Xbox account.");
                        return true;
                    }
                    sendLoginForm();
                    return true;
                case "connect":
                    if(isConnectedToServer()) {
                        sendMessage("You are connected to server.");
                        return true;
                    }
                    sendServerForm();
                    return true;
                case "antikb":
                    if(playerCheat.isAntikb()) {
                        playerCheat.setAntikb(false);
                        sendMessage("AntiKB Disabled");
                    } else {
                        playerCheat.setAntikb(true);
                        sendMessage("AntiKB Enabled");
                    }
                    return true;
                case "haste":
                    MobEffectPacket packet = new MobEffectPacket();
                    packet.setRuntimeEntityId(this.playerId);
                    packet.setEffectId(3);
                    if(playerCheat.isHaste()) {
                        packet.setEvent(MobEffectPacket.Event.ADD);
                        packet.setAmplifier(2);
                        packet.setDuration(1);
                        packet.setParticles(false);
                        sendMessage("Haste disabled");
                    } else {
                        packet.setEvent(MobEffectPacket.Event.ADD);
                        packet.setAmplifier(2);
                        packet.setDuration(999999999);
                        packet.setParticles(false);
                        playerCheat.setHaste(false);
                        playerCheat.setHaste(true);
                        sendMessage("Haste enabled");
                    }
                    clientSession.sendPacket(packet);
                    return true;
                case "fakelag":
                    if(playerCheat.isFakeLag()) {
                        for(BedrockPacket next = fakeLagQueuedPackets.poll(); next != null; next = fakeLagQueuedPackets.poll()) {
                            serverSession.sendPacket(next);
                            clientSession.sendPacket(next);
                        }
                        playerCheat.setFakeLag(false);
                        fakeLagTimer.cancel();
                        sendMessage("§eFakeLag Disabled");
                    } else {
                        fakeLagForm();
                    }
                    return true;
                case "fakesound":
                    try {
                        if (isConnectedToServer()) {
                            LevelSoundEventPacket sound = new LevelSoundEventPacket();
                            sound.setPosition(getPosition());
                            sound.setBabySound(false);
                            sound.setExtraData(1);
                            sound.setRelativeVolumeDisabled(false);
                            sound.setSound(SoundEvent.valueOf(args[1].toUpperCase(Locale.ROOT)));
                            serverSession.sendPacket(sound);
                            sendMessage("");
                        }
                    } catch (Exception e) {
                        sendMessage("Sound " + args[1] + " not found!");
                    }
                    return true;
                case "help":
                    sendMessage(
                            "/.login - Login to your Xbox Account\n" +
                                    "/.connect - Connect to server\n" +
                                    "/.antikb - Enable/Disable Anti Knockback\n" +
                                    "/.nogravity - Enable/Disable Anti Gravity\n" +
                                    "/.haste - Enable/Disable Haste Effect\n" +
                                    "/.fakelag - Enable/Disable Fake Lag\n"
                    );
                    return true;
            }
            sendMessage("Command /." + cmd + " not found!");
            return true;
        }
        return false;
    }

    private void fakeLagForm() {
        ModalFormRequestPacket form = new ModalFormRequestPacket();
        form.setFormId(-50003);

        JSONObject formData = new JSONObject();
        formData.put("type", "custom_form");
        formData.put("title", "Fake Lag");

        JSONArray formContent = new JSONArray();

        JSONObject input1 = new JSONObject();
        input1.put("type", "slider");
        input1.put("text", "Delay (in millisecond)");
        input1.put("min", 50);
        input1.put("max", 1000);
        input1.put("step", 50);
        input1.put("default", 250);
        formContent.add(input1);

        formData.put("content", formContent);

        form.setFormData(JSON.toJSONString(formData));
        clientSession.sendPacket(form);
    }

    private void sendServerForm() {
        ModalFormRequestPacket form = new ModalFormRequestPacket();
        form.setFormId(-50002);

        JSONObject formData = new JSONObject();
        formData.put("type", "custom_form");
        formData.put("title", "Connect to Server");

        JSONArray formContent = new JSONArray();

        JSONObject input1 = new JSONObject();
        input1.put("type", "input");
        input1.put("text", "IP Address");
        input1.put("placeholder", "play.server.net");
        input1.put("default", null);
        formContent.add(input1);

        JSONObject input2 = new JSONObject();
        input2.put("type", "input");
        input2.put("text", "Port");
        input2.put("placeholder", "19132");
        input2.put("default", "19132");
        formContent.add(input2);

        JSONObject input3 = new JSONObject();
        input3.put("type", "input");
        input3.put("text", "Fake Device Model (optional)");
        input3.put("placeholder", "");
        input3.put("default", null);
        formContent.add(input3);

        JSONObject input4 = new JSONObject();
        input4.put("type", "dropdown");
        input4.put("text", "Fake Current Input Mode (optional)");

        ArrayList<String> currentInputMode = new ArrayList<>();
        currentInputMode.add("");
        currentInputMode.add("Unknown");
        currentInputMode.add("Mouse");
        currentInputMode.add("Touch");
        currentInputMode.add("Controller");

        input4.put("options", currentInputMode);
        input4.put("default", null);
        formContent.add(input4);

        JSONObject input5 = new JSONObject();
        input5.put("type", "dropdown");
        input5.put("text", "Fake Device OS (optional)");

        ArrayList<String> os = new ArrayList<>();
        os.add("");
        os.add("Android");
        os.add("iOS");
        os.add("macOS");
        os.add("Amazon");
        os.add("Gear VR");
        os.add("HoloLens");
        os.add("Windows 10");
        os.add("Windows");
        os.add("Dedicated");
        os.add("Orbis");
        os.add("PlayStation 4");
        os.add("Nintendo Switch");
        os.add("Xbox One");
        os.add("Windows Phone");

        input5.put("options", os);
        input5.put("default", null);
        formContent.add(input5);

        formData.put("content", formContent);

        form.setFormData(JSON.toJSONString(formData));
        clientSession.sendPacket(form);
    }

    private void sendLoginForm() {
        ModalFormRequestPacket form = new ModalFormRequestPacket();
        form.setFormId(-50001);

        JSONObject formData = new JSONObject();
        formData.put("type", "custom_form");
        formData.put("title", "Login with XBOX Live");

        JSONArray formContent = new JSONArray();

        JSONObject input1 = new JSONObject();
        input1.put("type", "input");
        input1.put("text", "2FA Account are not supported\n\nEmail Address");
        input1.put("placeholder", "user@email.com");
        input1.put("default", null);
        formContent.add(input1);

        JSONObject input2 = new JSONObject();
        input2.put("type", "input");
        input2.put("text", "Password");
        input2.put("placeholder", "");
        input2.put("default", null);
        formContent.add(input2);

        formData.put("content", formContent);

        form.setFormData(JSON.toJSONString(formData));
        clientSession.sendPacket(form);
    }

    public boolean handleFormResponse(ModalFormResponsePacket packet) {
        int formId = packet.getFormId();
        if(formId == -50001) { // login
            ArrayList<String> formData = JSON.parseObject(packet.getFormData(), new TypeReference<>(){});
            if(formData.get(1) == null) {
                return true;
            }
            if(accessToken != null) {
                sendMessage("You are logged in with your Xbox account.");
                return true;
            }
            String email = formData.get(0);
            String password = formData.get(1);
            if(loginProcess) {
                return true;
            }
            loginProcess = true;
            try {
                accessToken = XboxLogin.getAccessToken(email, password);
            } catch (Exception e) {
                sendMessage("Failed to login.");
                loginProcess = false;
                return true;
            }
            loginProcess = false;
            sendMessage("Login successfully! To connect to the server type /.connect");
            return true;
        }
        if(formId == -50002) { // connect
            ArrayList<Object> formData = JSON.parseObject(packet.getFormData(), new TypeReference<>(){});
            if(formData.get(4) == null) {
                return true;
            }
            if(isConnectedToServer()) {
                sendMessage("You are connected to server.");
                return true;
            }
            String address = (String) formData.get(0);
            Integer port = Integer.parseInt((String) formData.get(1));
            String deviceModel = (String) formData.get(2);
            playerCheat.setDeviceModel(deviceModel);
            int currentInputMode = (int) formData.get(3) - 1;
            playerCheat.setCurrentInputMode(currentInputMode);
            int deviceOS = (int) formData.get(4);
            if(deviceOS != 0) {
                playerCheat.setDeviceOS(deviceOS);
            }
            sendMessage("Connecting to " + address + ":" + port + "...");
            connectToServer(address, port);
            return true;
        }
        if(formId == -50003) { // fake lag
            if(!isConnectedToServer()) {
                sendMessage("§cYou are not connected to server.");
                return true;
            }
            ArrayList<String> formData = JSON.parseObject(packet.getFormData(), new TypeReference<>(){});
            if(formData.get(0) == null) {
                return true;
            }
            getPlayerCheat().setFakeLag(true);
            createFakeLagTimer((int) Double.parseDouble(formData.get(0)));
            sendMessage("§bFakeLag Enabled with delay " + formData.get(0) + "ms! To disable type /.fakelag");
            return true;
        }
        return false;
    }

    public void sendMessage(String str) {
        TextPacket packet = new TextPacket();
        packet.setMessage(PROXY_PREFIX + str);
        packet.setType(TextPacket.Type.RAW);
        packet.setPlatformChatId("");
        packet.setNeedsTranslation(false);
        packet.setXuid("");
        packet.setParameters(new LinkedList<>());
        packet.setSourceName("");
        clientSession.sendPacket(packet);
    }

    public void setPlayerFlag(EntityFlag flags, boolean value) {
        SetEntityDataPacket packet = new SetEntityDataPacket();
        packet.setRuntimeEntityId(playerId);
        packet.setTick(0);
        EntityFlags flag = new EntityFlags();
        flag.setFlag(flags, value);
        packet.getMetadata().putFlags(flag);
        clientSession.sendPacket(packet);
    }
}
