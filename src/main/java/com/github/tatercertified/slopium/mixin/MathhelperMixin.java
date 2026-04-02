package com.github.tatercertified.slopium.mixin;

import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Mth.class)
public class MathhelperMixin {
    private static final float DEG_360F = 360.0F;
    private static final float DEG_180F = 180.0F;
    private static final float DEG_NEG_180F = -180.0F;
    private static final double DEG_360D = 360.0D;
    private static final double DEG_180D = 180.0D;
    private static final double DEG_NEG_180D = -180.0D;

    private static int fastAbs(int value) {
        final int mask = value >> 31;
        return (value ^ mask) - mask;
    }

    private static float fastAbs(float value) {
        return Float.intBitsToFloat(Float.floatToRawIntBits(value) & Integer.MAX_VALUE);
    }

    private static double fastAbs(double value) {
        return Double.longBitsToDouble(Double.doubleToRawLongBits(value) & Long.MAX_VALUE);
    }

    /**
     * @author tatercertified
     * @reason Avoids Math.floor entirely.
     */
    @Overwrite
    public static int floor(float value) {
        final int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    /**
     * @author tatercertified
     * @reason Avoids Math.floor entirely.
     */
    @Overwrite
    public static int floor(double value) {
        final int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    /**
     * @author tatercertified
     * @reason Avoids Math.floor entirely.
     */
    @Overwrite
    public static long lfloor(double value) {
        final long truncated = (long) value;
        return value < truncated ? truncated - 1L : truncated;
    }

    /**
     * @author tatercertified
     * @reason Avoids Math.ceil entirely.
     */
    @Overwrite
    public static int ceil(float value) {
        final int truncated = (int) value;
        return value > truncated ? truncated + 1 : truncated;
    }

    /**
     * @author tatercertified
     * @reason Avoids Math.ceil entirely.
     */
    @Overwrite
    public static int ceil(double value) {
        final int truncated = (int) value;
        return value > truncated ? truncated + 1 : truncated;
    }

    /**
     * @author tatercertified
     * @reason Avoids Math.ceil entirely.
     */
    @Overwrite
    public static long ceilLong(double value) {
        final long truncated = (long) value;
        return value > truncated ? truncated + 1L : truncated;
    }

    /**
     * @author tatercertified
     * @reason Replaces Math.abs with a branchless integer abs.
     */
    @Overwrite
    public static int abs(int value) {
        return fastAbs(value);
    }

    /**
     * @author tatercertified
     * @reason Replaces Math.abs with raw bit masking.
     */
    @Overwrite
    public static float abs(float value) {
        return fastAbs(value);
    }

    /**
     * @author tatercertified
     * @reason Removes nested Math.min/Math.max calls.
     */
    @Overwrite
    public static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }

        return value > max ? max : value;
    }

    /**
     * @author tatercertified
     * @reason Removes nested Math.min/Math.max calls.
     */
    @Overwrite
    public static long clamp(long value, long min, long max) {
        if (value < min) {
            return min;
        }

        return value > max ? max : value;
    }

