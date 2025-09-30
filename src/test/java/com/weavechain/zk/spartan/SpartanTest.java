package com.weavechain.zk.spartan;

import com.google.common.truth.Truth;
import com.weavechain.curves.*;
import com.weavechain.zk.spartan.generators.SNARKGens;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SpartanTest {

    private static final boolean DEBUG = false;

    @Test
    public void testEvalTinyBls() throws IOException {
        PointFactory pointFactory = new G2PointFactory();
        ScalarFactory scalarFactory = new FrScalarFactory();

        List<Scalar> privateInputs = new ArrayList<>();
        Scalar ONE = scalarFactory.one();
        privateInputs.add(ONE);
        privateInputs.add(ONE.add(ONE));

        R1CS circuit = tinyR1CS(privateInputs, scalarFactory);
        testCircuit(circuit, privateInputs, pointFactory, scalarFactory);
    }

    @Test
    public void testEvalTinyRistretto() throws IOException {
        PointFactory pointFactory = new RistrettoPointFactory();
        ScalarFactory scalarFactory = new RScalar25519Factory();

        List<Scalar> privateInputs = new ArrayList<>();
        Scalar ONE = scalarFactory.one();
        privateInputs.add(ONE);
        privateInputs.add(ONE.add(ONE));

        R1CS circuit = tinyR1CS(privateInputs, scalarFactory);
        testCircuit(circuit, privateInputs, pointFactory, scalarFactory);
    }

    public void testCircuit(R1CS circuit, List<Scalar> privateInputs, PointFactory pointFactory, ScalarFactory scalarFactory) throws IOException {
        //Spartan.init();

        String genLabel = "gens_r1cs_eval";
        String satLabel = "gens_r1cs_sat";
        String transcriptLabel = "snark_example";
        String randomTapeLabel = "proof";

        byte[] encoded;
        Snark proof2;
        {
            SNARKGens proverGens = new SNARKGens(
                    circuit.getNumCons(),
                    circuit.getNumVars(),
                    circuit.getNumInputs(),
                    circuit.getNumNonZeroEntries(),
                    genLabel.getBytes(StandardCharsets.UTF_8),
                    satLabel.getBytes(StandardCharsets.UTF_8),
                    pointFactory
            );

            Transcript proverTranscript = new Transcript(transcriptLabel.getBytes(StandardCharsets.UTF_8), scalarFactory, pointFactory);
            RandomTape proverRandomTape = new RandomTape(randomTapeLabel.getBytes(StandardCharsets.UTF_8), scalarFactory, pointFactory);

            Snark proof = Snark.prove(
                    circuit,
                    proverGens,
                    proverTranscript,
                    proverRandomTape
            );

            encoded = proof.serialize();
        }

        // Validate
        {
            proof2 = Snark.deserialize(encoded, pointFactory, scalarFactory);
            Truth.assertThat(proof2.serialize()).isEqualTo(encoded);

            SNARKGens verifierGens = new SNARKGens(
                    circuit.getNumCons(),
                    circuit.getNumVars(),
                    circuit.getNumInputs(),
                    circuit.getNumNonZeroEntries(),
                    genLabel.getBytes(StandardCharsets.UTF_8),
                    satLabel.getBytes(StandardCharsets.UTF_8),
                    pointFactory
            );

            Assignment verifyInputs = new Assignment(privateInputs);
            Transcript verifierTranscript = new Transcript(transcriptLabel.getBytes(StandardCharsets.UTF_8), scalarFactory, pointFactory);

            boolean test = proof2.verify(
                    circuit,
                    verifyInputs,
                    verifierGens,
                    verifierTranscript
            );
            Truth.assertThat(test).isTrue();
        }
    }

    private R1CS tinyR1CS(List<Scalar> privateInputs, ScalarFactory scalarFactory) {
        Scalar ZERO = scalarFactory.zero();
        Scalar ONE = scalarFactory.one();

        // (Z0 + Z1) * I0 - Z2 = 0
        // (Z0 + I1) * Z2 - Z3 = 0
        // Z4 * 1 - 0 = 0

        long numCons = 4;
        long numVars = 5;
        long numInputs = 2;
        long numNonZeroEntries = 5;

        List<Constraint> A = new ArrayList<>();
        List<Constraint> B = new ArrayList<>();
        List<Constraint> C = new ArrayList<>();

        // (Z0 + Z1) * I0 - Z2 = 0.
        A.add(new Constraint(0, 0, ONE));
        A.add(new Constraint(0, 1, ONE));
        B.add(new Constraint(0, numVars + 1, ONE));
        C.add(new Constraint(0, 2, ONE));

        // (Z0 + I1) * Z2 - Z3 = 0
        A.add(new Constraint(1, 0, ONE));
        A.add(new Constraint(1, numVars + 2, ONE));
        B.add(new Constraint(1, 2, ONE));
        C.add(new Constraint(1, 3, ONE));

        // Z4 * 1 - 0 = 0
        A.add(new Constraint(2, 4, ONE));
        B.add(new Constraint(2, numVars, ONE));

        R1CSInstance inst = R1CSInstance.create(numCons, numVars, numInputs, A, B, C, scalarFactory);

        Random rnd = new Random();
        byte[] buf = new byte[64];
        rnd.nextBytes(buf);
        Scalar s = DEBUG ? ONE : scalarFactory.fromBytesModOrderWide(buf);

        Scalar i0 = privateInputs.get(0);
        Scalar i1 = privateInputs.get(1);
        Scalar z0 = s.add(s).add(s);
        Scalar z1 = s.add(s).add(s).add(s);
        Scalar z2 = (z0.add(z1)).multiply(i0); // constraint 0
        Scalar z3 = (z0.add(i1)).multiply(z2); // constraint 1
        Scalar z4 = ZERO; //constraint 2

        Assignment varsAssignment = new Assignment(List.of(z0, z1, z2, z3, z4));
        Assignment inputsAssignment = new Assignment(List.of(i0, i1));

        boolean isSatisfiable = inst.isSatisfiable(varsAssignment, inputsAssignment, scalarFactory);
        Truth.assertThat(isSatisfiable).isTrue();

        return new R1CS(
                numCons,
                numVars,
                numInputs,
                numNonZeroEntries,
                inst,
                varsAssignment,
                inputsAssignment
        );
    }
}