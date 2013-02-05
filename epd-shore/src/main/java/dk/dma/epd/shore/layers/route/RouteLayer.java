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
package dk.dma.epd.shore.layers.route;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.List;

import javax.swing.SwingUtilities;

import com.bbn.openmap.MapBean;
import com.bbn.openmap.event.MapMouseListener;
import com.bbn.openmap.layer.OMGraphicHandlerLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import com.bbn.openmap.omGraphics.OMGraphicList;
import com.bbn.openmap.omGraphics.OMList;
import com.bbn.openmap.proj.coords.LatLonPoint;

import dk.dma.enav.model.geometry.Position;
import dk.dma.epd.common.math.Vector2D;
import dk.dma.epd.common.prototype.ais.AisAdressedRouteSuggestion;
import dk.dma.epd.common.prototype.layers.route.ActiveRouteGraphic;
import dk.dma.epd.common.prototype.layers.route.MetocGraphic;
import dk.dma.epd.common.prototype.layers.route.MetocPointGraphic;
import dk.dma.epd.common.prototype.layers.route.RouteGraphic;
import dk.dma.epd.common.prototype.layers.route.RouteLegGraphic;
import dk.dma.epd.common.prototype.layers.route.SuggestedRouteGraphic;
import dk.dma.epd.common.prototype.layers.route.WaypointCircle;
import dk.dma.epd.common.prototype.model.route.ActiveRoute;
import dk.dma.epd.common.prototype.model.route.IRoutesUpdateListener;
import dk.dma.epd.common.prototype.model.route.Route;
import dk.dma.epd.common.prototype.model.route.RouteWaypoint;
import dk.dma.epd.common.prototype.model.route.RoutesUpdateEvent;
import dk.dma.epd.shore.EPDShore;
import dk.dma.epd.shore.event.DragMouseMode;
import dk.dma.epd.shore.event.NavigationMouseMode;
import dk.dma.epd.shore.event.SelectMouseMode;
import dk.dma.epd.shore.gui.views.JMapFrame;
import dk.dma.epd.shore.gui.views.MapMenu;
import dk.dma.epd.shore.route.RouteManager;
import dk.frv.enav.common.xml.metoc.MetocForecast;
import dk.frv.enav.common.xml.metoc.MetocForecastPoint;


//import dk.frv.enav.ins.gui.MapMenu;

/**
 * Layer for showing routes
 */
public class RouteLayer extends OMGraphicHandlerLayer implements IRoutesUpdateListener, MapMouseListener {

    private static final long serialVersionUID = 1L;

    private RouteManager routeManager;
    private MetocInfoPanel metocInfoPanel;
    private WaypointInfoPanel waypointInfoPanel;
    private MapBean mapBean;

    private OMGraphicList graphics = new OMGraphicList();
    private OMGraphicList metocGraphics = new OMGraphicList();
    private boolean arrowsVisible;
    private OMGraphic closest;
    private OMGraphic selectedGraphic;
    private MetocGraphic routeMetoc;
    private SuggestedRouteGraphic suggestedRoute;
    private JMapFrame jMapFrame;
    private MapMenu routeMenu;
    private boolean dragging;


    public RouteLayer() {
        routeManager = EPDShore.getRouteManager();
        routeManager.addListener(this);
    }

