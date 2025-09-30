package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.util.Function3;
import com.weavechain.zk.spartan.util.Tuple4;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class Layers {

    private final ProductLayer prodLayer;

    private static final boolean DEBUG_EVAL = false;

    public static Layers create(
            List<Scalar> evalTable,
            AddrTimestamps addrTimestamps,
            List<DensePolynomial> polyOpsVal,
            Pair<Scalar, Scalar> rMemCheck,
            ScalarFactory scalarFactory
    ) {
        Tuple4<DensePolynomial, List<DensePolynomial>, List<DensePolynomial>, DensePolynomial> h = buildHashLayer(
                evalTable,
                addrTimestamps.getOpsAddr(),
                polyOpsVal,
                addrTimestamps.getReadTs(),
                addrTimestamps.getAuditTs(),
                rMemCheck,
                scalarFactory
        );
        DensePolynomial polyInitHashed = h.getValue1();
        List<DensePolynomial> polyReadHashed = h.getValue2();
        List<DensePolynomial> polyWriteHashed = h.getValue3();
        DensePolynomial polyAuditHashed = h.getValue4();

        ProductCircuit prodInit = ProductCircuit.create(polyInitHashed);
        List<ProductCircuit> prodReadVec = new ArrayList<>();
        for (DensePolynomial p : polyReadHashed) {
            prodReadVec.add(ProductCircuit.create(p));
        }
        List<ProductCircuit> prodWriteVec = new ArrayList<>();
        for (DensePolynomial p : polyWriteHashed) {
            prodWriteVec.add(ProductCircuit.create(p));
        }

        ProductCircuit prodAudit = ProductCircuit.create(polyAuditHashed);

        if (DEBUG_EVAL) {
            Scalar hashedReads = scalarFactory.one();
            for (ProductCircuit p : prodReadVec) {
                hashedReads = hashedReads.multiply(p.evaluate());
            }
            Scalar hashedReadSet = hashedReads.multiply(prodAudit.evaluate());

            Scalar hashedWrites = scalarFactory.one();
            for (ProductCircuit p : prodWriteVec) {
                hashedWrites = hashedWrites.multiply(p.evaluate());
            }
            Scalar hashedWriteSet = prodInit.evaluate().multiply(hashedWrites);
            if (!hashedReadSet.equals(hashedWriteSet)) {
                throw new IllegalArgumentException("Invalid check");
            }
        }

        return new Layers(
                new ProductLayer(
                        prodInit,
                        prodReadVec,
                        prodWriteVec,
                        prodAudit
                )
        );
    }

    public static Tuple4<DensePolynomial, List<DensePolynomial>, List<DensePolynomial>, DensePolynomial> buildHashLayer(
            List<Scalar> evalTable,
            List<DensePolynomial> addrsVec,
            List<DensePolynomial> derefsVec,
            List<DensePolynomial> readTsVec,
            DensePolynomial auditTs,
            Pair<Scalar, Scalar> rMemCheck,
            ScalarFactory scalarFactory
    ) {
        Scalar rHash = rMemCheck.getValue1();
        Scalar rHashSq = rHash.square();
        Scalar rMultisetCheck = rMemCheck.getValue2();

        Function3<Scalar, Scalar, Scalar, Scalar> hashFn = (addr, val, ts) -> ts.multiply(rHashSq).add(val.multiply(rHash)).add(addr);

        long numMemCells = evalTable.size();

        List<Scalar> polyInit = new ArrayList<>();
        for (int i = 0; i < numMemCells; i++) {
            Scalar r = hashFn.apply(scalarFactory.scalar((long)i), evalTable.get(i), scalarFactory.zero()).subtract(rMultisetCheck);
            polyInit.add(r);
        }
        DensePolynomial polyInitHashed = DensePolynomial.create(polyInit);

        List<Scalar> polyAudit = new ArrayList<>();
        for (int i = 0; i < numMemCells; i++) {
            Scalar r = hashFn.apply(scalarFactory.scalar((long)i), evalTable.get(i), auditTs.get(i)).subtract(rMultisetCheck);
            polyAudit.add(r);
        }
        DensePolynomial polyAuditHashed = DensePolynomial.create(polyAudit);

        List<DensePolynomial> polyReadHashedVec = new ArrayList<>();
        List<DensePolynomial> polyWriteHashedVec = new ArrayList<>();
        for (int j = 0; j < addrsVec.size(); j++) {
            DensePolynomial addrs = addrsVec.get(j);
            DensePolynomial derefs = derefsVec.get(j);
            DensePolynomial readTs = readTsVec.get(j);

            long numOps = addrs.getLen();

            List<Scalar> polyReadHashed = new ArrayList<>();
            for (int i = 0; i < numOps; i++) {
                Scalar r = hashFn.apply(addrs.get(i), derefs.get(i), readTs.get(i)).subtract(rMultisetCheck);
                polyReadHashed.add(r);
            }
            polyReadHashedVec.add(DensePolynomial.create(polyReadHashed));

            List<Scalar> polyWriteHashed = new ArrayList<>();
            for (int i = 0; i < numOps; i++) {
                Scalar r = hashFn.apply(addrs.get(i), derefs.get(i), readTs.get(i).add(scalarFactory.one())).subtract(rMultisetCheck);
                polyWriteHashed.add(r);
            }
            polyWriteHashedVec.add(DensePolynomial.create(polyWriteHashed));
        }

        return new Tuple4<>(
                polyInitHashed,
                polyReadHashedVec,
                polyWriteHashedVec,
                polyAuditHashed
        );
    }
}
