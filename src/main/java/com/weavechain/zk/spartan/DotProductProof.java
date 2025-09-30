package com.weavechain.zk.spartan;

import com.weavechain.curves.Point;
import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.Commitments;
import com.weavechain.zk.spartan.generators.MultiCommitGens;
import com.weavechain.zk.spartan.util.Tuple3;
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
public class DotProductProof {

    public static String PROTOCOL_NAME = "dot product proof";

    private final Point delta;

    private final Point beta;

    private final List<Scalar> z;

    private final Scalar zDelta;

    private final Scalar zBeta;

    public static Scalar computeDotProduct(List<Scalar> a, List<Scalar> b, ScalarFactory scalarFactory) {
        if (a.size() != b.size()) {
            throw new IllegalArgumentException("Invalid size");
        }

        Scalar result = scalarFactory.zero();
        for (int i = 0; i < a.size(); i++) {
            Scalar e = a.get(i).multiply(b.get(i));
            result = result.add(e);
        }
        return result;
    }

    public static Tuple3<DotProductProof, Point, Point> prove(
            MultiCommitGens gens1,
            MultiCommitGens gensn,
            Transcript transcript,
            RandomTape randomTape,
            List<Scalar> xVec,
            Scalar blindX,
            List<Scalar> aVec,
            Scalar y,
            Scalar blindY
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        PointFactory pointFactory = transcript.getPointFactory();

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        int n = xVec.size();
        if (n != aVec.size() || gensn.getN() != aVec.size() || gens1.getN() != 1) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        List<Scalar> dVec = randomTape.randomVector("d_vec".getBytes(StandardCharsets.UTF_8), n);
        Scalar rDelta = randomTape.randomScalar("r_delta".getBytes(StandardCharsets.UTF_8));
        Scalar rBeta = randomTape.randomScalar("r_beta".getBytes(StandardCharsets.UTF_8));

        Point Cx = Commitments.batchCommit(xVec, blindX, gensn, pointFactory);
        transcript.appendPoint("Cx".getBytes(StandardCharsets.UTF_8), Cx);

        Point Cy = Commitments.commit(y, blindY, gens1);
        transcript.appendPoint("Cy".getBytes(StandardCharsets.UTF_8), Cy);

        transcript.appendScalars("a".getBytes(StandardCharsets.UTF_8), aVec);

        Point delta = Commitments.batchCommit(dVec, rDelta, gensn, pointFactory);
        transcript.appendPoint("delta".getBytes(StandardCharsets.UTF_8), delta);

        Scalar dotProductAD = computeDotProduct(aVec, dVec, scalarFactory);

        Point beta = Commitments.commit(dotProductAD, rBeta, gens1);
        transcript.appendPoint("beta".getBytes(StandardCharsets.UTF_8), beta);

        Scalar c = transcript.challengeScalar("c".getBytes(StandardCharsets.UTF_8));

        List<Scalar> z = new ArrayList<>();
        for (int i = 0; i < dVec.size(); i++) {
            z.add(c.multiply(xVec.get(i)).add(dVec.get(i)));
        }

        Scalar zDelta = c.multiply(blindX).add(rDelta);
        Scalar zBeta = c.multiply(blindY).add(rBeta);

        return new Tuple3<>(
                new DotProductProof(
                        delta,
                        beta,
                        z,
                        zDelta,
                        zBeta
                ),
                Cx,
                Cy
        );
    }

    public boolean verify(
            List<Scalar> a,
            Point Cx,
            Point Cy,
            MultiCommitGens gens1,
            MultiCommitGens gensN,
            Transcript transcript
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        PointFactory pointFactory = transcript.getPointFactory();

        if (gensN.getN() != a.size() || gens1.getN() != 1) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        transcript.appendPoint("Cx".getBytes(StandardCharsets.UTF_8), Cx);
        transcript.appendPoint("Cy".getBytes(StandardCharsets.UTF_8), Cy);
        transcript.appendScalars("a".getBytes(StandardCharsets.UTF_8), a);
        transcript.appendPoint("delta".getBytes(StandardCharsets.UTF_8), delta);
        transcript.appendPoint("beta".getBytes(StandardCharsets.UTF_8), beta);

        Scalar c = transcript.challengeScalar("c".getBytes(StandardCharsets.UTF_8));

        Point comm1 = Commitments.batchCommit(z, zDelta, gensN, pointFactory);
        if (!Cx.multiply(c).add(delta).equals(comm1)) {
            return false;
        }

        Scalar dotprodZA = DotProductProof.computeDotProduct(z, a, scalarFactory);
        Point comm2 = Commitments.commit(dotprodZA, zBeta, gens1);
        return Cy.multiply(c).add(beta).equals(comm2);
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        Serialization.serialize(packer, delta);
        Serialization.serialize(packer, beta);
        Serialization.serializeScalarList(packer, z);
        Serialization.serialize(packer, zDelta);
        Serialization.serialize(packer, zBeta);
    }

    public static DotProductProof unpack(MessageUnpacker unpacker, PointFactory pointFactory, ScalarFactory scalarFactory) throws IOException {
        Point delta = Serialization.deserializePoint(unpacker, pointFactory);
        Point beta = Serialization.deserializePoint(unpacker, pointFactory);
        List<Scalar> z = Serialization.deserializeScalarList(unpacker, scalarFactory);
        Scalar zDelta = Serialization.deserializeScalar(unpacker, scalarFactory);
        Scalar zBeta = Serialization.deserializeScalar(unpacker, scalarFactory);
        return new DotProductProof(delta, beta, z, zDelta, zBeta);
    }
}
