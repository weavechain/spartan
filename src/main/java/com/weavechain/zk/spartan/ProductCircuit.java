package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class ProductCircuit {

    private final List<DensePolynomial> leftVec;

    private final List<DensePolynomial> rightVec;

    public Scalar evaluate() {
        int len = leftVec.size();
        return leftVec.get(len - 1).get(0).multiply(rightVec.get(len - 1).get(0));
    }

    public static ProductCircuit create(DensePolynomial poly) {
        List<DensePolynomial> leftVec = new ArrayList<>();
        List<DensePolynomial> rightVec = new ArrayList<>();

        long numLayers = Utils.log2(poly.getLen());
        int sidx = (int)poly.getLen() / 2;
        DensePolynomial outPLeft = DensePolynomial.create(poly.getZ().subList(0, sidx));
        DensePolynomial outPRight = DensePolynomial.create(poly.getZ().subList(sidx, poly.getZ().size()));

        leftVec.add(outPLeft);
        rightVec.add(outPRight);

        for (int i = 0; i < numLayers - 1; i++) {
            Pair<DensePolynomial, DensePolynomial> c = computeLayer(leftVec.get(i), rightVec.get(i));
            leftVec.add(c.getValue1());
            rightVec.add(c.getValue2());
        }

        return new ProductCircuit(leftVec, rightVec);
    }

    public static Pair<DensePolynomial, DensePolynomial> computeLayer(DensePolynomial inpLeft, DensePolynomial inpRight) {
        int len = (int)(inpLeft.getLen() + inpRight.getLen());

        List<Scalar> outpLeft = new ArrayList<>();
        for (int i = 0; i < len / 4; i++) {
            outpLeft.add(inpLeft.get(i).multiply(inpRight.get(i)));
        }

        List<Scalar> outpRight = new ArrayList<>();
        for (int i = len / 4; i < len / 2; i++) {
            outpRight.add(inpLeft.get(i).multiply(inpRight.get(i)));
        }

        return new Pair<>(
                DensePolynomial.create(outpLeft),
                DensePolynomial.create(outpRight)
        );
    }
}
