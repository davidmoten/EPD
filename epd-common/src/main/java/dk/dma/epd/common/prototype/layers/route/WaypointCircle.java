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
package dk.dma.epd.common.prototype.layers.route;

import dk.dma.epd.common.prototype.layers.common.WpCircle;
import dk.dma.epd.common.prototype.model.route.Route;

/**
 * Graphic for waypoint circle
 */
public class WaypointCircle extends WpCircle {
    private static final long serialVersionUID = 1L;
    
    private Route route;
    private int wpIndex;

    private int routeIndex;

    public WaypointCircle(Route route, int routeIndex, int wpIndex) {
        super();
        this.routeIndex = routeIndex;
        this.route = route;
        this.wpIndex = wpIndex;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public int getWpIndex() {
        return wpIndex;
    }

    public void setWpIndex(int wpIndex) {
        this.wpIndex = wpIndex;
    }
    
    public int getRouteIndex() {
        return routeIndex;
    }
}
