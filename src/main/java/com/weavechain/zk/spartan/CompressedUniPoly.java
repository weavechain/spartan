package com.weavechain.zk.spartan;

import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class CompressedUniPoly {

    private final List<Scalar> coeffsExceptLinearTerm;

    public UniPoly decompress(Scalar hint) {
        Scalar linearTerm = hint.subtract(coeffsExceptLinearTerm.get(0)).subtract(coeffsExceptLinearTerm.get(0));
        for (int i = 1; i < coeffsExceptLinearTerm.size(); i++) {
            linearTerm = linearTerm.subtract(coeffsExceptLinearTerm.get(i));
        }

        List<Scalar> coeffs = new ArrayList<>();
        coeffs.add(coeffsExceptLinearTerm.get(0));
        coeffs.add(linearTerm);
        coeffs.addAll(coeffsExceptLinearTerm.subList(1, coeffsExceptLinearTerm.size()));
        if (coeffsExceptLinearTerm.size() + 1 != coeffs.size()) {
            throw new IllegalArgumentException("Invalid sizes");
        }
        return new UniPoly(coeffs);
    }

    public void pack(MessageBufferPacker packer) throws IOException {
        Serialization.serializeScalarList(packer, coeffsExceptLinearTerm);
    }

    public static CompressedUniPoly unpack(MessageUnpacker unpacker, ScalarFactory scalarFactory) throws IOException {
        List<Scalar> coeffsExceptLinearTerm = Serialization.deserializeScalarList(unpacker, scalarFactory);
        return new CompressedUniPoly(coeffsExceptLinearTerm);
    }
}
