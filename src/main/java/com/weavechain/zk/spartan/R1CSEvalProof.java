package com.weavechain.zk.spartan;

import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.R1CSCommitment;
import com.weavechain.zk.spartan.commit.R1CSDecommitment;
import com.weavechain.zk.spartan.generators.R1CSCommitmentGens;
import com.weavechain.zk.spartan.util.Tuple3;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.util.List;

@Getter
@AllArgsConstructor
public class R1CSEvalProof {

    private final SparseMatPolyEvalProof proof;

    public static R1CSEvalProof prove(
            R1CSDecommitment decomm,
            List<Scalar> rx,
            List<Scalar> ry,
            List<Scalar> evals,
            R1CSCommitmentGens gens,
            Transcript transcript,
            RandomTape randomTape
    ) {
        SparseMatPolyEvalProof proof = SparseMatPolyEvalProof.prove(
                decomm.getDense(),
                rx,
                ry,
                evals,
                gens.getGens(),
                transcript,
                randomTape
        );

        return new R1CSEvalProof(proof);
    }

    public boolean verify(
            R1CSCommitment comm,
            List<Scalar> rx,
            List<Scalar> ry,
            Tuple3<Scalar, Scalar, Scalar> evals,
            R1CSCommitmentGens gens,
            Transcript transcript
    ) {
        return proof.verify(
                comm.getComm(),
                rx,
                ry,
                List.of(evals.getValue1(), evals.getValue2(), evals.getValue3()),
                gens.getGens(),
                transcript
        );
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        proof.pack(packer);
    }

    public static R1CSEvalProof unpack(MessageUnpacker unpacker, PointFactory pointFactory, ScalarFactory scalarFactory) throws IOException {
        SparseMatPolyEvalProof proof = SparseMatPolyEvalProof.unpack(unpacker, pointFactory, scalarFactory);
        return new R1CSEvalProof(proof);
    }
}
