package com.weavechain.zk.spartan;

import com.weavechain.curves.Point;
import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.Commitments;
import com.weavechain.zk.spartan.commit.PolyCommitment;
import com.weavechain.zk.spartan.generators.R1CSGens;
import com.weavechain.zk.spartan.generators.R1CSSumcheckGens;
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
public class R1CSProof {

    public static String PROTOCOL_NAME = "R1CS proof";

    private final PolyCommitment commVars;

    private final ZKSumcheckInstanceProof scProofPhase1;

    private final List<Point> claimsPhase2;

    private final Pair<KnowledgeProof, ProductProof> pokClaimsPhase2;

    private final EqualityProof proofEqScPhase1;

    private final ZKSumcheckInstanceProof scProofPhase2;

    private final Point commVarsAtRy;

    private final PolyEvalProof proofEvalVarsAtRy;

    private final EqualityProof proofEqScPhase2;

    private static Scalar combFunc1(Scalar polyAComp, Scalar polyBComp, Scalar polyCComp, Scalar polyDComp) {
        return polyAComp.multiply(polyBComp.multiply(polyCComp).subtract(polyDComp));
    }

    public static Tuple4<ZKSumcheckInstanceProof, List<Scalar>, List<Scalar>, Scalar> provePhase1(
            long numNounds,
            DensePolynomial evalsTau,
            DensePolynomial evalsAz,
            DensePolynomial evalsBz,
            DensePolynomial evalsCz,
            R1CSSumcheckGens gens,
            Transcript transcript,
            RandomTape randomTape
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        Scalar ZERO = scalarFactory.zero();

        return ZKSumcheckInstanceProof.proveCubicWithAdditiveTerm(
                ZERO,
                ZERO,
                numNounds,
                evalsTau,
                evalsAz,
                evalsBz,
                evalsCz,
                R1CSProof::combFunc1,
                gens.getGens1(),
                gens.getGens4(),
                transcript,
                randomTape
        );
    }

    private static Scalar combFunc2(Scalar polyAComp, Scalar polyBComp) {
        return polyAComp.multiply(polyBComp);
    }

    public static Tuple4<ZKSumcheckInstanceProof, List<Scalar>, List<Scalar>, Scalar> provePhase2(
            long numRounds,
            Scalar claim,
            Scalar blindClaim,
            DensePolynomial evalsZ,
            DensePolynomial evalsABC,
            R1CSSumcheckGens gens,
            Transcript transcript,
            RandomTape randomTape
    ) {
        return ZKSumcheckInstanceProof.proveQuad(
                claim,
                blindClaim,
                numRounds,
                evalsZ,
                evalsABC,
                R1CSProof::combFunc2,
                gens.getGens1(),
                gens.getGens3(),
                transcript,
                randomTape
        );
    }

