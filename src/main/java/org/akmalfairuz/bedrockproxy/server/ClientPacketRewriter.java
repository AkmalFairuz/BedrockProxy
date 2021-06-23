package org.akmalfairuz.bedrockproxy.server;

import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.data.entity.EntityLinkData;
import com.nukkitx.protocol.bedrock.packet.*;
import org.akmalfairuz.bedrockproxy.Player;
import org.akmalfairuz.bedrockproxy.utils.Log;

import java.util.ListIterator;

public class ClientPacketRewriter {

    public static void rewrite(Player player, BedrockPacket packet) {
        if(packet instanceof MovePlayerPacket) {
            ((MovePlayerPacket) packet).setRuntimeEntityId(convertEntityId(player, ((MovePlayerPacket) packet).getRuntimeEntityId()));
            return;
        }
        if(packet instanceof AnimatePacket) {
            ((AnimatePacket) packet).setRuntimeEntityId(convertEntityId(player, ((AnimatePacket) packet).getRuntimeEntityId()));
            return;
        }
        if(packet instanceof AdventureSettingsPacket) {
            ((AdventureSettingsPacket) packet).setUniqueEntityId(convertEntityId(player, ((AdventureSettingsPacket) packet).getUniqueEntityId()));
            return;
        }
        if(packet instanceof SetEntityDataPacket) {
            ((SetEntityDataPacket) packet).setRuntimeEntityId(convertEntityId(player, ((SetEntityDataPacket) packet).getRuntimeEntityId()));
            return;
        }
        if(packet instanceof SetEntityLinkPacket) {
            long from = convertEntityId(player, ((SetEntityLinkPacket) packet).getEntityLink().getFrom());
            long to = convertEntityId(player, ((SetEntityLinkPacket) packet).getEntityLink().getTo());
            ((SetEntityLinkPacket) packet).setEntityLink(new EntityLinkData(from, to, ((SetEntityLinkPacket) packet).getEntityLink().getType(), ((SetEntityLinkPacket) packet).getEntityLink().isImmediate()));
            return;
        }
        if(packet instanceof SetEntityMotionPacket) {
            ((SetEntityMotionPacket) packet).setRuntimeEntityId(convertEntityId(player, ((SetEntityMotionPacket) packet).getRuntimeEntityId()));
            return;
        }
        if(packet instanceof AddPlayerPacket) {
            ((AddPlayerPacket) packet).setRuntimeEntityId(convertEntityId(player, ((AddPlayerPacket) packet).getRuntimeEntityId()));
            ((AddPlayerPacket) packet).setUniqueEntityId(convertEntityId(player, ((AddPlayerPacket) packet).getUniqueEntityId()));
            ListIterator<EntityLinkData> iterator = ((AddPlayerPacket) packet).getEntityLinks().listIterator();
            while (iterator.hasNext()) {
                EntityLinkData entityLink = iterator.next();
                long from = convertEntityId(player, entityLink.getFrom());
                long to = convertEntityId(player, entityLink.getTo());
                if (entityLink.getFrom() != from || entityLink.getTo() != to) {
                    iterator.set(new EntityLinkData(from, to, entityLink.getType(), entityLink.isImmediate(), entityLink.isRiderInitiated()));
                }
            }
            return;
        }
        if(packet instanceof PlayerActionPacket) {
            ((PlayerActionPacket) packet).setRuntimeEntityId(convertEntityId(player, ((PlayerActionPacket) packet).getRuntimeEntityId()));
            return;
        }
        if(packet instanceof TakeItemEntityPacket) {
            ((TakeItemEntityPacket) packet).setRuntimeEntityId(convertEntityId(player, ((TakeItemEntityPacket) packet).getRuntimeEntityId()));
            return;
        }
        if(packet instanceof InteractPacket) {
            ((InteractPacket) packet).setRuntimeEntityId(convertEntityId(player, ((InteractPacket) packet).getRuntimeEntityId()));
            return;
        }
        if(packet instanceof EntityEventPacket) {
            ((EntityEventPacket) packet).setRuntimeEntityId(convertEntityId(player, ((EntityEventPacket) packet).getRuntimeEntityId()));
            return;
        }
        if(packet instanceof UpdateAttributesPacket) {
            ((UpdateAttributesPacket) packet).setRuntimeEntityId(convertEntityId(player, ((UpdateAttributesPacket) packet).getRuntimeEntityId()));
            return;
        }
        if(packet instanceof UpdateTradePacket) {
            ((UpdateTradePacket) packet).setPlayerUniqueEntityId(convertEntityId(player, ((UpdateTradePacket) packet).getPlayerUniqueEntityId()));
            ((UpdateTradePacket) packet).setTraderUniqueEntityId(convertEntityId(player, ((UpdateTradePacket) packet).getTraderUniqueEntityId()));
            return;
        }
        if(packet instanceof MobArmorEquipmentPacket) {
            ((MobArmorEquipmentPacket) packet).setRuntimeEntityId(convertEntityId(player, ((MobArmorEquipmentPacket) packet).getRuntimeEntityId()));
            return;
        }
        if(packet instanceof MobEquipmentPacket) {
            ((MobEquipmentPacket) packet).setRuntimeEntityId(convertEntityId(player, ((MobEquipmentPacket) packet).getRuntimeEntityId()));
            return;
        }
        if(packet instanceof AddEntityPacket) {
            ((AddEntityPacket) packet).setRuntimeEntityId(convertEntityId(player, ((AddEntityPacket) packet).getRuntimeEntityId()));
            ((AddEntityPacket) packet).setUniqueEntityId(convertEntityId(player, ((AddEntityPacket) packet).getUniqueEntityId()));

            ((AddEntityPacket) packet).setRuntimeEntityId(convertEntityId(player, ((AddEntityPacket) packet).getRuntimeEntityId()));

            ListIterator<EntityLinkData> iterator = ((AddEntityPacket) packet).getEntityLinks().listIterator();
            while (iterator.hasNext()) {
                EntityLinkData entityLink = iterator.next();
                long from = convertEntityId(player, entityLink.getFrom());
                long to = convertEntityId(player, entityLink.getTo());
                if (entityLink.getFrom() != from || entityLink.getTo() != to) {
                    iterator.set(new EntityLinkData(from, to, entityLink.getType(), entityLink.isImmediate(), entityLink.isRiderInitiated()));
                }
            }
            return;
        }
        if(packet instanceof RemoveEntityPacket) {
            ((RemoveEntityPacket) packet).setUniqueEntityId(convertEntityId(player, ((RemoveEntityPacket) packet).getUniqueEntityId()));
            return;
        }
        if(packet instanceof MobEffectPacket) {
            ((MobEffectPacket) packet).setRuntimeEntityId(convertEntityId(player, ((MobEffectPacket) packet).getRuntimeEntityId()));
            return;
        }
        if(packet instanceof RespawnPacket) {
            ((RespawnPacket) packet).setRuntimeEntityId(convertEntityId(player, ((RespawnPacket) packet).getRuntimeEntityId()));
            return;
        }
        if(packet instanceof MoveEntityDeltaPacket) {
            ((MoveEntityDeltaPacket) packet).setRuntimeEntityId(convertEntityId(player, ((MoveEntityDeltaPacket) packet).getRuntimeEntityId()));
            return;
        }
        if(packet instanceof MoveEntityAbsolutePacket) {
            ((MoveEntityAbsolutePacket) packet).setRuntimeEntityId(convertEntityId(player, ((MoveEntityAbsolutePacket) packet).getRuntimeEntityId()));
            return;
        }
    }

    public static long convertEntityId(Player player, long id) {
        if(id == player.getPlayerId()) {
            return player.getPlayerIdServer();
        }
        return id;
    }
}
