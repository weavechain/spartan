package com.weavechain.curves;

import com.weavechain.curve25519.MulUtils;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import supranational.blst.P2;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class G2PointFactory implements PointFactory {

    private static final G2Point ZERO = G2Point.IDENTITY;

    private static final G2Point GEN = new G2Point(P2.generator());

    public static final BigInteger G = new BigInteger("93e02b6052719f607dacd3a088274f65596bd0d09920b61ab5da61bbdc7f5049334cf11213945d57e5ac7d055d042b7e024aa2b2f08f0a91260805272dc51051c6e47ad4fa403b02b4510b647ae3d1770bac0326a805bbefd48056c8c121bdb8", 16);

    @Override
    public Point zero() {
        return ZERO;
    }

    @Override
    public Point generator() {
        return GEN;
    }

    @Override
    public int compressedSize() {
        return 96;
    }

    @Override
    public Point fromUniformBytes(byte[] input) {
        return new G2Point(P2.generator().hash_to(input));
    }

    @Override
    public Point fromCompressedBytes(byte[] input) throws IOException {
        return G2Point.fromBytes(input);
    }

    @Override
    public Point multiscalarMul(List<Scalar> scalars, List<Point> bases) {
        if (scalars.size() >= 30) {
            return mulPippenger(scalars, bases);
        } else if (scalars.size() > 3) {
            return mulStraus(scalars, bases);
        } else {
            Point result = null;
            for (int i = 0; i < scalars.size(); i++) {
                Point p =  bases.get(i).multiply(scalars.get(i));
                result = result != null ?  result.add(p) : p;
            }
            return result;
        }
    }

    public static Point mulStraus(List<Scalar> scalars, List<Point> points) {
        List<Point[]> lookupTables = new ArrayList<>();
        for (Point point : points) {
            lookupTables.add(createLookupTable(point));
        }

        List<byte[]> scalarRadix = new ArrayList<>();
        for (Scalar scalar : scalars) {
            scalarRadix.add(toRadix16(scalar));
        }

        Point Q = G2Point.IDENTITY;

        for (int i = 63; i >= 0; i--) {
            Q = Q.multiply(FrScalar.fromLong(16));

            for (int j = 0; j < points.size(); j++) {
                byte digit = scalarRadix.get(j)[i];
                if (digit != 0) {
                    Point term = lookupTables.get(j)[Math.abs(digit)];
                    Q = digit > 0 ? Q.add(term) : Q.subtract(term);
                }
            }
        }

        return Q;
    }

    public static Point mulPippenger(List<Scalar> scalars, List<Point> points) {
        int c = points.size() < 500 ? 6 : points.size() < 800 ? 7 : 8;

        List<byte[]> scalarRadix = new ArrayList<>();
        for (Scalar scalar : scalars) {
            scalarRadix.add(toRadix2w(scalar, c));
        }

        int bucketsCount = 1 << (c - 1);
        int digits = (255 + c - 1) / c;

        Point Q = null;

        for (int k = digits - 1; k >= 0; k--) {
            Point[] buckets = new G2Point[bucketsCount];
            for (int i = 0; i < bucketsCount; i++) {
                buckets[i] = G2Point.IDENTITY;
            }

            for (int i = 0; i < points.size(); i++) {
                byte d = scalarRadix.get(i)[k];
                if (d != 0) {
                    int idx = Math.abs(d) - 1;
                    buckets[idx] = d > 0
                            ? buckets[idx].add(points.get(i))
                            : buckets[idx].subtract(points.get(i));
                }
            }

            Point sum = buckets[bucketsCount - 1];
            Point bsum = buckets[bucketsCount - 1];
            for (int i = bucketsCount - 2; i >= 0; i--) {
                sum = sum.add(buckets[i]);
                bsum = bsum.add(sum);
            }

            if (Q == null) {
                Q = bsum;
            } else {
                Q = Q.multiply(FrScalar.fromBigInteger(BigInteger.ONE.shiftLeft(c))).add(bsum);
            }
        }

        return Q;
    }

    private static Point[] createLookupTable(Point point) {
        Point[] table = new G2Point[9];
        table[0] = G2Point.IDENTITY;
        table[1] = point;
        for (int i = 2; i <= 8; i++) {
            table[i] = table[i - 1].add(point);
        }
        return table;
    }

    private static byte[] toRadix16(Scalar scalar) {
        byte[] s = scalar.toByteArray();
        final byte[] e = new byte[64];
        int i;
        for (i = 0; i < 32; i++) {
            e[2 * i + 0] = (byte) (s[i] & 15);
            e[2 * i + 1] = (byte) ((s[i] >> 4) & 15);
        }
        int carry = 0;
        for (i = 0; i < 63; i++) {
            e[i] += carry;
            carry = e[i] + 8;
            carry >>= 4;
            e[i] -= carry << 4;
        }
        e[63] += carry;
        return e;
    }

    private static byte[] toRadix2w(Scalar scalar, int w) {
        long[] scalar64x4 = new long[4];
        ByteBuffer.wrap(scalar.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get(scalar64x4);

        long radix = 1L << w;
        long windowMask = radix - 1;

        long carry = 0L;
        byte[] digits = new byte[43];
        int digitsCount = (256 + w - 1) / w;
        for (int i = 0; i < digitsCount; i++) {
            int bitOffset = i * w;
            int u64Idx = bitOffset / 64;
            int bitIdx = bitOffset % 64;

            long bitBuf;
            if (bitIdx < 64 - w || u64Idx == 3) {
                bitBuf = scalar64x4[u64Idx] >>> bitIdx;
            } else {
                bitBuf = (scalar64x4[u64Idx] >>> bitIdx) | (scalar64x4[1 + u64Idx] << (64 - bitIdx));
            }

            long coef = carry + (bitBuf & windowMask);

            carry = (coef + (radix / 2)) >>> w;
            digits[i] = (byte) ((coef - (carry << w)));
        }

        if (w == 8) {
            digits[digitsCount] += carry;
        } else {
            digits[digitsCount - 1] += (carry << w);
        }

        return digits;
    }
}
