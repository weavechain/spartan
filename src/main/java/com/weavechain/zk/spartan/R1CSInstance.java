package com.weavechain.zk.spartan;

import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.R1CSCommitment;
import com.weavechain.zk.spartan.commit.R1CSDecommitment;
import com.weavechain.zk.spartan.commit.SparseMatPolyCommitment;
import com.weavechain.zk.spartan.generators.R1CSCommitmentGens;
import com.weavechain.zk.spartan.util.Tuple3;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class R1CSInstance {

    private final long numCons;

    private final long numVars;

    private final long numInputs;

    private final SparseMatPolynomial constraints;

    private final SparseMatPolynomial vars;

    private final SparseMatPolynomial inputs;

    public List<Scalar> evaluate(List<Scalar> rx, List<Scalar> ry, ScalarFactory scalarFactory) {
        return SparseMatPolynomial.multiEvaluate(List.of(constraints, vars, inputs), rx, ry, scalarFactory);
    }

    public static R1CSInstance create(long numCons, long numVars, long numInputs, List<Constraint> A, List<Constraint> B, List<Constraint> C, ScalarFactory scalarFactory) {
        long numVarsPadded = Utils.nextPow2(Math.max(numVars, numInputs + 1));
        long numConsPadded = Utils.nextPow2(Math.max(numCons, 2));

        long numPolyVarsX = Utils.log2(numConsPadded);
        long numPolyVarsY = Utils.log2(2 * numVarsPadded);

        List<SparseMatEntry> matA = pad(A, numCons, numVars, numVarsPadded, numConsPadded, scalarFactory);
        List<SparseMatEntry> matB = pad(B, numCons, numVars, numVarsPadded, numConsPadded, scalarFactory);
        List<SparseMatEntry> matC = pad(C, numCons, numVars, numVarsPadded, numConsPadded, scalarFactory);

        SparseMatPolynomial polyA = new SparseMatPolynomial(numPolyVarsX, numPolyVarsY, matA);
        SparseMatPolynomial polyB = new SparseMatPolynomial(numPolyVarsX, numPolyVarsY, matB);
        SparseMatPolynomial polyC = new SparseMatPolynomial(numPolyVarsX, numPolyVarsY, matC);

        return new R1CSInstance(
                numConsPadded,
                numVarsPadded,
                numInputs,
                polyA,
                polyB,
                polyC
        );
    }

    private static List<SparseMatEntry> pad(List<Constraint> vec, long numCons, long numVars, long numVarsPadded, long numConsPadded, ScalarFactory scalarFactory) {
        List<SparseMatEntry> result = new ArrayList<>();
        for (int i = 0; i < vec.size(); i++) {
            Constraint c = vec.get(i);
            if (c.getCol() >= numVars) {
                result.add(new SparseMatEntry(c.getRow(), c.getCol() + numVarsPadded - numVars, c.getVal()));
            } else {
                result.add(new SparseMatEntry(c.getRow(), c.getCol(), c.getVal()));
            }
        }
        if (numCons < 2) {
            for (int i = vec.size(); i < numConsPadded; i++) {
                result.add(new SparseMatEntry(i, numVars, scalarFactory.zero()));
            }
        }
        return result;
    }

    public boolean isSatisfiable(Assignment vars, Assignment inputs, ScalarFactory scalarFactory) {
        if (vars.getAssignment().size() > numVars) {
            return false;
        }

        if (inputs.getAssignment().size() > numInputs) {
            return false;
        }

        long numPaddedVars = Utils.nextPow2(Math.max(numVars, numInputs + 1));
        long numVars = vars.getAssignment().size();
        Assignment paddedVars = numPaddedVars > numVars ? Assignment.createPadded(vars, (int)numPaddedVars, scalarFactory) : vars;

        List<Scalar> z = new ArrayList<>(paddedVars.getAssignment());
        z.add(scalarFactory.one());
        z.addAll(inputs.getAssignment());

        List<Scalar> Az = constraints.multiplyVec(numCons, numPaddedVars + numInputs + 1, z, scalarFactory);
        List<Scalar> Bz = this.vars.multiplyVec(numCons, numPaddedVars + numInputs + 1, z, scalarFactory);
        List<Scalar> Cz = this.inputs.multiplyVec(numCons, numPaddedVars + numInputs + 1, z, scalarFactory);

        if (Az.size() != numCons || Bz.size() != numCons || Cz.size() != numCons) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        //verify if Az * Bz - Cz = [0...]
        for (int i = 0; i < numCons; i++) {
            if (!Az.get(i).multiply(Bz.get(i)).equals(Cz.get(i))) {
                return false;
            }
        }

        return true;
    }

    public Pair<R1CSCommitment, R1CSDecommitment> commit(R1CSCommitmentGens gens, ScalarFactory scalarFactory, PointFactory pointFactory) {
        Pair<SparseMatPolyCommitment, MultiSparseMatPolynomialAsDense> res = SparseMatPolynomial.multiCommit(List.of(constraints, vars, inputs), gens.getGens(), scalarFactory, pointFactory);

        R1CSCommitment comm = new R1CSCommitment(
                numCons,
                numVars,
                numInputs,
                res.getValue1()
        );

        R1CSDecommitment decomm = new R1CSDecommitment(res.getValue2());

        return new Pair<>(comm, decomm);
    }

    public Tuple3<DensePolynomial, DensePolynomial, DensePolynomial> multiplyVec(long numRows, long numCols, List<Scalar> z, ScalarFactory scalarFactory) {
        if (numRows != numCons) {
            throw new IllegalArgumentException("Invalid number of rows");
        }
        if (z.size() != numCols) {
            throw new IllegalArgumentException("Invalid number of cols");
        }
        if (numCols <= numVars) {
            throw new IllegalArgumentException("Invalid number of cols");
        }

        return new Tuple3<>(
                DensePolynomial.create(constraints.multiplyVec(numRows, numCols, z, scalarFactory)),
                DensePolynomial.create(vars.multiplyVec(numRows, numCols, z, scalarFactory)),
                DensePolynomial.create(inputs.multiplyVec(numRows, numCols, z, scalarFactory))
        );
    }

    private  List<Scalar> computeEvalTableSparse(SparseMatPolynomial m, List<Scalar> rx, long numRows, long numCols, ScalarFactory scalarFactory) {
        List<Scalar> evals = new ArrayList<>();
        for (int i = 0; i < numCols; i++) {
            evals.add(scalarFactory.zero());
        }

        for (int i = 0; i < m.getM().size(); i++) {
            SparseMatEntry entry = m.get(i);
            Scalar prev = evals.get((int)entry.getCol());
            Scalar e = rx.get((int)entry.getRow()).multiply(entry.getVal());
            evals.set((int)entry.getCol(), prev.add(e));
        }

        return evals;
    }

    public Tuple3<List<Scalar>, List<Scalar>, List<Scalar>> computeEvalTableSparse(long numRows, long numCols, List<Scalar> evals, ScalarFactory scalarFactory) {
        if (numRows != numCons || numCols < numVars) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        List<Scalar> evalsA = computeEvalTableSparse(constraints, evals, numRows, numCols, scalarFactory);
        List<Scalar> evalsB = computeEvalTableSparse(vars, evals, numRows, numCols, scalarFactory);
        List<Scalar> evalsC = computeEvalTableSparse(inputs, evals, numRows, numCols, scalarFactory);
        return new Tuple3<>(
                evalsA,
                evalsB,
                evalsC
        );
    }
}