    @Override
    public synchronized void routesChanged(RoutesUpdateEvent e) {
        if(e == RoutesUpdateEvent.ROUTE_MSI_UPDATE) {
            return;
        }

        graphics.clear();

        Stroke stroke = new BasicStroke(
                3.0f,                      // Width
                BasicStroke.CAP_SQUARE,    // End cap
                BasicStroke.JOIN_MITER,    // Join style
                10.0f,                     // Miter limit
                new float[] { 3.0f, 10.0f }, // Dash pattern
                0.0f);
        Stroke activeStroke = new BasicStroke(
                3.0f,                      // Width
                BasicStroke.CAP_SQUARE,    // End cap
                BasicStroke.JOIN_MITER,    // Join style
                10.0f,                     // Miter limit
                new float[] { 10.0f, 8.0f }, // Dash pattern
                0.0f);                     // Dash phase
        Color ECDISOrange = new Color(213, 103, 45, 255);

        int activeRouteIndex = routeManager.getActiveRouteIndex();
        for (int i = 0; i < routeManager.getRoutes().size(); i++) {
            Route route = routeManager.getRoutes().get(i);
            if(route.isVisible() && i != activeRouteIndex){
                RouteGraphic routeGraphic = new RouteGraphic(route, i, arrowsVisible, stroke, ECDISOrange);
                graphics.add(routeGraphic);
            }
        }

        if (routeManager.isRouteActive()) {
            ActiveRoute activeRoute = routeManager.getActiveRoute();
            if (activeRoute.isVisible()) {
                ActiveRouteGraphic activeRouteExtend = new ActiveRouteGraphic(activeRoute, activeRouteIndex, arrowsVisible, activeStroke, Color.RED);
                graphics.add(activeRouteExtend);
            }
        }

        // Handle route metoc
        metocGraphics.clear();
        for (int i = 0; i < routeManager.getRoutes().size(); i++) {
            Route route = routeManager.getRoutes().get(i);
            boolean activeRoute = false;

            if (routeManager.isActiveRoute(i)) {
                route = routeManager.getActiveRoute();
                activeRoute = true;
            }

            if (routeManager.showMetocForRoute(route)) {
                routeMetoc = new MetocGraphic(route, activeRoute, EPDShore.getSettings().getEnavSettings());
                metocGraphics.add(routeMetoc);
            }
        }
        if (metocGraphics.size() > 0) {
            graphics.add(0, metocGraphics);
        }

        for (AisAdressedRouteSuggestion routeSuggestion : routeManager
                .getAddressedSuggestedRoutes()) {
            if (!routeSuggestion.isHidden()) {
                suggestedRoute = new SuggestedRouteGraphic(routeSuggestion,
                        stroke);
                graphics.add(suggestedRoute);
            }
        }


        graphics.project(getProjection(), true);

        doPrepare();
    }

    /**
     * Calculate distance between displayed METOC-points projected onto the screen
     * @param metocGraphic METOC-graphics containing METOC-points
     * @return The smallest distance between displayed METOC-points projected onto the screen
     */
    public double calculateMetocDistance(MetocGraphic metocGraphic){
        List<OMGraphic> forecasts = metocGraphic.getTargets();
        double minDist = 0;
        for (int i = 0; i < forecasts.size(); i++) {
            if(i < forecasts.size()-2){
                MetocPointGraphic metocForecastPoint = (MetocPointGraphic) forecasts.get(i);
                MetocPointGraphic metocForecastPointNext = (MetocPointGraphic) forecasts.get(i+1);
                double lat = metocForecastPoint.getLat();
                double lon = metocForecastPoint.getLon();

                double latnext = metocForecastPointNext.getLat();
                double lonnext = metocForecastPointNext.getLon();

                Point2D current = getProjection().forward(lat, lon);
                Point2D next = getProjection().forward(latnext, lonnext);

                Vector2D vector = new Vector2D(current.getX(),current.getY(),next.getX(),next.getY());

                double newDist = vector.norm();

                if(i == 0){
                    minDist = newDist;
                }

                if(minDist > newDist){
                    minDist = newDist;
                }
            }
        }
        return minDist;
    }

    /**
     * Calculate distance between each METOC-point projected onto the screen
     * @param route The route which contains metoc data (check for this before!)
     * @return The smallest distance between METOC-points projected onto the screen
     */
    public double calculateMetocDistance(Route route){
        MetocForecast routeMetoc  = route.getMetocForecast();
        List<MetocForecastPoint> forecasts = routeMetoc.getForecasts();
        double minDist = 0;
        for (int i = 0; i < forecasts.size(); i++) {
            if(i < forecasts.size()-2){
                MetocForecastPoint metocForecastPoint = forecasts.get(i);
                MetocForecastPoint metocForecastPointNext = forecasts.get(i+1);
                double lat = metocForecastPoint.getLat();
                double lon = metocForecastPoint.getLon();

                double latnext = metocForecastPointNext.getLat();
                double lonnext = metocForecastPointNext.getLon();

                Point2D current = getProjection().forward(lat, lon);
                Point2D next = getProjection().forward(latnext, lonnext);

                Vector2D vector = new Vector2D(current.getX(),current.getY(),next.getX(),next.getY());

                double newDist = vector.norm();

                if(i == 0){
                    minDist = newDist;
                }

                if(minDist > newDist){
                    minDist = newDist;
                }
            }
        }
        return minDist;
    }

