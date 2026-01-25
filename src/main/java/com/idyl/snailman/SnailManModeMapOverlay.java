package com.idyl.snailman;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Named;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.Area;
import java.util.List;
import java.util.Objects;

public class SnailManModeMapOverlay extends Overlay {
    private final Client client;
    private final SnailManModePlugin plugin;
    private final SnailManModeConfig config;
    private final boolean developerMode;

    private Area mapClipArea;

    private BufferedImage mapIcon;

    @Inject
    private SnailManModeMapOverlay(Client client, SnailManModePlugin plugin, SnailManModeConfig config, @Named("developerMode") boolean developerMode) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.developerMode = developerMode;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(Overlay.PRIORITY_LOW);
        setLayer(OverlayLayer.MANUAL);
        drawAfterLayer(InterfaceID.Worldmap.MAP_CONTAINER);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if(!config.showOnMap()) return null;

        if (client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER) == null) {
            return null;
        }

        mapClipArea = getWorldMapClipArea(Objects.requireNonNull(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).getBounds());
        graphics.setClip(mapClipArea);

        if(this.developerMode && plugin.pathfinder != null) {
            List<WorldPoint> path = plugin.pathfinder.getPath();
            for (WorldPoint point : path) {
                Point graphicsPoint = plugin.mapWorldPointToGraphicsPoint(point);
                graphics.setColor(Color.GREEN);
                graphics.drawRect(graphicsPoint.getX(), graphicsPoint.getY(), 1, 1);
            }
        }

        BufferedImage marker = getMapIconImage();
        Point point = plugin.mapWorldPointToGraphicsPoint(plugin.getSnailWorldPoint());
        graphics.drawImage(marker, point.getX() - marker.getWidth() / 2, point.getY() - marker.getHeight() / 2, null);

        return null;
    }

    private BufferedImage getMapIconImage() {
        if(mapIcon == null) {
            mapIcon = ImageUtil.loadImageResource(getClass(), "/marker.png");
        }

        return mapIcon;
    }

    private Area getWorldMapClipArea(Rectangle baseRectangle) {
        final Widget overview = client.getWidget(InterfaceID.Worldmap.OVERVIEW_CONTAINER);
        final Widget surfaceSelector = client.getWidget(InterfaceID.Worldmap.MAPLIST_BOX_GRAPHIC0);

        Area clipArea = new Area(baseRectangle);

        if (overview != null && !overview.isHidden()) {
            clipArea.subtract(new Area(overview.getBounds()));
        }

        if (surfaceSelector != null && !surfaceSelector.isHidden()) {
            clipArea.subtract(new Area(surfaceSelector.getBounds()));
        }

        return clipArea;
    }
}
