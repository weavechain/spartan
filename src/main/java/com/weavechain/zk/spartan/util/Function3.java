package com.weavechain.zk.spartan.util;

public interface Function3<T1, T2, T3, T> {
    T apply(T1 a, T2 b, T3 c);
}