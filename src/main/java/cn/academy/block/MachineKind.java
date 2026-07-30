package cn.academy.block;

public enum MachineKind {
    NODE_BASIC(15_000, 150, 0),
    NODE_STANDARD(50_000, 300, 0),
    NODE_ADVANCED(200_000, 900, 0),
    MATRIX(200_000, 540, 0),
    CAT_ENGINE(2_000, 200, 500),
    SOLAR_GENERATOR(1_000, 100, 3),
    PHASE_GENERATOR(6_000, 50, 50),
    WIND_BASE(20_000, 300, 0),
    WIND_PILLAR(20_000, 300, 0),
    WIND_GENERATOR(20_000, 300, 15),
    IMAG_FUSOR(2_000, 50, 0),
    METAL_FORMER(3_000, 50, 0),
    DEVELOPER_NORMAL(50_000, 100, 0),
    DEVELOPER_ADVANCED(200_000, 300, 0),
    ABILITY_INTERFERER(10_000, 50, 0),
    RF_INPUT(8_000, 400, 0),
    RF_OUTPUT(8_000, 400, 0),
    EU_INPUT(8_000, 400, 0),
    EU_OUTPUT(8_000, 400, 0);

    private final int capacity;
    private final int transfer;
    private final int generation;

    MachineKind(int capacity, int transfer, int generation) {
        this.capacity = capacity;
        this.transfer = transfer;
        this.generation = generation;
    }

    public int capacity() { return capacity; }
    public int transfer() { return transfer; }
    public int generation() { return generation; }

    public boolean isGenerator() {
        return generation > 0;
    }

    public boolean isProcessor() {
        return this == IMAG_FUSOR || this == METAL_FORMER;
    }

    public boolean isBridgeInput() {
        return this == RF_INPUT || this == EU_INPUT;
    }

    public boolean isBridgeOutput() {
        return this == RF_OUTPUT || this == EU_OUTPUT;
    }

    public boolean isNetworkNode() {
        return this == NODE_BASIC || this == NODE_STANDARD || this == NODE_ADVANCED || this == MATRIX;
    }
}
