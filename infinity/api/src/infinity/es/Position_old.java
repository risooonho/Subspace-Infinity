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
package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;

/**
 * The position of static or mostly static objects. This is the position for
 * entities that move infrequently and is the initial position used for mobile
 * objects. A physics listener may also occasionally publish updates to the real
 * position of mobile objects.
 *
 * @author Paul Speed
 */
public class Position_old implements EntityComponent {

    private final Vec3d location;
    private final Quatd facing;
    private final double rotation;

    // When we want to filter static objects based on position then
    // this will be very useful and we'll need to generate it whenever the
    // position changes.
    private long cellId;

    public Position_old() {
        this(new Vec3d(), new Quatd(), 0.0, 0);
    }

    public Position_old(final Vec3d loc, final Quatd quat, final double rotation) {
        location = loc;
        facing = quat;
        this.rotation = rotation;
    }

    public Position_old(final Vec3d loc, final Quatd quat, final double rotation, final long cellId) {
        location = loc;
        facing = quat;
        this.rotation = rotation;
        this.cellId = cellId;
    }

    public Position_old newCellId(final long id) {
        return new Position_old(location, facing, rotation, id);
    }

    // public Position changeLocation( Vec3d location ) {
    // return new Position(location, facing, 0.0);
    // }
    // public Position changeFacing( Quatd facing ) {
    // return new Position(location, facing, 0.0);
    // }
    public Vec3d getLocation() {
        return location;
    }

    public Quatd getFacing() {
        return facing;
    }

    public double getRotation() {
        return rotation;
    }

    public long getCellId() {
        return cellId;
    }

    @Override
    public String toString() {
        return "Position{" + "location=" + location + ", facing=" + facing + ", rotation=" + rotation + '}';
    }

}
