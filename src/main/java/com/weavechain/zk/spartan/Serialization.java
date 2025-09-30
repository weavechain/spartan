package com.weavechain.zk.spartan;

import com.weavechain.curves.Point;
import com.weavechain.curves.PointFactory;
import com.weavechain.curves.Scalar;
import com.weavechain.curves.ScalarFactory;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Serialization {

    public static void serialize(MessagePacker packer, Scalar scalar) throws IOException {
        packer.writePayload(scalar.toByteArray());
    }

    public static void serialize(MessagePacker packer, Point point) throws IOException {
        packer.writePayload(point.toByteArray());
    }

    public static void serializeScalarList(MessagePacker packer, List<Scalar> scalars) throws IOException {
        packer.packInt(scalars.size());
        for (Scalar p : scalars) {
            packer.writePayload(p.toByteArray());
        }
    }

    public static void serializePointList(MessagePacker packer, List<Point> points) throws IOException {
        packer.packInt(points.size());
        for (Point p : points) {
            packer.writePayload(p.toByteArray());
        }
    }

    public static Scalar deserializeScalar(MessageUnpacker unpacker, ScalarFactory scalarFactory) throws IOException {
        byte[] data = unpacker.readPayload(scalarFactory.serializedSize());
        return scalarFactory.fromBits(data);
    }

    public static Point deserializePoint(MessageUnpacker unpacker, PointFactory pointFactory) throws IOException {
        byte[] data = unpacker.readPayload(pointFactory.compressedSize());
        return pointFactory.fromCompressedBytes(data);
    }

    public static List<Point> deserializePointList(MessageUnpacker unpacker, PointFactory pointFactory) throws IOException {
        List<Point> result = new ArrayList<>();
        int len = unpacker.unpackInt();
        for (int i = 0; i < len; i++) {
            result.add(deserializePoint(unpacker, pointFactory));
        }
        return result;
    }

    public static List<Scalar> deserializeScalarList(MessageUnpacker unpacker, ScalarFactory scalarFactory) throws IOException {
        List<Scalar> result = new ArrayList<>();
        int len = unpacker.unpackInt();
        for (int i = 0; i < len; i++) {
            result.add(deserializeScalar(unpacker, scalarFactory));
        }
        return result;
    }
}
