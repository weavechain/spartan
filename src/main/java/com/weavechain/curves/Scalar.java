package com.weavechain.curves;

import java.math.BigInteger;

public interface Scalar {

    byte[] toByteArray();

    Scalar invert();

    Scalar add(Scalar other);

    Scalar subtract(Scalar other);

    Scalar multiply(Scalar other);

    Scalar square();

    Scalar negate();
}