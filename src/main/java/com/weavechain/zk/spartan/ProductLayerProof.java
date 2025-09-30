package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.util.Tuple3;
import com.weavechain.zk.spartan.util.Tuple4;
import com.weavechain.zk.spartan.util.Tuple5;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@AllArgsConstructor
public class ProductLayerProof {

    private static final String PROTOCOL_NAME = "Sparse polynomial product layer proof";

    private final Tuple4<Scalar, List<Scalar>, List<Scalar>, Scalar> evalRow;

    private final Tuple4<Scalar, List<Scalar>, List<Scalar>, Scalar> evalCol;

    private final Pair<List<Scalar>, List<Scalar>> evalVal;

    private final ProductCircuitEvalProofBatched proofMem;

    private final ProductCircuitEvalProofBatched proofOps;

    public static Tuple3<ProductLayerProof, List<Scalar>, List<Scalar>> prove(
            ProductLayer rowProdLayer,
            ProductLayer colProdLayer,
            MultiSparseMatPolynomialAsDense dense,
            Derefs derefs,
            List<Scalar> eval,
            Transcript transcript
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        Scalar ONE = scalarFactory.one();

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        Scalar rowEvalInit = rowProdLayer.getInit().evaluate();
        Scalar rowEvalAudit = rowProdLayer.getAudit().evaluate();
        List<Scalar> rowEvalRead = new ArrayList<>();
        for (ProductCircuit p : rowProdLayer.getReadVec()) {
            rowEvalRead.add(p.evaluate());
        }
        List<Scalar> rowEvalWrite = new ArrayList<>();
        for (ProductCircuit p : rowProdLayer.getWriteVec()) {
            rowEvalWrite.add(p.evaluate());
        }

        Scalar rowRS = ONE;
        for (Scalar s : rowEvalRead) {
            rowRS = rowRS.multiply(s);
        }
        Scalar rowWS = ONE;
        for (Scalar s : rowEvalWrite) {
            rowWS = rowWS.multiply(s);
        }
        if (!rowEvalInit.multiply(rowWS).equals(rowRS.multiply(rowEvalAudit))) {
            throw new IllegalArgumentException("Check failed");
        }

        transcript.appendScalar("claim_row_eval_init".getBytes(StandardCharsets.UTF_8), rowEvalInit);
        transcript.appendScalars("claim_row_eval_read".getBytes(StandardCharsets.UTF_8), rowEvalRead);
        transcript.appendScalars("claim_row_eval_write".getBytes(StandardCharsets.UTF_8), rowEvalWrite);
        transcript.appendScalar("claim_row_eval_audit".getBytes(StandardCharsets.UTF_8), rowEvalAudit);

        Scalar colEvalInit = colProdLayer.getInit().evaluate();
        Scalar colEvalAudit = colProdLayer.getAudit().evaluate();
        List<Scalar> colEvalRead = new ArrayList<>();
        for (ProductCircuit p : colProdLayer.getReadVec()) {
            colEvalRead.add(p.evaluate());
        }
        List<Scalar> colEvalWrite = new ArrayList<>();
        for (ProductCircuit p : colProdLayer.getWriteVec()) {
            colEvalWrite.add(p.evaluate());
        }

        Scalar colRS = ONE;
        for (Scalar s : colEvalRead) {
            colRS = colRS.multiply(s);
        }
        Scalar colWS = ONE;
        for (Scalar s : colEvalWrite) {
            colWS = colWS.multiply(s);
        }
        if (!colEvalInit.multiply(colWS).equals(colRS.multiply(colEvalAudit))) {
            throw new IllegalArgumentException("Check failed");
        }

        transcript.appendScalar("claim_col_eval_init".getBytes(StandardCharsets.UTF_8), colEvalInit);
        transcript.appendScalars("claim_col_eval_read".getBytes(StandardCharsets.UTF_8), colEvalRead);
        transcript.appendScalars("claim_col_eval_write".getBytes(StandardCharsets.UTF_8), colEvalWrite);
        transcript.appendScalar("claim_col_eval_audit".getBytes(StandardCharsets.UTF_8), colEvalAudit);

        // prepare dotproduct circuit for batching then with ops-related product circuits
        if (eval.size() != derefs.getRowOpsVal().size() || eval.size() != derefs.getColOpsVal().size() || eval.size() != dense.getVal().size()) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        List<DotProductCircuit> dotpCircuitLeftVec = new ArrayList<>();
        List<DotProductCircuit> dotpCircuitRightVec = new ArrayList<>();
        List<Scalar> evalDotpLeftVec = new ArrayList<>();
        List<Scalar> evalDotpRightVec = new ArrayList<>();

        for (int i = 0; i < derefs.getRowOpsVal().size(); i++) {
            DensePolynomial left = derefs.getRowOpsVal().get(i);
            DensePolynomial right = derefs.getColOpsVal().get(i);
            DensePolynomial weights = dense.getVal().get(i);

            DotProductCircuit dotpCircuitLeft = new DotProductCircuit(left.half(0), right.half(0), weights.half(0));
            DotProductCircuit dotpCircuitRight = new DotProductCircuit(left.half(1), right.half(1), weights.half(1));

            Scalar evalDotpLeft = dotpCircuitLeft.evaluate(scalarFactory);
            Scalar evalDotpRight = dotpCircuitRight.evaluate(scalarFactory);
            transcript.appendScalar("claim_eval_dotp_left".getBytes(StandardCharsets.UTF_8), evalDotpLeft);
            transcript.appendScalar("claim_eval_dotp_right".getBytes(StandardCharsets.UTF_8), evalDotpRight);
            if (!evalDotpLeft.add(evalDotpRight).equals(eval.get(i))) {
                throw new IllegalArgumentException("Invalid check");
            }
            evalDotpLeftVec.add(evalDotpLeft);
            evalDotpRightVec.add(evalDotpRight);

            dotpCircuitLeftVec.add(dotpCircuitLeft);
            dotpCircuitRightVec.add(dotpCircuitRight);
        }

        if (rowProdLayer.getReadVec().size() != 3) {
            throw new IllegalArgumentException("Unsupported batch size");
        }

        List<ProductCircuit> rowReadA = rowProdLayer.getReadVec().subList(0, 1);
        List<ProductCircuit> rowReadB = rowProdLayer.getReadVec().subList(1, 2);
        List<ProductCircuit> rowReadC = rowProdLayer.getReadVec().subList(2, rowProdLayer.getReadVec().size());
        List<ProductCircuit> rowWriteA = rowProdLayer.getWriteVec().subList(0, 1);
        List<ProductCircuit> rowWriteB = rowProdLayer.getWriteVec().subList(1, 2);
        List<ProductCircuit> rowWriteC = rowProdLayer.getWriteVec().subList(2, rowProdLayer.getWriteVec().size());
        List<ProductCircuit> colReadA = colProdLayer.getReadVec().subList(0, 1);
        List<ProductCircuit> colReadB = colProdLayer.getReadVec().subList(1, 2);
        List<ProductCircuit> colReadC = colProdLayer.getReadVec().subList(2, colProdLayer.getReadVec().size());
        List<ProductCircuit> colWriteA = colProdLayer.getWriteVec().subList(0, 1);
        List<ProductCircuit> colWriteB = colProdLayer.getWriteVec().subList(1, 2);
        List<ProductCircuit> colWriteC = colProdLayer.getWriteVec().subList(2, colProdLayer.getWriteVec().size());
        List<DotProductCircuit> dotpLeftA = dotpCircuitLeftVec.subList(0, 1);
        List<DotProductCircuit> dotpLeftB = dotpCircuitLeftVec.subList(1, 2);
        List<DotProductCircuit> dotpLeftC = dotpCircuitLeftVec.subList(2, dotpCircuitLeftVec.size());
        List<DotProductCircuit> dotpRightA = dotpCircuitRightVec.subList(0, 1);
        List<DotProductCircuit> dotpRightB = dotpCircuitRightVec.subList(1, 2);
        List<DotProductCircuit> dotpRightC = dotpCircuitRightVec.subList(2, dotpCircuitRightVec.size());

        Pair<ProductCircuitEvalProofBatched, List<Scalar>> pops = ProductCircuitEvalProofBatched.prove(
                List.of(
                        rowReadA.get(0),
                        rowReadB.get(0),
                        rowReadC.get(0),
                        rowWriteA.get(0),
                        rowWriteB.get(0),
                        rowWriteC.get(0),
                        colReadA.get(0),
                        colReadB.get(0),
                        colReadC.get(0),
                        colWriteA.get(0),
                        colWriteB.get(0),
                        colWriteC.get(0)
                ),
                List.of(
                        dotpLeftA.get(0),
                        dotpRightA.get(0),
                        dotpLeftB.get(0),
                        dotpRightB.get(0),
                        dotpLeftC.get(0),
                        dotpRightC.get(0)
                ),
                transcript
        );
        ProductCircuitEvalProofBatched proofOps = pops.getValue1();
        List<Scalar> randOps = pops.getValue2();

        Pair<ProductCircuitEvalProofBatched, List<Scalar>> pmem = ProductCircuitEvalProofBatched.prove(
                List.of(
                        rowProdLayer.getInit(),
                        rowProdLayer.getAudit(),
                        colProdLayer.getInit(),
                        colProdLayer.getAudit()
                ),
                Collections.emptyList(),
                transcript
        );
        ProductCircuitEvalProofBatched proofMem = pmem.getValue1();
        List<Scalar> randMem = pmem.getValue2();

        ProductLayerProof productLayerProof = new ProductLayerProof(
                new Tuple4<>(rowEvalInit, rowEvalRead, rowEvalWrite, rowEvalAudit),
                new Tuple4<>(colEvalInit, colEvalRead, colEvalWrite, colEvalAudit),
                new Pair<>(evalDotpLeftVec, evalDotpRightVec),
                proofMem,
                proofOps
        );

        return new Tuple3<>(
                productLayerProof,
                randMem,
                randOps
        );
    }

