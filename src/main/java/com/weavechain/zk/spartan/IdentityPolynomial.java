package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class IdentityPolynomial {

    private long sizePoint;

    public Scalar evaluate(List<Scalar> r, ScalarFactory scalarFactory) {
        Scalar result = scalarFactory.zero();
        int len = r.size();
        for (int i = 0; i < len; i++) {
            result = result.add(scalarFactory.scalar(1L << (len - i - 1)).multiply(r.get(i)));
        }
        return result;
    }
}
