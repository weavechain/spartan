package com.weavechain.curves;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigInteger;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class RScalar25519 implements Scalar {

    private final com.weavechain.curve25519.Scalar scalar;

    @Override
    public byte[] toByteArray() {
        return scalar.toByteArray();
    }

    @Override
    public Scalar invert() {
        return new RScalar25519(scalar.invert());
    }

    @Override
    public Scalar square() {
        return new RScalar25519(scalar.square());
    }

    @Override
    public Scalar negate() {
        return new RScalar25519(com.weavechain.curve25519.Scalar.ZERO.subtract(scalar));
    }

    @Override
    public Scalar add(Scalar other) {
        return new RScalar25519(scalar.add(((RScalar25519)other).scalar));
    }

    @Override
    public Scalar subtract(Scalar other) {
        return new RScalar25519(scalar.subtract(((RScalar25519)other).scalar));
    }

    @Override
    public Scalar multiply(Scalar other) {
        return new RScalar25519(scalar.multiply(((RScalar25519)other).scalar));
    }

    @Override
    public String toString() {
        return scalar.toString();
    }
}
