package com.weavechain.zk.spartan;

import com.weavechain.curves.Point;
import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.Commitments;
import com.weavechain.zk.spartan.generators.MultiCommitGens;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Getter
@AllArgsConstructor
public class KnowledgeProof {

    private static final String PROTOCOL_NAME = "knowledge proof";

    private final Point alpha;

    private final Scalar z1;

    private final Scalar z2;

    public static Pair<KnowledgeProof, Point> prove(
            MultiCommitGens gens,
            Transcript transcript,
            RandomTape randomTape,
            Scalar x,
            Scalar r
    ) {
        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        Scalar t1 = randomTape.randomScalar("t1".getBytes(StandardCharsets.UTF_8));
        Scalar t2 = randomTape.randomScalar("t2".getBytes(StandardCharsets.UTF_8));

        Point C = Commitments.commit(x, r, gens);
        transcript.appendPoint("C".getBytes(StandardCharsets.UTF_8), C);

        Point alpha = Commitments.commit(t1, t2, gens);
        transcript.appendPoint("alpha".getBytes(StandardCharsets.UTF_8), alpha);

        Scalar c = transcript.challengeScalar("c".getBytes(StandardCharsets.UTF_8));

        Scalar z1 = x.multiply(c).add(t1);
        Scalar z2 = r.multiply(c).add(t2);

        return new Pair<>(
            new KnowledgeProof(
                    alpha,
                    z1,
                    z2
            ),
            C
        );
    }

    public boolean verify(
            MultiCommitGens gensN,
            Point C,
            Transcript transcript
    ) {
        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        transcript.appendPoint("C".getBytes(StandardCharsets.UTF_8), C);
        transcript.appendPoint("alpha".getBytes(StandardCharsets.UTF_8), alpha);

        Scalar c = transcript.challengeScalar("c".getBytes(StandardCharsets.UTF_8));

        Point lhs = Commitments.commit(z1, z2, gensN);
        Point rhs = C.multiply(c).add(alpha);

        return lhs.equals(rhs);
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        Serialization.serialize(packer, alpha);
        Serialization.serialize(packer, z1);
        Serialization.serialize(packer, z2);
    }

    public static KnowledgeProof unpack(MessageUnpacker unpacker, PointFactory pointFactory, ScalarFactory scalarFactory) throws IOException {
        Point alpha = Serialization.deserializePoint(unpacker, pointFactory);
        Scalar z1 = Serialization.deserializeScalar(unpacker, scalarFactory);
        Scalar z2 = Serialization.deserializeScalar(unpacker, scalarFactory);
        return new KnowledgeProof(alpha, z1, z2);
    }
}
