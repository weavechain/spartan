package com.weavechain.zk.spartan;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class R1CS {

    private final long numCons;

    private final long numVars;

    private final long numInputs;

    private final long numNonZeroEntries;

    private final R1CSInstance inst;

    private final Assignment varsAssignment;

    private final Assignment inputsAssignment;
}
