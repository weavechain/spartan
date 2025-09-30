## Spartan

A Java implementation of Spartan using BLS12-381 and Ristretto.

`Spartan` is a high-speed zero-knowledge proof system, a cryptographic primitive that enables a prover to prove a mathematical statement to a verifier without revealing anything besides the validity of the statement. 
This repository provides a Java implementation that implements a zero-knowledge succinct non-interactive argument of knowledge (zkSNARK), which is a type of zero-knowledge proof system with short proofs and fast verification times. 

Based on the [Rust Spartan implementation](https://github.com/microsoft/Spartan) from Microsoft

[Paper](https://eprint.iacr.org/2019/550) 
(Srinath Setty 
CRYPTO 2020)

## Highlights

- **No "toxic" waste:** Spartan is a _transparent_ zkSNARK and does not require a trusted setup.

- **General-purpose:** Spartan produces proofs for arbitrary NP statements.
  
- **Sub-linear verification costs:** Spartan is the first transparent proof system with sub-linear verification costs for arbitrary NP statements (e.g., R1CS).

- **Standardized security:** Spartan's security relies on the hardness of computing discrete logarithms (a standard cryptographic assumption) in the random oracle model. This implementation supports BLS12-381 and Ristretto255 curves.

- **State-of-the-art performance:** At the time of its creation, Spartan offered the fastest prover among transparent SNARKs. It still remains state of the art, with fast proving, short proofs and low verification times.

### Why Java?

Java is one of the languages of choice for Fintech and Banking software. At the same time the use of advanced privacy preserving technologies is lagging behind in these sectors. One of the reasons could be that the libraries for advanced cryptographic primitives are not readily available, and this is our contribution to gradually close the gap.

### Gradle Groovy DSL
```
implementation 'com.weavechain:spartan:1.0.3'
```

### Gradle Kotlin DSL

```
implementation("com.weavechain:spartan:1.0.3")
```

#### Apache Maven

```xml
<dependency>
  <groupId>com.weavechain</groupId>
  <artifactId>spartan</artifactId>
  <version>1.0.3</version>
</dependency>
```

### Usage

The following example shows how to use the library to create and verify a SNARK proof.

```java
public static void testEvalTinyBls() throws IOException {
    PointFactory pointFactory = new G2PointFactory();
    ScalarFactory scalarFactory = new FrScalarFactory();

    List<Scalar> privateInputs = new ArrayList<>();
    Scalar ONE = scalarFactory.one();
    privateInputs.add(ONE);
    privateInputs.add(ONE.add(ONE));

    R1CS circuit = tinyR1CS(privateInputs, scalarFactory);
    testCircuit(circuit, privateInputs, pointFactory, scalarFactory);
}

public static void testEvalTinyRistretto() throws IOException {
    PointFactory pointFactory = new RistrettoPointFactory();
    ScalarFactory scalarFactory = new RScalar25519Factory();

    List<Scalar> privateInputs = new ArrayList<>();
    Scalar ONE = scalarFactory.one();
    privateInputs.add(ONE);
    privateInputs.add(ONE.add(ONE));

    R1CS circuit = tinyR1CS(privateInputs, scalarFactory);
    testCircuit(circuit, privateInputs, pointFactory, scalarFactory);
}

public static void testCircuit(R1CS circuit, List<Scalar> privateInputs, PointFactory pointFactory, ScalarFactory scalarFactory) throws IOException {
    String genLabel = "gens_r1cs_eval";
    String satLabel = "gens_r1cs_sat";
    String transcriptLabel = "snark_example";
    String randomTapeLabel = "proof";

    byte[] encoded;
    Snark proof2;
    // Generate
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
        System.out.println("Is verified: " + test);
    }
}

private static R1CS tinyR1CS(List<Scalar> privateInputs, ScalarFactory scalarFactory) {
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
    Scalar s = scalarFactory.fromBytesModOrderWide(buf);

    Scalar i0 = privateInputs.get(0);
    Scalar i1 = privateInputs.get(1);
    Scalar z0 = s.add(s).add(s);
    Scalar z1 = s.add(s).add(s).add(s);
    Scalar z2 = (z0.add(z1)).multiply(i0);
    Scalar z3 = (z0.add(i1)).multiply(z2);
    Scalar z4 = ZERO;

    Assignment varsAssignment = new Assignment(List.of(z0, z1, z2, z3, z4));
    Assignment inputsAssignment = new Assignment(List.of(i0, i1));

    boolean isSatisfiable = inst.isSatisfiable(varsAssignment, inputsAssignment, scalarFactory);
    System.out.println("Is satisfiable: " + isSatisfiable);

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

public static void main(String[] args) throws IOException {
    testEvalTinyBls();
    testEvalTinyRistretto();
}
```


### Warning

This spartan library has been not audited and is provided as-is, we make no guarantees or warranties to its safety, security and reliability.

#### Weavechain

Weavechain is a Layer-0 for Data, adding Web3 Security and Data Economics to data stored in private vaults in any of the traditional databases.

Read more on [https://docs.weavechain.com](https://docs.weavechain.com)