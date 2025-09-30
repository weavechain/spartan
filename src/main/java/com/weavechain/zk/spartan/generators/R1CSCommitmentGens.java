package com.weavechain.zk.spartan.generators;

import com.weavechain.curves.PointFactory;
import com.weavechain.zk.spartan.Utils;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class R1CSCommitmentGens {

    private final SparseMatPolyCommitmentGens gens;

    public R1CSCommitmentGens(byte[] label, long numCons, long numVars, long numInputs, long numNonZeroEntries, PointFactory pointFactory) {
        long numPolyVarsX = Utils.log2(numCons);
        long numPolyVarsY = Utils.log2(2 * numVars);

        int batchSize = 3;
        gens = new SparseMatPolyCommitmentGens(label, numPolyVarsX, numPolyVarsY, numNonZeroEntries, batchSize, pointFactory);
    }
}
