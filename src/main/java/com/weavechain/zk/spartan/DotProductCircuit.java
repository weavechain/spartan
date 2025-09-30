package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DotProductCircuit {

    private final DensePolynomial left;

    private final DensePolynomial right;

    private final DensePolynomial weight;

    public Scalar evaluate(ScalarFactory scalarFactory) {
        Scalar result = scalarFactory.zero();
        for (int i = 0; i < left.getLen(); i++) {
            Scalar e = left.get(i).multiply(right.get(i)).multiply(weight.get(i));
            result = result.add(e);
        }
        return result;
    }
}
