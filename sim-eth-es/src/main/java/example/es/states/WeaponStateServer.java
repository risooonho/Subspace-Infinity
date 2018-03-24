package example.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.GameConstants;
import example.PhysicsConstants;
import example.es.Attack;
import example.es.AttackDirection;
import example.es.AttackVelocity;
import example.es.WeaponType;
import example.es.WeaponTypes;
import example.es.BodyPosition;
import example.es.Damage;
import example.es.GravityWell;
import example.es.ship.weapons.Guns;
import example.es.HitPoints;
import example.es.PhysicsVelocity;
import example.es.Position;
import example.es.ship.weapons.Bombs;
import example.es.ship.weapons.BombsCooldown;
import example.es.ship.weapons.Bursts;
import example.es.ship.weapons.BurstsCooldown;
import example.es.ship.weapons.GravityBombs;
import example.es.ship.weapons.GravityBombsCooldown;
import example.es.ship.weapons.GunsCooldown;
import example.es.ship.weapons.Mines;
import example.es.ship.weapons.MinesCooldown;
import example.sim.SimpleBody;
import example.sim.CoreGameEntities;
import example.sim.SimplePhysics;
import java.util.HashSet;
import org.dyn4j.geometry.Transform;
import org.dyn4j.geometry.Vector2;

/**
 * This state authorizes and attacks upon requests
 *
 * @author Asser Fahrenholz
 */
public class WeaponStateServer extends AbstractGameSystem {

    private EntityData ed;
    private EntitySet attacks;
    private Object gameSystems;
    private SimplePhysics physics;
    private SimTime time;
    private HealthState health;

    //Entities that has guns
    private EntitySet guns;
    //Entities that has bombs
    private EntitySet bombs;
    //Entities that has bursts
    private EntitySet bursts;
    //Entities that has gravityBombs
    private EntitySet gravityBombs;
    //Entities that has mines
    private EntitySet mines;
    private EntitySet gunsCooldowns;
    private EntitySet bombsCooldowns;
    private EntitySet burstsCooldowns;
    private EntitySet gravityBombsCooldowns;
    private EntitySet minesCooldowns;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        // Get the physics system... it's not available yet when onInitialize() is called.
        physics = getSystem(SimplePhysics.class);
        if (physics == null) {
            throw new RuntimeException("GameSessionHostedService requires a SimplePhysics system.");
        }

        health = getSystem(HealthState.class);

        attacks = ed.getEntities(Attack.class, WeaponType.class, Damage.class);

        guns = ed.getEntities(Guns.class);
        gunsCooldowns = ed.getEntities(GunsCooldown.class);

        bombs = ed.getEntities(Bombs.class);
        bombsCooldowns = ed.getEntities(BombsCooldown.class);

        bursts = ed.getEntities(Bursts.class);
        burstsCooldowns = ed.getEntities(BurstsCooldown.class);

        gravityBombs = ed.getEntities(GravityBombs.class);
        gravityBombsCooldowns = ed.getEntities(GravityBombsCooldown.class);

