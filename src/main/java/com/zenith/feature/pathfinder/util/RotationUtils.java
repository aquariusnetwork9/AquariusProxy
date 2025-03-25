package com.zenith.feature.pathfinder.util;

import com.zenith.feature.pathfinder.PlayerContext;
import com.zenith.feature.world.Rotation;
import com.zenith.feature.world.RotationHelper;
import com.zenith.feature.world.raycast.BlockRaycastResult;
import com.zenith.feature.world.raycast.RaycastHelper;
import com.zenith.mc.block.BlockPos;
import org.cloudburstmc.math.vector.Vector3d;

import java.util.Optional;

public class RotationUtils {

    /**
     * Constant that a degree value is multiplied by to get the equivalent radian value
     */
    public static final double DEG_TO_RAD = Math.PI / 180.0;
    public static final float DEG_TO_RAD_F = (float) DEG_TO_RAD;

    /**
     * Constant that a radian value is multiplied by to get the equivalent degree value
     */
    public static final double RAD_TO_DEG = 180.0 / Math.PI;
    public static final float RAD_TO_DEG_F = (float) RAD_TO_DEG;

    /**
     * Offsets from the root block position to the center of each side.
     */
    private static final Vector3d[] BLOCK_SIDE_MULTIPLIERS = new Vector3d[]{
        Vector3d.from(0.5, 0, 0.5), // Down
        Vector3d.from(0.5, 1, 0.5), // Up
        Vector3d.from(0.5, 0.5, 0), // North
        Vector3d.from(0.5, 0.5, 1), // South
        Vector3d.from(0, 0.5, 0.5), // West
        Vector3d.from(1, 0.5, 0.5)  // East
    };

    private RotationUtils() {}


    /**
     * Wraps the target angles to a relative value from the current angles. This is done by
     * subtracting the current from the target, normalizing it, and then adding the current
     * angles back to it.
     *
     * @param current The current angles
     * @param target  The target angles
     * @return The wrapped angles
     */
    public static Rotation wrapAnglesToRelative(Rotation current, Rotation target) {
        if (current.yawIsReallyClose(target)) {
            return new Rotation(current.yaw(), target.pitch());
        }
        return target.subtract(current).normalize().add(current);
    }

    /**
     * Calculates the rotation from Vec<sub>dest</sub> to Vec<sub>orig</sub> and makes the
     * return value relative to the specified current rotations.
     *
     * @param orig    The origin position
     * @param dest    The destination position
     * @param current The current rotations
     * @return The rotation from the origin to the destination
     * @see #wrapAnglesToRelative(Rotation, Rotation)
     */
    public static Rotation calcRotationFromVec3d(Vector3d orig, Vector3d dest, Rotation current) {
        return wrapAnglesToRelative(current, calcRotationFromVec3d(orig, dest));
    }

    /**
     * Calculates the rotation from Vec<sub>dest</sub> to Vec<sub>orig</sub>
     *
     * @param orig The origin position
     * @param dest The destination position
     * @return The rotation from the origin to the destination
     */
    private static Rotation calcRotationFromVec3d(Vector3d orig, Vector3d dest) {
        double[] delta = {orig.getX() - dest.getX(), orig.getY() - dest.getY(), orig.getZ() - dest.getZ()};
        double yaw = Math.atan2(delta[0], -delta[2]);
        double dist = Math.sqrt(delta[0] * delta[0] + delta[2] * delta[2]);
        double pitch = Math.atan2(delta[1], dist);
        return new Rotation(
            (float) (yaw * RAD_TO_DEG),
            (float) (pitch * RAD_TO_DEG)
        );
    }

    /**
     * Calculates the look vector for the specified yaw/pitch rotations.
     *
     * @param rotation The input rotation
     * @return Look vector for the rotation
     */
    public static Vector3d calcLookDirectionFromRotation(Rotation rotation) {
        double flatZ = (float) Math.cos((-rotation.yaw() * DEG_TO_RAD_F) - (float) Math.PI);
        double flatX = (float) Math.sin((-rotation.yaw() * DEG_TO_RAD_F) - (float) Math.PI);
        double pitchBase = -Math.cos(-rotation.pitch() * DEG_TO_RAD_F);
        double pitchHeight = Math.sin(-rotation.pitch() * DEG_TO_RAD_F);
        return Vector3d.from(flatX * pitchBase, pitchHeight, flatZ * pitchBase);
    }

