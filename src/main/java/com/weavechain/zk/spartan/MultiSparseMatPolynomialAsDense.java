package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class MultiSparseMatPolynomialAsDense {

    private final long batchSize;

    private final AddrTimestamps row;

    private final AddrTimestamps col;

    private final List<DensePolynomial> val;

    private final DensePolynomial combOps;

    private final DensePolynomial combMem;

    public Derefs deref(List<Scalar> rowMemVal, List<Scalar> colMemVal, ScalarFactory scalarFactory) {
        return Derefs.create(
                row.deref(rowMemVal),
                col.deref(colMemVal),
                scalarFactory
        );
    }
}