        mines = ed.getEntities(Mines.class);
        minesCooldowns = ed.getEntities(MinesCooldown.class);

    }

    @Override
    protected void terminate() {
        attacks.release();
        attacks = null;

        guns.release();
        guns = null;

        bombs.release();
        bombs = null;

        gravityBombs.release();
        gravityBombs = null;

        mines.release();
        mines = null;

        bursts.release();
        bursts = null;

        gunsCooldowns.release();
        gunsCooldowns = null;

        bombsCooldowns.release();
        bombsCooldowns = null;

        burstsCooldowns.release();
        burstsCooldowns = null;

        gravityBombsCooldowns.release();
        gravityBombsCooldowns = null;

        minesCooldowns.release();
        minesCooldowns = null;

    }

    @Override
    public void update(SimTime tpf) {

        //Update who has what ship weapons
        guns.applyChanges();
        gunsCooldowns.applyChanges();

        bombs.applyChanges();
        bombsCooldowns.applyChanges();

        gravityBombs.applyChanges();
        gravityBombsCooldowns.applyChanges();

        mines.applyChanges();
        minesCooldowns.applyChanges();

        bursts.applyChanges();
        burstsCooldowns.applyChanges();

        for (Entity e : gunsCooldowns) {
            GunsCooldown d = e.get(GunsCooldown.class);
            if (d.getPercent() >= 1.0) {
                ed.removeComponent(e.getId(), GunsCooldown.class);
            }
        }

        for (Entity e : bombsCooldowns) {
            BombsCooldown d = e.get(BombsCooldown.class);
            if (d.getPercent() >= 1.0) {
                ed.removeComponent(e.getId(), BombsCooldown.class);
            }
        }

        for (Entity e : gravityBombsCooldowns) {
            GravityBombsCooldown d = e.get(GravityBombsCooldown.class);
            if (d.getPercent() >= 1.0) {
                ed.removeComponent(e.getId(), GravityBombsCooldown.class);
            }
        }

        for (Entity e : minesCooldowns) {
            MinesCooldown d = e.get(MinesCooldown.class);
            if (d.getPercent() >= 1.0) {
                ed.removeComponent(e.getId(), MinesCooldown.class);
            }
        }

        for (Entity e : burstsCooldowns) {
            BurstsCooldown d = e.get(BurstsCooldown.class);
            if (d.getPercent() >= 1.0) {
                ed.removeComponent(e.getId(), BurstsCooldown.class);
            }
        }

        if (attacks.applyChanges()) {
            for (Entity e : attacks.getAddedEntities()) {
                Attack a = e.get(Attack.class);
                WeaponType at = e.get(WeaponType.class);
                Damage damage = e.get(Damage.class);

                this.attack(a, at, damage);

                ed.removeEntity(e.getId()); //Attack has been processed, now remove it
            }
        }

        time = tpf;
    }

    //TODO: Get the damage from some setting instead of hard coded
    public void entityAttackGuns(EntityId requestor) {
        //Check authorization and cooldown
        if (!guns.containsId(requestor) || gunsCooldowns.containsId(requestor)) {
            return;
        }
        Guns shipGuns = guns.getEntity(requestor).get(Guns.class);

        //Check health
        if (!health.hasHealth(requestor) || health.getHealth(requestor) < shipGuns.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor, -1*shipGuns.getCost());

        //Perform attack
        this.attack(new Attack(requestor), WeaponTypes.bullet(ed), new Damage(-20));

        //Set new cooldown
        ed.setComponent(requestor, new GunsCooldown(shipGuns.getCooldown()));
    }

    public void entityAttackBomb(EntityId requestor) {
        //Check authorization
        if (!bombs.containsId(requestor) || bombsCooldowns.containsId(requestor)) {
            return;
        }

        Bombs shipBombs = bombs.getEntity(requestor).get(Bombs.class);

        //Check health
        if (!health.hasHealth(requestor) || health.getHealth(requestor) < shipBombs.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor, -1*shipBombs.getCost());

        //Perform attack
        this.attack(new Attack(requestor), WeaponTypes.bomb(ed), new Damage(-20));

        //Set new cooldown
        ed.setComponent(requestor, new GunsCooldown(shipBombs.getCooldown()));

    }

    public void entityPlaceMine(EntityId requestor) {
        //Check authorization
        if (!mines.containsId(requestor) || minesCooldowns.containsId(requestor)) {
            return;
        }

        Mines shipMines = mines.getEntity(requestor).get(Mines.class);

        //Check health
        if (!health.hasHealth(requestor) || health.getHealth(requestor) < shipMines.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor, -1*shipMines.getCost());

        //Perform attack 
        this.attack(new Attack(requestor), WeaponTypes.mine(ed), new Damage(-20));

        //Set new cooldown
        ed.setComponent(requestor, new GunsCooldown(shipMines.getCooldown()));
    }

    public void entityBurst(EntityId requestor) {

        throw new UnsupportedOperationException();
        /*
        //Check authorization
        if (!bursts.containsId(requestor)) {
            return;
        }
        //Deduct health
        //Perform attack
        this.attack(new Attack(requestor), WeaponTypes.burst(ed), new Damage(-20));
         */
    }

    public void entityAttackGravityBomb(EntityId requestor) {
        //Check authorization
        if (!gravityBombs.containsId(requestor) || gravityBombsCooldowns.containsId(requestor)) {
            return;
        }

        GravityBombs shipGravityBombs = gravityBombs.getEntity(requestor).get(GravityBombs.class);

        //Check health
        if (!health.hasHealth(requestor) || health.getHealth(requestor) < shipGravityBombs.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor,-1*shipGravityBombs.getCost());

        //Perform attack
        this.attack(new Attack(requestor), WeaponTypes.gravityBomb(ed), new Damage(-20));

        //Set new cooldown
        ed.setComponent(requestor, new GunsCooldown(shipGravityBombs.getCooldown()));
    }

    private void attack(Attack a, WeaponType type, Damage damage) {
        EntityId eId = a.getOwner();
        SimTime localTime = time; //Copy incase someone else is writing to it
        Entity e = ed.getEntity(eId, HitPoints.class);

        Quatd orientation = new Quatd();
        Vec3d location;
        double rotation;
        Vector2 attackVel;

        if (e.get(HitPoints.class) != null) {
            // Attacking body:
            SimpleBody shipBody = physics.getBody(e.getId());

            Transform shipTransform = shipBody.getTransform();

            //Bomb velocity:
            attackVel = this.getAttackVelocity(shipTransform.getRotation(), type, shipBody.getLinearVelocity());

            //Position
            Vector2 attackPos = this.getAttackPosition(shipTransform.getRotation(), shipTransform.getTranslation());

            //Convert to Vec3d because that's what the rest of sim-eth-es uses
            location = new Vec3d(attackPos.x, attackPos.y, 0);
            orientation = shipBody.orientation;
            rotation = shipTransform.getRotation();
        } else {
            // towers
            e = ed.getEntity(eId, AttackVelocity.class, Position.class, AttackDirection.class);
            location = e.get(Position.class).getLocation().add(new Vec3d(0, 0, 0));

            Vector2 ori = e.get(AttackDirection.class).getDirection();

            orientation = orientation.fromAngles(ori.getAngleBetween(new Vector2(1, 0)), ori.getAngleBetween(new Vector2(0, 1)), 0);
            rotation = 0;
            // skal angive længde og ikke bare multiply...
            attackVel = ori.multiply(e.get(AttackVelocity.class).getVelocity());
        }
        EntityId projectile;
        switch (type.getTypeName(ed)) {
            case WeaponTypes.BOMB:
                projectile = CoreGameEntities.createBomb(location, orientation, rotation, attackVel, GameConstants.BULLETDECAY, ed);
                ed.setComponent(projectile, new Damage(damage.getDamage()));
                break;
            case WeaponTypes.BULLET:
                projectile = CoreGameEntities.createBullet(location, orientation, rotation, attackVel, GameConstants.BULLETDECAY, ed);
                ed.setComponent(projectile, new Damage(damage.getDamage()));
                break;
            case WeaponTypes.GRAVITYBOMB:
                HashSet<EntityComponent> delayedComponents = new HashSet<>();
                delayedComponents.add(new GravityWell(5, GameConstants.GRAVBOMBWORMHOLEFORCE, GravityWell.PULL));             //Suck everything in
                delayedComponents.add(new PhysicsVelocity(new Vector2(0, 0))); //Freeze the bomb
                delayedComponents.add(WeaponTypes.gravityBomb(ed));
                projectile = CoreGameEntities.createDelayedBomb(location, orientation, rotation, attackVel, GameConstants.GRAVBOMBDECAY, GameConstants.GRAVBOMBDELAY, delayedComponents, ed);
                ed.setComponent(projectile, new Damage(damage.getDamage()));
                break;
        }
    }

    private Vector2 getAttackVelocity(double rotation, WeaponType type, Vector2 linearVelocity) {
        Vector2 attackVel = new Vector2(0, 1);

        attackVel.rotate(rotation);

        attackVel.multiply(GameConstants.BASEPROJECTILESPEED);

        if (type.getTypeName(ed).equals(WeaponTypes.BOMB)) {
            attackVel.multiply(GameConstants.BOMBPROJECTILESPEED);
            attackVel.add(linearVelocity); //Add ships velocity to account for direction of ship and rotation

        } else if (type.getTypeName(ed).equals(WeaponTypes.BULLET)) {
            attackVel.multiply(GameConstants.BULLETPROJECTILESPEED);
            attackVel.add(linearVelocity); //Add ships velocity to account for direction of ship and rotation

        } else if (type.getTypeName(ed).equals(WeaponTypes.GRAVITYBOMB)) {
            attackVel.multiply(GameConstants.GRAVBOMBPROJECTILESPEED);
            attackVel.add(linearVelocity); //Add ships velocity to account for direction of ship and rotation

        } else if (type.getTypeName(ed).equals(WeaponTypes.MINE)) {
            attackVel.multiply(0); //A mine stands still
        }

        return attackVel;
    }

    private Vector2 getAttackPosition(double rotation, Vector2 translation) {
        Vector2 attackPos = new Vector2(0, 1);
        attackPos.rotate(rotation);
        attackPos.multiply(PhysicsConstants.PROJECTILEOFFSET);
        attackPos.add(translation.x, translation.y);
        return attackPos;
    }

}