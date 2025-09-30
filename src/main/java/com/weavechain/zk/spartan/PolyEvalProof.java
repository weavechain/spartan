package com.weavechain.zk.spartan;

import com.weavechain.curves.Point;
import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.Commitments;
import com.weavechain.zk.spartan.commit.PolyCommitment;
import com.weavechain.zk.spartan.generators.PolyCommitmentGens;
import com.weavechain.zk.spartan.util.Tuple3;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Getter
@AllArgsConstructor
public class PolyEvalProof {

    public static String PROTOCOL_NAME = "polynomial evaluation proof";

    private final DotProductProofLog proof;

    public static Pair<PolyEvalProof, Point> prove(
            DensePolynomial poly,
            PolyCommitmentBlinds blindsOpt,
            List<Scalar> r,
            Scalar Zr,
            Scalar blindZrOpt,
            PolyCommitmentGens gens,
            Transcript transcript,
            RandomTape randomTape
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        Scalar ZERO = scalarFactory.zero();

        if (poly.getNumVars() != r.size()) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        int lSize = 1 << (r.size() / 2);
        int rSize = 1 << (r.size() - r.size() / 2);

        List<Scalar> blinds = blindsOpt != null ? blindsOpt.getBlinds() : new PolyCommitmentBlinds(Collections.nCopies(lSize, ZERO)).getBlinds();
        if (blinds.size() != lSize) {
            throw new IllegalArgumentException("Invalid blinds size");
        }

        Scalar blindZr = blindZrOpt != null ? blindZrOpt : ZERO;

        EqPolynomial eq = new EqPolynomial(r);
        Pair<List<Scalar>, List<Scalar>> eres = eq.computeFactoredEvals(scalarFactory);
        List<Scalar> L = eres.getValue1();
        List<Scalar> R = eres.getValue2();
        if (L.size() != lSize || R.size() != rSize) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        List<Scalar> LZ = poly.bound(L, scalarFactory);
        Scalar LZBlind = ZERO;
        for (int i = 0; i < L.size(); i++) {
            Scalar b = blinds.get(i).multiply(L.get(i));
            LZBlind = LZBlind.add(b);
        }

        Tuple3<DotProductProofLog, Point, Point> dpres = DotProductProofLog.prove(
                gens.getGens(),
                transcript,
                randomTape,
                LZ,
                LZBlind,
                R,
                Zr,
                blindZr
        );

        return new Pair<>(
                new PolyEvalProof(dpres.getValue1()),
                dpres.getValue3()
        );
    }

    public boolean verify(
            List<Scalar> r,
            Point C_Zr,
            PolyCommitment comm,
            PolyCommitmentGens gens,
            Transcript transcript
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        PointFactory pointFactory = transcript.getPointFactory();

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        EqPolynomial eq = new EqPolynomial(r);
        Pair<List<Scalar>, List<Scalar>> res = eq.computeFactoredEvals(scalarFactory);
        List<Scalar> L = res.getValue1();
        List<Scalar> R = res.getValue2();

        Point C_LZ = pointFactory.multiscalarMul(L, comm.getC());
        return proof.verify(
                R.size(),
                R,
                C_LZ,
                C_Zr,
                gens.getGens(),
                transcript
        );
    }

    public boolean verifyPlain(
            List<Scalar> r,
            Scalar Zr,
            PolyCommitment comm,
            PolyCommitmentGens gens,
            Transcript transcript
    ) {
        Scalar ZERO = transcript.getScalarFactory().zero();

        Point C_Zr = Commitments.commit(Zr, ZERO, gens.getGens().getGens1());
        return verify(r, C_Zr, comm, gens, transcript);
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        proof.pack(packer);
    }

    public static PolyEvalProof unpack(MessageUnpacker unpacker, PointFactory pointFactory, ScalarFactory scalarFactory) throws IOException {
        DotProductProofLog proof = DotProductProofLog.unpack(unpacker, pointFactory, scalarFactory);
        return new PolyEvalProof(proof);
    }
}
