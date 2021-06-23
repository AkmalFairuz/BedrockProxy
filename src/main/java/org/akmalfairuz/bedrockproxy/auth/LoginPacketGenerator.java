package org.akmalfairuz.bedrockproxy.auth;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.nukkitx.protocol.bedrock.packet.LoginPacket;
import com.nukkitx.protocol.bedrock.util.EncryptionUtils;
import io.netty.util.AsciiString;
import org.akmalfairuz.bedrockproxy.Player;
import org.akmalfairuz.bedrockproxy.utils.Log;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginPacketGenerator {

    public static LoginPacket create(Player player, String serverIp, int serverPort) throws Exception {
        LoginPacket loginPacket = new LoginPacket();

        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("EC");
        keyPairGen.initialize(new ECGenParameterSpec("secp256r1")); //use P-256

        KeyPair ecdsa256KeyPair = keyPairGen.generateKeyPair(); //for xbox live, xbox live requests use, ES256, ECDSA256
        player.publicKey = (ECPublicKey) ecdsa256KeyPair.getPublic();
        player.privateKey = (ECPrivateKey) ecdsa256KeyPair.getPrivate();

        Xbox xbox = new Xbox(player.getAccessToken());
        String userToken = xbox.getUserToken(player.publicKey, player.privateKey);
        String deviceToken = xbox.getDeviceToken(player.publicKey, player.privateKey);
        String titleToken = xbox.getTitleToken(player.publicKey, player.privateKey, deviceToken);
        String xsts = xbox.getXstsToken(userToken, deviceToken, titleToken, player.publicKey, player.privateKey);

        KeyPair ecdsa384KeyPair = EncryptionUtils.createKeyPair(); //use ES384, ECDSA384
        player.publicKey = (ECPublicKey) ecdsa384KeyPair.getPublic();
        player.privateKey = (ECPrivateKey) ecdsa384KeyPair.getPrivate();

        /*
         * So we get a "chain"(json array with info(that has 2 objects)) from minecraft.net using our xsts token
         * from there we have to add our own chain at the beginning of the chain(json array that minecraft.net sent us),
         * When is all said and done, we have 3 chains(they are jwt objects, header.payload.signature)
         * which we send to the server to check
         */
        String chainData = xbox.requestMinecraftChain(xsts, player.publicKey);
        JSONObject chainDataObject = JSONObject.parseObject(chainData);
        JSONArray minecraftNetChain = chainDataObject.getJSONArray("chain");
        String firstChainHeader = minecraftNetChain.getString(0);
        firstChainHeader = firstChainHeader.split("\\.")[0]; //get the jwt header(base64)
        firstChainHeader = new String(Base64.getDecoder().decode(firstChainHeader.getBytes())); //decode the jwt base64 header
        String firstKeyx5u = JSONObject.parseObject(firstChainHeader).getString("x5u");

        JSONObject newFirstChain = new JSONObject();
        newFirstChain.put("certificateAuthority", true);
        newFirstChain.put("exp", Instant.now().getEpochSecond() + TimeUnit.HOURS.toSeconds(6));
        newFirstChain.put("identityPublicKey", firstKeyx5u);
        newFirstChain.put("nbf", Instant.now().getEpochSecond() - TimeUnit.HOURS.toSeconds(6));

        {
            String publicKeyBase64 = Base64.getEncoder().encodeToString(player.publicKey.getEncoded());
            JSONObject jwtHeader = new JSONObject();
            jwtHeader.put("alg", "ES384");
            jwtHeader.put("x5u", publicKeyBase64);

            String header = Base64.getUrlEncoder().withoutPadding().encodeToString(jwtHeader.toJSONString().getBytes());
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(newFirstChain.toJSONString().getBytes());

            byte[] dataToSign = (header + "." + payload).getBytes();
            byte[] signatureBytes = null;
            try {
                Signature signature = Signature.getInstance("SHA384withECDSA");
                signature.initSign(player.privateKey);
                signature.update(dataToSign);
                signatureBytes = JoseStuff.DERToJOSE(signature.sign(), JoseStuff.AlgorithmType.ECDSA384);
            } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException ignored) {
            }

            String signatureString = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

            String jwt = header + "." + payload + "." + signatureString;

            JSONArray jsonArray = new JSONArray();
            jsonArray.add(jwt);
            jsonArray.addAll(minecraftNetChain);
            chainDataObject.put("chain", jsonArray); //replace the chain with our new chain
        }
        {
            //we are now going to get some data from a chain minecraft sent us(the last chain)
            String lastChain = minecraftNetChain.getString(minecraftNetChain.size() - 1);
            String lastChainPayload = lastChain.split("\\.")[1]; //get the middle(payload) jwt thing
            lastChainPayload = new String(Base64.getDecoder().decode(lastChainPayload.getBytes())); //decode the base64

            JSONObject payloadObject = JSONObject.parseObject(lastChainPayload);
            JSONObject extraData = payloadObject.getJSONObject("extraData");

            player.username = extraData.getString("displayName");
            player.xuid = extraData.getString("XUID");
            player.UUID = extraData.getString("identity");
        }

        loginPacket.setChainData(new AsciiString(chainDataObject.toJSONString().getBytes(StandardCharsets.UTF_8)));

        String splitted = player.getLoginPacket().getSkinData().toString().split("\\.")[1];
        String skinDataJwt = new String(Base64.getUrlDecoder().decode(splitted));
        JSONObject skinData = JSONObject.parseObject(skinDataJwt, new TypeReference<>(){});
        skinData.put("ServerAddress", serverIp + ":" + serverPort);
        skinData.put("ThirdPartyName", player.username);
        skinData.put("SelfSignedId", player.UUID);

        String publicKeyBase64 = Base64.getEncoder().encodeToString(player.publicKey.getEncoded());

        JSONObject jwtHeader = new JSONObject();
        jwtHeader.put("alg", "ES384");
        jwtHeader.put("x5u", publicKeyBase64);

        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(jwtHeader.toJSONString().getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(skinData.toJSONString().getBytes());

        byte[] dataToSign = (header + "." + payload).getBytes();
        byte[] signatureBytes = null;
        try {
            Signature signature = Signature.getInstance("SHA384withECDSA");
            signature.initSign(player.privateKey);
            signature.update(dataToSign);
            signatureBytes = JoseStuff.DERToJOSE(signature.sign(), JoseStuff.AlgorithmType.ECDSA384);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException ignored) {
        }
        String signatureString = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

        loginPacket.setSkinData(new AsciiString(header + "." + payload + "." + signatureString));
        loginPacket.setProtocolVersion(440);
        return loginPacket;
    }
}