    public Tuple5<List<Scalar>, List<Scalar>, List<Scalar>, List<Scalar>, List<Scalar>> verify(
            long numOps,
            long numCells,
            List<Scalar> eval,
            Transcript transcript
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        Scalar ONE = scalarFactory.one();

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        long numInstances = eval.size();

        Scalar rowEvalInit = evalRow.getValue1();
        List<Scalar> rowEvalRead = evalRow.getValue2();
        List<Scalar> rowEvalWrite = evalRow.getValue3();
        Scalar rowEvalAudit = evalRow.getValue4();
        if (rowEvalRead.size() != numInstances || rowEvalWrite.size() != numInstances) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        Scalar rowRS = ONE;
        for (Scalar s : rowEvalRead) {
            rowRS = rowRS.multiply(s);
        }
        Scalar rowWS = ONE;
        for (Scalar s : rowEvalWrite) {
            rowWS = rowWS.multiply(s);
        }
        if (!rowEvalInit.multiply(rowWS).equals(rowRS.multiply(rowEvalAudit))) {
            return null;
        }

        transcript.appendScalar("claim_row_eval_init".getBytes(StandardCharsets.UTF_8), rowEvalInit);
        transcript.appendScalars("claim_row_eval_read".getBytes(StandardCharsets.UTF_8), rowEvalRead);
        transcript.appendScalars("claim_row_eval_write".getBytes(StandardCharsets.UTF_8), rowEvalWrite);
        transcript.appendScalar("claim_row_eval_audit".getBytes(StandardCharsets.UTF_8), rowEvalAudit);

        Scalar colEvalInit = evalCol.getValue1();
        List<Scalar> colEvalRead = evalCol.getValue2();
        List<Scalar> colEvalWrite = evalCol.getValue3();
        Scalar colEvalAudit = evalCol.getValue4();
        if (colEvalRead.size() != numInstances || colEvalWrite.size() != numInstances) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        Scalar colRS = ONE;
        for (Scalar s : colEvalRead) {
            colRS = colRS.multiply(s);
        }
        Scalar colWS = ONE;
        for (Scalar s : colEvalWrite) {
            colWS = colWS.multiply(s);
        }
        if (!colEvalInit.multiply(colWS).equals(colRS.multiply(colEvalAudit))) {
            return null;
        }

        transcript.appendScalar("claim_col_eval_init".getBytes(StandardCharsets.UTF_8), colEvalInit);
        transcript.appendScalars("claim_col_eval_read".getBytes(StandardCharsets.UTF_8), colEvalRead);
        transcript.appendScalars("claim_col_eval_write".getBytes(StandardCharsets.UTF_8), colEvalWrite);
        transcript.appendScalar("claim_col_eval_audit".getBytes(StandardCharsets.UTF_8), colEvalAudit);

        List<Scalar> evalDotpLeft = evalVal.getValue1();
        List<Scalar> evalDotpRight = evalVal.getValue2();
        if (evalDotpLeft.size() != numInstances || evalDotpRight.size() != numInstances) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        List<Scalar> claimsDotpCircuit = new ArrayList<>();
        for (int i = 0; i < numInstances; i++) {
            if (!evalDotpLeft.get(i).add(evalDotpRight.get(i)).equals(eval.get(i))) {
                return null;
            }

            transcript.appendScalar("claim_eval_dotp_left".getBytes(StandardCharsets.UTF_8), evalDotpLeft.get(i));
            transcript.appendScalar("claim_eval_dotp_right".getBytes(StandardCharsets.UTF_8), evalDotpRight.get(i));

            claimsDotpCircuit.add(evalDotpLeft.get(i));
            claimsDotpCircuit.add(evalDotpRight.get(i));
        }

        List<Scalar> claimsProdCircuit = new ArrayList<>();
        claimsProdCircuit.addAll(rowEvalRead);
        claimsProdCircuit.addAll(rowEvalWrite);
        claimsProdCircuit.addAll(colEvalRead);
        claimsProdCircuit.addAll(colEvalWrite);

        Tuple3<List<Scalar>, List<Scalar>, List<Scalar>> pops = proofOps.verify(
                claimsProdCircuit,
                claimsDotpCircuit,
                numOps,
                transcript
        );

        Tuple3<List<Scalar>, List<Scalar>, List<Scalar>> pmem = proofMem.verify(
                List.of(rowEvalInit, rowEvalAudit, colEvalInit, colEvalAudit),
                new ArrayList<>(),
                numCells,
                transcript
        );

        return new Tuple5<>(
                pmem.getValue1(),
                pmem.getValue3(),
                pops.getValue1(),
                pops.getValue2(),
                pops.getValue3()
        );
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        Serialization.serialize(packer, evalRow.getValue1());
        Serialization.serializeScalarList(packer, evalRow.getValue2());
        Serialization.serializeScalarList(packer, evalRow.getValue3());
        Serialization.serialize(packer, evalRow.getValue4());
        Serialization.serialize(packer, evalCol.getValue1());
        Serialization.serializeScalarList(packer, evalCol.getValue2());
        Serialization.serializeScalarList(packer, evalCol.getValue3());
        Serialization.serialize(packer, evalCol.getValue4());
        Serialization.serializeScalarList(packer, evalVal.getValue1());
        Serialization.serializeScalarList(packer, evalVal.getValue2());
        proofMem.pack(packer);
        proofOps.pack(packer);
    }

