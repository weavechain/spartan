package com.weavechain.zk.spartan.generators;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class R1CSSumcheckGens {

    private final MultiCommitGens gens1;

    private final MultiCommitGens gens3;

    private final MultiCommitGens gens4;

    public R1CSSumcheckGens(byte[] label, MultiCommitGens gens) {
        gens1 = gens;
        gens3 = new MultiCommitGens(3, label, gens.getPointFactory());
        gens4 = new MultiCommitGens(4, label, gens.getPointFactory());
    }
}
