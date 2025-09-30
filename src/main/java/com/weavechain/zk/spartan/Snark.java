
package com.weavechain.zk.spartan;

import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.R1CSCommitment;
import com.weavechain.zk.spartan.commit.R1CSDecommitment;
import com.weavechain.zk.spartan.generators.SNARKGens;
import com.weavechain.zk.spartan.util.Tuple3;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Getter
@AllArgsConstructor
public class Snark {

    public static String PROTOCOL_NAME = "Spartan SNARK proof";

    private final R1CSProof r1csSatProof;

    private final List<Scalar> instEvals;

    private final R1CSEvalProof r1csEvalProof;

    public static Pair<R1CSCommitment, R1CSDecommitment> encode(R1CSInstance inst, SNARKGens gens, ScalarFactory scalarFactory, PointFactory pointFactory) {
        return inst.commit(gens.getGensR1csEval(), scalarFactory, pointFactory);
    }

    public static Snark prove(
            R1CS circuit,
            SNARKGens gens,
            Transcript transcript,
            RandomTape randomTape
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        PointFactory pointFactory = transcript.getPointFactory();

        Pair<R1CSCommitment, R1CSDecommitment> snark = Snark.encode(circuit.getInst(), gens, scalarFactory, pointFactory);
        R1CSCommitment comm = snark.getValue1();
        R1CSDecommitment decomm = snark.getValue2();

        R1CSInstance inst = circuit.getInst();
        Assignment vars = circuit.getVarsAssignment();
        Assignment inputs = circuit.getInputsAssignment();

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        transcript.appendCommitment("comm".getBytes(StandardCharsets.UTF_8), comm);

        long numPaddedVars = inst.getNumVars();
        long numVars = vars.getAssignment().size();
        Assignment paddedVars = numPaddedVars > numVars ? Assignment.createPadded(vars, (int)numPaddedVars, transcript.getScalarFactory()) : vars;

        Tuple3<R1CSProof, List<Scalar>, List<Scalar>> res = R1CSProof.prove(
                inst,
                paddedVars.getAssignment(),
                inputs.getAssignment(),
                gens.getGensR1csSat(),
                transcript,
                randomTape
        );
        R1CSProof r1csProof = res.getValue1();
        List<Scalar> rx = res.getValue2();
        List<Scalar> ry = res.getValue3();

        List<Scalar> instEvals = inst.evaluate(rx, ry, transcript.getScalarFactory());
        Scalar Ar = instEvals.get(0);
        Scalar Br = instEvals.get(1);
        Scalar Cr = instEvals.get(2);
        transcript.appendScalar("Ar_claim".getBytes(StandardCharsets.UTF_8), Ar);
        transcript.appendScalar("Br_claim".getBytes(StandardCharsets.UTF_8), Br);
        transcript.appendScalar("Cr_claim".getBytes(StandardCharsets.UTF_8), Cr);

        R1CSEvalProof r1csEvalProof = R1CSEvalProof.prove(
                decomm,
                rx,
                ry,
                instEvals,
                gens.getGensR1csEval(),
                transcript,
                randomTape
        );

        return new Snark(
                r1csProof,
                instEvals,
                r1csEvalProof
        );
    }

    public boolean verify(
            R1CS circuit,
            Assignment input,
            SNARKGens gens,
            Transcript transcript
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        PointFactory pointFactory = transcript.getPointFactory();

        Pair<R1CSCommitment, R1CSDecommitment> snark = Snark.encode(circuit.getInst(), gens, scalarFactory, pointFactory);
        R1CSCommitment comm = snark.getValue1();
        R1CSDecommitment decomm = snark.getValue2();

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));
        transcript.appendCommitment("comm".getBytes(StandardCharsets.UTF_8), comm);

        if (input.getAssignment().size() != comm.getNumInputs()) {
            throw new IllegalArgumentException("Invalid input size");
        }

        Scalar Ar = instEvals.get(0);
        Scalar Br = instEvals.get(1);
        Scalar Cr = instEvals.get(2);

        Pair<List<Scalar>, List<Scalar>> sat = r1csSatProof.verify(
                comm.getNumVars(),
                comm.getNumCons(),
                input.getAssignment(),
                new Tuple3<>(Ar, Br, Cr),
                transcript,
                gens.getGensR1csSat()
        );
        if (sat == null) {
            return false;
        }
        List<Scalar> rx = sat.getValue1();
        List<Scalar> ry = sat.getValue2();

        transcript.appendScalar("Ar_claim".getBytes(StandardCharsets.UTF_8), Ar);
        transcript.appendScalar("Br_claim".getBytes(StandardCharsets.UTF_8), Br);
        transcript.appendScalar("Cr_claim".getBytes(StandardCharsets.UTF_8), Cr);

        return r1csEvalProof.verify(
                comm,
                rx,
                ry,
                new Tuple3<>(Ar, Br, Cr),
                gens.getGensR1csEval(),
                transcript
        );
    }


    public byte[] serialize() throws IOException {
        MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
        r1csSatProof.pack(packer);
        Serialization.serializeScalarList(packer, instEvals);
        r1csEvalProof.pack(packer);
        packer.close();

        return packer.toMessageBuffer().toByteArray();
    }

    public static Snark deserialize(byte[] data, PointFactory pointFactory, ScalarFactory scalarFactory) throws IOException {
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data);
        R1CSProof r1csSatProof = R1CSProof.unpack(unpacker, pointFactory, scalarFactory);
        List<Scalar> instEvals = Serialization.deserializeScalarList(unpacker, scalarFactory);
        R1CSEvalProof r1csEvalProof = R1CSEvalProof.unpack(unpacker, pointFactory, scalarFactory);
        unpacker.close();

        return new Snark(r1csSatProof, instEvals, r1csEvalProof);
    }
}
