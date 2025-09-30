package com.weavechain.zk.spartan.commit;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class R1CSCommitment {

    private final long numCons;

    private final long numVars;

    private final long numInputs;

    private final SparseMatPolyCommitment comm;
}