    @Override
    public synchronized OMGraphicList prepare() {
//        System.out.println("Entering RouteLayer.prepare()");
//        long start = System.nanoTime();
        for (OMGraphic omgraphic : graphics) {
            if(omgraphic instanceof RouteGraphic){
                ((RouteGraphic) omgraphic).showArrowHeads(getProjection().getScale() < EPDShore.getSettings().getNavSettings().getShowArrowScale());
            }
        }

        List<OMGraphic> metocList = metocGraphics.getTargets();
        for (OMGraphic omGraphic : metocList) {
            MetocGraphic metocGraphic = (MetocGraphic) omGraphic;
            Route route = metocGraphic.getRoute();
            if(routeManager.showMetocForRoute(route)){
                double minDist = calculateMetocDistance(route);
                int step = (int) (5/minDist);
                if(step < 1) {
                    step = 1;
                }
                metocGraphic.setStep(step);
                metocGraphic.paintMetoc();
            }
        }

        graphics.project(getProjection());
//        System.out.println("Finished RouteLayer.prepare() in " + EeINS.elapsed(start) + " ms\n---");
        return graphics;
    }

//    @Override
//    public void paint(Graphics g) {
//        System.out.println("Entering RouteLayer.paint)");
//        long start = System.nanoTime();
//        super.paint(g);
//        System.out.println("Finished RouteLayer.paint() in " + EeINS.elapsed(start) + " ms\n---");
//    }

    @Override
    public void findAndInit(Object obj) {
        if (obj instanceof RouteManager) {
            System.out.println("yo");
            routeManager = (RouteManager)obj;
            routeManager.addListener(this);
        }

//        if (obj instanceof MainFrame) {
//            MainFrame mainFrame = (MainFrame) obj;
////            routeManager = mainFrame.getRouteManagerDialog();
//            System.out.println("yo yo yo yo yo");
//        }

        if (obj instanceof JMapFrame){
            if (waypointInfoPanel == null && routeManager != null) {
                waypointInfoPanel = new WaypointInfoPanel();
            }

            jMapFrame = (JMapFrame) obj;
            metocInfoPanel = new MetocInfoPanel();
            jMapFrame.getGlassPanel().add(metocInfoPanel);
            jMapFrame.getGlassPanel().add(waypointInfoPanel);
        }
        if (obj instanceof MapBean){
            mapBean = (MapBean)obj;
        }
        if(obj instanceof MapMenu){
            routeMenu = (MapMenu) obj;
        }

    }

    @Override
    public void findAndUndo(Object obj) {
        if (obj == routeManager) {
            routeManager.removeListener(this);
        }
    }

    public MapMouseListener getMapMouseListener() {
        return this;
    }

    @Override
    public String[] getMouseModeServiceList() {
        String[] ret = new String[3];
        ret[0] = DragMouseMode.MODEID; // "DragMouseMode"
        ret[1] = NavigationMouseMode.MODEID; // "ZoomMouseMode"
        ret[2] = SelectMouseMode.MODEID; // "SelectMouseMode"
        return ret;
    }

    @Override
    public boolean mouseClicked(MouseEvent e) {
        if(e.getButton() != MouseEvent.BUTTON3){
            return false;
        }

        selectedGraphic = null;
        OMList<OMGraphic> allClosest = graphics.findAll(e.getX(), e.getY(), 5.0f);
        for (OMGraphic omGraphic : allClosest) {
            if (omGraphic instanceof SuggestedRouteGraphic || omGraphic instanceof WaypointCircle || omGraphic instanceof RouteLegGraphic) {
                selectedGraphic = omGraphic;
                break;
            }
        }


        if(selectedGraphic instanceof WaypointCircle){
            WaypointCircle wpc = (WaypointCircle) selectedGraphic;
            waypointInfoPanel.setVisible(false);
            routeMenu.routeWaypointMenu(wpc.getRouteIndex(), wpc.getWpIndex());
            routeMenu.setVisible(true);
            routeMenu.show(this, e.getX()-2, e.getY()-2);
            return true;
        }
        if(selectedGraphic instanceof RouteLegGraphic){
            RouteLegGraphic rlg = (RouteLegGraphic) selectedGraphic;
            waypointInfoPanel.setVisible(false);
            routeMenu.routeLegMenu(rlg.getRouteIndex(), rlg.getRouteLeg(), e.getPoint());
            routeMenu.setVisible(true);
            routeMenu.show(this, e.getX()-2, e.getY()-2);
            return true;
        }
//
        return false;
    }

