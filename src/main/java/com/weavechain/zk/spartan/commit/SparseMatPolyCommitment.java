package com.weavechain.zk.spartan.commit;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SparseMatPolyCommitment {

    private final long batchSize;

    private final long numMemCells;

    private final long numOps;

    private final PolyCommitment commCombOps;

    private final PolyCommitment commCombMem;
}
