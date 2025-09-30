package com.weavechain.zk.spartan;

import com.weavechain.curves.Point;
import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.Commitments;
import com.weavechain.zk.spartan.generators.MultiCommitGens;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class UniPoly {

    private final List<Scalar> coeffs;

    public long degree() {
        return coeffs.size() - 1;
    }

    public Scalar evaluate(Scalar r) {
        Scalar eval = coeffs.get(0);
        Scalar power = r;
        for (int i = 1; i < coeffs.size(); i++) {
            eval = eval.add(power.multiply(coeffs.get(i)));
            power = power.multiply(r);
        }
        return eval;
    }

    public Scalar evalAtZero() {
        return coeffs.get(0);
    }

    public Scalar evalAtOne(ScalarFactory scalarFactory) {
        Scalar result = scalarFactory.zero();
        for (Scalar c : coeffs) {
            result = result.add(c);
        }
        return result;
    }

    public Point commit(MultiCommitGens gens, Scalar blind, PointFactory pointFactory) {
        return Commitments.batchCommit(coeffs, blind, gens, pointFactory);
    }

    public CompressedUniPoly compress() {
        List<Scalar> compressed = new ArrayList<>();
        compressed.add(coeffs.get(0));
        compressed.addAll(coeffs.subList(2, coeffs.size()));
        return new CompressedUniPoly(compressed);
    }

    public static UniPoly fromEvals(List<Scalar> evals, ScalarFactory scalarFactory) {
        if (evals.size() != 3 && evals.size() != 4) {
            throw new IllegalArgumentException("Invalid degree");
        }

        Scalar TWO_INV = scalarFactory.twoInv();
        Scalar SIX_INV = scalarFactory.sixInv();

        if (evals.size() == 3) {
            // ax^2 + bx + c

            Scalar c = evals.get(0);
            Scalar a = evals.get(2).subtract(evals.get(1)).subtract(evals.get(1)).add(c).multiply(TWO_INV);
            Scalar b = evals.get(1).subtract(c).subtract(a);

            return new UniPoly(List.of(c, b, a));
        } else {
            // ax^3 + bx^2 + cx + d

            Scalar eval0 = evals.get(0);
            Scalar eval1 = evals.get(1);
            Scalar eval2 = evals.get(2);
            Scalar eval3 = evals.get(3);

            Scalar eval1x3 = eval1.add(eval1).add(eval1);
            Scalar eval1x5 = eval1x3.add(eval1).add(eval1);
            Scalar eval2x3 = eval2.add(eval2).add(eval2);
            Scalar eval2x4 = eval2x3.add(eval2);

            Scalar d = eval0;
            Scalar a = eval3.subtract(eval2x3).add(eval1x3).subtract(eval0).multiply(SIX_INV);
            Scalar b = eval0.add(eval0).subtract(eval1x5).add(eval2x4).subtract(eval3).multiply(TWO_INV);
            Scalar c = eval1.subtract(d).subtract(a).subtract(b);

            return new UniPoly(List.of(d, c, b, a));
        }
    }
}
