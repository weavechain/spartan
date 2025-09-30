package com.weavechain.zk.spartan;

import com.weavechain.curves.Point;
import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.DerefsCommitment;
import com.weavechain.zk.spartan.commit.SparseMatPolyCommitment;
import com.weavechain.zk.spartan.generators.SparseMatPolyCommitmentGens;
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
public class HashLayerProof {

    private static final String PROTOCOL_NAME = "Sparse polynomial hash layer proof";

    private final Tuple3<List<Scalar>, List<Scalar>, Scalar> evalRow;

    private final Tuple3<List<Scalar>, List<Scalar>, Scalar> evalCol;

    private final List<Scalar> evalVal;

    private final Pair<List<Scalar>, List<Scalar>> evalDerefs;

    private final PolyEvalProof proofOps;

    private final PolyEvalProof proofMem;

    private final DerefsEvalProof proofDerefs;

    public static Tuple3<List<Scalar>, List<Scalar>, Scalar> proveHelper(
            List<Scalar> randMem,
            List<Scalar> randOps,
            AddrTimestamps addrTimestamps,
            ScalarFactory scalarFactory
    ) {
        List<Scalar> evalOpsAddrVec = new ArrayList<>();
        for (DensePolynomial p : addrTimestamps.getOpsAddr()) {
            evalOpsAddrVec.add(p.evaluate(randOps, scalarFactory));
        }

        List<Scalar> evalReadTsVec = new ArrayList<>();
        for (DensePolynomial p : addrTimestamps.getReadTs()) {
            evalReadTsVec.add(p.evaluate(randOps, scalarFactory));
        }

        Scalar evalAuditTs = addrTimestamps.getAuditTs().evaluate(randMem, scalarFactory);

        return new Tuple3<>(
                evalOpsAddrVec,
                evalReadTsVec,
                evalAuditTs
        );
    }

    public static HashLayerProof prove(
            List<Scalar> randMem,
            List<Scalar> randOps,
            MultiSparseMatPolynomialAsDense dense,
            Derefs derefs,
            SparseMatPolyCommitmentGens gens,
            Transcript transcript,
            RandomTape randomTape
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        List<Scalar> evalRowOpsVal = new ArrayList<>();
        for (DensePolynomial p : derefs.getRowOpsVal()) {
            evalRowOpsVal.add(p.evaluate(randOps, scalarFactory));
        }

        List<Scalar> evalColOpsVal = new ArrayList<>();
        for (DensePolynomial p : derefs.getColOpsVal()) {
            evalColOpsVal.add(p.evaluate(randOps, scalarFactory));
        }

        DerefsEvalProof proofDerefs = DerefsEvalProof.prove(
                derefs,
                evalRowOpsVal,
                evalColOpsVal,
                randOps,
                gens.getGensDerefs(),
                transcript,
                randomTape
        );

        Tuple3<List<Scalar>, List<Scalar>, Scalar> evalRow = proveHelper(randMem, randOps, dense.getRow(), scalarFactory);
        Scalar evalRowAuditTs = evalRow.getValue3();
        Tuple3<List<Scalar>, List<Scalar>, Scalar> evalCol = proveHelper(randMem, randOps, dense.getCol(), scalarFactory);
        Scalar evalColAuditTs = evalCol.getValue3();
        List<Scalar> evalValVec = new ArrayList<>();
        for (DensePolynomial p : dense.getVal()) {
            evalValVec.add(p.evaluate(randOps, scalarFactory));
        }

        List<Scalar> evalOps = new ArrayList<>();
        evalOps.addAll(evalRow.getValue1());
        evalOps.addAll(evalRow.getValue2());
        evalOps.addAll(evalCol.getValue1());
        evalOps.addAll(evalCol.getValue2());
        evalOps.addAll(evalValVec);
        for (int i = evalOps.size(); i < Utils.nextPow2(evalOps.size()); i++) {
            evalOps.add(scalarFactory.zero());
        }

        transcript.appendScalars("claim_evals_ops".getBytes(StandardCharsets.UTF_8), evalOps);

        List<Scalar> challengesOps = transcript.challengeVector("challenge_combine_n_to_one".getBytes(StandardCharsets.UTF_8), Utils.log2((long)evalOps.size()));

        DensePolynomial polyEvalsOps = DensePolynomial.create(evalOps);
        for (int i = challengesOps.size() - 1; i >= 0; i--) {
            polyEvalsOps.boundPolyVarBot(challengesOps.get(i));
        }

        Scalar jointClaimEvalOps = polyEvalsOps.get(0);
        transcript.appendScalar("joint_claim_eval_ops".getBytes(StandardCharsets.UTF_8), jointClaimEvalOps);

        List<Scalar> rJointOps = new ArrayList<>(challengesOps);
        rJointOps.addAll(randOps);

        Pair<PolyEvalProof, Point> pops = PolyEvalProof.prove(
                dense.getCombOps(),
                null,
                rJointOps,
                jointClaimEvalOps,
                null,
                gens.getGensOps(),
                transcript,
                randomTape
        );
        PolyEvalProof proofOps = pops.getValue1();

        List<Scalar> evalsMem = List.of(evalRowAuditTs, evalColAuditTs);

        transcript.appendScalars("claim_evals_mem".getBytes(StandardCharsets.UTF_8), evalsMem);

        List<Scalar> challengesMem = transcript.challengeVector("challenge_combine_two_to_one".getBytes(StandardCharsets.UTF_8), Utils.log2((long)evalsMem.size()));

        DensePolynomial polyEvalsMem = DensePolynomial.create(evalsMem);
        for (int i = challengesMem.size() - 1; i >= 0; i--) {
            polyEvalsMem.boundPolyVarBot(challengesMem.get(i));
        }

        Scalar jointClaimEvalMem = polyEvalsMem.get(0);
        transcript.appendScalar("joint_claim_eval_mem".getBytes(StandardCharsets.UTF_8), jointClaimEvalMem);

        List<Scalar> rJointMem = new ArrayList<>(challengesMem);
        rJointMem.addAll(randMem);

        Pair<PolyEvalProof, Point> pmem = PolyEvalProof.prove(
                dense.getCombMem(),
                null,
                rJointMem,
                jointClaimEvalMem,
                null,
                gens.getGensMem(),
                transcript,
                randomTape
        );
        PolyEvalProof proofMem = pmem.getValue1();

        return new HashLayerProof(
                evalRow,
                evalCol,
                evalValVec,
                new Pair<>(evalRowOpsVal, evalColOpsVal),
                proofOps,
                proofMem,
                proofDerefs
        );
    }

