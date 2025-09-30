package com.weavechain.curves;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class RScalar25519Factory implements ScalarFactory {

    public static final Scalar ZERO = new RScalar25519(com.weavechain.curve25519.Scalar.ZERO);

    public static final Scalar ONE = new RScalar25519(com.weavechain.curve25519.Scalar.ONE);

    private static final Scalar TWO_INV = new RScalar25519(com.weavechain.curve25519.Scalar.ONE.add(com.weavechain.curve25519.Scalar.ONE).invert());

    private static final Scalar SIX_INV = new RScalar25519(com.weavechain.curve25519.Scalar.ONE.add(com.weavechain.curve25519.Scalar.ONE).add(com.weavechain.curve25519.Scalar.ONE).add(com.weavechain.curve25519.Scalar.ONE).add(com.weavechain.curve25519.Scalar.ONE).add(com.weavechain.curve25519.Scalar.ONE).invert());

    private static final ThreadLocal<SecureRandom> RANDOM = ThreadLocal.withInitial(SecureRandom::new);

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
        return new RScalar25519(com.weavechain.curve25519.Scalar.fromBits(input));
    }

    @Override
    public Scalar fromBytesModOrderWide(byte[] input) {
        return new RScalar25519(com.weavechain.curve25519.Scalar.fromBytesModOrderWide(input));
    }

    @Override
    public Scalar scalar(Long value) {
        ByteBuffer buffer = ByteBuffer.allocate(32);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(value);
        buffer.flip();
        return fromBits(buffer.array());
    }

    @Override
    public Scalar rndScalar() {
        byte[] r = new byte[serializedSize()];
        RANDOM.get().nextBytes(r);
        return fromBits(r);
    }

    @Override
    public int serializedSize() {
        return 32;
    }
}
