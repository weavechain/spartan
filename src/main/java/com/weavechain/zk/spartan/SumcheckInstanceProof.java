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
public class SumcheckInstanceProof {

    private final List<CompressedUniPoly> compressedPolys;

    public static Tuple4<
            SumcheckInstanceProof,
            List<Scalar>,
            Tuple3<List<Scalar>, List<Scalar>, Scalar>,
            Tuple3<List<Scalar>, List<Scalar>, List<Scalar>>
        > proveCubicBatched(
            Scalar claim,
            long numRounds,
            List<DensePolynomial> polyAVecPar,
            List<DensePolynomial> polyBVecPar,
            DensePolynomial polyCPar,
            List<DensePolynomial> polyAVecSeq,
            List<DensePolynomial> polyBVecSeq,
            List<DensePolynomial> polyCVecSeq,
            List<Scalar> coeffs,
            Function3<Scalar, Scalar, Scalar, Scalar> combFunc,
            Transcript transcript
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        Scalar ZERO = scalarFactory.zero();
        Scalar ONE = scalarFactory.one();

        Scalar e = claim;
        List<Scalar> r = new ArrayList<>();
        List<CompressedUniPoly> cubicPolys = new ArrayList<>();

        for (int j = 0; j < numRounds; j++) {
            List<Tuple3<Scalar, Scalar, Scalar>> evals = new ArrayList<>();

            for (int k = 0; k < polyAVecPar.size(); k++) {
                DensePolynomial polyA = polyAVecPar.get(k);
                DensePolynomial polyB = polyBVecPar.get(k);

                Scalar evalPoint0 = ZERO;
                Scalar evalPoint2 = ZERO;
                Scalar evalPoint3 = ZERO;

                int len = (int)polyA.getLen() / 2;

                for (int i = 0; i < len; i++) {
                    // eval 0: bound_func is A(low)
                    evalPoint0 = evalPoint0.add(combFunc.apply(polyA.get(i), polyB.get(i), polyCPar.get(i)));

                    // eval 2: bound_func is -A(low) + 2*A(high)
                    Scalar polyABoundPoint = polyA.get(len + i).add(polyA.get(len + i)).subtract(polyA.get(i));
                    Scalar polyBBoundPoint = polyB.get(len + i).add(polyB.get(len + i)).subtract(polyB.get(i));
                    Scalar polyCBoundPoint = polyCPar.get(len + i).add(polyCPar.get(len + i)).subtract(polyCPar.get(i));
                    evalPoint2 = evalPoint2.add(combFunc.apply(polyABoundPoint, polyBBoundPoint, polyCBoundPoint));

                    // eval 3: bound_func is -2A(low) + 3A(high); computed incrementally with bound_func applied to eval(2)
                    polyABoundPoint = polyABoundPoint.add(polyA.get(len + i)).subtract(polyA.get(i));
                    polyBBoundPoint = polyBBoundPoint.add(polyB.get(len + i)).subtract(polyB.get(i));
                    polyCBoundPoint = polyCBoundPoint.add(polyCPar.get(len + i)).subtract(polyCPar.get(i));
                    evalPoint3 = evalPoint3.add(combFunc.apply(polyABoundPoint, polyBBoundPoint, polyCBoundPoint));
                }

                evals.add(new Tuple3<>(evalPoint0, evalPoint2, evalPoint3));
            }

            for (int k = 0; k < polyAVecSeq.size(); k++) {
                DensePolynomial polyA = polyAVecSeq.get(k);
                DensePolynomial polyB = polyBVecSeq.get(k);
                DensePolynomial polyC = polyCVecSeq.get(k);

                Scalar evalPoint0 = ZERO;
                Scalar evalPoint2 = ZERO;
                Scalar evalPoint3 = ZERO;

                int len = (int)polyA.getLen() / 2;

                for (int i = 0; i < len; i++) {
                    evalPoint0 = evalPoint0.add(combFunc.apply(polyA.get(i), polyB.get(i), polyC.get(i)));

                    Scalar polyABoundPoint = polyA.get(len + i).add(polyA.get(len + i)).subtract(polyA.get(i));
                    Scalar polyBBoundPoint = polyB.get(len + i).add(polyB.get(len + i)).subtract(polyB.get(i));
                    Scalar polyCBoundPoint = polyC.get(len + i).add(polyC.get(len + i)).subtract(polyC.get(i));
                    evalPoint2 = evalPoint2.add(combFunc.apply(polyABoundPoint, polyBBoundPoint, polyCBoundPoint));

                    // eval 3: bound_func is -2A(low) + 3A(high); computed incrementally with bound_func applied to eval(2)
                    polyABoundPoint = polyABoundPoint.add(polyA.get(len + i)).subtract(polyA.get(i));
                    polyBBoundPoint = polyBBoundPoint.add(polyB.get(len + i)).subtract(polyB.get(i));
                    polyCBoundPoint = polyCBoundPoint.add(polyC.get(len + i)).subtract(polyC.get(i));
                    evalPoint3 = evalPoint3.add(combFunc.apply(polyABoundPoint, polyBBoundPoint, polyCBoundPoint));
                }

                evals.add(new Tuple3<>(evalPoint0, evalPoint2, evalPoint3));
            }

            Scalar evalsCombined0 = ZERO;
            Scalar evalsCombined2 = ZERO;
            Scalar evalsCombined3 = ZERO;
            for (int i = 0; i < evals.size(); i++) {
                Scalar c = coeffs.get(i);
                evalsCombined0 = evalsCombined0.add(evals.get(i).getValue1().multiply(c));
                evalsCombined2 = evalsCombined2.add(evals.get(i).getValue2().multiply(c));
                evalsCombined3 = evalsCombined3.add(evals.get(i).getValue3().multiply(c));
            }

            List<Scalar> evalsComb = List.of(evalsCombined0, e.subtract(evalsCombined0), evalsCombined2, evalsCombined3);
            UniPoly poly = UniPoly.fromEvals(evalsComb, scalarFactory);
            transcript.appendCommitment("poly".getBytes(StandardCharsets.UTF_8), poly);

            Scalar rj = transcript.challengeScalar("challenge_nextround".getBytes(StandardCharsets.UTF_8));
            r.add(rj);

            // bound all tables to the verifier's challenge
            for (int i = 0; i < polyAVecPar.size(); i++) {
                polyAVecPar.get(i).boundPolyVarTop(rj);
                polyBVecPar.get(i).boundPolyVarTop(rj);
            }
            polyCPar.boundPolyVarTop(rj);

            for (int i = 0; i < polyAVecSeq.size(); i++) {
                polyAVecSeq.get(i).boundPolyVarTop(rj);
                polyBVecSeq.get(i).boundPolyVarTop(rj);
                polyCVecSeq.get(i).boundPolyVarTop(rj);
            }

            e = poly.evaluate(rj);
            cubicPolys.add(poly.compress());
        }

        List<Scalar> polyAParFinal = new ArrayList<>();
        for (DensePolynomial p : polyAVecPar) {
            polyAParFinal.add(p.get(0));
        }
        List<Scalar> polyBParFinal = new ArrayList<>();
        for (DensePolynomial p : polyBVecPar) {
            polyBParFinal.add(p.get(0));
        }

        List<Scalar> polyASeqFinal = new ArrayList<>();
        for (DensePolynomial p : polyAVecSeq) {
            polyASeqFinal.add(p.get(0));
        }
        List<Scalar> polyBSeqFinal = new ArrayList<>();
        for (DensePolynomial p : polyBVecSeq) {
            polyBSeqFinal.add(p.get(0));
        }
        List<Scalar> polyCSeqFinal = new ArrayList<>();
        for (DensePolynomial p : polyCVecSeq) {
            polyCSeqFinal.add(p.get(0));
        }

        return new Tuple4<>(
                new SumcheckInstanceProof(cubicPolys),
                r,
                new Tuple3<>(polyAParFinal, polyBParFinal, polyCPar.get(0)),
                new Tuple3<>(polyASeqFinal, polyBSeqFinal, polyCSeqFinal)
        );
    }

