package com.weavechain.zk.spartan;

import com.weavechain.curves.Point;
import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.Commitments;
import com.weavechain.zk.spartan.generators.MultiCommitGens;
import com.weavechain.zk.spartan.util.Function4;
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
import java.util.function.BiFunction;

@Getter
@AllArgsConstructor
public class ZKSumcheckInstanceProof {

    private final List<Point> commPolys;

    private final List<Point> commEvals;

    private final List<DotProductProof> proofs;

    public static Tuple4<ZKSumcheckInstanceProof, List<Scalar>, List<Scalar>, Scalar> proveQuad(
            Scalar claim,
            Scalar blindClaim,
            long numRounds,
            DensePolynomial polyA,
            DensePolynomial polyB,
            BiFunction<Scalar, Scalar, Scalar> combFunc,
            MultiCommitGens gens1,
            MultiCommitGens gensn,
            Transcript transcript,
            RandomTape randomTape
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        PointFactory pointFactory = transcript.getPointFactory();
        Scalar ZERO = scalarFactory.zero();
        Scalar ONE = scalarFactory.one();

        List<Scalar> blindsPoly = randomTape.randomVector("blinds_poly".getBytes(StandardCharsets.UTF_8), numRounds);
        List<Scalar> blindsEvals = randomTape.randomVector("blinds_evals".getBytes(StandardCharsets.UTF_8), numRounds);

        Scalar claimPerRound = claim;
        Point commClaimPerRound = Commitments.commit(claimPerRound, blindClaim, gens1);

        List<Scalar> r = new ArrayList<>();
        List<Point> commPolys = new ArrayList<>();
        List<Point> commEvals = new ArrayList<>();
        List<DotProductProof> proofs = new ArrayList<>();

        for (int j = 0; j < numRounds; j++) {
            Scalar evalPoint0 = ZERO;
            Scalar evalPoint2 = ZERO;

            int len = (int)polyA.getLen() / 2;
            for (int i = 0; i < len; i++) {
                evalPoint0 = evalPoint0.add(combFunc.apply(polyA.get(i), polyB.get(i)));

                Scalar polyABoundPoint = polyA.get(len + i).add(polyA.get(len + i)).subtract(polyA.get(i));
                Scalar polyBBoundPoint = polyB.get(len + i).add(polyB.get(len + i)).subtract(polyB.get(i));
                evalPoint2 = evalPoint2.add(combFunc.apply(polyABoundPoint, polyBBoundPoint));
            }

            List<Scalar> evals = List.of(
                    evalPoint0,
                    claimPerRound.subtract(evalPoint0),
                    evalPoint2
            );
            UniPoly poly = UniPoly.fromEvals(evals, scalarFactory);
            Point commPoly = poly.commit(gensn, blindsPoly.get(j), pointFactory);

            transcript.appendPoint("comm_poly".getBytes(StandardCharsets.UTF_8), commPoly);
            commPolys.add(commPoly);

            Scalar rj = transcript.challengeScalar("challenge_nextround".getBytes(StandardCharsets.UTF_8));

            polyA.boundPolyVarTop(rj);
            polyB.boundPolyVarTop(rj);

            Scalar eval = poly.evaluate(rj);
            Point commEval = Commitments.commit(eval, blindsEvals.get(j), gens1);

            // we need to prove the following under homomorphic commitments:
            // (1) poly(0) + poly(1) = claim_per_round
            // (2) poly(r_j) = eval

            // Our technique is to leverage dot product proofs:
            // (1) we can prove: <poly_in_coeffs_form, (2, 1, 1, 1)> = claim_per_round
            // (2) we can prove: <poly_in_coeffs_form, (1, r_j, r^2_j, ..) = eval
            // for efficiency we batch them using random weights

            transcript.appendPoint("comm_claim_per_round".getBytes(StandardCharsets.UTF_8), commClaimPerRound);
            transcript.appendPoint("comm_eval".getBytes(StandardCharsets.UTF_8), commEval);

            List<Scalar> w = transcript.challengeVector("combine_two_claims_to_one".getBytes(StandardCharsets.UTF_8), 2);

            Scalar target = w.get(0).multiply(claimPerRound).add(w.get(1).multiply(eval));

            List<Point> bases = List.of(commClaimPerRound, commEval) ;
            Point commTarget = pointFactory.multiscalarMul(w, bases);
            Scalar blindSc = j == 0 ? blindClaim : blindsEvals.get(j - 1);
            Scalar blindEval = blindsEvals.get(j);

            Scalar blind = w.get(0).multiply(blindSc).add(w.get(1).multiply(blindEval));
            Point ct = Commitments.commit(target, blind, gens1);
            if (!ct.equals(commTarget)) {
                throw new IllegalArgumentException("Commitment not matching");
            }

            // the vector to use to decommit for sum-check test
            List<Scalar> aSc = new ArrayList<>();
            for (int i = 0; i < poly.degree() + 1; i++) {
                aSc.add(i == 0 ? ONE.add(ONE) : ONE);
            }

            // the vector to use to decommit for evaluation
            List<Scalar> aEval = new ArrayList<>();
            for (int i = 0; i < poly.degree() + 1; i++) {
                aEval.add(ONE);
            }
            for (int i = 1; i < poly.degree() + 1; i++) {
                aEval.set(i, aEval.get(i - 1).multiply(rj));
            }

            List<Scalar> a = new ArrayList<>();
            for (int i = 0; i < poly.degree() + 1; i++) {
                Scalar val = w.get(0).multiply(aSc.get(i)).add(w.get(1).multiply(aEval.get(i)));
                a.add(val);
            }

            // take weighted sum of the two vectors using w
            Tuple3<DotProductProof, Point, Point> res = DotProductProof.prove(
                    gens1,
                    gensn,
                    transcript,
                    randomTape,
                    poly.getCoeffs(),
                    blindsPoly.get(j),
                    a,
                    target,
                    blind
            );
            DotProductProof proof = res.getValue1();
            claimPerRound = eval;
            commClaimPerRound = commEval;

            proofs.add(proof);
            r.add(rj);
            commEvals.add(commClaimPerRound);
        }

        return new Tuple4<>(
                new ZKSumcheckInstanceProof(commPolys, commEvals, proofs),
                r,
                List.of(polyA.get(0), polyB.get(0)),
                blindsEvals.get((int)numRounds - 1)
        );
    }

