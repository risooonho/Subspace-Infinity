/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es.states;

import com.dongbat.walkable.FloatArray;
import com.jme3.math.Vector3f;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.GameConstants;
import example.es.MobPath;
import example.sim.MobDriver;
import example.sim.SimpleBody;
import example.sim.SimplePhysics;
import java.util.HashMap;
import org.dyn4j.geometry.Vector2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Asser
 */
public class BasicSteeringState extends AbstractGameSystem {

    private EntityData ed;
    private EntitySet mobs;
    private SimTime tpf;
    private HashMap<EntityId, Integer> currentTargets = new HashMap<>();
    private SimplePhysics simplePhysics;
    private MobDrivers mobDrivers;
    private HashMap<Entity, MobDriver> drivers = new HashMap<>();
    static Logger log = LoggerFactory.getLogger(BasicSteeringState.class);

    @Override
    protected void initialize() {

        this.ed = getSystem(EntityData.class);

        //Get every entity that has a physical position and a path to follow
        this.mobs = ed.getEntities(MobPath.class);

        simplePhysics = getSystem(SimplePhysics.class);
    }

    @Override
    protected void terminate() {
        this.mobs.release();
        this.mobs = null;
    }

    @Override
    public void start() {
        mobDrivers = new MobDrivers(ed);
        mobDrivers.start();

    }

    @Override
    public void stop() {
        mobDrivers.stop();
        mobDrivers = null;
    }

    @Override
    public void update(SimTime tpf) {
        //Get a local copy for use in looking up positions in the bodyposition
        this.tpf = tpf;
        if (tpf.getTpf() < 1) {
            mobDrivers.updateDrivers();
        }
    }

    private void createSteeringForce(Entity e, SimTime tpf) {
        //This should be fun
        Vector2 steeringForce = steer(e);
        //log.info("(createSteeringForce) steeringForce: " + steeringForce.toString());
        //Drive the ship according to the steering force
        Vector3f thrust = new Vector3f((float) steeringForce.x, (float) steeringForce.y, 0);
        //log.info("(createSteeringForce) thrust: " + thrust.toString());
        SimpleBody b = simplePhysics.getBody(e.getId());
        log.info("Position p:" + b.getTransform().getTranslation().toString() + ", thrust:" + thrust.toString());

        //TODO: Convert thrust to a correct steering vector (default is keyboard inputs, not a force vector)
        ((MobDriver) b.driver).applyMovementState(thrust);
    }

    private float distance(Vector2 a, Vector2 b) {
        return (float) Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y));
    }

    //Should be used whenever we need to move towards current target
    private int getCurrentPathIndex(EntityId eId) {
        if (currentTargets.containsKey(eId)) {
            return currentTargets.get(eId);
        } else {
            //Should not happen since we check on added and changed entities and call restartPath there
            currentTargets.put(eId, 0);
            return 0; //First index
        }
    }

    private void setNextWayPoint(EntityId eId) {
        currentTargets.put(eId, currentTargets.get(eId) + 2);
    }

    //Should be called whenver MobPath component is updated on entity
    private void restartPath(EntityId eId) {
        currentTargets.put(eId, 0);
    }

    private Vector2 seek(Vector2 currentTarget, Vector2 currentPosition, Vector2 currentVelocity) {
        //TODO: Validate this calculation
        Vector2 force;

        Vector2 desired = currentTarget.subtract(currentPosition);
        desired.normalize();
        desired.multiply(GameConstants.MOBSPEED);
        //log.info("(seek), desired velocity: " + desired.toString());

        force = desired.subtract(currentVelocity);
        //log.info("(seek), force generated: " + force.toString());

        force = force.multiply(tpf.getTpf());
        //log.info("(seek) steeringForce after tpf: " + force.toString());
        return force;
    }

    private Vector2 steer(Entity e) {
        Vector2 steeringForce = new Vector2();

        Vector2 pathFollow = pathFollowing(e);
        //log.info("(steer) pathFollow: " + pathFollow.toString());
        steeringForce.add(pathFollow);
        //log.info("(steer) steering force: " + steeringForce.toString());
        steeringForce.setMagnitude(Math.min(GameConstants.MOBMAXFORCE, steeringForce.getMagnitude()));
        //log.info("(steer) steering force after magnitude: " + steeringForce.toString());

        return steeringForce;
    }
    
    private Vector2 getWayPoint(FloatArray path, EntityId eId){
        int currentPathIndex = getCurrentPathIndex(eId);
        float x = path.get(currentPathIndex);
        float y = path.get(currentPathIndex + 1);
        Vector2 wayPoint = new Vector2(x, y);
        return wayPoint;
    }

    private Vector2 pathFollowing(Entity e) {
        //Get current position
        Vector2 currentPosition = simplePhysics.getBody(e.getId()).getTransform().getTranslation();
        //Get current target
        MobPath mobPath = e.get(MobPath.class);
        FloatArray path = mobPath.getPath();
        
        Vector2 currentTarget = getWayPoint(path, e.getId());

        //Check distance to current target node
        if (distance(currentPosition, currentTarget) < GameConstants.PATHWAYPOINTDISTANCE) {
            //Set next target
            setNextWayPoint(e.getId());
            currentTarget = getWayPoint(path, e.getId());
        }

        Vector2 currentVelocity = simplePhysics.getBody(e.getId()).getLinearVelocity();
        if (currentVelocity.x == 0 && currentVelocity.y == 0) {
            currentVelocity = new Vector2(0,1);
        }
        //Move towards current target
        Vector2 seek = seek(currentTarget, currentPosition, currentVelocity);
        return seek;
    }

    //Map the mobs to a path, to be used by other State (SteeringState?)
    private class MobDrivers extends EntityContainer<MobDriver> {

        public MobDrivers(EntityData ed) {
            super(ed, MobPath.class);
        }

        @Override
        protected MobDriver[] getArray() {
            return super.getArray();
        }

        @Override
        protected MobDriver addObject(Entity e) {
            MobDriver driver = new MobDriver();
            simplePhysics.getBody(e.getId()).driver = driver;
            
            restartPath(e.getId());

            drivers.put(e, driver);

            return driver;
        }

        @Override
        protected void updateObject(MobDriver object, Entity e) {
            restartPath(e.getId());
        }

        @Override
        protected void removeObject(MobDriver object, Entity e) {
            drivers.remove(e);
        }

        public void updateDrivers() {
            this.update();

            for (Entity e : drivers.keySet()) {
                createSteeringForce(e, tpf);
            }
        }
    }
}
