/* Copyright (c) 2011 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dk.dma.epd.shore.voct;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JDialog;

import dk.dma.enav.model.geometry.Position;
import dk.dma.epd.common.prototype.EPD;
import dk.dma.epd.common.prototype.model.route.IRoutesUpdateListener;
import dk.dma.epd.common.prototype.model.route.RoutesUpdateEvent;
import dk.dma.epd.common.prototype.model.voct.SAR_TYPE;
import dk.dma.epd.common.prototype.model.voct.SearchPatternGenerator;
import dk.dma.epd.common.prototype.model.voct.sardata.SARData;
import dk.dma.epd.common.prototype.model.voct.sardata.SearchPatternRoute;
import dk.dma.epd.common.prototype.voct.VOCTManagerCommon;
import dk.dma.epd.common.prototype.voct.VOCTUpdateEvent;
import dk.dma.epd.common.prototype.voct.VOCTUpdateListener;
import dk.dma.epd.common.util.Util;
import dk.dma.epd.shore.EPDShore;
import dk.dma.epd.shore.gui.views.JMenuWorkspaceBar;
import dk.dma.epd.shore.gui.views.MapFrameType;
import dk.dma.epd.shore.gui.voct.SARInput;
import dk.dma.epd.shore.gui.voct.SRUManagerDialog;
import dk.dma.epd.shore.layers.voct.VoctLayerCommon;
import dk.dma.epd.shore.route.RouteManager;
import dk.dma.epd.shore.service.VoctHandler;

/**
 * The VOCTManager is responsible for maintaining current VOCT Status and all information relevant to the VOCT
 * 
 * The VOCT Manager can be initiated through the cloud or manually by the user
 * 
 * 
 */

public class VOCTManager extends VOCTManagerCommon implements IRoutesUpdateListener {

    private static final long serialVersionUID = 1L;
    private SARInput sarInputDialog;
    private SRUManagerDialog sruManagerDialog;
    private RouteManager routeManager;

    private VoctHandler voctHandler;
    private SRUManager sruManager;

    List<VoctLayerCommon> voctLayers = new ArrayList<VoctLayerCommon>();

    protected SARData sarData;

    private long voctID = -1;

    public VOCTManager() {
        EPDShore.startThread(this, "VOCTManager");
        LOG.info("Started VOCT Manager");
    }

    /**
     * @return the voctID
     */
    public long getVoctID() {
        return voctID;
    }

    @Override
    public void showSarInput() {
        LOG.info("Started new SAR Operation");
        if (!hasSar) {
            hasSar = true;

            voctID = System.currentTimeMillis();
            // Create the GUI input boxes

            // Voct specific test
            sarInputDialog = new SARInput(this);

            sarInputDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            sarInputDialog.setVisible(true);

        } else {
            // Cannot inititate a SAR without terminating the existing one, show
            // existing dialog?
            sarInputDialog.setVisible(true);
        }

    }

    public void showSRUManagerDialog() {

        if (sruManagerDialog != null) {
            sruManagerDialog.setVisible(true);
        }

    }

    /**
     * @return the sruManagerDialog
     */
    public SRUManagerDialog getSruManagerDialog() {
        return sruManagerDialog;
    }

    @Override
    protected void updateLayers() {
        System.out.println("Update layers");
        System.out.println(EPDShore.getInstance().getVoctManager().getSarData() + " IS IT NULL");
        if (voctLayers.size() == 0) {
            EPDShore.getInstance().getMainFrame().addSARWindow(MapFrameType.SAR_Planning);
        }

        notifyListeners(VOCTUpdateEvent.SAR_DISPLAY);
    }

    @Override
    public void addListener(VOCTUpdateListener listener) {
        super.addListener(listener);

        if (listener instanceof VoctLayerCommon) {
            voctLayers.add((VoctLayerCommon) listener);
        }
    }

    @Override
    public void run() {

        // Maintanaince routines
        while (true) {
            Util.sleep(10000);

        }

    }

    public void loadVOCTManager() {

        // Where we load or serialize old VOCTS

        try (FileInputStream fileIn = new FileInputStream(VOCT_FILE); ObjectInputStream objectIn = new ObjectInputStream(fileIn);) {

            SARData sarDataLoaded = (SARData) objectIn.readObject();
            setLoadSarFromSerialize(true);
            initializeFromSerializedFile(sarDataLoaded);
            System.out.println("Loaded");

        } catch (FileNotFoundException e) {
            // Not an error
        }
        // catch (Exception e) {
        // LOG.error("Failed to load VOCT file: " + e.getMessage());
        // // Delete possible corrupted or old file
        // // new File(VOCT_FILE).delete();
        // }
        catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // return voctManager;

    }

