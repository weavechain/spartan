package com.weavechain.zk.spartan.generators;

import com.weavechain.curves.PointFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DotProductProofGens {

    private final int n;

    private final MultiCommitGens gensN;

    private final MultiCommitGens gens1;

    public DotProductProofGens(int n, byte[] label, PointFactory pointFactory) {
        this.n = n;

        MultiCommitGens g = new MultiCommitGens(n + 1, label, pointFactory);
        gensN = new MultiCommitGens(n, g.getG().subList(0, n), g.getH(), pointFactory);
        gens1 = new MultiCommitGens(1, g.getG().subList(n, n + 1), g.getH(), pointFactory);
    }
}