    public static ProductLayerProof unpack(MessageUnpacker unpacker, ScalarFactory scalarFactory) throws IOException {
        Tuple4<Scalar, List<Scalar>, List<Scalar>, Scalar> evalRow = new Tuple4<>(
                Serialization.deserializeScalar(unpacker, scalarFactory),
                Serialization.deserializeScalarList(unpacker, scalarFactory),
                Serialization.deserializeScalarList(unpacker, scalarFactory),
                Serialization.deserializeScalar(unpacker, scalarFactory)
        );
        Tuple4<Scalar, List<Scalar>, List<Scalar>, Scalar> evalCol = new Tuple4<>(
                Serialization.deserializeScalar(unpacker, scalarFactory),
                Serialization.deserializeScalarList(unpacker, scalarFactory),
                Serialization.deserializeScalarList(unpacker, scalarFactory),
                Serialization.deserializeScalar(unpacker, scalarFactory)
        );
        Pair<List<Scalar>, List<Scalar>> evalVal = new Pair<>(
                Serialization.deserializeScalarList(unpacker, scalarFactory),
                Serialization.deserializeScalarList(unpacker, scalarFactory)
        );
        ProductCircuitEvalProofBatched proofMem = ProductCircuitEvalProofBatched.unpack(unpacker, scalarFactory);
        ProductCircuitEvalProofBatched proofOps = ProductCircuitEvalProofBatched.unpack(unpacker, scalarFactory);
        return new ProductLayerProof(evalRow, evalCol, evalVal, proofMem, proofOps);
    }
}
