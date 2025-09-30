package com.weavechain.zk.spartan.generators;

import com.github.aelstad.keccakj.fips202.Shake256;
import com.weavechain.curves.G2PointFactory;
import com.weavechain.curves.Point;
import com.weavechain.curves.PointFactory;
import com.weavechain.zk.spartan.util.ChaChaRng;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@AllArgsConstructor
public class MultiCommitGens {

    private static final boolean DEBUG = false;

    private final int n;

    private final List<Point> G;

    private final Point h;

    private final PointFactory pointFactory;

    public MultiCommitGens(int n, byte[] label, PointFactory pointFactory) {
        this.pointFactory = pointFactory;
        this.n = n;
        G = new ArrayList<>();

        Shake256 gDigest = new Shake256();
        gDigest.getAbsorbStream().write(label);

        byte[] buf = G2PointFactory.G.toByteArray();
        gDigest.getAbsorbStream().write(buf.length > 48 && buf[0] == 0 ? Arrays.copyOfRange(buf, 1, buf.length) : buf);

        gDigest.getAbsorbStream().close();

        byte[] seed = new byte[32];
        gDigest.getSqueezeStream().read(seed);

        ChaChaRng chacha = new ChaChaRng(seed);

        Point hp = null;
        for (int j = 0; j < n + 1; j++) {
            byte[] data = new byte[pointFactory.compressedSize()];
            Point point;
            if (DEBUG) {
                point = (j % 2 == 0) ? pointFactory.generator() : pointFactory.generator().add(pointFactory.generator());
            } else {
                chacha.fillBytes(data);
                point = pointFactory.fromUniformBytes(data);
            }

            if (j < n) {
                G.add(point);
            } else {
                hp = point;
            }
        }

        h = hp;
    }
}
