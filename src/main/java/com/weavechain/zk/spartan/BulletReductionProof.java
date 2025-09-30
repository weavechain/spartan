package com.weavechain.zk.spartan;

import com.weavechain.curves.Point;
import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import com.weavechain.zk.spartan.util.Tuple3;
import com.weavechain.zk.spartan.util.Tuple6;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
@AllArgsConstructor
public class BulletReductionProof {

    private final List<Point> lVec;

    private final List<Point> rVec;

    public static Tuple6<BulletReductionProof, Point, Scalar, Scalar, Point, Scalar> prove(
            Transcript transcript,
            Point Q,
            List<Point> G,
            Point H,
            List<Scalar> a,
            List<Scalar> b,
            Scalar blind,
            List<Scalar> blindVec1,
            List<Scalar> blindVec2
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        PointFactory pointFactory = transcript.getPointFactory();

        int n = G.size();
        int lgn = (int)Utils.log2((long)n);

        if (n != (1 << lgn) || n != a.size()) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        List<Point> lVec = new CopyOnWriteArrayList<>();
        List<Point> rVec = new CopyOnWriteArrayList<>();

        Scalar blindFin = blind;
        int bidx = 0;

        while (n != 1) {
            n = n / 2;

            List<Scalar> aL = a.subList(0, n);
            List<Scalar> aR = a.subList(n, a.size());
            List<Scalar> bL = b.subList(0, n);
            List<Scalar> bR = b.subList(n, b.size());
            List<Point> GL = G.subList(0, n);
            List<Point> GR = G.subList(n, G.size());

            Scalar cL = DotProductProof.computeDotProduct(aL, bR, scalarFactory);
            Scalar cR = DotProductProof.computeDotProduct(aR, bL, scalarFactory);

            Scalar blindL = blindVec1.get(bidx);
            Scalar blindR = blindVec2.get(bidx);
            bidx++;

            List<Scalar> scalars1 = new ArrayList<>(aL);
            scalars1.add(cL);
            scalars1.add(blindL);
            List<Point> bases1 = new ArrayList<>(GR);
            bases1.add(Q);
            bases1.add(H);
            Point L = pointFactory.multiscalarMul(scalars1, bases1);

            List<Scalar> scalars2 = new ArrayList<>(aR);
            scalars2.add(cR);
            scalars2.add(blindR);
            List<Point> bases2 = new ArrayList<>(GL);
            bases2.add(Q);
            bases2.add(H);
            Point R = pointFactory.multiscalarMul(scalars2, bases2);

            transcript.appendPoint("L".getBytes(StandardCharsets.UTF_8), L);
            transcript.appendPoint("R".getBytes(StandardCharsets.UTF_8), R);

            Scalar u = transcript.challengeScalar("u".getBytes(StandardCharsets.UTF_8));
            Scalar uinv = u.invert();

            for (int i = 0; i < n; i++) {
                aL.set(i, aL.get(i).multiply(u).add(uinv.multiply(aR.get(i))));
                bL.set(i, bL.get(i).multiply(uinv).add(u.multiply(bR.get(i))));

                GL.set(i, GL.get(i).multiply(uinv).add(GR.get(i).multiply(u)));
            }

            blindFin = blindFin.add(blindL.multiply(u.multiply(u)).add(blindR.multiply(uinv.multiply(uinv))));

            lVec.add(L);
            rVec.add(R);

            a = aL;
            b = bL;
            G = GL;
        }

        Point gammaHat = G.get(0).multiply(a.get(0)).add(Q.multiply(a.get(0)).multiply(b.get(0))).add(H.multiply(blindFin));

        return new Tuple6<>(
                new BulletReductionProof(lVec, rVec),
                gammaHat,
                a.get(0),
                b.get(0),
                G.get(0),
                blindFin
        );
    }

    private Tuple3<List<Scalar>,List<Scalar>, List<Scalar>> verificationScalars(long n, Transcript transcript) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        Scalar ONE = scalarFactory.one();

        long lgn = lVec.size();
        if (lgn >= 32) {
            throw new IllegalArgumentException("Overflow");
        }

        if (n != (1L << lgn)) {
            throw new IllegalArgumentException("Invalid sizes");
        }

        List<Scalar> challengesSq = new ArrayList<>();
        List<Scalar> challengesInvSq = new ArrayList<>();
        Scalar allInv = ONE;
        for (int i = 0; i < lVec.size(); i++) {
            transcript.appendPoint("L".getBytes(StandardCharsets.UTF_8), lVec.get(i));
            transcript.appendPoint("R".getBytes(StandardCharsets.UTF_8), rVec.get(i));

            Scalar c = transcript.challengeScalar("u".getBytes(StandardCharsets.UTF_8));
            Scalar cinv = c.invert();
            allInv = allInv.multiply(cinv);

            challengesSq.add(c.square());
            challengesInvSq.add(cinv.square());
        }

        List<Scalar> s = new ArrayList<>();
        s.add(allInv);
        for (long i = 1; i < n; i++) {
            long lgi = Utils.log2(i);
            long k = 1L << lgi;
            Scalar uLgiSq = challengesSq.get((int)(lgn - 1 - lgi));
            s.add(s.get((int)(i - k)).multiply(uLgiSq));
        }

        return new Tuple3<>(
                challengesSq,
                challengesInvSq,
                s
        );
    }

    private Scalar innerProduct(List<Scalar> left, List<Scalar> right, ScalarFactory scalarFactory) {
        Scalar result = scalarFactory.zero();
        for (int i = 0; i < left.size(); i++) {
            Scalar p = left.get(i).multiply(right.get(i));
            result = result.add(p);
        }
        return result;

    }

    public Tuple3<Point, Point, Scalar> verify(
            long n,
            List<Scalar> a,
            Point gamma,
            List<Point> G,
            Transcript transcript
    ) {
        ScalarFactory scalarFactory = transcript.getScalarFactory();
        PointFactory pointFactory = transcript.getPointFactory();

        Scalar ONE = scalarFactory.one();

        Tuple3<List<Scalar>, List<Scalar>, List<Scalar>> vs = verificationScalars(n, transcript);
        List<Scalar> uSq = vs.getValue1();
        List<Scalar> uInvSq = vs.getValue2();
        List<Scalar> s = vs.getValue3();

        Point gHat = pointFactory.multiscalarMul(s, G);
        Scalar aHat = innerProduct(a, s, scalarFactory);

        List<Point> bases = new ArrayList<>(lVec);
        bases.addAll(rVec);
        bases.add(gamma);

        List<Scalar> scalars = new ArrayList<>(uSq);
        scalars.addAll(uInvSq);
        scalars.add(ONE);

        Point gammaHat = pointFactory.multiscalarMul(scalars, bases);

        return new Tuple3<>(
                gHat,
                gammaHat,
                aHat
        );
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        Serialization.serializePointList(packer, lVec);
        Serialization.serializePointList(packer, rVec);
    }

    public static BulletReductionProof unpack(MessageUnpacker unpacker, PointFactory pointFactory) throws IOException {
        List<Point> lVec = Serialization.deserializePointList(unpacker, pointFactory);
        List<Point> rVec = Serialization.deserializePointList(unpacker, pointFactory);
        return new BulletReductionProof(lVec, rVec);
    }
}
