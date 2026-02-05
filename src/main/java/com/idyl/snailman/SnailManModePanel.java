package com.idyl.snailman;

import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class SnailManModePanel extends PluginPanel {
    private final ItemManager itemManager;
    private final SnailManModePlugin plugin;

    /* This is the panel the tabs' respective panels will be displayed on. */
    private final JPanel display = new JPanel();
    private final Map<Tab, MaterialTab> uiTabs = new HashMap<>();
    private final MaterialTabGroup tabGroup = new MaterialTabGroup(this.display);

    private boolean active;

    @Inject
    public SnailManModePanel(ItemManager itemManager, SnailManModePlugin plugin) {
        super(false);
        this.itemManager = itemManager;
        this.plugin = plugin;

        this.setLayout(new BorderLayout());
        this.setBackground(ColorScheme.DARK_GRAY_COLOR);
        this.display.setBorder(new EmptyBorder(10, 10, 8, 10));
        this.display.setLayout(new GridLayout(0, 1, 0, 5));

        JButton resetButton = new JButton("Reset Snail Data");
        resetButton.addActionListener(l -> plugin.reset());

        this.display.add(resetButton);

        if (this.plugin.developerMode) {
            JButton testAnim = new JButton("Test Death Anim");
            testAnim.addActionListener(l -> plugin.testDeathAnim());
            this.display.add(testAnim);
        }

        this.add(this.display, BorderLayout.NORTH);
    }
}