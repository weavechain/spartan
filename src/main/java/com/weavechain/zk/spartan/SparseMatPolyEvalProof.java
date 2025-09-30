package com.weavechain.zk.spartan;

import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.DerefsCommitment;
import com.weavechain.zk.spartan.commit.SparseMatPolyCommitment;
import com.weavechain.zk.spartan.generators.SparseMatPolyCommitmentGens;
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
public class SparseMatPolyEvalProof {

    public static String PROTOCOL_NAME = "Sparse polynomial evaluation proof";

    private final DerefsCommitment commDerefs;

    private final PolyEvalNetworkProof polyEvalNetworkProof;

    public static Pair<List<Scalar>, List<Scalar>> equalize(List<Scalar> rx, List<Scalar> ry, ScalarFactory scalarFactory) {
        int rxSize = rx.size();
        int rySize = ry.size();

        if (rxSize < rySize) {
            int diff = rySize - rxSize;
            List<Scalar> rxExt = new ArrayList<>(Collections.nCopies(diff, scalarFactory.zero()));
            rxExt.addAll(rx);
            return new Pair<>(rxExt, new ArrayList<>(ry));
        } else if (rxSize > rySize) {
            int diff = rxSize - rySize;
            List<Scalar> ryExt = new ArrayList<>(Collections.nCopies(diff, scalarFactory.zero()));
            ryExt.addAll(ry);
            return new Pair<>(new ArrayList<>(rx), ryExt);
        } else {
            return new Pair<>(new ArrayList<>(rx), new ArrayList<>(ry));
        }
    }

    public static SparseMatPolyEvalProof prove(
            MultiSparseMatPolynomialAsDense dense,
            List<Scalar> rx,
            List<Scalar> ry,
            List<Scalar> evals,
            SparseMatPolyCommitmentGens gens,
            Transcript transcript,
            RandomTape randomTape
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        PointFactory pointFactory = transcript.getPointFactory();

        if (evals.size() != dense.getBatchSize()) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        Pair<List<Scalar>, List<Scalar>> eq = SparseMatPolyEvalProof.equalize(rx, ry, scalarFactory);
        List<Scalar> memRx = new EqPolynomial(eq.getValue1()).evals(scalarFactory);
        List<Scalar> memRy = new EqPolynomial(eq.getValue2()).evals(scalarFactory);

        Derefs derefs = dense.deref(memRx, memRy, scalarFactory);

        DerefsCommitment comm = derefs.commit(gens.getGensDerefs(), scalarFactory, pointFactory);
        transcript.appendCommitment("comm_poly_row_col_ops_val".getBytes(StandardCharsets.UTF_8), comm);

        List<Scalar> rMemCheck = transcript.challengeVector("challenge_r_hash".getBytes(StandardCharsets.UTF_8), 2);

        PolyEvalNetwork net = PolyEvalNetwork.create(
                dense,
                derefs,
                memRx,
                memRy,
                new Pair<>(rMemCheck.get(0), rMemCheck.get(1)),
                scalarFactory
        );


        PolyEvalNetworkProof polyEvalNetworkProof = PolyEvalNetworkProof.prove(
                net,
                dense,
                derefs,
                evals,
                gens,
                transcript,
                randomTape
        );

        return new SparseMatPolyEvalProof(
                comm,
                polyEvalNetworkProof
        );
    }

    public boolean verify(
            SparseMatPolyCommitment comm,
            List<Scalar> rx,
            List<Scalar> ry,
            List<Scalar> evals,
            SparseMatPolyCommitmentGens gens,
            Transcript transcript
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        Pair<List<Scalar>, List<Scalar>> eq = SparseMatPolyEvalProof.equalize(rx, ry, scalarFactory);
        List<Scalar> rxExt = eq.getValue1();
        List<Scalar> ryExt = eq.getValue2();

        long nz = comm.getNumOps();
        long numMemCells = comm.getNumMemCells();
        if ((1L << rxExt.size()) != numMemCells) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        transcript.appendCommitment("comm_poly_row_col_ops_val".getBytes(StandardCharsets.UTF_8), commDerefs);

        List<Scalar> rMemCheck = transcript.challengeVector("challenge_r_hash".getBytes(StandardCharsets.UTF_8), 2);

        return polyEvalNetworkProof.verify(
                comm,
                commDerefs,
                evals,
                gens,
                rxExt,
                ryExt,
                rMemCheck,
                nz,
                transcript
        );
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        commDerefs.pack(packer);
        polyEvalNetworkProof.pack(packer);
    }

    public static SparseMatPolyEvalProof unpack(MessageUnpacker unpacker, PointFactory pointFactory, ScalarFactory scalarFactory) throws IOException {
        DerefsCommitment commDerefs = DerefsCommitment.unpack(unpacker, pointFactory);
        PolyEvalNetworkProof polyEvalNetworkProof = PolyEvalNetworkProof.unpack(unpacker, pointFactory, scalarFactory);
        return new SparseMatPolyEvalProof(commDerefs, polyEvalNetworkProof);
    }
}
