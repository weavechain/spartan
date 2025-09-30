package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PolyEvalNetwork {

    private final Layers rowLayers;

    private final Layers colLayers;

    public static PolyEvalNetwork create(
            MultiSparseMatPolynomialAsDense dense,
            Derefs derefs,
            List<Scalar> memRx,
            List<Scalar> memRy,
            Pair<Scalar, Scalar> rMemCheck,
            ScalarFactory scalarFactory
    ) {
        Layers rowLayers = Layers.create(memRx, dense.getRow(), derefs.getRowOpsVal(), rMemCheck, scalarFactory);
        Layers colLayers = Layers.create(memRy, dense.getCol(), derefs.getColOpsVal(), rMemCheck, scalarFactory);

        return new PolyEvalNetwork(
                rowLayers,
                colLayers
        );
    }
}
