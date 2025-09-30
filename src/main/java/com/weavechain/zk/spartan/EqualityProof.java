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

@Getter
@AllArgsConstructor
public class EqualityProof {

    public static String PROTOCOL_NAME = "equality proof";

    private final Point alpha;

    private final Scalar z;

    public static Tuple3<EqualityProof, Point, Point> prove(
            MultiCommitGens gens,
            Transcript transcript,
            RandomTape randomTape,
            Scalar v1,
            Scalar s1,
            Scalar v2,
            Scalar s2
    ) {
        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        Scalar r = randomTape.randomScalar("r".getBytes(StandardCharsets.UTF_8));

        Point C1 = Commitments.commit(v1, s1, gens);
        transcript.appendPoint("C1".getBytes(StandardCharsets.UTF_8), C1);

        Point C2 = Commitments.commit(v2, s2, gens);
        transcript.appendPoint("C2".getBytes(StandardCharsets.UTF_8), C2);

        Point alpha = gens.getH().multiply(r);
        transcript.appendPoint("alpha".getBytes(StandardCharsets.UTF_8), alpha);

        Scalar c = transcript.challengeScalar("c".getBytes(StandardCharsets.UTF_8));

        Scalar z = c.multiply(s1.subtract(s2)).add(r);

        return new Tuple3<>(
                new EqualityProof(alpha, z),
                C1,
                C2
        );
    }

    public boolean verify(
            Point C1,
            Point C2,
            MultiCommitGens gens,
            Transcript transcript
    ) {
        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        transcript.appendPoint("C1".getBytes(StandardCharsets.UTF_8), C1);
        transcript.appendPoint("C2".getBytes(StandardCharsets.UTF_8), C2);
        transcript.appendPoint("alpha".getBytes(StandardCharsets.UTF_8), alpha);

        Scalar c = transcript.challengeScalar("c".getBytes(StandardCharsets.UTF_8));

        Point lhs = gens.getH().multiply(z);
        Point rhs = C1.subtract(C2).multiply(c).add(alpha);

        return lhs.equals(rhs);
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        Serialization.serialize(packer, alpha);
        Serialization.serialize(packer, z);
    }

    public static EqualityProof unpack(MessageUnpacker unpacker, PointFactory pointFactory, ScalarFactory scalarFactory) throws IOException {
        Point alpha = Serialization.deserializePoint(unpacker, pointFactory);
        Scalar z = Serialization.deserializeScalar(unpacker, scalarFactory);
        return new EqualityProof(alpha, z);
    }
}
