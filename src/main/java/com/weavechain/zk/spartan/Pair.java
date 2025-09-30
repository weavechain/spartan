package com.weavechain.zk.spartan;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class Pair<T1, T2> {

    private final T1 value1;

    private final T2 value2;
}
