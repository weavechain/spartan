package com.weavechain.zk.spartan;

import com.weavechain.curves.Point;
import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.Commitments;
import com.weavechain.zk.spartan.generators.MultiCommitGens;
import com.weavechain.zk.spartan.util.Tuple4;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Getter
@AllArgsConstructor
public class ProductProof {

    public static String PROTOCOL_NAME = "product proof";

    private final Point alpha;

    private final Point beta;

    private final Point delta;

    private final List<Scalar> z;

    public static Tuple4<ProductProof, Point, Point, Point> prove(
            MultiCommitGens gens,
            Transcript transcript,
            RandomTape randomTape,
            Scalar x,
            Scalar rX,
            Scalar y,
            Scalar rY,
            Scalar z,
            Scalar rZ
    ) {
        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        Scalar b1 = randomTape.randomScalar("b1".getBytes(StandardCharsets.UTF_8));
        Scalar b2 = randomTape.randomScalar("b2".getBytes(StandardCharsets.UTF_8));
        Scalar b3 = randomTape.randomScalar("b3".getBytes(StandardCharsets.UTF_8));
        Scalar b4 = randomTape.randomScalar("b4".getBytes(StandardCharsets.UTF_8));
        Scalar b5 = randomTape.randomScalar("b5".getBytes(StandardCharsets.UTF_8));

        Point X = Commitments.commit(x, rX, gens);
        transcript.appendPoint("X".getBytes(StandardCharsets.UTF_8), X);

        Point Y = Commitments.commit(y, rY, gens);
        transcript.appendPoint("Y".getBytes(StandardCharsets.UTF_8), Y);

        Point Z = Commitments.commit(z, rZ, gens);
        transcript.appendPoint("Z".getBytes(StandardCharsets.UTF_8), Z);

        Point alpha = Commitments.commit(b1, b2, gens);
        transcript.appendPoint("alpha".getBytes(StandardCharsets.UTF_8), alpha);

        Point beta = Commitments.commit(b3, b4, gens);
        transcript.appendPoint("beta".getBytes(StandardCharsets.UTF_8), beta);

        MultiCommitGens gensX = new MultiCommitGens(1, List.of(X), gens.getH(), gens.getPointFactory());
        Point delta = Commitments.commit(b3, b5, gensX);
        transcript.appendPoint("delta".getBytes(StandardCharsets.UTF_8), delta);

        Scalar c = transcript.challengeScalar("c".getBytes(StandardCharsets.UTF_8));

        Scalar z1 = b1.add(c.multiply(x));
        Scalar z2 = b2.add(c.multiply(rX));
        Scalar z3 = b3.add(c.multiply(y));
        Scalar z4 = b4.add(c.multiply(rY));
        Scalar z5 = b5.add(c.multiply(rZ.subtract(rX.multiply(y))));
        List<Scalar> zz = List.of(z1, z2, z3, z4, z5);

        return new Tuple4<>(
                new ProductProof(
                        alpha,
                        beta,
                        delta,
                        zz
                ),
                X,
                Y,
                Z
        );
    }

    private boolean checkEquality(
            Point P,
            Point X,
            Scalar c,
            MultiCommitGens gens,
            Scalar z1,
            Scalar z2
    ) {
        Point lhs = P.add(X.multiply(c));
        Point rhs = Commitments.commit(z1, z2, gens);
        return lhs.equals(rhs);
    }

    public boolean verify(
            Point X,
            Point Y,
            Point Z,
            MultiCommitGens gensN,
            Transcript transcript
    ) {
        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        transcript.appendPoint("X".getBytes(StandardCharsets.UTF_8), X);
        transcript.appendPoint("Y".getBytes(StandardCharsets.UTF_8), Y);
        transcript.appendPoint("Z".getBytes(StandardCharsets.UTF_8), Z);
        transcript.appendPoint("alpha".getBytes(StandardCharsets.UTF_8), alpha);
        transcript.appendPoint("beta".getBytes(StandardCharsets.UTF_8), beta);
        transcript.appendPoint("delta".getBytes(StandardCharsets.UTF_8), delta);

        Scalar z1 = z.get(0);
        Scalar z2 = z.get(1);
        Scalar z3 = z.get(2);
        Scalar z4 = z.get(3);
        Scalar z5 = z.get(4);

        Scalar c = transcript.challengeScalar("c".getBytes(StandardCharsets.UTF_8));

        return checkEquality(alpha, X, c, gensN, z1, z2)
                && checkEquality(beta, Y, c, gensN, z3, z4)
                && checkEquality(delta, Z, c, new MultiCommitGens(1, List.of(X), gensN.getH(), gensN.getPointFactory()), z3, z5);
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        Serialization.serialize(packer, alpha);
        Serialization.serialize(packer, beta);
        Serialization.serialize(packer, delta);
        Serialization.serializeScalarList(packer, z);
    }

    public static ProductProof unpack(MessageUnpacker unpacker, PointFactory pointFactory, ScalarFactory scalarFactory) throws IOException {
        Point alpha = Serialization.deserializePoint(unpacker, pointFactory);
        Point beta = Serialization.deserializePoint(unpacker, pointFactory);
        Point delta = Serialization.deserializePoint(unpacker, pointFactory);
        List<Scalar> z = Serialization.deserializeScalarList(unpacker, scalarFactory);
        return new ProductProof(alpha, beta, delta, z);
    }
}
