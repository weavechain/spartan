package com.weavechain.zk.spartan.generators;

import com.weavechain.curves.PointFactory;
import com.weavechain.zk.spartan.Utils;
import lombok.Getter;

@Getter
public class SNARKGens {

    private final R1CSGens gensR1csSat;

    private final R1CSCommitmentGens gensR1csEval;

    public SNARKGens(long numCons, long numVars, long numInputs, long numNonZeroEntries, byte[] genLabel, byte[] satLabel, PointFactory pointFactory) {
        long numVarsPadded = Utils.nextPow2(Math.max(numVars, numInputs + 1));

        gensR1csSat = new R1CSGens(satLabel, numCons, numVarsPadded, pointFactory);
        gensR1csEval = new R1CSCommitmentGens(genLabel, numCons, numVarsPadded, numInputs, numNonZeroEntries, pointFactory);
    }
}
