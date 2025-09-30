package com.weavechain.curves;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class FrScalar implements Scalar {

    private static final int N = 4;

    public static final BigInteger MODULUS = new BigInteger("52435875175126190479447740508185965837690552500527637822603658699938581184513");

    public static final BigInt MOD = BigInt.fromBigInteger(MODULUS, N);

    private static final BigInteger UNSIGNED_LONG_MASK = BigInteger.ONE.shiftLeft(Long.SIZE).subtract(BigInteger.ONE);

    private static final int GENERATOR = 7;

    private static final int SMALL_SUBGROUP_BASE = 3;

    private static final int SMALL_SUBGROUP_POWER = 1;

    public static final FrScalar R = new FrScalar(montgomeryR());

    public static final FrScalar R2 = new FrScalar(montgomeryR2());

    public static final FrScalar ZERO = fromLong(0L);

    public static final FrScalar ONE = fromLong(1L);

    public static final long INV = inv();

    private static final boolean MODULUS_HAS_SPARE_BIT = MOD.getValues()[N - 1] >>> 63 == 0;

    private BigInt value;

    public static Scalar scalar(long value) {
        return fromLong(value);
    }

    public static Scalar fromByteArray(byte[] bytes) {
        return new FrScalar(BigInt.fromBytes(bytes, N)).multiply(R2);
    }

    public static BigInt montgomeryR() {
        return BigInt.fromBigInteger(BigInteger.TWO.pow(4 * 64).mod(MODULUS), 4);
    }

    public static BigInt montgomeryR2() {
        return BigInt.fromBigInteger(BigInteger.TWO.pow(8 * 64).mod(MODULUS), 4);
    }

    public static boolean inplaceMul2(long[] value) {
        long last = 0;
        for (int i = 0; i < N; i++) {
            long a = value[i];
            long tmp = a >>> 63;
            value[i] <<= 1;
            value[i] |= last;
            last = tmp;
        }
        return last != 0;
    }

    public static long sbb(long a, long b, AtomicBoolean borrow) {
        BigInteger res = BigInteger.TWO.pow(64).add(BigInteger.valueOf(a)).subtract(BigInteger.valueOf(b)).subtract(BigInteger.valueOf(borrow.get() ? 1 : 0));
        borrow.set(res.testBit(64));
        return res.longValue();
    }

    public static boolean inplaceSub(long[] value, long[] other) {
        AtomicBoolean borrow = new AtomicBoolean(false);
        for (int i = 0; i < N; i++) {
            value[i] = sbb(value[i], other[i], borrow);
        }
        return borrow.get();
    }

    public static FrScalar fromLong(long value) {
        return (FrScalar)new FrScalar(BigInt.fromLong(value, N)).multiply(R2);
    }

    public static FrScalar fromBigInteger(BigInteger value) {
        return (FrScalar)new FrScalar(BigInt.fromBigInteger(value, N)).multiply(R2);
    }

    @Override
    public byte[] toByteArray() {
        return toByteArray(true);
    }

    public byte[] toByteArray(boolean montgomery) {
        return montgomery ? new BigInt(montgomeryReduce(value.getValues())).toByteArray() : value.toByteArray();
    }

    public static Scalar fromLeBytesModOrder(byte[] bytes) {
        int modBytes = (MODULUS.bitLength() + 7) / 8;
        int size = Math.min(modBytes - 1, bytes.length);

        byte[] toConvert = new byte[size];
        byte[] remainingBytes = new byte[bytes.length - size];

        System.arraycopy(bytes, 0, remainingBytes, 0, remainingBytes.length);
        System.arraycopy(bytes, remainingBytes.length, toConvert, 0, toConvert.length);

        byte[] reversedDirectBytes = new byte[toConvert.length];
        for (int i = 0; i < toConvert.length; i++) {
            reversedDirectBytes[i] = toConvert[toConvert.length - 1 - i];
        }

        BigInteger directValue = new BigInteger(1, reversedDirectBytes);
        Scalar res = fromBigInteger(directValue);

        Scalar windowSize = fromLong(256L);

        for (int i = remainingBytes.length - 1; i >= 0; i--) {
            res = res.multiply(windowSize);
            int unsignedByte = remainingBytes[i] & 0xFF;
            res = res.add(fromLong(unsignedByte));
        }

        return res;
    }

    private long[] montgomeryReduce(long[] values) {
        long[] r = values.clone();
        final int N = r.length;

        for (int i = 0; i < N; i++) {
            long k = r[i] * INV;

            long[] carry = new long[] { 0 };

            macWithCarryOptimized(r[i], k, MOD.getValues()[0], carry);

            for (int j = 1; j < N; j++) {
                int idx = (j + i) % N;
                long temp = macWithCarryOptimized(r[idx], k, MOD.getValues()[j], carry);
                r[idx] = temp;
            }

            r[i % N] = carry[0];
        }

        return r;
    }

    public static long macWithCarryOptimized(long a, long b, long c, long[] carry) {
        long bLow = b & 0xFFFFFFFFL;
        long bHigh = b >>> 32;
        long cLow = c & 0xFFFFFFFFL;
        long cHigh = c >>> 32;

        long r0 = bLow * cLow;
        long r1 = bLow * cHigh;
        long r2 = bHigh * cLow;
        long r3 = bHigh * cHigh;

        long mid1 = r1 + (r0 >>> 32);
        long mid2 = r2 + (mid1 & 0xFFFFFFFFL);
        long hi = r3 + (mid1 >>> 32) + (mid2 >>> 32);
        long lo = (mid2 << 32) | (r0 & 0xFFFFFFFFL);

        long newLo = lo + a;
        if (Long.compareUnsigned(newLo, lo) < 0) {
            hi += 1;
        }

        long finalLo = newLo + carry[0];
        if (Long.compareUnsigned(finalLo, newLo) < 0) {
            hi += 1;
        }

        carry[0] = hi;
        return finalLo;
    }

    public static long inv() {
        long inv = 1L;
        long[] mod = MOD.getValues();
        for (int i = 0; i < 63; i++) {
            inv = wrappingMul(inv, inv);
            inv = wrappingMul(inv, mod[0]);
        }
        return wrappingNeg(inv);
    }

    private static long wrappingMul(long a, long b) {
        return a * b;
    }

    private static long wrappingNeg(long a) {
        return -a;
    }

    @Override
    public Scalar invert() {
        BigInt u = value.clone();
        BigInt v = MOD.clone();
        BigInt b = R2.value.clone();
        BigInt c = ZERO.value.clone();

        BigInt one = BigInt.fromLong(1, u.getValues().length);

        while (!one.equals(u) && !one.equals(v)) {
            while (u.isEven()) {
                u.shiftRight();

                if (b.isEven()) {
                    b.shiftRight();
                } else {
                    boolean carry = b.addWithCarry(MOD);
                    b.shiftRight();
                    if (!MODULUS_HAS_SPARE_BIT && carry) {
                        b.getValues()[N - 1] |= (1L << 63);
                    }
                }
            }

            while (v.isEven()) {
                v.shiftRight();

                if (c.isEven()) {
                    c.shiftRight();
                } else {
                    boolean carry = c.addWithCarry(MOD);
                    c.shiftRight();
                    if (!MODULUS_HAS_SPARE_BIT && carry) {
                        c.getValues()[N - 1] |= (1L << 63);
                    }
                }
            }

            if (v.compareTo(u) < 0) {
                u.subWithBorrow(v);
                b.sub(c, MOD);
            } else {
                v.subWithBorrow(u);
                c.sub(b, MOD);
            }
        }

        return new FrScalar(one.equals(u) ? b : c);
    }

    @Override
    public Scalar add(Scalar other) {
        BigInteger result = value.toBigInteger().add(((FrScalar)other).value.toBigInteger()).mod(MODULUS);
        return new FrScalar(BigInt.fromBigInteger(result, N));
    }

    @Override
    public Scalar subtract(Scalar other) {
        BigInteger result = value.toBigInteger().subtract(((FrScalar)other).value.toBigInteger()).mod(MODULUS);
        return new FrScalar(BigInt.fromBigInteger(result, N));
    }

    public BigInteger mmul(BigInteger a, BigInteger b) {
        BigInteger result = BigInteger.ZERO;
        for (int i = 0; i < N * 64; i++) {
            if (a.testBit(i)) {
                result = result.add(b);
            }

            if (result.testBit(0)) {
                result = result.add(MODULUS);
            }

            result = result.shiftRight(1);
        }

        if (result.compareTo(MODULUS) >= 0) {
            result = result.subtract(MODULUS);
        }

        return result;
    }

    @Override
    public Scalar multiply(Scalar other) {
        BigInteger result = mmul(value.toBigInteger(), ((FrScalar)other).value.toBigInteger());
        return new FrScalar(BigInt.fromBigInteger(result, N));
    }

    @Override
    public Scalar square() {
        return multiply(new FrScalar(value.clone()));
    }

    @Override
    public Scalar negate() {
        return ZERO.subtract(new FrScalar(value.clone()));
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public FrScalar clone() {
        return new FrScalar(new BigInt(value.getValues().clone()));
    }
}