    /**
     * @author tatercertified
     * @reason Removes Math.min from the float path.
     */
    @Overwrite
    public static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }

        return value > max ? max : value;
    }

    /**
     * @author tatercertified
     * @reason Removes Math.min from the double path.
     */
    @Overwrite
    public static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }

        return value > max ? max : value;
    }

    /**
     * @author tatercertified
     * @reason Avoids repeated Math.abs and Math.max calls.
     */
    @Overwrite
    public static int absMax(int left, int right) {
        final int leftAbs = fastAbs(left);
        final int rightAbs = fastAbs(right);
        return leftAbs > rightAbs ? leftAbs : rightAbs;
    }

    /**
     * @author tatercertified
     * @reason Avoids repeated Math.abs and Math.max calls.
     */
    @Overwrite
    public static float absMax(float left, float right) {
        final float leftAbs = fastAbs(left);
        final float rightAbs = fastAbs(right);
        return leftAbs > rightAbs ? leftAbs : rightAbs;
    }

    /**
     * @author tatercertified
     * @reason Avoids repeated Math.abs and Math.max calls.
     */
    @Overwrite
    public static double absMax(double left, double right) {
        final double leftAbs = fastAbs(left);
        final double rightAbs = fastAbs(right);
        return leftAbs > rightAbs ? leftAbs : rightAbs;
    }

    /**
     * @author tatercertified
     * @reason Avoids Math.abs on a tiny hot helper.
     */
    @Overwrite
    public static boolean equal(float left, float right) {
        return fastAbs(right - left) < 1.0E-5F;
    }

    /**
     * @author tatercertified
     * @reason Avoids Math.abs on a tiny hot helper.
     */
    @Overwrite
    public static boolean equal(double left, double right) {
        return fastAbs(right - left) < 9.999999747378752E-6D;
    }

    /**
     * @author tatercertified
     * @reason Replaces Math.floorMod with a cheaper remainder fixup.
     */
    @Overwrite
    public static int positiveModulo(int value, int modulus) {
        final int remainder = value % modulus;
        return (remainder ^ modulus) < 0 && remainder != 0 ? remainder + modulus : remainder;
    }

    /**
     * @author tatercertified
     * @reason Avoids the double remainder pattern.
     */
    @Overwrite
    public static float positiveModulo(float value, float modulus) {
        final float remainder = value % modulus;
        return remainder < 0.0F ? remainder + modulus : remainder;
    }

    /**
     * @author tatercertified
     * @reason Avoids the double remainder pattern.
     */
    @Overwrite
    public static double positiveModulo(double value, double modulus) {
        final double remainder = value % modulus;
        return remainder < 0.0D ? remainder + modulus : remainder;
    }

    /**
     * @author tatercertified
     * @reason Uses the fast floor overwrite directly.
     */
    @Overwrite
    public static byte packDegrees(float degrees) {
        return (byte) floor(degrees * 256.0F / DEG_360F);
    }

    /**
     * @author tatercertified
     * @reason Inlines degree wrapping without helper calls.
     */
    @Overwrite
    public static int wrapDegrees(int degrees) {
        int wrapped = degrees % 360;
        if (wrapped >= 180) {
            wrapped -= 360;
        } else if (wrapped < -180) {
            wrapped += 360;
        }

        return wrapped;
    }

    /**
     * @author tatercertified
     * @reason Inlines degree wrapping without helper calls.
     */
    @Overwrite
    public static float wrapDegrees(long degrees) {
        float wrapped = degrees % 360L;
        if (wrapped >= DEG_180F) {
            wrapped -= DEG_360F;
        } else if (wrapped < DEG_NEG_180F) {
            wrapped += DEG_360F;
        }

        return wrapped;
    }

    /**
     * @author tatercertified
     * @reason Inlines degree wrapping without helper calls.
     */
    @Overwrite
    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % DEG_360F;
        if (wrapped >= DEG_180F) {
            wrapped -= DEG_360F;
        } else if (wrapped < DEG_NEG_180F) {
            wrapped += DEG_360F;
        }

        return wrapped;
    }

    /**
     * @author tatercertified
     * @reason Inlines degree wrapping without helper calls.
     */
    @Overwrite
    public static double wrapDegrees(double degrees) {
        double wrapped = degrees % DEG_360D;
        if (wrapped >= DEG_180D) {
            wrapped -= DEG_360D;
        } else if (wrapped < DEG_NEG_180D) {
            wrapped += DEG_360D;
        }

        return wrapped;
    }

    /**
     * @author tatercertified
     * @reason Avoids chaining through wrapDegrees.
     */
    @Overwrite
    public static float degreesDifference(float from, float to) {
        float wrapped = (to - from) % DEG_360F;
        if (wrapped >= DEG_180F) {
            wrapped -= DEG_360F;
        } else if (wrapped < DEG_NEG_180F) {
            wrapped += DEG_360F;
        }

        return wrapped;
    }

    /**
     * @author tatercertified
     * @reason Avoids helper chaining and Math.abs.
     */
    @Overwrite
    public static float degreesDifferenceAbs(float from, float to) {
        float wrapped = (to - from) % DEG_360F;
        if (wrapped >= DEG_180F) {
            wrapped -= DEG_360F;
        } else if (wrapped < DEG_NEG_180F) {
            wrapped += DEG_360F;
        }

        return fastAbs(wrapped);
    }

    /**
     * @author tatercertified
     * @reason Avoids nested helper calls in the rotate clamp path.
     */
    @Overwrite
    public static float rotateIfNecessary(float current, float target, float maxDelta) {
        float delta = (target - current) % DEG_360F;
        if (delta >= DEG_180F) {
            delta -= DEG_360F;
        } else if (delta < DEG_NEG_180F) {
            delta += DEG_360F;
        }

        if (delta < -maxDelta) {
            delta = -maxDelta;
        } else if (delta > maxDelta) {
            delta = maxDelta;
        }

        return target - delta;
    }

    /**
     * @author tatercertified
     * @reason Avoids helper chaining in the simple approach path.
     */
    @Overwrite
    public static float approach(float current, float target, float delta) {
        final float magnitude = fastAbs(delta);
        if (current < target) {
            final float next = current + magnitude;
            return next > target ? target : next;
        }

        final float next = current - magnitude;
        return next < target ? target : next;
    }

    /**
     * @author tatercertified
     * @reason Avoids helper chaining in angular approach.
     */
    @Overwrite
    public static float approachDegrees(float current, float target, float delta) {
        float difference = (target - current) % DEG_360F;
        if (difference >= DEG_180F) {
            difference -= DEG_360F;
        } else if (difference < DEG_NEG_180F) {
            difference += DEG_360F;
        }

        target = current + difference;
        final float magnitude = fastAbs(delta);
        if (current < target) {
            final float next = current + magnitude;
            return next > target ? target : next;
        }

        final float next = current - magnitude;
        return next < target ? target : next;
    }

    /**
     * @author tatercertified
     * @reason Lets HotSpot use the intrinsic directly.
     */
    @Overwrite
    public static int smallestEncompassingPowerOfTwo(int value) {
        return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    }

    /**
     * @author tatercertified
     * @reason Uses JDK bit intrinsics instead of lookup-table plumbing.
     */
    @Overwrite
    public static int ceillog2(int value) {
        return 32 - Integer.numberOfLeadingZeros(value - 1);
    }

    /**
     * @author tatercertified
     * @reason Uses JDK bit intrinsics instead of helper chaining.
     */
    @Overwrite
    public static int log2(int value) {
        return 31 - Integer.numberOfLeadingZeros(value);
    }

    /**
     * @author tatercertified
     * @reason Avoids helper dispatch on a hot arithmetic primitive.
     */
    @Overwrite
    public static float frac(float value) {
        final int truncated = (int) value;
        return value - (value < truncated ? truncated - 1 : truncated);
    }

    /**
     * @author tatercertified
     * @reason Avoids helper dispatch on a hot arithmetic primitive.
     */
    @Overwrite
    public static double frac(double value) {
        final long truncated = (long) value;
        return value - (value < truncated ? truncated - 1L : truncated);
    }

    /**
     * @author tatercertified
     * @reason Avoids helper chaining on interpolation.
     */
    @Overwrite
    public static double inverseLerp(double value, double start, double end) {
        return (value - start) / (end - start);
    }

    /**
     * @author tatercertified
     * @reason Avoids helper chaining on interpolation.
     */
    @Overwrite
    public static float inverseLerp(float value, float start, float end) {
        return (value - start) / (end - start);
    }

    /**
     * @author tatercertified
     * @reason Avoids helper chaining and extra dispatch.
     */
    @Overwrite
    public static double clampedLerp(double delta, double start, double end) {
        if (delta < 0.0D) {
            return start;
        }

        if (delta > 1.0D) {
            return end;
        }

        return start + delta * (end - start);
    }

    /**
     * @author tatercertified
     * @reason Avoids helper chaining and extra dispatch.
     */
    @Overwrite
    public static float clampedLerp(float delta, float start, float end) {
        if (delta < 0.0F) {
            return start;
        }

        if (delta > 1.0F) {
            return end;
        }

        return start + delta * (end - start);
    }

    /**
     * @author tatercertified
     * @reason Avoids helper dispatch in lerp.
     */
    @Overwrite
    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    /**
     * @author tatercertified
     * @reason Avoids helper dispatch in lerp.
     */
    @Overwrite
    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    /**
     * @author tatercertified
     * @reason Avoids helper chaining in map.
     */
    @Overwrite
    public static double map(double value, double oldStart, double oldEnd, double newStart, double newEnd) {
        return newStart + ((value - oldStart) / (oldEnd - oldStart)) * (newEnd - newStart);
    }

    /**
     * @author tatercertified
     * @reason Avoids helper chaining in map.
     */
    @Overwrite
    public static float map(float value, float oldStart, float oldEnd, float newStart, float newEnd) {
        return newStart + ((value - oldStart) / (oldEnd - oldStart)) * (newEnd - newStart);
    }

    /**
     * @author tatercertified
     * @reason Avoids helper chaining in clampedMap.
     */
    @Overwrite
    public static double clampedMap(double value, double oldStart, double oldEnd, double newStart, double newEnd) {
        final double delta = (value - oldStart) / (oldEnd - oldStart);
        if (delta < 0.0D) {
            return newStart;
        }

        if (delta > 1.0D) {
            return newEnd;
        }

        return newStart + delta * (newEnd - newStart);
    }

    /**
     * @author tatercertified
     * @reason Avoids helper chaining in clampedMap.
     */
    @Overwrite
    public static float clampedMap(float value, float oldStart, float oldEnd, float newStart, float newEnd) {
        final float delta = (value - oldStart) / (oldEnd - oldStart);
        if (delta < 0.0F) {
            return newStart;
        }

        if (delta > 1.0F) {
            return newEnd;
        }

        return newStart + delta * (newEnd - newStart);
    }
}
