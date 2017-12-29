package com.kinetic.sdk.inride;

public class ConfigData {

    public enum SensorUpdateRate {
        Millis1000,
        Millis500,
        Millis250
    }

    public boolean proFlywheel;
    public double currentSpindownTime;

    int calibrationReady;
    int calibrationStart;
    int calibrationEnd;
    int calibrationDebounce;
    int updateRateDefault;
    int updateRateCalibration;

}
