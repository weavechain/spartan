package com.weavechain.curves;

import java.io.IOException;
import java.util.List;

public interface PointFactory {

    Point zero();

    Point generator();

    int compressedSize();

    Point fromUniformBytes(byte[] input);

    Point fromCompressedBytes(byte[] input) throws IOException;

    Point multiscalarMul(List<Scalar> scalars, List<Point> bases);
}