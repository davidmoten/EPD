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
package dk.dma.epd.common.prototype.layers.ais;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import com.bbn.openmap.omGraphics.OMGraphicList;

import dk.dma.enav.model.geometry.Position;
import dk.dma.epd.common.prototype.ais.MobileTarget;
import dk.dma.epd.common.prototype.ais.PastTrackPoint;

/**
 * Graphic for past-track route
 * <p>
 * 16-12-2013: Class moved to epd-common from epd-shore
 */
public class PastTrackGraphic extends OMGraphicList {
    private static final long serialVersionUID = 1L;
    
    private static Color LEG_COLOR = Color.black;
    private static Color GONE_LEG_COLOR = Color.lightGray;

    private MobileTarget mobileTarget;
    private long lastPastTrackChangeTime = -1L;
    private boolean lastPastTrackVisibility;
    private Position lastPastTrackTargetPosition;
    private PastTrackLegGraphic activePastTrackLine;
    private String name;
    private boolean arrowsVisible;
    private long mmsi = -1;

    private List<PastTrackLegGraphic> routeLegs = new ArrayList<>();
    private List<PastTrackWpCircle> routeWps = new ArrayList<>();

    /**
     * No-arg constructor
     */
    public PastTrackGraphic() {
        super();
        Position nullGeoLocation = Position.create(0, 0);
        activePastTrackLine = new PastTrackLegGraphic(0, this, true,
                nullGeoLocation, nullGeoLocation, LEG_COLOR);
    }
    
    /**
     * Returns the mmsi associated with this target
     * @return the mmsi associated with this target
     */
    public long getMmsi() {
        return mmsi;
    }

    /**
     * Sets the mmsi associated with this target
     * @param mmsi the mmsi associated with this target
     */
    public void setMmsi(long mmsi) {
        this.mmsi = mmsi;
    }

    /**
     * Returns a reference to the associated mobile target
     * @return a reference to the associated mobile target
     */
    public MobileTarget getMobileTarget() {
        return mobileTarget;
    }
    
    /**
     * Adds a new past track leg line
     * @param index index of leg in list of past track records
     * @param start start point of leg
     * @param end end point of leg
     */
    private void makeLegLine(int index, PastTrackPoint start, PastTrackPoint end) {
    
        Color legColor = start.hasGone() ? GONE_LEG_COLOR : LEG_COLOR;
        
        PastTrackLegGraphic leg = new PastTrackLegGraphic(
                index, 
                this,
                false, 
                start.getPosition(), 
                end.getPosition(), 
                legColor);
        routeLegs.add(leg);
        add(leg);
    }

    /**
     * Adds a new past track circle
     * @param index the index of the circle in list of past track records
     * @param p the past track point
     */
    private void makeWpCircle(int index, PastTrackPoint p) {
        PastTrackWpCircle wpCircle = new PastTrackWpCircle(this, index,
                p.getPosition().getLatitude(), p.getPosition().getLongitude(), 0, 0, 2, 2, p.getDate());
        wpCircle.setStroke(new BasicStroke(3));
        wpCircle.setLinePaint(LEG_COLOR);
        
        routeWps.add(wpCircle);
        add(wpCircle);
    }

    /**
     * Returns the name associated with this target
     * @return the name associated with this target
     */
    public String getName() {
        return name;
    }

    /**
     * Sets whether to show arrow heads or not
     * @param show whether to show arrow heads or not
     */
    public void showArrowHeads(boolean show) {
        if (this.arrowsVisible != show) {
            for (PastTrackLegGraphic routeLeg : routeLegs) {
                routeLeg.setArrows(show);
            }
            this.arrowsVisible = show;
        }
    }
    
    /**
     * Utility method that returns if the past-tracks of the given mobile target is visible
     * @param mobileTarget the mobileTarget to check
     * @return if the past-tracks of the mobileTarget should be visible
     */
    private boolean pastTrackVisible(MobileTarget mobileTarget) {
        if (mobileTarget == null) {
            return false;
        }
        return mobileTarget.getSettings().isShowPastTrack();
    }
    
    /**
     * Returns if the actual target position has changed since the last update.
     * <p>
     * Used to update the line from the last past-track point to the vessel.
     * 
     * @param mobileTarget the mobileTarget to check
     * @return if the target position has changed
     */
    private boolean targetPositionChanged(MobileTarget mobileTarget) {
        if (mobileTarget == null) {
            return false;
        } else if (lastPastTrackTargetPosition == null) {
            return true;
        }
        return !lastPastTrackTargetPosition.equals(mobileTarget.getPositionData().getPos());
    }
    
    /**
     * Updates gui PastTrack from the given mobile target
     * 
     * @param mobileTarget the mobile target, i.e. vessel or sar targets
     */
    public synchronized void update(MobileTarget mobileTarget) {

        boolean pastTrackVisible = pastTrackVisible(mobileTarget);
        
        // Check if we need to update anything
        if (this.mobileTarget == mobileTarget && 
                mobileTarget != null &&
                mobileTarget.getPastTrackData().getLastChangeTime() == lastPastTrackChangeTime &&
                pastTrackVisible == lastPastTrackVisibility &&
                !targetPositionChanged(mobileTarget)) {
            return;
        }
        
        // Update the graphics
        this.mobileTarget = mobileTarget;
        lastPastTrackChangeTime = this.mobileTarget.getPastTrackData().getLastChangeTime();
        lastPastTrackVisibility = pastTrackVisible;
        lastPastTrackTargetPosition = mobileTarget.getPositionData().getPos();
        setMmsi(mobileTarget.getMmsi());
        
        // Clear old data
        clear();
        routeLegs.clear();
        routeWps.clear();
        
        // If the past track is not visible, return
        if (!pastTrackVisible) {
            return;
        }
        
        // Compute how long back we want to display the past-track route
        Calendar pastTrackDisplayTime = Calendar.getInstance();
        pastTrackDisplayTime.add(Calendar.MINUTE, -mobileTarget.getSettings().getPastTrackDisplayTime());
                
        // Build the graphics
        PastTrackPoint lastPoint = null;
        int count = 0;            
        for (PastTrackPoint point : mobileTarget.getPastTrackData().getPointsNewerThan(pastTrackDisplayTime.getTime())) {
                
            // Add the graphics
            count++;
            makeWpCircle(count, point);
            if (lastPoint != null) {
                makeLegLine(count, lastPoint, point);
            }
            lastPoint = point;
        }

        // Create the line from the latest past-track point to the targets current position
        if (lastPoint != null) {
            double[] activePastTrackLineLL = new double[] {
                    lastPastTrackTargetPosition.getLatitude(),
                    lastPastTrackTargetPosition.getLongitude(),
                    lastPoint.getPosition().getLatitude(),
                    lastPoint.getPosition().getLongitude(),
            };
            activePastTrackLine.setLL(activePastTrackLineLL);
            add(activePastTrackLine);
        }
    }

    /**
     * Render nicely anti-aliased
     * @param gr the graphical context
     */
    @Override
    public void render(Graphics gr) {
        Graphics2D image = (Graphics2D) gr;
        image.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        super.render(image);
    }
}
