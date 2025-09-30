package com.weavechain.zk.spartan;

import com.weavechain.curves.Point;
import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.commit.Commitments;
import com.weavechain.zk.spartan.generators.DotProductProofGens;
import com.weavechain.zk.spartan.generators.MultiCommitGens;
import com.weavechain.zk.spartan.util.Tuple3;
import com.weavechain.zk.spartan.util.Tuple6;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Getter
@AllArgsConstructor
public class DotProductProofLog {

    public static String PROTOCOL_NAME = "dot product proof (log)";

    private final BulletReductionProof bulletReductionProof;

    private final Point delta;

    private final Point beta;

    private final Scalar z1;

    private final Scalar z2;

    public static Scalar computeDotProduct(List<Scalar> a, List<Scalar> b, ScalarFactory scalarFactory) {
        if (a.size() != b.size()) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        Scalar res = scalarFactory.zero();
        for (int i = 0; i < a.size(); i++) {
            Scalar e = a.get(i).multiply(b.get(i));
            res = res.add(e);
        }
        return res;
    }

    public static Tuple3<DotProductProofLog, Point, Point> prove(
            DotProductProofGens gens,
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

        long n = xVec.size();
        if (n != aVec.size() || gens.getN() != n) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        Scalar d = randomTape.randomScalar("d".getBytes(StandardCharsets.UTF_8));
        Scalar rDelta = randomTape.randomScalar("r_delta".getBytes(StandardCharsets.UTF_8));

        Scalar rBeta = randomTape.randomScalar("r_beta".getBytes(StandardCharsets.UTF_8));
        List<Scalar> blindsVec1 = randomTape.randomVector("blinds_vec_1".getBytes(StandardCharsets.UTF_8), 2 * Utils.log2(n));
        List<Scalar> blindsVec2 = randomTape.randomVector("blinds_vec_2".getBytes(StandardCharsets.UTF_8), 2 * Utils.log2(n));

        Point Cx = Commitments.batchCommit(xVec, blindX, gens.getGensN(), pointFactory);
        transcript.appendPoint("Cx".getBytes(StandardCharsets.UTF_8), Cx);

        Point Cy = Commitments.commit(y, blindY, gens.getGens1());
        transcript.appendPoint("Cy".getBytes(StandardCharsets.UTF_8), Cy);

        transcript.appendScalars("a".getBytes(StandardCharsets.UTF_8), aVec);

        Scalar blindGamma = blindX.add(blindY);

        Tuple6<BulletReductionProof, Point, Scalar, Scalar, Point, Scalar> bres = BulletReductionProof.prove(
                transcript,
                gens.getGens1().getG().get(0),
                gens.getGensN().getG(),
                gens.getGensN().getH(),
                xVec,
                aVec,
                blindGamma,
                blindsVec1,
                blindsVec2
        );
        BulletReductionProof bulletReductionProof = bres.getValue1();
        Point gammaHat = bres.getValue2();
        Scalar xHat = bres.getValue3();
        Scalar aHat = bres.getValue4();
        Point gHat = bres.getValue5();
        Scalar rHatGamma = bres.getValue6();

        Scalar yHat = xHat.multiply(aHat);

        MultiCommitGens gensHat = new MultiCommitGens(1, List.of(gHat), gens.getGens1().getH(), pointFactory);
        Point delta = Commitments.commit(d, rDelta, gensHat);
        transcript.appendPoint("delta".getBytes(StandardCharsets.UTF_8), delta);

        Point beta = Commitments.commit(d, rBeta, gens.getGens1());
        transcript.appendPoint("beta".getBytes(StandardCharsets.UTF_8), beta);

        Scalar c = transcript.challengeScalar("c".getBytes(StandardCharsets.UTF_8));

        Scalar z1 = d.add(c.multiply(yHat));
        Scalar z2 = aHat.multiply(c.multiply(rHatGamma).add(rBeta)).add(rDelta);

        return new Tuple3<>(
                new DotProductProofLog(
                        bulletReductionProof,
                        delta,
                        beta,
                        z1,
                        z2
                ),
                Cx,
                Cy
        );

    }

    public boolean verify(
            long n,
            List<Scalar> a,
            Point Cx,
            Point Cy,
            DotProductProofGens gens,
            Transcript transcript
    ) {
        if (gens.getN() != n || a.size() != n) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        transcript.appendMessage("protocol-name".getBytes(StandardCharsets.UTF_8), PROTOCOL_NAME.getBytes(StandardCharsets.UTF_8));

        transcript.appendPoint("Cx".getBytes(StandardCharsets.UTF_8), Cx);
        transcript.appendPoint("Cy".getBytes(StandardCharsets.UTF_8), Cy);
        transcript.appendScalars("a".getBytes(StandardCharsets.UTF_8), a);

        Point gamma = Cx.add(Cy);
        Tuple3<Point, Point, Scalar> bp = bulletReductionProof.verify(n, a, gamma, gens.getGensN().getG(), transcript);
        if (bp == null) {
            return false;
        }
        Point gHat = bp.getValue1();
        Point gammaHat = bp.getValue2();
        Scalar aHat = bp.getValue3();

        transcript.appendPoint("delta".getBytes(StandardCharsets.UTF_8), delta);
        transcript.appendPoint("beta".getBytes(StandardCharsets.UTF_8), beta);

        Scalar c = transcript.challengeScalar("c".getBytes(StandardCharsets.UTF_8));

        Point lhs = gammaHat.multiply(c).add(beta).multiply(aHat).add(delta);
        Point rhs = gHat.add(gens.getGens1().getG().get(0).multiply(aHat)).multiply(z1).add(gens.getGens1().getH().multiply(z2));
        return lhs.equals(rhs);
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        bulletReductionProof.pack(packer);
        Serialization.serialize(packer, delta);
        Serialization.serialize(packer, beta);
        Serialization.serialize(packer, z1);
        Serialization.serialize(packer, z2);
    }

    public static DotProductProofLog unpack(MessageUnpacker unpacker, PointFactory pointFactory, ScalarFactory scalarFactory) throws IOException {
        BulletReductionProof bulletReductionProof = BulletReductionProof.unpack(unpacker, pointFactory);
        Point delta = Serialization.deserializePoint(unpacker, pointFactory);
        Point beta = Serialization.deserializePoint(unpacker, pointFactory);
        Scalar z1 = Serialization.deserializeScalar(unpacker, scalarFactory);
        Scalar z2 = Serialization.deserializeScalar(unpacker, scalarFactory);
        return new DotProductProofLog(bulletReductionProof, delta, beta, z1, z2);
    }
}
