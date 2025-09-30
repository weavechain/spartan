package com.weavechain.curves;

import com.weavechain.curve25519.CompressedRistretto;
import com.weavechain.curve25519.RistrettoElement;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.IOException;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class RistrettoPoint implements Point {

    private final RistrettoElement point;

    public static Point fromBytes(byte[] input) throws IOException {
        try {
            return new RistrettoPoint(new CompressedRistretto(input).decompress());
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public Point add(Point other) {
        return new RistrettoPoint(point.add(((RistrettoPoint)other).getPoint()));
    }

    @Override
    public Point subtract(Point other) {
        return new RistrettoPoint(point.subtract(((RistrettoPoint)other).getPoint()));
    }

    @Override
    public Point multiply(Scalar scalar) {
        return new RistrettoPoint(point.multiply(((RScalar25519)scalar).getScalar()));
    }

    @Override
    public byte[] toByteArray() {
        return point.compress().toByteArray();
    }

    @Override
    public String toString() {
        return point.toString();
    }
}
