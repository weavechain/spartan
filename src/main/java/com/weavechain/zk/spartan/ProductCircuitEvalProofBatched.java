package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.util.Function3;
import com.weavechain.zk.spartan.util.Tuple3;
import com.weavechain.zk.spartan.util.Tuple4;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class ProductCircuitEvalProofBatched {

    private final List<LayerProofBatched> proof;

    private final Tuple3<List<Scalar>, List<Scalar>, List<Scalar>> claimsDotp;

    public static Pair<ProductCircuitEvalProofBatched, List<Scalar>> prove(
            List<ProductCircuit> prodCircuitVec,
            List<DotProductCircuit> dotpCircuitVec,
            Transcript transcript
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        Scalar ZERO = scalarFactory.zero();

        List<LayerProofBatched> proofLayers = new ArrayList<>();
        List<Scalar> claimsToVerify = new ArrayList<>();
        for (ProductCircuit p : prodCircuitVec) {
            claimsToVerify.add(p.evaluate());
        }

        Tuple3<List<Scalar>, List<Scalar>, List<Scalar>> claimsDotpFinal = new Tuple3<>(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );

        int numLayers = prodCircuitVec.get(0).getLeftVec().size();

        List<Scalar> rand = new ArrayList<>();
        for (int layerId = numLayers - 1; layerId >= 0; layerId--) {
            long len = prodCircuitVec.get(0).getLeftVec().get(layerId).getLen() +  prodCircuitVec.get(0).getRightVec().get(layerId).getLen();

            DensePolynomial polyCPar = DensePolynomial.create(new EqPolynomial(rand).evals(scalarFactory));
            if (polyCPar.getLen() != len / 2) {
                throw new IllegalArgumentException("Invalid sizes");
            }

            long numRoundsProd = Utils.log2(polyCPar.getLen());
            Function3<Scalar, Scalar, Scalar, Scalar> combFn = (polyAComp, polyBComp, polyCComp) -> polyAComp.multiply(polyBComp).multiply(polyCComp);

            List<DensePolynomial> polyABatchedPar = new ArrayList<>();
            List<DensePolynomial> polyBBatchedPar = new ArrayList<>();
            for (ProductCircuit p : prodCircuitVec) {
                polyABatchedPar.add(p.getLeftVec().get(layerId));
                polyBBatchedPar.add(p.getRightVec().get(layerId));
            }

            List<DensePolynomial> polyABatchedSeq = new ArrayList<>();
            List<DensePolynomial> polyBBatchedSeq = new ArrayList<>();
            List<DensePolynomial> polyCBatchedSeq = new ArrayList<>();
            if (layerId == 0 && !dotpCircuitVec.isEmpty()) {
                for (DotProductCircuit item : dotpCircuitVec) {
                    claimsToVerify.add(item.evaluate(scalarFactory));
                    if (item.getLeft().getLen() != len / 2 || item.getRight().getLen() != len / 2 || item.getWeight().getLen() != len / 2) {
                        throw new IllegalArgumentException("Invalid sizes");
                    }
                    polyABatchedSeq.add(item.getLeft());
                    polyBBatchedSeq.add(item.getRight());
                    polyCBatchedSeq.add(item.getWeight());
                }
            }

            List<Scalar> coeffVec = transcript.challengeVector("rand_coeffs_next_layer".getBytes(StandardCharsets.UTF_8), claimsToVerify.size());
            Scalar claim = ZERO;
            for (int i = 0; i < claimsToVerify.size(); i++) {
                claim = claim.add(claimsToVerify.get(i).multiply(coeffVec.get(i)));
            }

            Tuple4<SumcheckInstanceProof,
                    List<Scalar>,
                    Tuple3<List<Scalar>, List<Scalar>, Scalar>,
                    Tuple3<List<Scalar>, List<Scalar>, List<Scalar>>
                > psi = SumcheckInstanceProof.proveCubicBatched(
                    claim,
                    numRoundsProd,
                    polyABatchedPar,
                    polyBBatchedPar,
                    polyCPar,
                    polyABatchedSeq,
                    polyBBatchedSeq,
                    polyCBatchedSeq,
                    coeffVec,
                    combFn,
                    transcript
            );
            SumcheckInstanceProof proof = psi.getValue1();
            List<Scalar> randProd = psi.getValue2();
            Tuple3<List<Scalar>, List<Scalar>, Scalar> claimsProd = psi.getValue3();
            Tuple3<List<Scalar>, List<Scalar>, List<Scalar>> claimsDotp = psi.getValue4();

            List<Scalar> claimsProdLeft = psi.getValue3().getValue1();
            List<Scalar> claimsProdRight = psi.getValue3().getValue2();
            for (int i = 0; i < prodCircuitVec.size(); i++) {
                transcript.appendScalar("claim_prod_left".getBytes(StandardCharsets.UTF_8), claimsProdLeft.get(i));
                transcript.appendScalar("claim_prod_right".getBytes(StandardCharsets.UTF_8), claimsProdRight.get(i));
            }

            if (layerId == 0 && !dotpCircuitVec.isEmpty()) {
                List<Scalar> claimsDotpLeft = claimsDotp.getValue1();
                List<Scalar> claimsDotpRight = claimsDotp.getValue2();
                List<Scalar> claimsDotpWeight = claimsDotp.getValue3();

                for (int i = 0; i < dotpCircuitVec.size(); i++) {
                    transcript.appendScalar("claim_dotp_left".getBytes(StandardCharsets.UTF_8), claimsDotpLeft.get(i));
                    transcript.appendScalar("claim_dotp_right".getBytes(StandardCharsets.UTF_8), claimsDotpRight.get(i));
                    transcript.appendScalar("claim_dotp_weight".getBytes(StandardCharsets.UTF_8), claimsDotpWeight.get(i));
                }
                claimsDotpFinal = claimsDotp;
            }

            Scalar rLayer = transcript.challengeScalar("challenge_r_layer".getBytes(StandardCharsets.UTF_8));
            claimsToVerify = new ArrayList<>();
            for (int i = 0; i < prodCircuitVec.size(); i++) {
                claimsToVerify.add(claimsProdLeft.get(i).add(rLayer.multiply(claimsProdRight.get(i).subtract(claimsProdLeft.get(i)))));
            }

            List<Scalar> ext = new ArrayList<>();
            ext.add(rLayer);
            ext.addAll(randProd);
            rand = ext;

            proofLayers.add(new LayerProofBatched(
                    proof,
                    claimsProdLeft,
                    claimsProdRight
            ));
        }

        return new Pair<>(
                new ProductCircuitEvalProofBatched(
                        proofLayers,
                        claimsDotpFinal
                ),
                rand
        );
    }

    public Tuple3<List<Scalar>, List<Scalar>, List<Scalar>> verify(
            List<Scalar> claimsProdVec,
            List<Scalar> claimsDotpVec,
            long len,
            Transcript transcript
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        Scalar ZERO = scalarFactory.zero();
        Scalar ONE = scalarFactory.one();

        long numLayers = Utils.log2(len);
        if (proof.size() != numLayers) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        List<Scalar> rand = new ArrayList<>();
        List<Scalar> claimsToVerify = new ArrayList<>(claimsProdVec);
        List<Scalar> claimsToVerifyDotp = new ArrayList<>();

        List<Scalar> claimsDotpLeft = claimsDotp.getValue1();
        List<Scalar> claimsDotpRight = claimsDotp.getValue2();
        List<Scalar> claimsDotpWeight = claimsDotp.getValue3();

        for (int k = 0; k < numLayers; k++) {
            int numRounds = k;

            if (k == numLayers - 1) {
                claimsToVerify.addAll(claimsDotpVec);
            }

            List<Scalar> coeffVec = transcript.challengeVector("rand_coeffs_next_layer".getBytes(StandardCharsets.UTF_8), claimsToVerify.size());

            Scalar claim = ZERO;
            for (int i = 0; i < claimsToVerify.size(); i++) {
                claim = claim.add(claimsToVerify.get(i).multiply(coeffVec.get(i)));
            }

            Pair<Scalar, List<Scalar>> pi = proof.get(k).verify(
                    claim,
                    numRounds,
                    3,
                    transcript
            );
            Scalar claimLast = pi.getValue1();
            List<Scalar> randProd = pi.getValue2();

            List<Scalar> claimsProdLeft = proof.get(k).getClaimsProdLeft();
            List<Scalar> claimsProdRight = proof.get(k).getClaimsProdRight();
            if (claimsProdLeft.size() != claimsProdVec.size() || claimsProdRight.size() != claimsProdVec.size()) {
                throw new IllegalArgumentException("Invalid sizes");
            }

            for (int i = 0; i < claimsProdVec.size(); i++) {
                transcript.appendScalar("claim_prod_left".getBytes(StandardCharsets.UTF_8), claimsProdLeft.get(i));
                transcript.appendScalar("claim_prod_right".getBytes(StandardCharsets.UTF_8), claimsProdRight.get(i));
            }
            if (rand.size() != randProd.size()) {
                throw new IllegalArgumentException("Invalid sizes");
            }

            Scalar eq = ONE;
            for (int i = 0; i < rand.size(); i++) {
                Scalar p = rand.get(i).multiply(randProd.get(i)).add(ONE.subtract(rand.get(i)).multiply(ONE.subtract(randProd.get(i))));
                eq = eq.multiply(p);
            }

            Scalar claimExpected = ZERO;
            for (int i = 0; i < claimsProdVec.size(); i++) {
                Scalar c = coeffVec.get(i).multiply(claimsProdLeft.get(i).multiply(claimsProdRight.get(i).multiply(eq)));
                claimExpected = claimExpected.add(c);
            }

            if (k == numLayers - 1) {
                int numProdInstances = claimsProdVec.size();

                for (int i = 0; i < claimsDotpLeft.size(); i++) {
                    transcript.appendScalar("claim_dotp_left".getBytes(StandardCharsets.UTF_8), claimsDotpLeft.get(i));
                    transcript.appendScalar("claim_dotp_right".getBytes(StandardCharsets.UTF_8), claimsDotpRight.get(i));
                    transcript.appendScalar("claim_dotp_weight".getBytes(StandardCharsets.UTF_8), claimsDotpWeight.get(i));

                    claimExpected = claimExpected.add(
                            coeffVec.get(i + numProdInstances)
                                    .multiply(claimsDotpLeft.get(i))
                                    .multiply(claimsDotpRight.get(i))
                                    .multiply(claimsDotpWeight.get(i))
                    );
                }
            }

            if (!claimExpected.equals(claimLast)) {
                return null;
            }

            Scalar rLayer = transcript.challengeScalar("challenge_r_layer".getBytes(StandardCharsets.UTF_8));

            claimsToVerify = new ArrayList<>();
            for (int i = 0; i < claimsProdLeft.size(); i++) {
                Scalar c = claimsProdLeft.get(i).add(rLayer.multiply(claimsProdRight.get(i).subtract(claimsProdLeft.get(i))));
                claimsToVerify.add(c);
            }

            if (k == numLayers - 1) {
                for (int i = 0; i < claimsDotpVec.size() / 2; i++) {
                    Scalar claimLeft = claimsDotpLeft.get(2 * i).add(rLayer.multiply(claimsDotpLeft.get(2 * i + 1).subtract(claimsDotpLeft.get(2 * i))));
                    Scalar claimRight = claimsDotpRight.get(2 * i).add(rLayer.multiply(claimsDotpRight.get(2 * i + 1).subtract(claimsDotpRight.get(2 * i))));
                    Scalar claimWeight = claimsDotpWeight.get(2 * i).add(rLayer.multiply(claimsDotpWeight.get(2 * i + 1).subtract(claimsDotpWeight.get(2 * i))));

                    claimsToVerifyDotp.add(claimLeft);
                    claimsToVerifyDotp.add(claimRight);
                    claimsToVerifyDotp.add(claimWeight);
                }
            }

            List<Scalar> ext = new ArrayList<>();
            ext.add(rLayer);
            ext.addAll(randProd);
            rand = ext;
        }

        return new Tuple3<>(
                claimsToVerify,
                claimsToVerifyDotp,
                rand
        );
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        packer.packInt(proof.size());
        for (LayerProofBatched p : proof) {
            p.pack(packer);
        }
        Serialization.serializeScalarList(packer, claimsDotp.getValue1());
        Serialization.serializeScalarList(packer, claimsDotp.getValue2());
        Serialization.serializeScalarList(packer, claimsDotp.getValue3());
    }

    public static ProductCircuitEvalProofBatched unpack(MessageUnpacker unpacker, ScalarFactory scalarFactory) throws IOException {
        int len = unpacker.unpackInt();
        List<LayerProofBatched> proof = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            proof.add(LayerProofBatched.unpack(unpacker, scalarFactory));
        }
        Tuple3<List<Scalar>, List<Scalar>, List<Scalar>> claimsDotp = new Tuple3<>(
                Serialization.deserializeScalarList(unpacker, scalarFactory),
                Serialization.deserializeScalarList(unpacker, scalarFactory),
                Serialization.deserializeScalarList(unpacker, scalarFactory)
        );
        return new ProductCircuitEvalProofBatched(proof, claimsDotp);
    }
}