    public boolean verifyHelper(
            Pair<List<Scalar>, List<Scalar>> rand,
            Tuple4<Scalar, List<Scalar>, List<Scalar>, Scalar> claims,
            List<Scalar> evalOpsVal,
            List<Scalar> evalOpsAddr,
            List<Scalar> evalReadTs,
            Scalar evalAuditTs,
            List<Scalar> r,
            Scalar rHash,
            Scalar rMultisetCheck,
            ScalarFactory scalarFactory
    ) {
        Scalar ZERO = scalarFactory.zero();
        Scalar ONE = scalarFactory.one();

        Scalar rHashSq = rHash.square();
        Function3<Scalar, Scalar, Scalar, Scalar> hashFn = (addr, val, ts) -> ts.multiply(rHashSq).add(val.multiply(rHash).add(addr));

        List<Scalar> randMem = rand.getValue1();
        Scalar claimInit = claims.getValue1();
        List<Scalar> claimsRead = claims.getValue2();
        List<Scalar> claimsWrite =claims.getValue3();
        Scalar claimAudit = claims.getValue4();

        Scalar evalInitAddr = new IdentityPolynomial(randMem.size()).evaluate(randMem, scalarFactory);
        Scalar evalInitVal = new EqPolynomial(r).evaluate(randMem, scalarFactory);

        Scalar hashInitAtRandMem = hashFn.apply(evalInitAddr, evalInitVal, ZERO).subtract(rMultisetCheck);
        if (!hashInitAtRandMem.equals(claimInit)) {
            return false;
        }

        for (int i = 0; i < evalOpsAddr.size(); i++) {
            Scalar hashReadAtRandOps = hashFn.apply(evalOpsAddr.get(i), evalOpsVal.get(i), evalReadTs.get(i)).subtract(rMultisetCheck);
            if (!hashReadAtRandOps.equals(claimsRead.get(i))) {
                return false;
            }
        }

        for (int i = 0; i < evalOpsAddr.size(); i++) {
            Scalar evalWriteTs = evalReadTs.get(i).add(ONE);
            Scalar hashWriteAtRandOps = hashFn.apply(evalOpsAddr.get(i), evalOpsVal.get(i), evalWriteTs).subtract(rMultisetCheck);
            if (!hashWriteAtRandOps.equals(claimsWrite.get(i))) {
                return false;
            }
        }

        Scalar hashAuditAtRandMem = hashFn.apply(evalInitAddr, evalInitVal, evalAuditTs).subtract(rMultisetCheck);
        return hashAuditAtRandMem.equals(claimAudit);
    }

