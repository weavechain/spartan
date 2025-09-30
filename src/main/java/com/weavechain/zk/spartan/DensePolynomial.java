package com.weavechain.zk.spartan;

import com.weavechain.curves.Point;
import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.Commitments;
import com.weavechain.zk.spartan.commit.PolyCommitment;
import com.weavechain.zk.spartan.generators.MultiCommitGens;
import com.weavechain.zk.spartan.generators.PolyCommitmentGens;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@AllArgsConstructor
public class DensePolynomial {

    private long numVars;

    private long len;

    private List<Scalar> Z;

    public Scalar get(int idx) {
        return Z.get(idx);
    }

    public Pair<PolyCommitment, PolyCommitmentBlinds> commit(PolyCommitmentGens gens, RandomTape randomTape, ScalarFactory scalarFactory, PointFactory pointFactory) {
        long n = Z.size();
        if (n != 1L << numVars) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        int lSize = 1 << (int)(numVars / 2);
        long rSize = 1L << (numVars - numVars / 2);
        if (n != lSize * rSize) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        Scalar ZERO = scalarFactory.zero();
        List<Scalar> b = randomTape != null ? randomTape.randomVector("poly_blinds".getBytes(StandardCharsets.UTF_8), lSize) : Collections.nCopies(lSize, ZERO);
        PolyCommitmentBlinds blinds = new PolyCommitmentBlinds(b);

        return new Pair<>(
                commitInner(blinds.getBlinds(), gens.getGens().getGensN(), pointFactory),
                blinds
        );
    }

    public static DensePolynomial create(List<Scalar> Z) {
        long numVars = Utils.log2((long)Z.size());
        return new DensePolynomial(numVars, Z.size(), new ArrayList<>(Z));
    }

    public void boundPolyVarTop(Scalar r) {
        int n = (int)len / 2;
        for (int i = 0; i < n; i++) {
            Z.set(i, Z.get(i).add(r.multiply(Z.get(i + n).subtract(Z.get(i)))));
        }
        numVars--;
        len = n;
        Z = Z.subList(0, (int)len);
    }

    public void boundPolyVarBot(Scalar r) {
        int n = (int)len / 2;
        for (int i = 0; i < n; i++) {
            Z.set(i, Z.get(2 * i).add(r.multiply(Z.get(2 * i + 1).subtract(Z.get(2 * i)))));
        }
        numVars--;
        len = n;
        Z = Z.subList(0, (int)len);
    }

    public Scalar evaluate(List<Scalar> r, ScalarFactory scalarFactory) {
        if (r.size() != numVars) {
            throw new IllegalArgumentException("Invalid sizes");
        }
        List<Scalar> chis = new EqPolynomial(r).evals(scalarFactory);
        if (chis.size() != Z.size()) {
            throw new IllegalArgumentException("Invalid sizes");
        }
        return DotProductProofLog.computeDotProduct(Z, chis, scalarFactory);
    }

    public List<Scalar> bound(List<Scalar> L, ScalarFactory scalarFactory) {
        int lSize = 1 << (int)(numVars / 2);
        int rSize = 1 << (int)(numVars - numVars / 2);

        List<Scalar> res = new ArrayList<>();
        for (int i = 0; i < rSize; i++) {
            Scalar b = scalarFactory.zero();
            for (int j = 0; j < lSize; j++) {
                Scalar val = L.get(j).multiply(Z.get(j * rSize + i));
                b = b.add(val);
            }
            res.add(b);
        }
        return res;
    }

    public DensePolynomial half(int idx) {
        return DensePolynomial.create(idx == 0 ? Z.subList(0, Z.size() / 2) : Z.subList(Z.size() / 2, Z.size()));
    }

    @Override
    public DensePolynomial clone() {
        return new DensePolynomial(
                numVars,
                len,
                new ArrayList<>(Z)
        );
    }

    public void extend(DensePolynomial other) {
        Z.addAll(other.getZ());
        numVars++;
        len *= 2;
    }

    private PolyCommitment commitInner(List<Scalar> blinds, MultiCommitGens gens, PointFactory pointFactory) {
        int lSize = blinds.size();
        int rSize = Z.size() / lSize;
        if (lSize * rSize != Z.size()) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        List<Point> C = new ArrayList<>();
        for (int i = 0; i < lSize; i++) {
            List<Scalar> z = Z.subList(rSize * i, rSize * (i + 1));
            Point c = Commitments.batchCommit(z, blinds.get(i), gens, pointFactory);
            C.add(c);
        }

        return new PolyCommitment(C);
    }

    public static DensePolynomial fromSize(List<Long> val, ScalarFactory scalarFactory) {
        List<Scalar> s = new ArrayList<>();
        for (Long l : val) {
            s.add(scalarFactory.scalar(l));
        }
        return DensePolynomial.create(s);
    }

    public static DensePolynomial merge(List<List<DensePolynomial>> polys, ScalarFactory scalarFactory) {
        List<Scalar> merged = new ArrayList<>();
        for (List<DensePolynomial> it : polys) {
            for (DensePolynomial p : it) {
                merged.addAll(p.getZ());
            }
        }

        int len = (int)Utils.nextPow2(merged.size());
        for (int i = merged.size(); i < len; i++) {
            merged.add(scalarFactory.zero());
        }

        return DensePolynomial.create(merged);
    }
}
