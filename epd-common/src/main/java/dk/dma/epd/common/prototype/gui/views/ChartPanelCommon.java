/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dma.epd.common.prototype.gui.views;

import java.awt.Point;
import java.util.List;

import com.bbn.openmap.BufferedLayerMapBean;
import com.bbn.openmap.Layer;
import com.bbn.openmap.LayerHandler;
import com.bbn.openmap.MapBean;
import com.bbn.openmap.MapHandler;
import com.bbn.openmap.MouseDelegator;
import com.bbn.openmap.gui.OMComponentPanel;
import com.bbn.openmap.layer.shape.MultiShapeLayer;
import com.bbn.openmap.proj.Proj;
import com.bbn.openmap.proj.Projection;
import com.bbn.openmap.proj.coords.LatLonPoint;

import dk.dma.enav.model.geometry.Position;
import dk.dma.epd.common.prototype.EPD;
import dk.dma.epd.common.prototype.event.HistoryListener;
import dk.dma.epd.common.prototype.event.mouse.CommonDragMouseMode;
import dk.dma.epd.common.prototype.event.mouse.CommonNavigationMouseMode;
import dk.dma.epd.common.prototype.event.mouse.CommonRouteEditMouseMode;
import dk.dma.epd.common.prototype.gui.util.SimpleOffScreenMapRenderer;
import dk.dma.epd.common.prototype.layers.ais.AisLayerCommon;
import dk.dma.epd.common.prototype.layers.intendedroute.IntendedRouteLayerCommon;
import dk.dma.epd.common.prototype.layers.intendedroute.IntendedRouteTCPALayer;
import dk.dma.epd.common.prototype.layers.msi.MsiLayerCommon;
import dk.dma.epd.common.prototype.layers.route.RouteLayerCommon;
import dk.dma.epd.common.prototype.layers.routeedit.NewRouteContainerLayer;
import dk.dma.epd.common.prototype.layers.routeedit.RouteEditLayerCommon;
import dk.dma.epd.common.prototype.layers.wms.WMSLayer;
import dk.dma.epd.common.prototype.settings.MapSettings;

/**
 * The panel with chart. Initializes all layers to be shown on the map.
 * 
 * @author Jens Tuxen (mail@jenstuxen.com)
 */
public abstract class ChartPanelCommon extends OMComponentPanel {
    
    private static final long serialVersionUID = 1L;

    protected int maxScale = 5000;
    
    // Mouse modes
    protected MouseDelegator mouseDelegator;
    protected CommonNavigationMouseMode mapNavMouseMode;
    protected CommonDragMouseMode dragMouseMode;
    protected CommonRouteEditMouseMode routeEditMouseMode;
    
    // Layers and handlers
    protected MapHandler mapHandler;
    protected MapHandler dragMapHandler;
    protected LayerHandler layerHandler;
    protected BufferedLayerMapBean map;
    protected BufferedLayerMapBean dragMap;
    protected SimpleOffScreenMapRenderer dragMapRenderer;
    protected Layer encLayer;
    protected MultiShapeLayer bgLayer;
    protected WMSLayer wmsLayer;
    protected WMSLayer wmsDragLayer;
    protected AisLayerCommon<?> aisLayer;
    protected RouteLayerCommon routeLayer;
    protected RouteEditLayerCommon routeEditLayer;
    protected NewRouteContainerLayer newRouteContainerLayer;
    protected MsiLayerCommon msiLayer;
    protected IntendedRouteLayerCommon intendedRouteLayer;
    protected IntendedRouteTCPALayer intendedRouteTCPALayer;
    
    protected HistoryListener historyListener;
    
    /**
     * Constructor
     */
    protected ChartPanelCommon() {
        maxScale = EPD.getInstance().getSettings().getMapSettings().getMaxScale();
    }
    
    /**
     * Save chart settings for workspace
     */
    public void saveSettings() {
        MapSettings mapSettings = EPD.getInstance().getSettings().getMapSettings();
        mapSettings.setCenter((LatLonPoint) map.getCenter());
        mapSettings.setScale(map.getScale());
    }

    /**
     * Initiate drag map
     */
    protected void initDragMap() {

        MapSettings mapSettings = EPD.getInstance().getSettings().getMapSettings();
        dragMap = new BufferedLayerMapBean();
        dragMap.setDoubleBuffered(true);
        dragMap.setCenter(mapSettings.getCenter());
        dragMap.setScale(mapSettings.getScale());

        dragMapHandler.add(new LayerHandler());
        if (mapSettings.isUseWms() && mapSettings.isUseWmsDragging()) {
            dragMapHandler.add(dragMap);
            wmsDragLayer = new WMSLayer(mapSettings.getWmsQuery());
            wmsDragLayer.setVisible(true);
            dragMapHandler.add(wmsDragLayer);
            dragMapRenderer = new SimpleOffScreenMapRenderer(map, dragMap, 3);
        } else {
            // create dummy map dragging
            dragMapRenderer = new SimpleOffScreenMapRenderer(map, dragMap, true);
        }
        dragMapRenderer.start();
    }
    
    /**
     * Changes the current center of the map to a new position.
     * @param position Position to change to center.
     */
    public void goToPosition(Position position) {
        getMap().setCenter(position.getLatitude(), position.getLongitude());
        forceAisLayerUpdate();
    }
    
