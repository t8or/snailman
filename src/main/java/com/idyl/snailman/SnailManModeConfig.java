package com.idyl.snailman;

import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup(SnailManModeConfig.CONFIG_GROUP)
public interface SnailManModeConfig extends Config {
    String CONFIG_GROUP = "snailmanmode";
    String CONFIG_KEY_SNAIL_LOC = "snailWorldPoint";
    String CONFIG_KEY_IS_ALIVE = "isAlive";
    String CONFIG_KEY_VISUAL_MODE = "visualMode";

    @ConfigItem(
        keyName = "drawTile",
        name = "Draw Tile",
        description = "Draw the snail tile",
        position = 0
    )
    default boolean drawTile() { return false; }

    @ConfigItem(
        keyName = "snailColor",
        name = "Tile Color",
        description = "The color of the snail tile",
        position = 1
    )
    default Color tileColor() {
        return Color.RED;
    }

    @ConfigItem(
        keyName = CONFIG_KEY_VISUAL_MODE,
        name = "Snail Visual Mode",
        description = "The visual mode of the snail",
        position = 2
    )
    default SnailVisualMode visualMode() {
        return SnailVisualMode.MODEL;
    }

    @ConfigItem(
        keyName = "moveSpeed",
        name = "Move Speed",
        description = "How many ticks it takes the snail to move 1 tile",
        position = 3
    )
    @Range(
        min = 1,
        max = 30
    )
    default int moveSpeed() {
        return 1;
    }

    @ConfigItem(
        keyName = "speedBoost",
        name = "Speed Boost",
        description = "Snail moves 1 tile per tick when it gets close to you",
        position = 4
    )
    default boolean speedBoost() {
        return false;
    }

    @ConfigItem(
        keyName = "drawDistance",
        name = "Draw Distance",
        description = "Distance at which the snail is rendered",
        position = 6
    )
    @Range(
        min = 1,
        max = 32
    )
    default int drawDistance() {
        return 32;
    }

    @ConfigItem(
        keyName = "horrorMode",
        name = "Horror Mode",
        description = "Plays a sound when the snail is nearby (best experienced with minimum render distance & maximum fog in the GPU plugin)",
        position = 7
    )
    default boolean horrorMode() {
        return false;
    }

    @ConfigSection(
        name = "Extras",
        description = "Extra settings not often used",
        position = 99,
        closedByDefault = true
    )
    String extrasSection = "extras";

    @ConfigItem(
        keyName = "showOnMap",
        name = "Show Snail on World Map",
        description = "Show where the snail is on the world map (kind of defeats the purpose but its neat)",
        section = extrasSection,
        position = 0
    )
    default boolean showOnMap() {
        return false;
    }

    @ConfigItem(
        keyName = "pauseSnail",
        name = "Pause Snail",
        description = "Pause the snail so that it stops following you",
        section = extrasSection,
        position = 1
    )
    default boolean pauseSnail() {
        return false;
    }

    enum SnailVisualMode {
        SPRITE,
        MODEL,
    }
}
