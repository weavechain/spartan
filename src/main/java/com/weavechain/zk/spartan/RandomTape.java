package com.weavechain.zk.spartan;

import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Getter
@AllArgsConstructor
public class RandomTape {

    private final Transcript tape;

    public RandomTape(byte[] label, ScalarFactory scalarFactory, PointFactory pointFactory) {
        tape = new Transcript(label, scalarFactory, pointFactory);
        tape.appendScalar("init_randomness".getBytes(StandardCharsets.UTF_8), scalarFactory.rndScalar());
    }

    public Scalar randomScalar(byte[] label) {
        return tape.challengeScalar(label);
    }

    public List<Scalar> randomVector(byte[] label, long len) {
        return tape.challengeVector(label, len);
    }
}
