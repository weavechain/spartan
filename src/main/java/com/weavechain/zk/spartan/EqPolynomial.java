package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class EqPolynomial {

    public final List<Scalar> r;

    public List<Scalar> evals(ScalarFactory scalarFactory) {
        List<Scalar> result = new ArrayList<>();

        for (int i = 0; i < (1 << r.size()); i++) {
            result.add(scalarFactory.one());
        }

        int size = 1;
        for (int j = 0; j < r.size(); j++) {
            size *= 2;
            for (int i = size - 1; i >= 0; i -= 2) {
                Scalar s = result.get(i / 2);
                result.set(i, s.multiply(r.get(j)));
                result.set(i - 1, s.subtract(result.get(i)));
            }
        }

        return result;
    }

    public Scalar evaluate(List<Scalar> rx, ScalarFactory scalarFactory) {
        Scalar ONE = scalarFactory.one();
        Scalar result = ONE;
        for (int i = 0; i < rx.size(); i++) {
            Scalar p = r.get(i).multiply(rx.get(i)).add(ONE.subtract(r.get(i)).multiply(ONE.subtract(rx.get(i))));
            result = result.multiply(p);
        }
        return result;
    }

    public Pair<List<Scalar>, List<Scalar>> computeFactoredEvals(ScalarFactory scalarFactory) {
        int lSize = r.size() / 2;

        return new Pair<>(
                new EqPolynomial(r.subList(0, lSize)).evals(scalarFactory),
                new EqPolynomial(r.subList(lSize, r.size())).evals(scalarFactory)
        );
    }
}
