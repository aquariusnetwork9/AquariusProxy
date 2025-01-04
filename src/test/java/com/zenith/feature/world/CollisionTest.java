package com.zenith.feature.world;

import com.zenith.mc.block.LocalizedCollisionBox;
import com.zenith.util.math.MathHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CollisionTest {

    @Test
    public void collideZPushOutTest() {
        var cb1 = new LocalizedCollisionBox(-26.0, -25.0, 103.0, 104.0, 31.0, 32,0, -26.0, 103.0);
        var cb2 = new LocalizedCollisionBox(-25.749184311159002, -25.149184311159, 103.83999997377396, 105.63999997377395, 31.999999999999996, 32.599999999999994, -25.449184311159, 103.83999997377396, 32.3);

        double collisionResult = cb1.collideZ(cb2, -0.0978029544583795);
        Assertions.assertEquals(0, MathHelper.round(collisionResult, 10));
    }
}
