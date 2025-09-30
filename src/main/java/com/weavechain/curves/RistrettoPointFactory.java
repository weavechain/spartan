package com.weavechain.curves;

import com.weavechain.curve25519.CompressedRistretto;
import com.weavechain.curve25519.MulUtils;
import com.weavechain.curve25519.RistrettoElement;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class RistrettoPointFactory implements PointFactory {

    private static RistrettoPoint ZERO;
    static {
        try {
            ZERO = new RistrettoPoint(new CompressedRistretto(new byte[32]).decompress());
        } catch (Exception e) {
            //ignore
        }
    }

    private static RistrettoPoint GEN = new RistrettoPoint(RistrettoElement.BASEPOINT);

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
        return 32;
    }

    @Override
    public Point fromUniformBytes(byte[] input) {
        return new RistrettoPoint(RistrettoElement.fromUniformBytes(input));
    }

    @Override
    public Point fromCompressedBytes(byte[] input) throws IOException {
        return RistrettoPoint.fromBytes(input);
    }

    @Override
    public Point multiscalarMul(List<Scalar> scalars, List<Point> bases) {
        List<com.weavechain.curve25519.Scalar> s = new ArrayList<>();
        for (Scalar it : scalars) {
            s.add(((RScalar25519)it).getScalar());
        }
        List<RistrettoElement> p = new ArrayList<>();
        for (Point it : bases) {
            p.add(((RistrettoPoint)it).getPoint());
        }
        return new RistrettoPoint(MulUtils.multiscalarMulOpt(s, p));
    }

}
