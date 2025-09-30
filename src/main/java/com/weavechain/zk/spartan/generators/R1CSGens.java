package com.weavechain.zk.spartan.generators;

import com.weavechain.curves.PointFactory;
import com.weavechain.zk.spartan.Utils;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class R1CSGens {

    private final R1CSSumcheckGens gensSc;

    private final PolyCommitmentGens gensPc;

    public R1CSGens(byte[] label, long numCons, long numVars, PointFactory pointFactory) {
        long numPolyVars = Utils.log2(numVars);
        gensPc = new PolyCommitmentGens(numPolyVars, label, pointFactory);
        gensSc = new R1CSSumcheckGens(label, gensPc.getGens().getGens1());
    }
}
