package com.weavechain.zk.spartan.generators;

import com.weavechain.curves.PointFactory;
import com.weavechain.zk.spartan.Utils;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SparseMatPolyCommitmentGens {

    private final PolyCommitmentGens gensOps;

    private final PolyCommitmentGens gensMem;

    private final PolyCommitmentGens gensDerefs;

    public SparseMatPolyCommitmentGens(byte[] label, long numVarsX, long numVarsY, long numNonZeroEntries, int batchSize, PointFactory pointFactory) {
        long num = Utils.log2(Utils.nextPow2(numNonZeroEntries));
        long numVarOps = num + Utils.log2(Utils.nextPow2(batchSize * 5L));
        long numVarMem = Math.max(numVarsX, numVarsY) + 1;
        long numVarDerefs = Utils.log2(Utils.nextPow2(num)) + Utils.log2(Utils.nextPow2(batchSize * 2L));

        gensOps = new PolyCommitmentGens(numVarOps, label, pointFactory);
        gensMem = new PolyCommitmentGens(numVarMem, label, pointFactory);
        gensDerefs = new PolyCommitmentGens(numVarDerefs, label, pointFactory);
    }
}
