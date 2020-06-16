/*
 * $Id$
 * 
 * Copyright (c) 2018, Simsilica, LLC
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions 
 * are met:
 * 
 * 1. Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its 
 *    contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package infinity.client.view;

import org.slf4j.*;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.*;

import com.simsilica.mathd.*;
import com.simsilica.lemur.*;

import com.simsilica.builder.*;
import com.simsilica.pager.Grid;
import com.simsilica.pager.*;
import com.simsilica.pager.debug.*;

import com.simsilica.mworld.*;
import com.simsilica.mworld.net.client.WorldClientService;
import infinity.client.ConnectionState;

/**
 *
 *
 * @author Paul Speed
 */
public class WorldViewState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(WorldViewState.class);

    private World world;
    private CellObserver cellObserver = new CellObserver();

    private PagedGrid pager;
    private Node worldRoot;

    private Vector3f viewLoc = new Vector3f(0, 66.8f, 0);
    private Vector3f viewCell = new Vector3f();
    boolean showGrid = true;

    public WorldViewState() {
    }

    public void setViewLocation(Vector3f viewLoc, Vector3f lookAt) {
        this.viewLoc.set(viewLoc);
        if (pager != null) {
            pager.setCenterWorldLocation(viewLoc.x, viewLoc.z);

            getApplication().getCamera().setLocation(viewLoc);
            //getApplication().getCamera().lookAt(lookAt, Vector3f.UNIT_Y);

            pager.getGrid().toCell(viewLoc, viewCell);

            // The grid may be in 3D but the pager considers it a 2D grid for the
            // sake of center placement.
            viewCell.y = 0;
        }
    }

    public Vector3f getViewLocation() {
        return viewLoc;
    }

    public Vector3f getViewCell() {
        return viewCell;
    }

    public Vector3f cellToWorld(Vec3i cell, Vector3f target) {
        if (pager == null) {
            return target;
        }
        return pager.getGrid().toWorld(cell.x, cell.y, cell.z, target);
    }

    @Override
    protected void initialize(Application app) {
        world = getState(ConnectionState.class).getService(WorldClientService.class);
        //log.info("World:" + world);

        //LeafData data = world.getLeaf(0);
        //log.info("Data for leafId 0:" + data);
        //data = world.getLeaf(new Vec3i(0, 2, 0));
        //log.info("Data for leaf 0, 2, 0:" + data);
        Builder builder = getState(BuilderState.class).getBuilder();

        Grid rootGrid = new Grid(new Vector3f(32, 32, 32), new Vector3f(0, 0, 0));

        ZoneFactory rootFactory = new LeafDataZone.Factory(world);

        pager = new PagedGrid(rootFactory, builder, rootGrid, 5, 5);

        worldRoot = new Node("worldRoot");
        worldRoot.attachChild(pager.getGridRoot());

        if (showGrid) {
            Material boxMaterial = GuiGlobals.getInstance().createMaterial(new ColorRGBA(0.2f, 0.6f, 0.4f, 0.25f), false).getMaterial();
            boxMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
            boxMaterial.getAdditionalRenderState().setWireframe(true);
            ZoneFactory gridFactory = new BBoxZone.Factory(boxMaterial);

            PagedGrid gridPager = new PagedGrid(pager, gridFactory, builder, rootGrid, 5, 2);
            worldRoot.attachChild(gridPager.getGridRoot());
        }

        world.addCellChangeListener(cellObserver);

    }

    protected void cellChanged(CellChangeEvent event) {
        log.info("cellChanged(" + event + ")");
        Vec3i loc = event.getLeafWorld();
        pager.rebuildCell(loc.x, loc.y, loc.z);
    }

    @Override
    protected void cleanup(Application app) {
        world.removeCellChangeListener(cellObserver);
        pager.release();
    }

    /*public void update( float tpf ) {
        // For now just directly forward the camera location
        Vector3f loc = getApplication().getCamera().getLocation();
        
        pager.setCenterWorldLocation(loc.x, loc.z);
    }*/
    @Override
    protected void onEnable() {
        ((SimpleApplication) getApplication()).getRootNode().attachChild(worldRoot);

        pager.setCenterWorldLocation(viewLoc.x, viewLoc.z);
        getApplication().getCamera().setLocation(new Vector3f(0, viewLoc.y, 0));
    }

    @Override
    protected void onDisable() {
        worldRoot.removeFromParent();
    }

    private class CellObserver implements CellChangeListener {

        @Override
        public void cellChanged(CellChangeEvent event) {
            WorldViewState.this.cellChanged(event);
//BlockGeometryIndex.debug = true;            
        }
    }
}