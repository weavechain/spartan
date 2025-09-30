package com.weavechain.curves;

public interface Point {

    Point add(Point other);

    Point subtract(Point other);

    Point multiply(Scalar scalar);

    byte[] toByteArray();
}