    public boolean verify(
            Pair<List<Scalar>, List<Scalar>> rand,
            Tuple4<Scalar, List<Scalar>, List<Scalar>, Scalar> claimsRow,
            Tuple4<Scalar, List<Scalar>, List<Scalar>, Scalar> claimsCol,
            List<Scalar> claimsDotp,
            SparseMatPolyCommitment comm,
            SparseMatPolyCommitmentGens gens,
            DerefsCommitment commDerefs,
            List<Scalar> rx,
            List<Scalar> ry,
            Scalar rHash,
            Scalar rMultisetCheck,
            Transcript transcript
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();

        Scalar ZERO = scalarFactory.zero();

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        List<Scalar> randMem = rand.getValue1();
        List<Scalar> randOps = rand.getValue2();

        List<Scalar> evalRowOpsVal = evalDerefs.getValue1();
        List<Scalar> evalColOpsVal = evalDerefs.getValue2();
        if (evalRowOpsVal.size() != evalColOpsVal.size()) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        if (!proofDerefs.verify(
                randOps,
                evalRowOpsVal,
                evalColOpsVal,
                commDerefs,
                gens.getGensDerefs(),
                transcript
        )) {
            return false;
        }

        List<Scalar> evalValVec = new ArrayList<>(evalVal);
        if (claimsDotp.size() != 3 * evalRowOpsVal.size()) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        for (int i = 0; i < claimsDotp.size() / 3; i++) {
            Scalar claimRowOpsVal = claimsDotp.get(3 * i);
            Scalar claimColOpsVal = claimsDotp.get(3 * i + 1);
            Scalar claimVal = claimsDotp.get(3 * i + 2);

            if (!claimRowOpsVal.equals(evalRowOpsVal.get(i))
                    || !claimColOpsVal.equals(evalColOpsVal.get(i))
                    || !claimVal.equals(evalValVec.get(i))) {
                return false;
            }
        }

        List<Scalar> evalRowAddrVec = evalRow.getValue1();
        List<Scalar> evalRowReadTsVec = evalRow.getValue2();
        Scalar evalRowAuditTs = evalRow.getValue3();
        List<Scalar> evalColAddrVec = evalCol.getValue1();
        List<Scalar> evalColReadTsVec = evalCol.getValue2();
        Scalar evalColAuditTs = evalCol.getValue3();

        List<Scalar> evalsOps = new ArrayList<>();
        evalsOps.addAll(evalRowAddrVec);
        evalsOps.addAll(evalRowReadTsVec);
        evalsOps.addAll(evalColAddrVec);
        evalsOps.addAll(evalColReadTsVec);
        evalsOps.addAll(evalValVec);
        for (int i = evalsOps.size(); i < Utils.nextPow2(evalsOps.size()); i++) {
            evalsOps.add(ZERO);
        }

        transcript.appendScalars("claim_evals_ops".getBytes(StandardCharsets.UTF_8), evalsOps);

        List<Scalar> challengesOps = transcript.challengeVector("challenge_combine_n_to_one".getBytes(StandardCharsets.UTF_8), Utils.log2((long)evalsOps.size()));

        DensePolynomial polyEvalsOps = DensePolynomial.create(evalsOps);
        for (int i = challengesOps.size() - 1; i >= 0; i--) {
            polyEvalsOps.boundPolyVarBot(challengesOps.get(i));
        }

        Scalar jointClaimEvalOps = polyEvalsOps.get(0);
        List<Scalar> rJointOps = new ArrayList<>(challengesOps);
        rJointOps.addAll(randOps);

        transcript.appendScalar("joint_claim_eval_ops".getBytes(StandardCharsets.UTF_8), jointClaimEvalOps);

        if (!proofOps.verifyPlain(
                rJointOps,
                jointClaimEvalOps,
                comm.getCommCombOps(),
                gens.getGensOps(),
                transcript
        )) {
            return false;
        }

        List<Scalar> evalsMem = List.of(evalRowAuditTs, evalColAuditTs);
        transcript.appendScalars("claim_evals_mem".getBytes(StandardCharsets.UTF_8), evalsMem);

        List<Scalar> challengesMem = transcript.challengeVector("challenge_combine_two_to_one".getBytes(StandardCharsets.UTF_8), Utils.log2((long)evalsMem.size()));

        DensePolynomial polyEvalsMem = DensePolynomial.create(evalsMem);
        for (int i = challengesMem.size() - 1; i >= 0; i--) {
            polyEvalsMem.boundPolyVarBot(challengesMem.get(i));
        }

        Scalar jointClaimEvalMem = polyEvalsMem.get(0);
        List<Scalar> rJointMem = new ArrayList<>(challengesMem);
        rJointMem.addAll(randMem);

        transcript.appendScalar("joint_claim_eval_mem".getBytes(StandardCharsets.UTF_8), jointClaimEvalMem);

        return proofMem.verifyPlain(
                rJointMem,
                jointClaimEvalMem,
                comm.getCommCombMem(),
                gens.getGensMem(),
                transcript
        ) && verifyHelper(
                new Pair<>(randMem, randOps),
                claimsRow,
                evalRowOpsVal,
                evalRowAddrVec,
                evalRowReadTsVec,
                evalRowAuditTs,
                rx,
                rHash,
                rMultisetCheck,
                scalarFactory
        ) && verifyHelper(
                new Pair<>(randMem, randOps),
                claimsCol,
                evalColOpsVal,
                evalColAddrVec,
                evalColReadTsVec,
                evalColAuditTs,
                ry,
                rHash,
                rMultisetCheck,
                scalarFactory
        );
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        Serialization.serializeScalarList(packer, evalRow.getValue1());
        Serialization.serializeScalarList(packer, evalRow.getValue2());
        Serialization.serialize(packer, evalRow.getValue3());
        Serialization.serializeScalarList(packer, evalCol.getValue1());
        Serialization.serializeScalarList(packer, evalCol.getValue2());
        Serialization.serialize(packer, evalCol.getValue3());
        Serialization.serializeScalarList(packer, evalVal);
        Serialization.serializeScalarList(packer, evalDerefs.getValue1());
        Serialization.serializeScalarList(packer, evalDerefs.getValue2());
        proofOps.pack(packer);
        proofMem.pack(packer);
        proofDerefs.pack(packer);
    }