    public static Tuple4<ZKSumcheckInstanceProof, List<Scalar>, List<Scalar>, Scalar> proveCubicWithAdditiveTerm(
            Scalar claim,
            Scalar blindClaim,
            long numRounds,
            DensePolynomial polyA,
            DensePolynomial polyB,
            DensePolynomial polyC,
            DensePolynomial polyD,
            Function4<Scalar, Scalar, Scalar, Scalar, Scalar> combFunc,
            MultiCommitGens gens1,
            MultiCommitGens gensn,
            Transcript transcript,
            RandomTape randomTape
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        PointFactory pointFactory = transcript.getPointFactory();
        Scalar ZERO = scalarFactory.zero();
        Scalar ONE = scalarFactory.one();

        List<Scalar> blindsPoly = randomTape.randomVector("blinds_poly".getBytes(StandardCharsets.UTF_8), numRounds);
        List<Scalar> blindsEvals = randomTape.randomVector("blinds_evals".getBytes(StandardCharsets.UTF_8), numRounds);

        Scalar claimPerRound = claim;
        Point commClaimPerRound = Commitments.commit(claimPerRound, blindClaim, gens1);

        List<Scalar> r = new ArrayList<>();
        List<Point> commPolys = new ArrayList<>();
        List<Point> commEvals = new ArrayList<>();
        List<DotProductProof> proofs = new ArrayList<>();

        for (int j = 0; j < numRounds; j++) {
            Scalar evalPoint0 = ZERO;
            Scalar evalPoint2 = ZERO;
            Scalar evalPoint3 = ZERO;

            int len = (int)polyA.getLen() / 2;
            for (int i = 0; i < len; i++) {
                evalPoint0 = evalPoint0.add(combFunc.apply(polyA.get(i), polyB.get(i), polyC.get(i), polyD.get(i)));

                Scalar polyABoundPoint = polyA.get(len + i).add(polyA.get(len + i)).subtract(polyA.get(i));
                Scalar polyBBoundPoint = polyB.get(len + i).add(polyB.get(len + i)).subtract(polyB.get(i));
                Scalar polyCBoundPoint = polyC.get(len + i).add(polyC.get(len + i)).subtract(polyC.get(i));
                Scalar polyDBoundPoint = polyD.get(len + i).add(polyD.get(len + i)).subtract(polyD.get(i));
                evalPoint2 = evalPoint2.add(combFunc.apply(polyABoundPoint, polyBBoundPoint, polyCBoundPoint, polyDBoundPoint));

                Scalar polyABoundPoint2 = polyABoundPoint.add(polyA.get(len + i)).subtract(polyA.get(i));
                Scalar polyBBoundPoint2 = polyBBoundPoint.add(polyB.get(len + i)).subtract(polyB.get(i));
                Scalar polyCBoundPoint2 = polyCBoundPoint.add(polyC.get(len + i)).subtract(polyC.get(i));
                Scalar polyDBoundPoint2 = polyDBoundPoint.add(polyD.get(len + i)).subtract(polyD.get(i));
                evalPoint3 = evalPoint3.add(combFunc.apply(polyABoundPoint2, polyBBoundPoint2, polyCBoundPoint2, polyDBoundPoint2));
            }

            List<Scalar> evals = List.of(
                    evalPoint0,
                    claimPerRound.subtract(evalPoint0),
                    evalPoint2,
                    evalPoint3
            );
            UniPoly poly = UniPoly.fromEvals(evals, scalarFactory);
            Point commPoly = poly.commit(gensn, blindsPoly.get(j), pointFactory);

            transcript.appendPoint("comm_poly".getBytes(StandardCharsets.UTF_8), commPoly);
            commPolys.add(commPoly);

            Scalar rj = transcript.challengeScalar("challenge_nextround".getBytes(StandardCharsets.UTF_8));

            polyA.boundPolyVarTop(rj);
            polyB.boundPolyVarTop(rj);
            polyC.boundPolyVarTop(rj);
            polyD.boundPolyVarTop(rj);

            Scalar eval = poly.evaluate(rj);
            Point commEval = Commitments.commit(eval, blindsEvals.get(j), gens1);

            // we need to prove the following under homomorphic commitments:
            // (1) poly(0) + poly(1) = claim_per_round
            // (2) poly(r_j) = eval

            // The technique is to leverage dot product proofs:
            // (1) we can prove: <poly_in_coeffs_form, (2, 1, 1, 1)> = claim_per_round
            // (2) we can prove: <poly_in_coeffs_form, (1, r_j, r^2_j, ..) = eval
            // for efficiency we batch them using random weights

            transcript.appendPoint("comm_claim_per_round".getBytes(StandardCharsets.UTF_8), commClaimPerRound);
            transcript.appendPoint("comm_eval".getBytes(StandardCharsets.UTF_8), commEval);

            List<Scalar> w = transcript.challengeVector("combine_two_claims_to_one".getBytes(StandardCharsets.UTF_8), 2);

            Scalar target = w.get(0).multiply(claimPerRound).add(w.get(1).multiply(eval));

            List<Point> bases = List.of(commClaimPerRound, commEval) ;
            Point commTarget = pointFactory.multiscalarMul(w, bases);
            Scalar blindSc = j == 0 ? blindClaim : blindsEvals.get(j - 1);
            Scalar blindEval = blindsEvals.get(j);

            Scalar blind = w.get(0).multiply(blindSc).add(w.get(1).multiply(blindEval));
            Point ct = Commitments.commit(target, blind, gens1);
            if (!ct.equals(commTarget)) {
                throw new IllegalArgumentException("Commitment not matching");
            }

            // the vector to use to decommit for sum-check test
            List<Scalar> aSc = new ArrayList<>();
            for (int i = 0; i < poly.degree() + 1; i++) {
                aSc.add(i == 0 ? ONE.add(ONE) : ONE);
            }

            // the vector to use to decommit for evaluation
            List<Scalar> aEval = new ArrayList<>();
            for (int i = 0; i < poly.degree() + 1; i++) {
                aEval.add(ONE);
            }
            for (int i = 1; i < poly.degree() + 1; i++) {
                aEval.set(i, aEval.get(i - 1).multiply(rj));
            }

            List<Scalar> a = new ArrayList<>();
            for (int i = 0; i < poly.degree() + 1; i++) {
                Scalar val = w.get(0).multiply(aSc.get(i)).add(w.get(1).multiply(aEval.get(i)));
                a.add(val);
            }

            // take weighted sum of the two vectors using w
            Tuple3<DotProductProof, Point, Point> res = DotProductProof.prove(
                    gens1,
                    gensn,
                    transcript,
                    randomTape,
                    poly.getCoeffs(),
                    blindsPoly.get(j),
                    a,
                    target,
                    blind
            );
            DotProductProof proof = res.getValue1();
            claimPerRound = eval;
            commClaimPerRound = commEval;

            proofs.add(proof);
            r.add(rj);
            commEvals.add(commClaimPerRound);
        }

        return new Tuple4<>(
                new ZKSumcheckInstanceProof(commPolys, commEvals, proofs),
                r,
                List.of(polyA.get(0), polyB.get(0)),
                blindsEvals.get((int)numRounds - 1)
        );
    }

