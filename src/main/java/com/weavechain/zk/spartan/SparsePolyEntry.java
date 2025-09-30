package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SparsePolyEntry {

    private final long idx;

    private final Scalar val;
}
