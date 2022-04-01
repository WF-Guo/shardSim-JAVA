package edu.pku.infosec.customized;

public class VerificationResult {
    public final double timeCost;
    public final boolean passed;

    public VerificationResult(double timeCost, boolean passed) {
        this.timeCost = timeCost;
        this.passed = passed;
    }
}
