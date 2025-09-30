package com.weavechain.curves;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class BigInt {

    private static final BigInteger UNSIGNED_LONG_MASK = BigInteger.ONE.shiftLeft(Long.SIZE).subtract(BigInteger.ONE);

    private static final BigInteger POW64 = BigInteger.ONE.shiftLeft(64);

    private long[] values;

    private long c;

    public BigInt(long[] values) {
        this.values = values;
        this.c = 0;
    }

    public static BigInt fromLong(long value, int N) {
        long[] values = new long[N];
        values[0] = value;
        return new BigInt(values);
    }

    public static BigInt fromString(String value, int N) {
        return BigInt.fromBigInteger(new BigInteger(value), N);
    }

    public static BigInt fromBigInteger(BigInteger value, int N) {
        long[] values = new long[N];

        BigInteger v = value;
        for (int i = 0; i < N; i++) {
            //values[i] = ((l & 0xFFFFL) << 32) | ((l & 0xFFFF0000L) >> 32);
            values[i] = v.longValue();
            v = v.shiftRight(64);
        }

        return new BigInt(values);
    }

    public BigInteger toBigInteger() {
        BigInteger result = BigInteger.ZERO;
        int len = values.length;
        for (int i = 0; i < len; i++) {
            BigInteger it = BigInteger.valueOf(values[len - i - 1]).and(UNSIGNED_LONG_MASK);
            result = result.add(it);
            if (i < len - 1) {
                result = result.shiftLeft(Long.SIZE);
            }
        }

        return result;
    }

    public int compareTo(BigInt other) {
        for (int i = values.length; i < other.values.length; i++) {
            if (other.values[i] != 0) {
                return -1;
            }
        }
        for (int i = other.values.length; i < values.length; i++) {
            if (values[i] != 0) {
                return 1;
            }
        }

        int len = Math.min(values.length, other.values.length);
        for (int i = len - 1; i >= 0; i--) {
            long v1 = values[i];
            long v2 = other.values[i];
            int cmp = Long.compareUnsigned(v1, v2);
            if (cmp < 0) {
                return -1;
            } else if (cmp > 0) {
                return 1;
            }
        }
        return 0;
    }

    public boolean isEven() {
        return !isOdd();
    }

    public boolean isOdd() {
        return (values[0] & 1) == 1;
    }

    public void shiftRight() {
        long t = 0;
        for (int i = 0; i < values.length; i++) {
            long a = values[values.length - i - 1];
            long t2 = a << 63;
            a = a >>> 1;
            a |= t;
            t = t2;
            values[values.length - i - 1] = a;
        }
    }

    public boolean addWithCarry(BigInt other) {
        long[] a = values;
        long[] b = other.getValues();

        long carry = 0;
        int len = Math.min(values.length, other.values.length);
        for (int i = 0; i < len; i++) {
            BigInteger tmp = POW64
                    .add(BigInteger.valueOf(a[i]).and(UNSIGNED_LONG_MASK))
                    .add(BigInteger.valueOf(b[i]).and(UNSIGNED_LONG_MASK))
                    .add(BigInteger.valueOf(carry));
            a[i] = tmp.longValue();
            carry = tmp.testBit(64) ? 0 : 1;
        }
        return carry != 0;
    }

    public boolean subWithBorrow(BigInt other) {
        long[] a = values;
        long[] b = other.getValues();

        long borrow = 0;
        int len = Math.min(values.length, other.values.length);
        for (int i = 0; i < len; i++) {
            BigInteger tmp = POW64
                    .add(BigInteger.valueOf(a[i]).and(UNSIGNED_LONG_MASK))
                    .subtract(BigInteger.valueOf(b[i]).and(UNSIGNED_LONG_MASK))
                    .subtract(BigInteger.valueOf(borrow));
            a[i] = tmp.longValue();
            borrow = tmp.testBit(64) ? 0 : 1;
        }
        return borrow != 0;
    }

    public void sub(BigInt other, BigInt MOD) {
        if  (compareTo(other) < 0) {
            addWithCarry(MOD);
        }
        subWithBorrow(other);
    }

    public static BigInt fromBytes(byte[] input, int N) {
        ByteBuffer buffer = ByteBuffer.wrap(input);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        long[] values = new long[input.length / Long.BYTES];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.getLong();
        }
        return new BigInt(values);
    }

    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES * values.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (long v : values) {
            buffer.putLong(v);
        }
        buffer.flip();
        return buffer.array();
    }

    @Override
    public String toString() {
        return Hex.toHexString(toByteArray());
    }

    @Override
    public BigInt clone() {
        return new BigInt(values.clone());
    }
}
