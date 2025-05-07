package com.zenith.feature.player;

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

    @Test
    public void collideZPushOutTest2() {
        var cb1 = new LocalizedCollisionBox(-29.0, -28.0, 90.0, 91.0, 44.0, 45.0, -29.0, 90.0, 44.0);
        var cb2 = new LocalizedCollisionBox(-28.80947147697667, -28.20947147697667, 90.0, 91.8, 43.399999988079074, 43.99999998807907, -28.50947147697667, 90.0, 43.69999998807907);

        double collisionResult = cb1.collideZ(cb2, 0.0587181216390606);
        Assertions.assertEquals(0, MathHelper.round(collisionResult, 7));
    }
}
