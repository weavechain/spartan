package com.weavechain.zk.spartan.commit;

import com.weavechain.zk.spartan.MultiSparseMatPolynomialAsDense;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class R1CSDecommitment {

    private final MultiSparseMatPolynomialAsDense dense;
}
