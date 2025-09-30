package com.weavechain.curves;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.security.SecureRandom;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class FrScalarFactory implements ScalarFactory {

    private static final ThreadLocal<SecureRandom> RANDOM = ThreadLocal.withInitial(SecureRandom::new);

    private static final boolean DEBUG = false;

    public static final Scalar ZERO = FrScalar.scalar(0);

    public static final Scalar ONE = FrScalar.scalar(1);

    private static final Scalar TWO_INV = FrScalar.scalar(2).invert();
    private static final Scalar SIX_INV = FrScalar.scalar(6).invert();

    @Override
    public Scalar zero() {
        return ZERO;
    }

    @Override
    public Scalar one() {
        return ONE;
    }

    @Override
    public Scalar twoInv() {
        return TWO_INV;
    }

    @Override
    public Scalar sixInv() {
        return SIX_INV;
    }

    @Override
    public Scalar fromBits(byte[] input) {
        return FrScalar.fromByteArray(input);
    }

    @Override
    public Scalar fromBytesModOrderWide(byte[] input) {
        return FrScalar.fromLeBytesModOrder(input);
    }

    @Override
    public Scalar scalar(Long value) {
        return FrScalar.scalar(value);
    }

    @Override
    public Scalar rndScalar() {
        byte[] r = new byte[serializedSize()];
        if (DEBUG) {
            r[0] = 1;
        } else {
            RANDOM.get().nextBytes(r);
        }
        return fromBits(r);
    }

    @Override
    public int serializedSize() {
        return 32;
    }
}
