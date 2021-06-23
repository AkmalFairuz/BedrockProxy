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

    public PlayerCheat(Player player) {
        this.player = player;
    }
}
