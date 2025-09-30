package com.weavechain.zk.spartan.commit;

import com.weavechain.curves.Point;
import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.zk.spartan.generators.MultiCommitGens;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class Commitments {

    public static Point commit(Scalar input, Scalar blind, MultiCommitGens gens) {
        return gens.getG().get(0).multiply(input).add(gens.getH().multiply(blind));
    }

    public static Point batchCommit(List<Scalar> inputs, Scalar blind, MultiCommitGens gens, PointFactory pointFactory) {
        if (gens.getN() != inputs.size()) {
            throw new IllegalArgumentException("Invalid sizes, " + gens.getN() + " != " + inputs.size());
        }
        List<Point> bases = new ArrayList<>(gens.getG());
        List<Scalar> scalars = new ArrayList<>(inputs);
        bases.add(gens.getH());
        scalars.add(blind);

        return pointFactory.multiscalarMul(scalars, bases);
    }
}
