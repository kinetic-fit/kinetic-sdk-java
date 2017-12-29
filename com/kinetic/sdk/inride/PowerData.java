package com.kinetic.sdk.inride;

public class PowerData {

    public enum SensorState {
        Normal,
        SpindownIdle,
        SpindownReady,
        SpindownActive
    }


    public enum SensorCalibrationResult {
        Unknown,
        Success,
        TooFast,
        TooSlow,
        Middle
    }

    public enum SensorCommandResult {
        None,
        Success,
        NotSupported,
        InvalidRequest,
        CalibrationResult,
        UnknownError
    }

    public double timestamp;
    public SensorState state;
    public int power;
    public double speedKPH;
    public double rollerRPM;
    public double cadenceRPM;
    public boolean coasting;
    public double spindownTime;
    public SensorCalibrationResult calibrationResult;
    public double lastSpindownResultTime;
    public boolean proFlywheel;
    public SensorCommandResult commandResult;

}
