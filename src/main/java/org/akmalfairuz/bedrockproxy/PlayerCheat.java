package org.akmalfairuz.bedrockproxy;

import lombok.Getter;
import lombok.Setter;

public class PlayerCheat {

    private Player player;

    @Getter
    @Setter
    private boolean antikb = false;

    @Getter
    @Setter
    private boolean haste = false;

    @Getter
    @Setter
    private boolean fakeLag = false;

    @Getter
    @Setter
    private String deviceModel = "";

    @Getter
    @Setter
    private int deviceOS;

    @Getter
    @Setter
    private int currentInputMode;

    public PlayerCheat(Player player) {
        this.player = player;
    }
}
