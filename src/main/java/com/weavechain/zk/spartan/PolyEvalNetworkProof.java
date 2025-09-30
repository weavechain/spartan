package com.weavechain.zk.spartan;

import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.DerefsCommitment;
import com.weavechain.zk.spartan.commit.SparseMatPolyCommitment;
import com.weavechain.zk.spartan.generators.SparseMatPolyCommitmentGens;
import com.weavechain.zk.spartan.util.Tuple3;
import com.weavechain.zk.spartan.util.Tuple4;
import com.weavechain.zk.spartan.util.Tuple5;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Getter
@AllArgsConstructor
public class PolyEvalNetworkProof {

    public static String PROTOCOL_NAME = "Sparse polynomial evaluation network proof";

    private final ProductLayerProof proofProdLayer;

    private final HashLayerProof proofHashLayer;

    public static PolyEvalNetworkProof prove(
            PolyEvalNetwork network,
            MultiSparseMatPolynomialAsDense dense,
            Derefs derefs,
            List<Scalar> evals,
            SparseMatPolyCommitmentGens gens,
            Transcript transcript,
            RandomTape randomTape
    ) {
        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        Tuple3<ProductLayerProof, List<Scalar>, List<Scalar>> ppl = ProductLayerProof.prove(
                network.getRowLayers().getProdLayer(),
                network.getColLayers().getProdLayer(),
                dense,
                derefs,
                evals,
                transcript
        );
        ProductLayerProof proofProdLayer = ppl.getValue1();
        List<Scalar> randMem = ppl.getValue2();
        List<Scalar> randOps = ppl.getValue3();

        HashLayerProof proofHashLayer = HashLayerProof.prove(
                randMem,
                randOps,
                dense,
                derefs,
                gens,
                transcript,
                randomTape
        );

        return new PolyEvalNetworkProof(
                proofProdLayer,
                proofHashLayer
        );
    }

    public boolean verify(
            SparseMatPolyCommitment comm,
            DerefsCommitment commDerefs,
            List<Scalar> evals,
            SparseMatPolyCommitmentGens gens,
            List<Scalar> rx,
            List<Scalar> ry,
            List<Scalar> rMemCheck,
            long nz,
            Transcript transcript
    ) {
        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        int numInstances = evals.size();
        Scalar rHash = rMemCheck.get(0);
        Scalar rMultisetCheck = rMemCheck.get(1);

        long numOps = Utils.nextPow2(nz);
        long numCells = 1L << rx.size();

        Tuple5<List<Scalar>, List<Scalar>, List<Scalar>, List<Scalar>, List<Scalar>> res = proofProdLayer.verify(
                numOps,
                numCells,
                evals,
                transcript
        );
        List<Scalar> claimsMem = res.getValue1();
        List<Scalar> randMem = res.getValue2();
        List<Scalar> claimsOps = res.getValue3();
        List<Scalar> claimsDotp = res.getValue4();
        List<Scalar> randOps = res.getValue5();

        if (claimsMem.size() != 4 || claimsOps.size() != 4 * numInstances || claimsDotp.size() != 3 * numInstances) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        List<Scalar> claimsOpsRow = claimsOps.subList(0, 2 * numInstances);
        List<Scalar> claimsOpsCol = claimsOps.subList(2 * numInstances, claimsOps.size());
        List<Scalar> claimsOpsRowRead = claimsOpsRow.subList(0, numInstances);
        List<Scalar> claimsOpsRowWrite = claimsOpsRow.subList(numInstances, claimsOpsRow.size());
        List<Scalar> claimsOpsColRead = claimsOpsCol.subList(0, numInstances);
        List<Scalar> claimsOpsColWrite = claimsOpsCol.subList(numInstances, claimsOpsCol.size());

        return proofHashLayer.verify(
                new Pair<>(randMem, randOps),
                new Tuple4<>(
                        claimsMem.get(0),
                        claimsOpsRowRead,
                        claimsOpsRowWrite,
                        claimsMem.get(1)
                ),
                new Tuple4<>(
                        claimsMem.get(2),
                        claimsOpsColRead,
                        claimsOpsColWrite,
                        claimsMem.get(3)
                ),
                claimsDotp,
                comm,
                gens,
                commDerefs,
                rx,
                ry,
                rHash,
                rMultisetCheck,
                transcript
        );
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        proofProdLayer.pack(packer);
        proofHashLayer.pack(packer);
    }

    public static PolyEvalNetworkProof unpack(MessageUnpacker unpacker, PointFactory pointFactory, ScalarFactory scalarFactory) throws IOException {
        ProductLayerProof proofProdLayer = ProductLayerProof.unpack(unpacker, scalarFactory);
        HashLayerProof proofHashLayer = HashLayerProof.unpack(unpacker, pointFactory, scalarFactory);
        return new PolyEvalNetworkProof(proofProdLayer, proofHashLayer);
    }
}