    public Pair<Scalar, List<Scalar>> verify(
            Scalar claim,
            long numRounds,
            long degreeBound,
            Transcript transcript
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();

        Scalar e = claim;
        List<Scalar> r = new ArrayList<>();

        if (compressedPolys.size() != numRounds) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        for (int i = 0; i < compressedPolys.size(); i++) {
            UniPoly poly = compressedPolys.get(i).decompress(e);

            if (poly.degree() != degreeBound) {
                throw new IllegalArgumentException("Invalid degree");
            }

            Scalar eval = poly.evalAtZero().add(poly.evalAtOne(scalarFactory));
            if (!eval.equals(e)) {
                return null;
            }

            transcript.appendCommitment("poly".getBytes(StandardCharsets.UTF_8), poly);

            Scalar ri = transcript.challengeScalar("challenge_nextround".getBytes(StandardCharsets.UTF_8));
            r.add(ri);

            e = poly.evaluate(ri);
        }

        return new Pair<>(e, r);
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        packer.packInt(compressedPolys.size());
        for (CompressedUniPoly p : compressedPolys) {
            p.pack(packer);
        }
    }

    public static SumcheckInstanceProof unpack(MessageUnpacker unpacker, ScalarFactory scalarFactory) throws IOException {
        int len = unpacker.unpackInt();
        List<CompressedUniPoly> compressedPolys = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            compressedPolys.add(CompressedUniPoly.unpack(unpacker, scalarFactory));
        }
        return new SumcheckInstanceProof(compressedPolys);
    }
}
