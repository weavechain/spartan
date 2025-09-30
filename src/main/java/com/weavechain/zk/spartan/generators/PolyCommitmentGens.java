package com.weavechain.zk.spartan.generators;

import com.weavechain.curves.PointFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PolyCommitmentGens {

    private final DotProductProofGens gens;

    public PolyCommitmentGens(long numVars, byte[] label, PointFactory pointFactory) {
        int size = 1 << (int)(numVars - numVars / 2);
        gens = new DotProductProofGens(size, label, pointFactory);
    }
}
