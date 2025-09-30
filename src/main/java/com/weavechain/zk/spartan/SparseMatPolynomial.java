package com.weavechain.zk.spartan;

import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.PolyCommitment;
import com.weavechain.zk.spartan.commit.SparseMatPolyCommitment;
import com.weavechain.zk.spartan.generators.SparseMatPolyCommitmentGens;
import com.weavechain.zk.spartan.util.Tuple3;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@AllArgsConstructor
public class SparseMatPolynomial {

    private long numVarsX;

    private long numVarsY;

    private List<SparseMatEntry> M;

    public SparseMatEntry get(int idx) {
        return M.get(idx);
    }

    public long getNumNZEntries() {
        return Utils.nextPow2(M.size());
    }

    public Scalar evaluateWithTables(List<Scalar> evalTableRx, List<Scalar> evalTableRy, ScalarFactory scalarFactory) {
        Scalar result = scalarFactory.zero();
        for (int i = 0; i < M.size(); i++) {
            SparseMatEntry m = M.get(i);
            int row = (int)m.getRow();
            int col = (int)m.getCol();
            Scalar val = m.getVal();
            Scalar e = evalTableRx.get(row).multiply(evalTableRy.get(col)).multiply(val);
            result = result.add(e);
        }
        return result;
    }

    public Tuple3<List<Long>, List<Long>, List<Scalar>> sparseToDenseVecs(int N, ScalarFactory scalarFactory) {
        if (N < getNumNZEntries()) {
            throw new IllegalArgumentException("Invalid size");
        }
        List<Long> opsRow = new ArrayList<>(Collections.nCopies(N, 0L));
        List<Long> opsCol = new ArrayList<>(Collections.nCopies(N, 0L));
        List<Scalar> val = new ArrayList<>(Collections.nCopies(N, scalarFactory.zero()));

        for (int i = 0; i < M.size(); i++) {
            SparseMatEntry m = M.get(i);
            opsRow.set(i, m.getRow());
            opsCol.set(i, m.getCol());
            val.set(i, m.getVal());
        }

        return new Tuple3<>(
                opsRow,
                opsCol,
                val
        );
    }

    public static List<Scalar> multiEvaluate(List<SparseMatPolynomial> polys, List<Scalar> rx, List<Scalar> ry, ScalarFactory scalarFactory) {
        List<Scalar> evalTableRx = new EqPolynomial(rx).evals(scalarFactory);
        List<Scalar> evalTableRy = new EqPolynomial(ry).evals(scalarFactory);

        List<Scalar> result = new ArrayList<>();
        for (SparseMatPolynomial p : polys) {
            result.add(p.evaluateWithTables(evalTableRx, evalTableRy, scalarFactory));
        }
        return result;
    }

    public static Pair<SparseMatPolyCommitment, MultiSparseMatPolynomialAsDense> multiCommit(List<SparseMatPolynomial> sparsePolys, SparseMatPolyCommitmentGens gens, ScalarFactory scalarFactory, PointFactory pointFactory) {
        int batchSize = sparsePolys.size();
        MultiSparseMatPolynomialAsDense dense = multiSparseToDense(sparsePolys, scalarFactory);
        Pair<PolyCommitment, PolyCommitmentBlinds> opsComm = dense.getCombOps().commit(gens.getGensOps(), null, scalarFactory, pointFactory);
        Pair<PolyCommitment, PolyCommitmentBlinds> memComm = dense.getCombMem().commit(gens.getGensMem(), null, scalarFactory, pointFactory);

        return new Pair<>(
                new SparseMatPolyCommitment(
                        batchSize,
                        dense.getRow().getAuditTs().getLen(),
                        dense.getRow().getReadTs().get(0).getLen(),
                        opsComm.getValue1(),
                        memComm.getValue1()
                ),
                dense
        );
    }

    private static MultiSparseMatPolynomialAsDense multiSparseToDense(List<SparseMatPolynomial> sparsePolys, ScalarFactory scalarFactory) {
        long N = 0;
        for (SparseMatPolynomial p : sparsePolys) {
            long n = p.getNumNZEntries();
            if (n > N) {
                N = n;
            }
        }
        N = Utils.nextPow2(N);

        List<List<Long>> opsRowVec = new ArrayList<>();
        List<List<Long>> opsColVec = new ArrayList<>();
        List<DensePolynomial> valVec = new ArrayList<>();
        for (SparseMatPolynomial p : sparsePolys) {
            Tuple3<List<Long>, List<Long>, List<Scalar>> dense = p.sparseToDenseVecs((int)N, scalarFactory);
            opsRowVec.add(dense.getValue1());
            opsColVec.add(dense.getValue2());
            valVec.add(DensePolynomial.create(dense.getValue3()));
        }

        SparseMatPolynomial anyPoly = sparsePolys.get(0);

        long numMemCells = 1L << Math.max(anyPoly.getNumVarsX(), anyPoly.getNumVarsY());

        AddrTimestamps row = AddrTimestamps.create(numMemCells, N, opsRowVec, scalarFactory);
        AddrTimestamps col = AddrTimestamps.create(numMemCells, N, opsColVec, scalarFactory);

        DensePolynomial combOps = DensePolynomial.merge(List.of(row.getOpsAddr(), row.getReadTs(), col.getOpsAddr(), col.getReadTs(), valVec), scalarFactory);
        DensePolynomial combMem = row.getAuditTs().clone();
        combMem.extend(col.getAuditTs());

        return new MultiSparseMatPolynomialAsDense(
                sparsePolys.size(),
                row,
                col,
                valVec,
                combOps,
                combMem
        );
    }

    public List<Scalar> multiplyVec(long numRows, long numCols, List<Scalar> z, ScalarFactory scalarFactory) {
        List<Scalar> result = new ArrayList<>();
        if (z.size() != numCols) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        for (int i = 0; i < numRows; i++) {
            result.add(scalarFactory.zero());
        }

        for (SparseMatEntry entry : M) {
            int row = (int)entry.getRow();
            int col = (int)entry.getCol();
            Scalar val = entry.getVal();

            Scalar v = val.multiply(z.get(col));
            Scalar prev = result.get(row);
            result.set(row, prev.add(v));
        }

        return result;
    }
}
