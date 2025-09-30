package com.weavechain.zk.spartan;

import com.weavechain.curves.PointFactory;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.DerefsCommitment;
import com.weavechain.zk.spartan.commit.PolyCommitment;
import com.weavechain.zk.spartan.generators.PolyCommitmentGens;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class Derefs {

    private final List<DensePolynomial> rowOpsVal;

    private final List<DensePolynomial> colOpsVal;

    private final DensePolynomial comb;

    public DerefsCommitment commit(PolyCommitmentGens gens, ScalarFactory scalarFactory, PointFactory pointFactory) {
        Pair<PolyCommitment, PolyCommitmentBlinds> res = comb.commit(gens, null, scalarFactory, pointFactory);
        return new DerefsCommitment(res.getValue1());
    }

    public static Derefs create(List<DensePolynomial> rowOps, List<DensePolynomial> colOps, ScalarFactory scalarFactory) {
        DensePolynomial comb = DensePolynomial.merge(List.of(rowOps, colOps), scalarFactory);
        return new Derefs(
                rowOps,
                colOps,
                comb
        );
    }
}