    public static Tuple3<R1CSProof, List<Scalar>, List<Scalar>> prove(
            R1CSInstance inst,
            List<Scalar> vars,
            List<Scalar> input,
            R1CSGens gens,
            Transcript transcript,
            RandomTape randomTape
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        PointFactory pointFactory = transcript.getPointFactory();
        Scalar ZERO = scalarFactory.zero();
        Scalar ONE = scalarFactory.one();

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        if (input.size() >= vars.size()) {
            throw new IllegalArgumentException("Invalid input size");
        }

        transcript.appendScalars("input".getBytes(StandardCharsets.UTF_8), input);

        long numPaddedVars = inst.getNumVars();
        long numVars = vars.size();
        DensePolynomial polyVars = DensePolynomial.create(vars);
        Pair<PolyCommitment, PolyCommitmentBlinds> comm = polyVars.commit(gens.getGensPc(), randomTape, scalarFactory, pointFactory);
        PolyCommitment commVars = comm.getValue1();
        PolyCommitmentBlinds blindsVars = comm.getValue2();

        transcript.appendCommitment("poly_commitment".getBytes(StandardCharsets.UTF_8), commVars);

        List<Scalar> z = new ArrayList<>(vars);
        z.add(ONE);
        z.addAll(input);
        for (int i = 0; i < vars.size() - input.size() - 1; i++) {
            z.add(ZERO);
        }

        long numRoundsX = Utils.log2(inst.getNumCons());
        long numRoundsY = Utils.log2((long)z.size());
        List<Scalar> tau = transcript.challengeVector("challenge_tau".getBytes(StandardCharsets.UTF_8), numRoundsX);

        DensePolynomial polyTau = DensePolynomial.create(new EqPolynomial(tau).evals(scalarFactory));
        Tuple3<DensePolynomial, DensePolynomial, DensePolynomial> mul = inst.multiplyVec(inst.getNumCons(), z.size(), z, scalarFactory);
        DensePolynomial polyAz = mul.getValue1();
        DensePolynomial polyBz = mul.getValue2();
        DensePolynomial polyCz = mul.getValue3();

        Tuple4<ZKSumcheckInstanceProof, List<Scalar>, List<Scalar>, Scalar> phase1 = R1CSProof.provePhase1(
                numRoundsX,
                polyTau,
                polyAz,
                polyBz,
                polyCz,
                gens.getGensSc(),
                transcript,
                randomTape
        );
        ZKSumcheckInstanceProof scProofPhase1 = phase1.getValue1();
        List<Scalar> rx = phase1.getValue2();
        List<Scalar> claimsPhase1 = phase1.getValue3();
        Scalar blindClaimPostsc1 = phase1.getValue4();

        if (polyTau.getZ().size() != 1 || polyAz.getZ().size() != 1 || polyBz.getZ().size() != 1 || polyCz.getZ().size() != 1) {
            throw new IllegalArgumentException("Invalid poly size");
        }

        Scalar tauClaim = polyTau.get(0);
        Scalar AzClaim = polyAz.get(0);
        Scalar BzClaim = polyBz.get(0);
        Scalar CzClaim = polyCz.get(0);

        Scalar AzBlind = randomTape.randomScalar("Az_blind".getBytes(StandardCharsets.UTF_8));
        Scalar BzBlind = randomTape.randomScalar("Bz_blind".getBytes(StandardCharsets.UTF_8));
        Scalar CzBlind = randomTape.randomScalar("Cz_blind".getBytes(StandardCharsets.UTF_8));
        Scalar prodAzBzBlind = randomTape.randomScalar("prod_Az_Bz_blind".getBytes(StandardCharsets.UTF_8));

        Pair<KnowledgeProof, Point> kres = KnowledgeProof.prove(
                gens.getGensSc().getGens1(),
                transcript,
                randomTape,
                CzClaim,
                CzBlind
        );

        KnowledgeProof pokCzClaim = kres.getValue1();
        Point commCzClaim = kres.getValue2();

        Scalar prod = AzClaim.multiply(BzClaim);
        Tuple4<ProductProof, Point, Point, Point> pres = ProductProof.prove(
                gens.getGensSc().getGens1(),
                transcript,
                randomTape,
                AzClaim,
                AzBlind,
                BzClaim,
                BzBlind,
                prod,
                prodAzBzBlind
        );
        ProductProof proofProd = pres.getValue1();
        Point commAzClaim = pres.getValue2();
        Point commBzClaim = pres.getValue3();
        Point commProdAzBzClaim = pres.getValue4();

        transcript.appendPoint("comm_Az_claim".getBytes(StandardCharsets.UTF_8), commAzClaim);
        transcript.appendPoint("comm_Bz_claim".getBytes(StandardCharsets.UTF_8), commBzClaim);
        transcript.appendPoint("comm_Cz_claim".getBytes(StandardCharsets.UTF_8), commCzClaim);
        transcript.appendPoint("comm_prod_Az_Bz_claims".getBytes(StandardCharsets.UTF_8), commProdAzBzClaim);

        Scalar tausBoundRx = tauClaim;
        Scalar blindExpectedClaimPostsc1 = tausBoundRx.multiply(prodAzBzBlind.subtract(CzBlind));
        Scalar claimPostPhase1 = AzClaim.multiply(BzClaim).subtract(CzClaim).multiply(tausBoundRx);
        Tuple3<EqualityProof, Point, Point> eres = EqualityProof.prove(
                gens.getGensSc().getGens1(),
                transcript,
                randomTape,
                claimPostPhase1,
                blindExpectedClaimPostsc1,
                claimPostPhase1,
                blindClaimPostsc1
        );
        EqualityProof proofEqScPhase1 = eres.getValue1();

        Scalar rA = transcript.challengeScalar("challenge_Az".getBytes(StandardCharsets.UTF_8));
        Scalar rB = transcript.challengeScalar("challenge_Bz".getBytes(StandardCharsets.UTF_8));
        Scalar rC = transcript.challengeScalar("challenge_Cz".getBytes(StandardCharsets.UTF_8));
        Scalar claimPhase2 = rA.multiply(AzClaim).add(rB.multiply(BzClaim)).add(rC.multiply(CzClaim));
        Scalar blindClaimPhase2 = rA.multiply(AzBlind).add(rB.multiply(BzBlind)).add(rC.multiply(CzBlind));

        List<Scalar> evalsRx = new EqPolynomial(rx).evals(scalarFactory);
        Tuple3<List<Scalar>, List<Scalar>, List<Scalar>> sres = inst.computeEvalTableSparse(inst.getNumCons(), z.size(), evalsRx, scalarFactory);
        List<Scalar> evalsA = sres.getValue1();
        List<Scalar> evalsB = sres.getValue2();
        List<Scalar> evalsC = sres.getValue3();
        List<Scalar> evalsABC = new ArrayList<>();
        for (int i = 0; i < evalsA.size(); i++) {
            Scalar val = rA.multiply(evalsA.get(i)).add(rB.multiply(evalsB.get(i))).add(rC.multiply(evalsC.get(i)));
            evalsABC.add(val);
        }

        Tuple4<ZKSumcheckInstanceProof, List<Scalar>, List<Scalar>, Scalar> res = R1CSProof.provePhase2(
                numRoundsY,
                claimPhase2,
                blindClaimPhase2,
                DensePolynomial.create(z),
                DensePolynomial.create(evalsABC),
                gens.getGensSc(),
                transcript,
                randomTape
        );
        ZKSumcheckInstanceProof scProofPhase2 = res.getValue1();
        List<Scalar> ry = res.getValue2();
        List<Scalar> claimsPhase2 = res.getValue3();
        Scalar blindClaimPostsc2 = res.getValue4();

        List<Scalar> rys = ry.subList(1, ry.size());
        Scalar evalVarsAtRy = polyVars.evaluate(rys, transcript.getScalarFactory());
        Scalar blindEval = randomTape.randomScalar("blind_eval".getBytes(StandardCharsets.UTF_8));

        Pair<PolyEvalProof, Point> ppres = PolyEvalProof.prove(
                polyVars,
                blindsVars,
                rys,
                evalVarsAtRy,
                blindEval,
                gens.getGensPc(),
                transcript,
                randomTape
        );

        PolyEvalProof proofEvalVarsAtRy = ppres.getValue1();
        Point commVarsAtRy = ppres.getValue2();

        Scalar blindEvalZAtRy = ONE.subtract(ry.get(0)).multiply(blindEval);
        Scalar blindExpectedClaimPostsc2 = claimsPhase2.get(1).multiply(blindEvalZAtRy);
        Scalar claimPostPhase2 = claimsPhase2.get(0).multiply(claimsPhase2.get(1));
        Tuple3<EqualityProof, Point, Point> eqres = EqualityProof.prove(
                gens.getGensSc().getGens1(),
                transcript,
                randomTape,
                claimPostPhase2,
                blindExpectedClaimPostsc2,
                claimPostPhase2,
                blindClaimPostsc2
        );

        EqualityProof proofEqScPhase2 = eqres.getValue1();

        return new Tuple3<>(
                new R1CSProof(
                        commVars,
                        scProofPhase1,
                        List.of(commAzClaim, commBzClaim, commCzClaim, commProdAzBzClaim),
                        new Pair<>(pokCzClaim, proofProd),
                        proofEqScPhase1,
                        scProofPhase2,
                        commVarsAtRy,
                        proofEvalVarsAtRy,
                        proofEqScPhase2
                ),
                rx,
                ry
        );
    }

