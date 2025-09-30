package com.weavechain.zk.spartan;

import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.DerefsCommitment;
import com.weavechain.zk.spartan.commit.PolyCommitment;
import com.weavechain.zk.spartan.generators.PolyCommitmentGens;
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
public class DerefsEvalProof {

    public static String PROTOCOL_NAME = "Derefs evaluation proof";

    private final PolyEvalProof proofDerefs;

    public static PolyEvalProof proveSingle(
            DensePolynomial jointPoly,
            List<Scalar> r,
            List<Scalar> evals,
            PolyCommitmentGens gens,
            Transcript transcript,
            RandomTape randomTape
    ) {
        if (jointPoly.getNumVars() != r.size() + Utils.log2((long)evals.size())) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        transcript.appendScalars("evals_ops_val".getBytes(StandardCharsets.UTF_8), evals);

        List<Scalar> challenges = transcript.challengeVector("challenge_combine_n_to_one".getBytes(StandardCharsets.UTF_8), Utils.log2((long)evals.size()));

        DensePolynomial polyEvals = DensePolynomial.create(evals);
        for (int i = challenges.size() - 1; i >= 0; i--) {
            polyEvals.boundPolyVarBot(challenges.get(i));
        }

        Scalar jointClaimEval = polyEvals.get(0);
        List<Scalar> rJoint = new ArrayList<>(challenges);
        rJoint.addAll(r);

        transcript.appendScalar("joint_claim_eval".getBytes(StandardCharsets.UTF_8), jointClaimEval);

        return PolyEvalProof.prove(
                jointPoly,
                null,
                rJoint,
                jointClaimEval,
                null,
                gens,
                transcript,
                randomTape
        ).getValue1();
    }

    public static DerefsEvalProof prove(
            Derefs derefs,
            List<Scalar> evalRowOpsValVec,
            List<Scalar> evalColOpsValVec,
            List<Scalar> r,
            PolyCommitmentGens gens,
            Transcript transcript,
            RandomTape randomTape
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        Scalar ZERO = scalarFactory.zero();

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        List<Scalar> evals = new ArrayList<>(evalRowOpsValVec);
        evals.addAll(evalColOpsValVec);
        int len = (int)Utils.nextPow2(evals.size());
        for (int i = evals.size(); i < len; i++) {
            evals.add(ZERO);
        }

        PolyEvalProof proofDerefs = proveSingle(derefs.getComb(), r, evals, gens, transcript, randomTape);

        return new DerefsEvalProof(
                proofDerefs
        );
    }

    public boolean verifySingle(
            PolyEvalProof proof,
            PolyCommitment comm,
            List<Scalar> r,
            List<Scalar> evals,
            PolyCommitmentGens gens,
            Transcript transcript
    ) {
        transcript.appendScalars("evals_ops_val".getBytes(StandardCharsets.UTF_8), evals);

        List<Scalar> challenges = transcript.challengeVector("challenge_combine_n_to_one".getBytes(StandardCharsets.UTF_8), Utils.log2((long)evals.size()));

        DensePolynomial polyEvals = DensePolynomial.create(evals);
        for (int i = challenges.size() - 1; i >= 0; i--) {
            polyEvals.boundPolyVarBot(challenges.get(i));
        }

        Scalar jointClaimEval = polyEvals.get(0);
        List<Scalar> rJoint = new ArrayList<>(challenges);
        rJoint.addAll(r);

        transcript.appendScalar("joint_claim_eval".getBytes(StandardCharsets.UTF_8), jointClaimEval);

        return proof.verifyPlain(
                rJoint,
                jointClaimEval,
                comm,
                gens,
                transcript
        );
    }

    public boolean verify(
            List<Scalar> r,
            List<Scalar> evalRowOpsValVec,
            List<Scalar> evalColOpsValVec,
            DerefsCommitment comm,
            PolyCommitmentGens gens,
            Transcript transcript
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        Scalar ZERO = scalarFactory.zero();

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        List<Scalar> evals = new ArrayList<>(evalRowOpsValVec);
        evals.addAll(evalColOpsValVec);
        for (int i = evals.size(); i < Utils.nextPow2(evals.size()); i++) {
            evals.add(ZERO);
        }

        return verifySingle(
                proofDerefs,
                comm.getCommOpsVal(),
                r,
                evals,
                gens,
                transcript
        );
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        proofDerefs.pack(packer);
    }

    public static DerefsEvalProof unpack(MessageUnpacker unpacker, PointFactory pointFactory, ScalarFactory scalarFactory) throws IOException {
        PolyEvalProof proofDerefs = PolyEvalProof.unpack(unpacker, pointFactory, scalarFactory);
        return new DerefsEvalProof(proofDerefs);
    }
}