    public Pair<Point, List<Scalar>> verify(
            Point commClaim,
            long numRounds,
            long degreeBound,
            MultiCommitGens gens1,
            MultiCommitGens gensN,
            Transcript transcript
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        PointFactory pointFactory = transcript.getPointFactory();
        Scalar ZERO = scalarFactory.zero();
        Scalar ONE = scalarFactory.one();

        if (gensN.getN() != degreeBound + 1 || commEvals.size() != numRounds || commPolys.size() != numRounds) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        List<Scalar> r = new ArrayList<>();

        for (int i = 0; i < commPolys.size(); i++) {
            Point commPoly = commPolys.get(i);

            transcript.appendPoint("comm_poly".getBytes(StandardCharsets.UTF_8), commPoly);

            Scalar ri = transcript.challengeScalar("challenge_nextround".getBytes(StandardCharsets.UTF_8));

            Point commClaimPerRound = i == 0 ? commClaim : commEvals.get(i - 1);
            Point commEval = commEvals.get(i);

            transcript.appendPoint("comm_claim_per_round".getBytes(StandardCharsets.UTF_8), commClaimPerRound);
            transcript.appendPoint("comm_eval".getBytes(StandardCharsets.UTF_8), commEval);

            List<Scalar> w = transcript.challengeVector("combine_two_claims_to_one".getBytes(StandardCharsets.UTF_8), 2);
            List<Point> bases = List.of(commClaimPerRound, commEval);
            Point commTarget = pointFactory.multiscalarMul(w, bases);

            List<Scalar> aSc = new ArrayList<>();
            for (int j = 0; j < degreeBound + 1; j++) {
                aSc.add(j == 0 ? ONE.add(ONE) : ONE);
            }

            // the vector to use to decommit for evaluation
            List<Scalar> aEval = new ArrayList<>();
            for (int j = 0; j < degreeBound + 1; j++) {
                aEval.add(ONE);
            }
            for (int j = 1; j < degreeBound + 1; j++) {
                aEval.set(j, aEval.get(j - 1).multiply(ri));
            }
            if (aSc.size() != aEval.size()) {
                throw new IllegalArgumentException("Invalid sizes");
            }

            List<Scalar> a = new ArrayList<>();
            for (int j = 0; j < degreeBound + 1; j++) {
                Scalar val = w.get(0).multiply(aSc.get(j)).add(w.get(1).multiply(aEval.get(j)));
                a.add(val);
            }

            if (!proofs.get(i).verify(
                    a,
                    commPolys.get(i),
                    commTarget,
                    gens1,
                    gensN,
                    transcript
            )) {
                return null;
            }

            r.add(ri);
        }

        return new Pair<>(
                commEvals.get(commEvals.size() - 1),
                r
        );
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        Serialization.serializePointList(packer, commPolys);
        Serialization.serializePointList(packer, commEvals);
        packer.packInt(proofs.size());
        for (DotProductProof p : proofs) {
            p.pack(packer);
        }
    }

    public static ZKSumcheckInstanceProof unpack(MessageUnpacker unpacker, PointFactory pointFactory, ScalarFactory scalarFactory) throws IOException {
        List<Point> commPolys = Serialization.deserializePointList(unpacker, pointFactory);
        List<Point> commEvals = Serialization.deserializePointList(unpacker, pointFactory);
        List<DotProductProof> proofs = new ArrayList<>();
        int len = unpacker.unpackInt();
        for (int i = 0; i < len; i++) {
            DotProductProof p = DotProductProof.unpack(unpacker, pointFactory, scalarFactory);
            proofs.add(p);
        }
        return new ZKSumcheckInstanceProof(commPolys, commEvals, proofs);
    }
}