    public static HashLayerProof unpack(MessageUnpacker unpacker, PointFactory pointFactory, ScalarFactory scalarFactory) throws IOException {
        Tuple3<List<Scalar>, List<Scalar>, Scalar> evalRow = new Tuple3<>(
                Serialization.deserializeScalarList(unpacker, scalarFactory),
                Serialization.deserializeScalarList(unpacker, scalarFactory),
                Serialization.deserializeScalar(unpacker, scalarFactory)
        );
        Tuple3<List<Scalar>, List<Scalar>, Scalar> evalCol = new Tuple3<>(
                Serialization.deserializeScalarList(unpacker, scalarFactory),
                Serialization.deserializeScalarList(unpacker, scalarFactory),
                Serialization.deserializeScalar(unpacker, scalarFactory)
        );
        List<Scalar> evalVal = Serialization.deserializeScalarList(unpacker, scalarFactory);
        Pair<List<Scalar>, List<Scalar>> evalDerefs = new Pair<>(
                Serialization.deserializeScalarList(unpacker, scalarFactory),
                Serialization.deserializeScalarList(unpacker, scalarFactory)
        );
        PolyEvalProof proofOps = PolyEvalProof.unpack(unpacker, pointFactory, scalarFactory);
        PolyEvalProof proofMem = PolyEvalProof.unpack(unpacker, pointFactory, scalarFactory);
        DerefsEvalProof proofDerefs = DerefsEvalProof.unpack(unpacker, pointFactory, scalarFactory);

        return new HashLayerProof(evalRow, evalCol, evalVal, evalDerefs, proofOps, proofMem, proofDerefs);
    }
}
