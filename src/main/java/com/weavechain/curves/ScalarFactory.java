package com.weavechain.curves;

public interface ScalarFactory {

    Scalar zero();

    Scalar one();

    Scalar twoInv();

    Scalar sixInv();

    Scalar fromBits(byte[] input);

    Scalar fromBytesModOrderWide(byte[] input);

    Scalar scalar(Long value);

    Scalar rndScalar();

    int serializedSize();
}