    /**
     * @param ctx Context for the viewing entity
     * @param pos The target block position
     * @return The optional rotation
     * @see #reachable(PlayerContext, BlockPos, double)
     */
    public static Optional<Rotation> reachable(PlayerContext ctx, BlockPos pos) {
        return reachable(ctx, pos, false);
    }

    public static Optional<Rotation> reachable(PlayerContext ctx, BlockPos pos, boolean wouldSneak) {
        return reachable(ctx, pos, ctx.player().getBlockReachDistance(), wouldSneak);
    }

    /**
     * Determines if the specified entity is able to reach the center of any of the sides
     * of the specified block. It first checks if the block center is reachable, and if so,
     * that rotation will be returned. If not, it will return the first center of a given
     * side that is reachable. The return type will be {@link Optional#empty()} if the entity is
     * unable to reach any of the sides of the block.
     *
     * @param ctx                Context for the viewing entity
     * @param pos                The target block position
     * @param blockReachDistance The block reach distance of the entity
     * @return The optional rotation
     */
    public static Optional<Rotation> reachable(PlayerContext ctx, BlockPos pos, double blockReachDistance) {
        return reachable(ctx, pos, blockReachDistance, false);
    }

    public static Optional<Rotation> reachable(PlayerContext ctx, BlockPos pos, double blockReachDistance, boolean wouldSneak) {
//        if (BaritoneAPI.getSettings().remainWithExistingLookDirection.value && ctx.isLookingAt(pos)) {
//            /*
//             * why add 0.0001?
//             * to indicate that we actually have a desired pitch
//             * the way we indicate that the pitch can be whatever and we only care about the yaw
//             * is by setting the desired pitch to the current pitch
//             * setting the desired pitch to the current pitch + 0.0001 means that we do have a desired pitch, it's
//             * just what it currently is
//             *
//             * or if you're a normal person literally all this does it ensure that we don't nudge the pitch to a normal level
//             */
//            Rotation hypothetical = ctx.playerRotations().add(new Rotation(0, 0.0001F));
//            if (wouldSneak) {
//                // the concern here is: what if we're looking at it now, but as soon as we start sneaking we no longer are
//                HitResult result = RayTraceUtils.rayTraceTowards(ctx.player(), hypothetical, blockReachDistance, true);
//                if (result != null && result.getType() == HitResult.Type.BLOCK && ((BlockHitResult) result).getBlockPos().equals(pos)) {
//                    return Optional.of(hypothetical); // yes, if we sneaked we would still be looking at the block
//                }
//            } else {
//                return Optional.of(hypothetical);
//            }
//        }
//        Optional<Rotation> possibleRotation = reachableCenter(ctx, pos, blockReachDistance, wouldSneak);
//        //PATH_LOG.info("center: " + possibleRotation);
//        if (possibleRotation.isPresent()) {
//            return possibleRotation;
//        }
//
//        BlockState state = World.getBlockState(pos);
//        List<LocalizedCollisionBox> shape = state.getLocalizedCollisionBoxes();
//        for (Vector3d sideOffset : BLOCK_SIDE_MULTIPLIERS) {
//            double xDiff = shape.min(Direction.Axis.X) * sideOffset.x + shape.max(Direction.Axis.X) * (1 - sideOffset.x);
//            double yDiff = shape.min(Direction.Axis.Y) * sideOffset.y + shape.max(Direction.Axis.Y) * (1 - sideOffset.y);
//            double zDiff = shape.min(Direction.Axis.Z) * sideOffset.z + shape.max(Direction.Axis.Z) * (1 - sideOffset.z);
//            possibleRotation = reachableOffset(ctx, pos, Vector3d.from(pos.x(), pos.y(), pos.z()).add(xDiff, yDiff, zDiff), blockReachDistance, wouldSneak);
//            if (possibleRotation.isPresent()) {
//                return possibleRotation;
//            }
//        }
//        return Optional.empty();
        var rotation = RotationHelper.rotationTo(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
        BlockRaycastResult blockRaycastResult = RaycastHelper.blockRaycastFromPos(
            ctx.player().getX(), ctx.player().getEyeY(), ctx.player().getZ(),
            rotation.getX(), rotation.getY(),
            blockReachDistance,
            false);
        if (blockRaycastResult.hit() && blockRaycastResult.x() == pos.x() && blockRaycastResult.y() == pos.y() && blockRaycastResult.z() == pos.z()) {
            return Optional.of(new Rotation(rotation.getX(), rotation.getY()));
        }
        return Optional.empty();
    }

    /**
     * Determines if the specified entity is able to reach the specified block with
     * the given offsetted position. The return type will be {@link Optional#empty()} if
     * the entity is unable to reach the block with the offset applied.
     *
     * @param ctx                Context for the viewing entity
     * @param pos                The target block position
     * @param offsetPos          The position of the block with the offset applied.
     * @param blockReachDistance The block reach distance of the entity
     * @return The optional rotation
     */
//    public static Optional<Rotation> reachableOffset(IPlayerContext ctx, BlockPos pos, Vector3d offsetPos, double blockReachDistance, boolean wouldSneak) {
//        Vector3d eyes = Vector3d.from(ctx.player().getX(), ctx.player().getEyeY(), ctx.player().getZ()); // wouldSneak ? RayTraceUtils.inferSneakingEyePosition(ctx.player()) : ctx.player().getEyeY();
//        Rotation rotation = calcRotationFromVector3dd(eyes, offsetPos, new Rotation(ctx.player().getYaw(), ctx.player().getPitch()));
////        Rotation actualRotation = BaritoneAPI.getProvider().getBaritoneForPlayer(ctx.player()).getLookBehavior().getAimProcessor().peekRotation(rotation);
//        RaycastHelper.blockRaycastFromPos(ctx.player().getX(), ctx.player().getEyeY(), ctx.player().getZ(), )
//        HitResult result = RayTraceUtils.rayTraceTowards(ctx.player(), actualRotation, blockReachDistance, wouldSneak);
//        //PATH_LOG.info(result);
//        if (result != null && result.getType() == HitResult.Type.BLOCK) {
//            if (((BlockHitResult) result).getBlockPos().equals(pos)) {
//                return Optional.of(rotation);
//            }
//            if (ctx.world().getBlockState(pos).getBlock() instanceof BaseFireBlock && ((BlockHitResult) result).getBlockPos().equals(pos.below())) {
//                return Optional.of(rotation);
//            }
//        }
//        return Optional.empty();
//    }

    /**
     * Determines if the specified entity is able to reach the specified block where it is
     * looking at the direct center of it's hitbox.
     *
     * @param ctx                Context for the viewing entity
     * @param pos                The target block position
     * @param blockReachDistance The block reach distance of the entity
     * @return The optional rotation
     */
//    public static Optional<Rotation> reachableCenter(IPlayerContext ctx, BlockPos pos, double blockReachDistance, boolean wouldSneak) {
//        return reachableOffset(ctx, pos, VecUtils.calculateBlockCenter(ctx.world(), pos), blockReachDistance, wouldSneak);
//    }

//    @Deprecated
//    public static Optional<Rotation> reachable(LocalPlayer entity, BlockPos pos, double blockReachDistance) {
//        return reachable(entity, pos, blockReachDistance, false);
//    }
//
//    @Deprecated
//    public static Optional<Rotation> reachable(LocalPlayer entity, BlockPos pos, double blockReachDistance, boolean wouldSneak) {
//        IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(entity);
//        IPlayerContext ctx = baritone.getPlayerContext();
//        return reachable(ctx, pos, blockReachDistance, wouldSneak);
//    }

//    @Deprecated
//    public static Optional<Rotation> reachableOffset(Entity entity, BlockPos pos, Vector3d offsetPos, double blockReachDistance, boolean wouldSneak) {
//        Vector3d eyes = wouldSneak ? RayTraceUtils.inferSneakingEyePosition(entity) : entity.getEyePosition(1.0F);
//        Rotation rotation = calcRotationFromVector3dd(eyes, offsetPos, new Rotation(entity.getYRot(), entity.getXRot()));
//        HitResult result = RayTraceUtils.rayTraceTowards(entity, rotation, blockReachDistance, wouldSneak);
//        //PATH_LOG.info(result);
//        if (result != null && result.getType() == HitResult.Type.BLOCK) {
//            if (((BlockHitResult) result).getBlockPos().equals(pos)) {
//                return Optional.of(rotation);
//            }
//            if (entity.level().getBlockState(pos).getBlock() instanceof BaseFireBlock && ((BlockHitResult) result).getBlockPos().equals(pos.below())) {
//                return Optional.of(rotation);
//            }
//        }
//        return Optional.empty();
//    }

//    @Deprecated
//    public static Optional<Rotation> reachableCenter(Entity entity, BlockPos pos, double blockReachDistance, boolean wouldSneak) {
//        return reachableOffset(entity, pos, VecUtils.calculateBlockCenter(entity.level(), pos), blockReachDistance, wouldSneak);
//    }
}
