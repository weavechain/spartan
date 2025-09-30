package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SparseMatEntry {

    private final long row;

    private final long col;

    private final Scalar val;
}
