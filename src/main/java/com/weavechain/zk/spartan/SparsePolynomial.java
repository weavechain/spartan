package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class SparsePolynomial {

    private final long numVars;

    private final List<SparsePolyEntry> Z;

    public static List<Boolean> getBits(Long value, int numBits) {
        List<Boolean> result = new ArrayList<>();
        for (int i = 0; i < numBits; i++) {
            result.add((value & (1L << (numBits - i - 1))) > 0);
        }
        return result;
    }

    private Scalar computeChi(List<Boolean> a, List<Scalar> r, ScalarFactory scalarFactory) {
        if (a.size() != r.size()) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        Scalar ONE = scalarFactory.one();

        Scalar result = ONE;
        for (int i = 0; i < r.size(); i++) {
            if (a.get(i)) {
                result = result.multiply(r.get(i));
            } else {
                result = result.multiply(ONE.subtract(r.get(i)));
            }
        }
        return result;
    }

    public Scalar evaluate(List<Scalar> r, ScalarFactory scalarFactory) {
        if (numVars != r.size()) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        Scalar result = scalarFactory.zero();
        for (int i = 0; i < Z.size(); i++) {
            List<Boolean> bits = getBits(Z.get(i).getIdx(), r.size());
            Scalar c = computeChi(bits, r, scalarFactory).multiply(Z.get(i).getVal());
            result = result.add(c);
        }
        return result;
    }
}
