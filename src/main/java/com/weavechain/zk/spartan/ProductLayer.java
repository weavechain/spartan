package com.weavechain.zk.spartan;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ProductLayer {

    private final ProductCircuit init;

    private final List<ProductCircuit> readVec;

    private final List<ProductCircuit> writeVec;

    private final ProductCircuit audit;
}
