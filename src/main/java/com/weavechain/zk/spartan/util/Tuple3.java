package com.weavechain.zk.spartan.util;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class Tuple3<T1, T2, T3> {

    private final T1 value1;

    private final T2 value2;

    private final T3 value3;
}
