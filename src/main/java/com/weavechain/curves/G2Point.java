package com.weavechain.curves;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import supranational.blst.P2;

@Getter
@EqualsAndHashCode
public class G2Point implements Point {

    public static final int G2_SERIALIZED_SIZE = 96;

    public static final G2Point IDENTITY = new G2Point(P2.generator().add(P2.generator().neg()));

    private final P2 point;

    public G2Point(P2 point) {
        this.point = point;
    }

    public static Point fromBytes(byte[] input) {
        return new G2Point(new P2(input));
    }

    @Override
    public Point add(Point other) {
        return new G2Point(point.dup().add(((G2Point)other).point));
    }

    @Override
    public Point subtract(Point other) {
        return new G2Point(point.dup().add(((G2Point)other).point.dup().neg()));
    }

    @Override
    public Point multiply(Scalar scalar) {
        supranational.blst.Scalar s = new supranational.blst.Scalar().from_lendian(scalar.toByteArray());
        return new G2Point(point.dup().mult(s));
    }

    @Override
    public byte[] toByteArray() {
        return point.compress();
    }

    @Override
    public String toString() {
        return point.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other == null) {
            return false;
        } else if (getClass() != other.getClass()) {
            return false;
        } else {
            return point.is_equal(((G2Point)other).point);
        }
    }
}
