package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.util.List;

@Getter
@AllArgsConstructor
public class LayerProofBatched {

    private final SumcheckInstanceProof proof;

    private final List<Scalar> claimsProdLeft;

    private final List<Scalar> claimsProdRight;

    public Pair<Scalar, List<Scalar>> verify(
            Scalar claim,
            long numRounds,
            long degreeBound,
            Transcript transcript
    ) {
        return proof.verify(
                claim,
                numRounds,
                degreeBound,
                transcript
        );
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        proof.pack(packer);
        Serialization.serializeScalarList(packer, claimsProdLeft);
        Serialization.serializeScalarList(packer, claimsProdRight);
    }

    public static LayerProofBatched unpack(MessageUnpacker unpacker, ScalarFactory scalarFactory) throws IOException {
        SumcheckInstanceProof proof = SumcheckInstanceProof.unpack(unpacker, scalarFactory);
        List<Scalar> claimsProdLeft = Serialization.deserializeScalarList(unpacker, scalarFactory);
        List<Scalar> claimsProdRight = Serialization.deserializeScalarList(unpacker, scalarFactory);
        return new LayerProofBatched(proof, claimsProdLeft, claimsProdRight);
    }
}
