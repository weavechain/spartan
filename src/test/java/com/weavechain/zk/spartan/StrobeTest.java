package com.weavechain.zk.spartan;

import com.google.common.truth.Truth;
import org.apache.commons.codec.binary.Hex;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class StrobeTest {

    @Test
    public void testConformance() {
        Strobe128 s = new Strobe128("Conformance Test Protocol".getBytes(StandardCharsets.UTF_8));

        byte[] msg = new byte[1024];
        Arrays.fill(msg, (byte)99);

        s.metaAd("ms".getBytes(StandardCharsets.UTF_8), false);
        s.metaAd("g".getBytes(StandardCharsets.UTF_8), true);
        s.ad(msg, false);

        byte[] prf1 = new byte[32];
        s.metaAd("prf".getBytes(StandardCharsets.UTF_8), false);
        s.prf(prf1, false);

        Truth.assertThat(Hex.encodeHexString(prf1)).isEqualTo("b48e645ca17c667fd5206ba57a6a228d72d8e1903814d3f17f622996d7cfefb0");

        s.metaAd("key".getBytes(StandardCharsets.UTF_8), false);
        s.key(prf1, false);

        byte[] prf2 = new byte[32];
        s.metaAd("prf".getBytes(StandardCharsets.UTF_8), false);
        s.prf(prf2, false);

        Truth.assertThat(Hex.encodeHexString(prf2)).isEqualTo("07e45cce8078cee259e3e375bb85d75610e2d1e1201c5f645045a194edd49ff8");
    }
}