    public Pair<List<Scalar>, List<Scalar>> verify(
            long numVars,
            long numCons,
            List<Scalar> input,
            Tuple3<Scalar, Scalar, Scalar> evals,
            Transcript transcript,
            R1CSGens gens
    ) {
        PointFactory pointFactory = transcript.getPointFactory();
        ScalarFactory scalarFactory = transcript.getScalarFactory();

        Scalar ZERO = scalarFactory.zero();
        Scalar ONE = scalarFactory.one();

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));
        transcript.appendScalars("input".getBytes(StandardCharsets.UTF_8), input);

        long n = numVars;

        transcript.appendCommitment("poly_commitment".getBytes(StandardCharsets.UTF_8), commVars);

        long numRoundsX = Utils.log2(numCons);
        long numRoundsY = Utils.log2(2 * numVars);

        List<Scalar> tau = transcript.challengeVector("challenge_tau".getBytes(StandardCharsets.UTF_8), numRoundsX);

        // verify the first sum-check instance
        Point claimPhase1 = Commitments.commit(ZERO, ZERO, gens.getGensSc().getGens1());

        Pair<Point, List<Scalar>> p1 = scProofPhase1.verify(
                claimPhase1,
                numRoundsX,
                3,
                gens.getGensSc().getGens1(),
                gens.getGensSc().getGens4(),
                transcript
        );
        if (p1 == null) {
            return null;
        }
        Point commClaimPostPhase1 = p1.getValue1();
        List<Scalar> rx = p1.getValue2();

        Point commAzClaim = claimsPhase2.get(0);
        Point commBzClaim = claimsPhase2.get(1);
        Point commCzClaim = claimsPhase2.get(2);
        Point commProdAzBzClaims = claimsPhase2.get(3);
        KnowledgeProof pokCzClaim = pokClaimsPhase2.getValue1();
        ProductProof proofProd = pokClaimsPhase2.getValue2();

        if (!pokCzClaim.verify(
                gens.getGensSc().getGens1(),
                commCzClaim,
                transcript
        )) {
            return null;
        }
        if (!proofProd.verify(
                commAzClaim,
                commBzClaim,
                commProdAzBzClaims,
                gens.getGensSc().getGens1(),
                transcript
        )) {
            return null;
        }

        transcript.appendPoint("comm_Az_claim".getBytes(StandardCharsets.UTF_8), commAzClaim);
        transcript.appendPoint("comm_Bz_claim".getBytes(StandardCharsets.UTF_8), commBzClaim);
        transcript.appendPoint("comm_Cz_claim".getBytes(StandardCharsets.UTF_8), commCzClaim);
        transcript.appendPoint("comm_prod_Az_Bz_claims".getBytes(StandardCharsets.UTF_8), commProdAzBzClaims);

        Scalar tausBoundRx = ONE;
        for (int i = 0; i < rx.size(); i++) {
            Scalar p = rx.get(i).multiply(tau.get(i)).add(ONE.subtract(rx.get(i)).multiply(ONE.subtract(tau.get(i))));
            tausBoundRx = tausBoundRx.multiply(p);
        }
        Point expectedClaimPostPhase1 = commProdAzBzClaims.subtract(commCzClaim).multiply(tausBoundRx);
        if (!proofEqScPhase1.verify(
                expectedClaimPostPhase1,
                commClaimPostPhase1,
                gens.getGensSc().getGens1(),
                transcript
        )) {
            return null;
        }

        Scalar rA = transcript.challengeScalar("challenge_Az".getBytes(StandardCharsets.UTF_8));
        Scalar rB = transcript.challengeScalar("challenge_Bz".getBytes(StandardCharsets.UTF_8));
        Scalar rC = transcript.challengeScalar("challenge_Cz".getBytes(StandardCharsets.UTF_8));

        List<Scalar> scalars = List.of(rA, rB, rC);
        List<Point> bases = List.of(commAzClaim, commBzClaim, commCzClaim);

        Point commClaimPhase2 = pointFactory.multiscalarMul(scalars, bases);

        Pair<Point, List<Scalar>> pp2 = scProofPhase2.verify(
                commClaimPhase2,
                numRoundsY,
                2,
                gens.getGensSc().getGens1(),
                gens.getGensSc().getGens3(),
                transcript
        );
        if (pp2 == null) {
            return null;
        }

        Point commClaimPostPhase2 = pp2.getValue1();
        List<Scalar> ry = pp2.getValue2();

        if (!proofEvalVarsAtRy.verify(
                ry.subList(1, ry.size()),
                commVarsAtRy,
                commVars,
                gens.getGensPc(),
                transcript
        )) {
            return null;
        }

        List<SparsePolyEntry> inputPoly = new ArrayList<>();
        inputPoly.add(new SparsePolyEntry(0, ONE));
        for (int i = 0; i < input.size(); i++) {
            inputPoly.add(new SparsePolyEntry(i + 1, input.get(i)));
        }
        Scalar polyInputEval = new SparsePolynomial(Utils.log2(n), inputPoly).evaluate(ry.subList(1, ry.size()), scalarFactory);

        List<Scalar> scalarsZ = List.of(ONE.subtract(ry.get(0)), ry.get(0));
        List<Point> basesZ = List.of(
                commVarsAtRy,
                Commitments.commit(polyInputEval, ZERO, gens.getGensPc().getGens().getGens1())
        );

        Point commEvalZAtRy = pointFactory.multiscalarMul(scalarsZ, basesZ);

        Scalar evalAr = evals.getValue1();
        Scalar evalBr = evals.getValue2();
        Scalar evalCr = evals.getValue3();
        Point expectedClaimPostPhase2 = commEvalZAtRy.multiply(rA.multiply(evalAr).add(rB.multiply(evalBr)).add(rC.multiply(evalCr)));

        if (!proofEqScPhase2.verify(
                expectedClaimPostPhase2,
                commClaimPostPhase2,
                gens.getGensSc().getGens1(),
                transcript
        )) {
            return null;
        }

        return new Pair<>(
                rx,
                ry
        );
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        commVars.pack(packer);
        scProofPhase1.pack(packer);
        Serialization.serializePointList(packer, claimsPhase2);
        pokClaimsPhase2.getValue1().pack(packer);
        pokClaimsPhase2.getValue2().pack(packer);
        proofEqScPhase1.pack(packer);
        scProofPhase2.pack(packer);
        Serialization.serialize(packer, commVarsAtRy);
        proofEvalVarsAtRy.pack(packer);
        proofEqScPhase2.pack(packer);
    }

    public static R1CSProof unpack(MessageUnpacker unpacker, PointFactory pointFactory, ScalarFactory scalarFactory) throws IOException {
        PolyCommitment commVars = PolyCommitment.unpack(unpacker, pointFactory);
        ZKSumcheckInstanceProof scProofPhase1 = ZKSumcheckInstanceProof.unpack(unpacker, pointFactory, scalarFactory);
        List<Point> claimsPhase2 = Serialization.deserializePointList(unpacker, pointFactory);
        KnowledgeProof knowledgeProof = KnowledgeProof.unpack(unpacker, pointFactory, scalarFactory);
        ProductProof productProof = ProductProof.unpack(unpacker, pointFactory, scalarFactory);
        Pair<KnowledgeProof, ProductProof> pokClaimsPhase2 = new Pair<>(knowledgeProof, productProof);
        EqualityProof proofEqScPhase1 = EqualityProof.unpack(unpacker, pointFactory, scalarFactory);
        ZKSumcheckInstanceProof scProofPhase2 = ZKSumcheckInstanceProof.unpack(unpacker, pointFactory, scalarFactory);
        Point commVarsAtRy = Serialization.deserializePoint(unpacker, pointFactory);
        PolyEvalProof proofEvalVarsAtRy = PolyEvalProof.unpack(unpacker, pointFactory, scalarFactory);
        EqualityProof proofEqScPhase2 = EqualityProof.unpack(unpacker, pointFactory, scalarFactory);
        return new R1CSProof(
                commVars,
                scProofPhase1,
                claimsPhase2,
                pokClaimsPhase2,
                proofEqScPhase1,
                scProofPhase2,
                commVarsAtRy,
                proofEvalVarsAtRy,
                proofEqScPhase2
        );
    }
}
