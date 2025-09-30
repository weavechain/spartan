package com.weavechain.zk.spartan.commit;

import com.weavechain.curves.Point;
import com.weavechain.curves.PointFactory;
import com.weavechain.zk.spartan.Serialization;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.util.List;

@Getter
@AllArgsConstructor
public class PolyCommitment {

    private final List<Point> C;

    public void pack(MessageBufferPacker packer) throws IOException {
        Serialization.serializePointList(packer, C);
    }

    public static PolyCommitment unpack(MessageUnpacker unpacker, PointFactory pointFactory) throws IOException {
        List<Point> C = Serialization.deserializePointList(unpacker, pointFactory);
        return new PolyCommitment(C);
    }
}
