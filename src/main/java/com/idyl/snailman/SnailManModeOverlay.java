package com.idyl.snailman;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Named;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

@Slf4j
public class SnailManModeOverlay extends Overlay {
    private static final int MAX_DRAW_DISTANCE = 32;

    private final Client client;
    private final SnailManModeConfig config;
    private final SnailManModePlugin plugin;
    private final boolean developerMode;

    BufferedImage snailShell = null;

    @Inject
    private SnailManModeOverlay(Client client, SnailManModeConfig config, SnailManModePlugin plugin, @Named("developerMode") boolean developerMode) {
        this.client = client;
        this.config = config;
        this.plugin = plugin;
        this.developerMode = developerMode;

        this.setPosition(OverlayPosition.DYNAMIC);
        this.setPriority(OverlayPriority.LOW);
        this.setLayer(OverlayLayer.ABOVE_SCENE);
    }

    private void renderTransports(Graphics2D graphics) {
        for (WorldPoint a : this.plugin.pathfinderConfig.getTransports().keySet()) {
            this.drawTransport(graphics, a);

            java.awt.Point ca = this.tileCenter(a);

            if (ca == null) {
                continue;
            }

            for (Transport t : this.plugin.pathfinderConfig.getTransports().get(a)) {
                WorldPoint b = t.getOrigin();
                java.awt.Point cb = this.tileCenter(b);

                if (cb != null) {
                    graphics.drawLine(ca.x, ca.y, cb.x, cb.y);
                }
            }

            StringBuilder s = new StringBuilder();
            for (Transport t : this.plugin.pathfinderConfig.getTransports().get(a)) {
                WorldPoint b = t.getDestination();
                if (b.getPlane() > a.getPlane()) {
                    s.append("+");
                } else if (b.getPlane() < a.getPlane()) {
                    s.append("-");
                } else {
                    s.append("=");
                }
            }
            graphics.setColor(Color.WHITE);
            graphics.drawString(s.toString(), ca.x, ca.y);
        }
    }

    private java.awt.Point tileCenter(WorldPoint b) {
        if (b.getPlane() != this.client.getPlane()) {
            return null;
        }

        LocalPoint lp = LocalPoint.fromWorld(this.client, b);
        if (lp == null) {
            return null;
        }

        Polygon poly = Perspective.getCanvasTilePoly(this.client, lp);
        if (poly == null) {
            return null;
        }

        int cx = poly.getBounds().x + poly.getBounds().width / 2;
        int cy = poly.getBounds().y + poly.getBounds().height / 2;
        return new java.awt.Point(cx, cy);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (this.developerMode) this.renderTransports(graphics);

        WorldPoint snailPoint = this.plugin.getSnailWorldPoint();
        WorldPoint playerLocation = this.client.getLocalPlayer().getWorldLocation();

        if (this.developerMode && this.plugin.pathfinder != null) {
            List<WorldPoint> path = this.plugin.pathfinder.getPath();
            for (WorldPoint point : path) {
                this.drawTile(graphics, point, Color.GREEN, new BasicStroke((float) 2));
            }
        }

        long drawDistance = this.config.drawDistance();

        if (snailPoint.distanceTo(playerLocation) >= drawDistance) {
            return null;
        }

        if (this.config.drawTile()) {
            this.drawTile(graphics, snailPoint, this.config.tileColor(), new BasicStroke((float) 2));
        }
        if (this.config.visualMode() == SnailManModeConfig.SnailVisualMode.SPRITE) {
            this.drawImg(graphics, snailPoint);
        }
        return null;
    }

    private void drawTile(Graphics2D graphics, WorldPoint point, Color color, Stroke borderStroke) {

        LocalPoint lp = LocalPoint.fromWorld(this.client, point);
        if (lp == null) {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(this.client, lp);
        if (poly != null) {
            OverlayUtil.renderPolygon(graphics, poly, color, new Color(0, 0, 0, 1), borderStroke);
        }
    }

    private void drawImg(Graphics2D graphics, WorldPoint point) {
        LocalPoint lp = LocalPoint.fromWorld(this.client, point);
        if (lp == null) {
            return;
        }

        BufferedImage snailShell = this.getSnailImage();
//        snailShell = ImageUtil.resizeImage(snailShell, snailShell.getWidth() * 10, snailShell.getHeight() * 10);
        Point canvasImageLocation = Perspective.getCanvasImageLocation(this.client, lp, snailShell, 75);

        if (canvasImageLocation == null) {
            return;
        }

        OverlayUtil.renderImageLocation(graphics, canvasImageLocation, snailShell);
    }

    private void drawTransport(Graphics2D graphics, WorldPoint point) {
        if (point.getPlane() != this.client.getPlane()) {
            return;
        }

        LocalPoint lp = LocalPoint.fromWorld(this.client, point);
        if (lp == null) {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(this.client, lp);
        if (poly == null) {
            return;
        }

        graphics.setColor(Color.GREEN);
        graphics.fill(poly);
    }

    private BufferedImage getSnailImage() {
        if (this.snailShell == null) {
            this.snailShell = ImageUtil.loadImageResource(this.getClass(), "/snail_shell.png");
        }
        return this.snailShell;
    }
}