    @Override
    public void generateSearchPattern(SearchPatternGenerator.searchPattern type, Position CSP, int id) {

        updateEffectiveAreaLocation();

        sarData.setCSP(CSP);

        SearchPatternGenerator searchPatternGenerator = new SearchPatternGenerator(sarOperation);

        SearchPatternRoute searchRoute = searchPatternGenerator.generateSearchPattern(type, sarData, EPDShore.getInstance()
                .getSettings().getNavSettings(), id);

        // Remove old and overwrite
        if (sarData.getEffortAllocationData().get(id).getSearchPatternRoute() != null) {
            System.out.println("Previous route found");
            int routeIndex = EPDShore.getInstance().getRouteManager()
                    .getRouteIndex(sarData.getEffortAllocationData().get(id).getSearchPatternRoute());

            System.out.println("Route index of old is " + routeIndex);

            EPDShore.getInstance().getRouteManager().removeRoute(routeIndex);
        }

        sarData.getEffortAllocationData().get(id).setSearchPatternRoute(searchRoute);

        EPDShore.getInstance().getRouteManager().addRoute(searchRoute);

        notifyListeners(VOCTUpdateEvent.SEARCH_PATTERN_GENERATED);
    }

    @Override
    public void updateEffectiveAreaLocation() {
        // voctLayer.updateEffectiveAreaLocation(sarData);

        voctLayers.get(0).updateEffectiveAreaLocation(sarData);
    }

    @Override
    public void findAndInit(Object obj) {
        if (obj instanceof SRUManager) {

            sruManager = (SRUManager) obj;

        }

        if (obj instanceof SRUManagerDialog) {
            sruManagerDialog = (SRUManagerDialog) obj;
        }

        if (obj instanceof RouteManager) {
            routeManager = (RouteManager) obj;
            routeManager.addListener(this);
        }

        if (obj instanceof VoctHandler) {
            voctHandler = (VoctHandler) obj;
        }

    }

    /**
     * @return the sruManager
     */
    public SRUManager getSruManager() {
        return sruManager;
    }

    public void toggleSRUVisibility(int id, boolean visible) {
        System.out.println("Toggle visibility voctmanager");
        for (int i = 0; i < voctLayers.size(); i++) {
            voctLayers.get(i).toggleEffectiveAreaVisibility(id, visible);
        }

    }

    public void removeEffortAllocationData(int i) {

        if (sarData != null) {

            if (sarData.getEffortAllocationData().size() > i) {

                if (sarData.getEffortAllocationData().get(i).getSearchPatternRoute() != null) {

                    routeManager.getRoutes().remove(sarData.getEffortAllocationData().get(i).getSearchPatternRoute());
                    routeManager.notifyListeners(RoutesUpdateEvent.ROUTE_REMOVED);
                }

                sarData.getEffortAllocationData().remove(i);

                for (int j = 0; j < voctLayers.size(); j++) {
                    voctLayers.get(j).removeEffortAllocationArea(i);
                }
            }
        }

    }

    @Override
    public void routesChanged(RoutesUpdateEvent e) {
        if (e == RoutesUpdateEvent.ROUTE_REMOVED) {

            checkRoutes();
        }

    }

    private void checkRoutes() {

        if (sarData != null) {

            for (int i = 0; i < sarData.getEffortAllocationData().size(); i++) {
                if (!routeManager.getRoutes().contains(sarData.getEffortAllocationData().get(i).getSearchPatternRoute())) {
                    System.out.println("Route removed");
                    sarData.getEffortAllocationData().get(i).setSearchPatternRoute(null);
                }
            }
        }
    }

    @Override
    public void cancelSarOperation() {
        super.cancelSarOperation();

        // What do we need to cancel for the VOCT

        // Send cancel SAR message to all participants currently involved

        // Close down SAR TRACKINg and SAR Planning

        for (int i = 0; i < EPDShore.getInstance().getMainFrame().getMapWindows().size(); i++) {

            if (EPDShore.getInstance().getMainFrame().getMapWindows().get(i).getType() == MapFrameType.SAR_Tracking
                    || EPDShore.getInstance().getMainFrame().getMapWindows().get(i).getType() == MapFrameType.SAR_Planning) {
                // Resize windows
                EPDShore.getInstance().getMainFrame().getMapWindows().get(i).dispose();

            }
        }
        EPDShore.getInstance().getMainFrame().removeSARWindows();

        // Clear up voctLayers
        for (int i = 0; i < voctLayers.size(); i++) {
            voctLayers.get(i).dispose();
        }

        voctLayers.clear();

        voctHandler.sendCancelMessage(sruManager.cancelAllSRU());

        ((JMenuWorkspaceBar) EPD.getInstance().getMainFrame().getTopMenu()).setSarBtnEnabled();
    }

    @Override
    public void EffortAllocationDataEntered() {
        updateEffectiveAreaLocation();
        super.EffortAllocationDataEntered();

        checkRoutes();
    }

    @Override
    public void showSARFuture(int i) {

        if (this.sarOperation.getOperationType() != SAR_TYPE.SARIS_DATUM_POINT) {

            if (i == 0) {
                voctLayers.get(0).showFutureData(sarData);
            } else {
                voctLayers.get(0).showFutureData(sarFutureData.get((i / 30) - 1));
            }
        }

    }

}
