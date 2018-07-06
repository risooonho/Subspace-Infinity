/* 
 * Copyright (c) 2018, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package arena1;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.event.EventBus;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.SimTime;
import infinity.api.es.MobType;
import infinity.api.es.SteeringPath;
import infinity.api.sim.AccessLevel;
import infinity.api.sim.AccountManager;
import infinity.api.sim.AdaptiveLoader;
import infinity.api.sim.ArenaManager;
import infinity.api.sim.BaseGameModule;
import infinity.api.sim.ChatHostedPoster;
import infinity.api.sim.CommandConsumer;
import infinity.api.sim.ModuleGameEntities;
import infinity.api.sim.events.ShipEvent;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.ini4j.Ini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a state that holds the rules of a game. This is the state that will
 * create wave of mobs, tell other states how many healthpoints a mob has, how
 * fast a bullet flies etc.
 *
 * @author Asser
 */
public class arena1 extends BaseGameModule /*implements CommandListener*/ {

    static Logger log = LoggerFactory.getLogger(arena1.class);

    //As a baseline, spawn 10 mobs per wave
    private final int baseline_mobCountPerWave = 10;
    //As a baseline, add a factor of mobs each wave
    private final int baseline_waveMobFactor = 2;
    //The wait time (in seconds) before the next wave, after the last wave has ended
    private final double baseline_waveWaitTime = 10; //10 seconds
    //As a baseline, end the game after this many waves
    private final int baseline_endWave = 10;

    private long currentWaveEndTime;
    private int currentWave = 0;

    private final int timetoplay = 300; //Game length in seconds

    private EntityData ed;
    private EntitySet mobs;
    private EntityId baseId;
    private double timeSinceLastWave;
    private final Pattern arena1Command = Pattern.compile("\\~arena1\\s(\\w+)");
    
    private Ini settings;

    public arena1(ChatHostedPoster chp, AccountManager am, AdaptiveLoader loader, ArenaManager arenas) {
        super(chp, am, loader, arenas);
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        //Keep track of how many mobs are in the game currently
        this.mobs = ed.getEntities(MobType.class);
        
        try {
            settings = this.getLoader().loadSettings("arena1");
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(arena1.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    protected void terminate() {
        mobs.release();
        mobs = null;
    }

    @Override
    public void update(SimTime tpf) {
        timeSinceLastWave += tpf.getTpf();

        mobs.applyChanges();

        if (mobs.isEmpty() && currentWave == baseline_endWave) {
            //You won the game
        } else if (timeSinceLastWave > baseline_waveWaitTime && mobs.isEmpty()) {
            //Continue with the game
            if (currentWave < baseline_endWave) {
                spawnWave();
            }
        }
    }

    @Override
    public void start() {
        EventBus.addListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
        this.getChp().registerPatternBiConsumer(arena1Command, "The command to make this arena1 do stuff is ~arena1 <command>, where <command> is the command you want to execute", new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id, s) -> this.messageHandler(id, s)));

        startGame();
    }

    @Override
    public void stop() {
        EventBus.removeListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
        endGame();
    }

    //Spawn the required waves for the game
    private void spawnWave() {
        log.debug("Spawning wave");
        //TODO: This could be done on a seperate thread so as not to hold the game thread
        for (int i = 0; i < baseline_mobCountPerWave * currentWave * baseline_waveMobFactor; i++) {
            spawnMob();
        }
        currentWave++;
        timeSinceLastWave = 0;
    }

    private void spawnMob() {
        //TODO: Create mob, set it steering towards the base
        Vec3d randomSpawn = getMobSpawnPoint();
        log.debug("Spawning mob @ " + randomSpawn);
        //Create mob ourselves
        EntityId mobId = ModuleGameEntities.createMob(randomSpawn, ed, settings);

        ed.setComponent(mobId, new SteeringPath());
    }

    //Could probably use some more flexible system to spawn the base
    private void spawnBase() {
        Vec3d basePos = getBaseSpawnPoint();
        log.debug("Spawning base @ " + basePos);
        URL s = ModuleGameEntities.class.getResource("ModuleGameEntities.class");
        baseId = ModuleGameEntities.createBase(basePos, ed, settings);
    }

    private EntityId getBaseId() {
        return baseId;
    }

    private void startGame() {
        spawnBase();
    }

    private void endGame() {

    }

    private Vec3d getMobSpawnPoint() {
        Vec3d result = new Vec3d(Math.random() * 20 - 10, Math.random() * 20 - 10, 0);

        return result;
    }

    private Vec3d getBaseSpawnPoint() {
        Vec3d result = new Vec3d(30, 30, 0);

        return result;
    }

    //Event handling
    void onShipSpawned(ShipEvent shipEvent) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    //Event handling
    void onShipDestroyed(ShipEvent shipEvent) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Handle the message events
     *
     * @param id The entity id of the sender
     * @param s The message to handle
     */
    public void messageHandler(EntityId id, String s) {
    }

    /**
     * Example component used by this module
     */
    private class TestComponent implements EntityComponent {

    }
}