    /**
     * Given a set of points scale and center so that all points are contained in the view
     * 
     * @param waypoints
     */
    public void zoomTo(List<Position> waypoints) {
        if (waypoints.size() == 0) {
            return;
        }

        if (waypoints.size() == 1) {
            map.setCenter(waypoints.get(0).getLatitude(), waypoints.get(0).getLongitude());
            forceAisLayerUpdate();
            return;
        }

        // Find bounding box
        double maxLat = -91;
        double minLat = 91;
        double maxLon = -181;
        double minLon = 181;
        for (Position pos : waypoints) {
            if (pos.getLatitude() > maxLat) {
                maxLat = pos.getLatitude();
            }
            if (pos.getLatitude() < minLat) {
                minLat = pos.getLatitude();
            }
            if (pos.getLongitude() > maxLon) {
                maxLon = pos.getLongitude();
            }
            if (pos.getLongitude() < minLon) {
                minLon = pos.getLongitude();
            }
        }

        double centerLat = (maxLat + minLat) / 2.0;
        double centerLon = (maxLon + minLon) / 2.0;
        map.setCenter(centerLat, centerLon);
        forceAisLayerUpdate();
    }
    
    /**
     * Force an update in the AIS layer
     */
    public void forceAisLayerUpdate() {
        if (aisLayer != null) {
            aisLayer.forceLayerUpdate();
        }
    }
    
    /**
     * Change zoom level on map
     * 
     * @param factor
     */
    public void doZoom(float factor) {
        float newScale = map.getScale() * factor;
        if (newScale < maxScale) {
            newScale = maxScale;
        }
        map.setScale(newScale);
        forceAisLayerUpdate();

    }
    
    /**
     * Pans the map in the given direction
     * 
     * @param direction
     *            1 == Up 2 == Down 3 == Left 4 == Right
     * 
     *            Moving by 100 units in each direction Map center is [745, 445]
     */
    public void pan(int direction) {
        Point point = null;
        Projection projection = map.getProjection();

        int width = projection.getWidth();
        int height = projection.getHeight();

        switch (direction) {
        case 1:
            point = new Point(width / 2, height / 2 - 100);
            break;
        case 2:
            point = new Point(width / 2, height / 2 + 100);
            break;
        case 3:
            point = new Point(width / 2 - 100, height / 2);
            break;
        case 4:
            point = new Point(width / 2 + 100, height / 2);
            break;
        }

        Proj p = (Proj) projection;
        LatLonPoint llp = projection.inverse(point);
        p.setCenter(llp);
        map.setProjection(p);

        forceAisLayerUpdate();
    }
    
    /*******************************/
    /** Layer visibility          **/
    /*******************************/
    
    /**
     * Sets AIS layer visibility
     * 
     * @param visible the visibility
     */
    public void aisVisible(boolean visible) {
        if (aisLayer != null) {
            aisLayer.setVisible(visible);
        }
    }

    /**
     * Sets ENC layer visibility
     * 
     * @param visible the visibility
     */
    public void encVisible(boolean visible) {
        if (encLayer != null) {
            encLayer.setVisible(visible);
            bgLayer.setVisible(!visible);
            if (!visible) {
                // Force update of background layer
                bgLayer.doPrepare();
            }
        } else {
            bgLayer.setVisible(true);
        }
    }

    /**
     * Sets WMS layer visibility
     * 
     * @param visible the visibility
     */
    public void wmsVisible(boolean visible) {
        if (wmsLayer != null) {
            wmsLayer.setVisible(visible);
        }
    }

    /**
     * Sets Intended Route layer visibility
     * 
     * @param visible the visibility
     */
    public void intendedRouteLayerVisible(boolean visible) {
        if (intendedRouteLayer != null) {
            intendedRouteLayer.setVisible(visible);
        }
    }

    
    /*******************************/
    /** Getters and setters       **/
    /*******************************/
    
    public MapBean getMap() {
        return map;
    }
    
    public MapHandler getMapHandler() {
        return mapHandler;
    }
    
    public MultiShapeLayer getBgLayer() {
        return bgLayer;
    }
    
    public AisLayerCommon<?> getAisLayer() {
        return aisLayer;
    }
    
    public RouteLayerCommon getRouteLayer() {
        return routeLayer;
    }

    public RouteEditLayerCommon getRouteEditLayer() {
        return routeEditLayer;
    }

    public NewRouteContainerLayer getNewRouteContainerLayer() {
        return newRouteContainerLayer;
    }

    public MsiLayerCommon getMsiLayer() {
        return msiLayer;
    }

    public final BufferedLayerMapBean getDragMap() {
        return dragMap;
    }
    
    public WMSLayer getWmsDragLayer() {
        return wmsDragLayer;
    }

    public SimpleOffScreenMapRenderer getDragMapRenderer() {
        return dragMapRenderer;
    }

    public void setDragMapRenderer(SimpleOffScreenMapRenderer dragMapRenderer) {
        this.dragMapRenderer = dragMapRenderer;
    }

    public WMSLayer getWmsLayer() {
        return wmsLayer;
    }

    public Layer getEncLayer() {
        return encLayer;
    }
    
    public void setEncLayer(Layer encLayer) {
        this.encLayer = encLayer;
    }
    
    public HistoryListener getHistoryListener() {
        return historyListener;
    }
    
    public void setHistoryListener(HistoryListener historyListener2) {
        this.historyListener = historyListener2;
    }
    
    public MouseDelegator getMouseDelegator() {
        return mouseDelegator;
    }
}