    @Override
    public boolean mouseDragged(MouseEvent e) {
        if(!javax.swing.SwingUtilities.isLeftMouseButton(e)){
            return false;
        }

        if(!dragging){
            selectedGraphic = null;
            OMList<OMGraphic> allClosest = graphics.findAll(e.getX(), e.getY(), 5.0f);
            for (OMGraphic omGraphic : allClosest) {
                if (omGraphic instanceof WaypointCircle) {
                    selectedGraphic = omGraphic;
                    break;
                }
            }
        }

        if (selectedGraphic instanceof WaypointCircle) {
            WaypointCircle wpc = (WaypointCircle) selectedGraphic;
            if (routeManager.getActiveRouteIndex() != wpc.getRouteIndex()) {
                RouteWaypoint routeWaypoint = wpc.getRoute().getWaypoints()
                        .get(wpc.getWpIndex());
                LatLonPoint newLatLon = mapBean.getProjection().inverse(
                        e.getPoint());
                Position newLocation = Position.create(newLatLon.getLatitude(),
                        newLatLon.getLongitude());
                routeWaypoint.setPos(newLocation);
                routesChanged(RoutesUpdateEvent.ROUTE_WAYPOINT_MOVED);
                dragging = true;
                return true;
            }
        }

        return false;
    }

    @Override
    public void mouseEntered(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void mouseExited(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void mouseMoved() {
        graphics.deselect();
        repaint();
    }

    @Override
    public boolean mouseMoved(MouseEvent e) {
        OMGraphic newClosest = null;
        OMList<OMGraphic> allClosest = graphics.findAll(e.getX(), e.getY(), 2.0f);

        for (OMGraphic omGraphic : allClosest) {
            if (omGraphic instanceof MetocPointGraphic || omGraphic instanceof WaypointCircle) {
                newClosest = omGraphic;
                break;
            }
        }

        if (routeMetoc != null && metocInfoPanel != null) {
            if (newClosest != closest) {
                if (newClosest == null) {
                    metocInfoPanel.setVisible(false);
                    waypointInfoPanel.setVisible(false);
                    closest = null;
                } else {
                    if (newClosest instanceof MetocPointGraphic) {
                        closest = newClosest;
                        MetocPointGraphic pointGraphic = (MetocPointGraphic)newClosest;
                        MetocForecastPoint pointForecast = pointGraphic.getMetocPoint();
                        Point containerPoint = SwingUtilities.convertPoint(mapBean, e.getPoint(), jMapFrame);
                        metocInfoPanel.setPos((int)containerPoint.getX(), (int)containerPoint.getY());
                        metocInfoPanel.showText(pointForecast, pointGraphic.getMetocGraphic().getRoute().getRouteMetocSettings());
                        waypointInfoPanel.setVisible(false);
                        jMapFrame.getGlassPane().setVisible(true);
                        return true;
                    }
                }
            }
        }

        if (newClosest != closest) {
            if (newClosest instanceof WaypointCircle) {
                closest = newClosest;
                WaypointCircle waypointCircle = (WaypointCircle)closest;
                Point containerPoint = SwingUtilities.convertPoint(mapBean, e.getPoint(), jMapFrame);
                waypointInfoPanel.setPos((int)containerPoint.getX(), (int)containerPoint.getY() - 10);
                waypointInfoPanel.showWpInfo(waypointCircle.getRoute(), waypointCircle.getWpIndex());
                jMapFrame.getGlassPane().setVisible(true);
                metocInfoPanel.setVisible(false);
                return true;
            } else {
                waypointInfoPanel.setVisible(false);
                closest = null;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mousePressed(MouseEvent e) {
        return false;
    }

    @Override
    public boolean mouseReleased(MouseEvent e) {
        if(dragging){
            dragging = false;
            routeManager.notifyListeners(RoutesUpdateEvent.ROUTE_MSI_UPDATE);
            return true;
        }
        return false;
    }

}
