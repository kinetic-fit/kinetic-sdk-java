package com.kinetic.sdk.inride;

import com.kinetic.sdk.exceptions.InvalidDataException;

class DataProcessor {
    private static class SpindownTimes {
        private static final double Min = 1.5;
        private static final double MinPro = 4.7;
        private static final double Max = 2.0;
        private static final double MaxPro = 6.5;
    }


    static ConfigData ProcessConfigurationData(byte[] data) throws InvalidDataException {
        if (data.length != 20) {
            throw new InvalidDataException("Invalid inRide Data");
        }

        ConfigData config = new ConfigData();

        int idx = 0;
        config.calibrationReady = (int) (data[idx++]) | (int) (data[idx++]) << 8;
        config.calibrationStart = (int) (data[idx++]) | (int) (data[idx++]) << 8;
        config.calibrationEnd = (int) (data[idx++]) | (int) (data[idx++]) << 8;
        config.calibrationDebounce = (int) (data[idx++]) | (int) (data[idx++]) << 8;

        int currentSpindownTicks = (int) (data[idx++]) | (int) (data[idx++]) << 8 | (int) (data[idx++]) << 16 | (int) (data[idx++]) << 24;
        config.currentSpindownTime = ticksToSeconds(currentSpindownTicks);
        config.proFlywheel = hasProFlywheel(config.currentSpindownTime);

        config.updateRateDefault = (int) (data[idx++]) | (int) (data[idx++]) << 8;
        config.updateRateCalibration = (int) (data[idx++]) | (int) (data[idx++]) << 8;

        return config;
    }

    static PowerData ProcessPowerData(byte[] data, byte[] systemId) throws InvalidDataException {
        if (data.length != 20) {
            throw new InvalidDataException("Invalid inRide Data");
        }
        if (systemId.length != 6) {
            throw new InvalidDataException("Invalid System Id");
        }

        PowerData powerData = new PowerData();
        powerData.timestamp = System.currentTimeMillis() / 1000.0;

        byte[] powerBytes = translateBytes(data);

        int stateBits = powerBytes[0] & 0x30;
        if (stateBits == 0x10) {
            powerData.state = PowerData.SensorState.SpindownIdle;
        } else if (stateBits == 0x20) {
            powerData.state = PowerData.SensorState.SpindownReady;
        } else if (stateBits == 0x30) {
            powerData.state = PowerData.SensorState.SpindownActive;
        } else {
            powerData.state = PowerData.SensorState.Normal;
        }

        int commandBits = powerBytes[0] & 0x0F;
        if (commandBits == 0x01) {
            powerData.commandResult = PowerData.SensorCommandResult.Success;
        } else if (commandBits == 0x02) {
            powerData.commandResult = PowerData.SensorCommandResult.NotSupported;
        } else if (commandBits == 0x03) {
            powerData.commandResult = PowerData.SensorCommandResult.InvalidRequest;
        } else if (commandBits == 0x0A) {
            powerData.commandResult = PowerData.SensorCommandResult.CalibrationResult;
        } else if (commandBits == 0x0F) {
            powerData.commandResult = PowerData.SensorCommandResult.UnknownError;
        } else {
            powerData.commandResult = PowerData.SensorCommandResult.None;
        }

        int idx = 1;

        long interval = (powerBytes[idx++] & 0xFF) | ((powerBytes[idx++] & 0xFF) << 8) | ((powerBytes[idx++] & 0xFF) << 16);
        long ticks = (powerBytes[idx++] & 0xFF) | ((powerBytes[idx++] & 0xFF) << 8) | ((powerBytes[idx++] & 0xFF) << 16) | ((powerBytes[idx++] & 0xFF) << 24);
        long revs = powerBytes[idx++] & 0xFF;
        long ticksPrevious = (powerBytes[idx++] & 0xFF) | ((powerBytes[idx++] & 0xFF) << 8) | ((powerBytes[idx++] & 0xFF) << 16) | ((powerBytes[idx++] & 0xFF) << 24);
        long revsPrevious = powerBytes[idx++];
        long cadenceRaw = (powerBytes[idx++] & 0xFF) | ((powerBytes[idx++] & 0xFF) << 8);
        powerData.cadenceRPM = adjustCadence(cadenceRaw, powerData.timestamp);


        long spindownTicks = (powerBytes[idx++] & 0xFF) | ((powerBytes[idx++] & 0xFF) << 8) | ((powerBytes[idx++] & 0xFF) << 16) | ((powerBytes[idx++] & 0xFF) << 24);

        powerData.lastSpindownResultTime = ticksToSeconds(spindownTicks);
        powerData.speedKPH = speedForTicks(ticks, revs);

        powerData.rollerRPM = 0.0;
        if (ticks > 0) {
            double seconds = ticksToSeconds(ticks);
            double rollerRPS = revs / seconds;
            powerData.rollerRPM = rollerRPS * 60;
        }

        double speedKPHPrev = speedForTicks(ticksPrevious, revsPrevious);
        powerData.proFlywheel = false;

        powerData.spindownTime = (SpindownTimes.Max + SpindownTimes.Min) * 0.5;
        if (powerData.lastSpindownResultTime >= SpindownTimes.Min && powerData.lastSpindownResultTime <= SpindownTimes.Max) {
            powerData.spindownTime = powerData.lastSpindownResultTime;
        } else if (powerData.lastSpindownResultTime >= SpindownTimes.MinPro && powerData.lastSpindownResultTime <= SpindownTimes.MaxPro) {
            powerData.spindownTime = powerData.lastSpindownResultTime;
            powerData.proFlywheel = true;
        }

        AA aa = alpha(interval, ticks, revs, powerData.speedKPH, ticksPrevious, revsPrevious, speedKPHPrev, powerData.proFlywheel);
        powerData.coasting = aa.coasting;

        if (aa.coasting) {
            powerData.power = 0;
        } else {
            powerData.power = powerForSpeed(powerData.speedKPH, powerData.spindownTime, aa.alpha, revs);
        }
        powerData.calibrationResult = resultForSpindown(powerData.lastSpindownResultTime);

        return powerData;
    }

    private static PowerData.SensorCalibrationResult resultForSpindown(double time) {
        PowerData.SensorCalibrationResult calibrationResult;
        if (time >= SpindownTimes.Min && time <= SpindownTimes.Max) {
            calibrationResult = PowerData.SensorCalibrationResult.Success;
        } else if (time >= SpindownTimes.MinPro && time <= SpindownTimes.MaxPro) {
            calibrationResult = PowerData.SensorCalibrationResult.Success;
        } else if (time < SpindownTimes.Min) {
            calibrationResult = PowerData.SensorCalibrationResult.TooFast;
        } else if (time > SpindownTimes.MaxPro) {
            calibrationResult = PowerData.SensorCalibrationResult.TooSlow;
        } else {
            calibrationResult = PowerData.SensorCalibrationResult.Middle;
        }
        return calibrationResult;
    }

    private static class AA {
        boolean coasting;
        double alpha;

        private AA(boolean coasting, double alpha) {
            this.coasting = coasting;
            this.alpha = alpha;
        }
    }

    private static class CadenceMark {
        double timestamp;
        double cadenceRPM;
    }

    public static final int CADENCE_BUFFER_SIZE_MAX = 10;
    public static final int CADENCE_BUFFER_SIZE_DEFAULT = 3;
    public static final int CADENCE_BUFFER_WEIGHT_DEFAULT = 2;
    private static int cadenceBufferSize = CADENCE_BUFFER_SIZE_DEFAULT;
    private static int cadenceBufferWeight = CADENCE_BUFFER_WEIGHT_DEFAULT;
    private static int cadenceBufferCount = 0;
    private static CadenceMark[] cadenceBuffer = new CadenceMark[CADENCE_BUFFER_SIZE_MAX];

    private static double adjustCadence(long crankRPM, double timestamp) {
        if (crankRPM == 0) {
            return 0;
        }

        if (cadenceBufferCount > 0 && timestamp - cadenceBuffer[0].timestamp > 2) {
            cadenceBufferCount = 0;
        }

        double adjustedRPM = (0.8652 * ((double) crankRPM) + 5.2617);

        // shift cadence values down ...
        for (int i = cadenceBufferCount; i > 0; i--) {
            cadenceBuffer[i] = cadenceBuffer[i - 1];
        }
        cadenceBuffer[0] = new CadenceMark();
        cadenceBuffer[0].cadenceRPM = adjustedRPM;
        cadenceBuffer[0].timestamp = timestamp;

        cadenceBufferCount = Math.min(cadenceBufferSize, cadenceBufferCount + 1);

        double rollingRPM = adjustedRPM * cadenceBufferWeight;
        for (int i = 1; i < cadenceBufferSize; i++) {
            rollingRPM += cadenceBuffer[i].cadenceRPM;
        }
        rollingRPM /= cadenceBufferSize + cadenceBufferWeight - 1;

        return rollingRPM;
    }

    private static AA alpha(long interval, long ticks, long revs, double speedKPH, long ticksPrevious, long revsPrevious, double speedKPHPrevious, boolean proFlywheel) {
        if (ticks > 0 && ticksPrevious > 0) {
            double tpr = ticks / (double) revs;
            double ptpr = ticksPrevious / (double) revsPrevious;
            double dtpr = tpr - ptpr;
            if (dtpr > 0) {
                // slowing down...
                double deltaSpeed = speedKPHPrevious - speedKPH;
                double alpha = deltaSpeed * dtpr;
                // alpha is positive...
                // TODO: this alpha value needs to be adjusted after a deep dissection of the speed curve for deceleration on the flywheel
                if (alpha > 200 && !proFlywheel) {

                    return new AA(true, alpha);
                } else if (alpha > 20 && proFlywheel) {
                    return new AA(true, alpha);

                }
                return new AA(false, alpha);
            } else {
                new AA(false, 0);
                // speeding up! (or staying the same)
            }
        }
        return new AA(false, 0);
    }

    // second conversion 0.9313225746
    private static class PowerConstants {
        private static final double MomentOfIntertia = 4.0;
        // drop 20 watts @ 300 power
        private static final double PowerSlopeOffset = 4.55;              // 9.0516435765      // 8.43     <-- adjusted for spindown timing fix
        private static final double PowerSlopeOffsetPro = 2.62;              // 2.44     <-- adjusted for spindown timing fix
        private static final double DragSlopeOffset = -0.1425;             // -0.1398  <-- adjusted for spindown timing fix
        private static final double DragSlopeOffsetPro = -0.021;            // -0.0201  <-- adjusted for spindown timing fix
        private static final double YIntercept = 236.20;
        private static final double YInterceptPro = 104.97;
    }

    private static int powerForSpeed(double kph, double spindown, double aa, long revolutions) {
        double mph = kph * 0.621371;
        double rawPower = (5.244820 * mph) + (0.019168 * (mph * mph * mph));
        double dragOffset = 0;
        if (spindown > 0 && rawPower > 0) {
            boolean proFlywheel = hasProFlywheel(spindown);

            double spindownTimeMS = spindown * 1000;

            double dragOffsetSlope = proFlywheel ? PowerConstants.DragSlopeOffsetPro : PowerConstants.DragSlopeOffset;
            double dragOffsetPowerSlope = proFlywheel ? PowerConstants.PowerSlopeOffsetPro : PowerConstants.PowerSlopeOffset;
            double yIntercept = proFlywheel ? PowerConstants.YInterceptPro : PowerConstants.YIntercept;
            dragOffset = (dragOffsetPowerSlope * spindownTimeMS * rawPower * 0.00001) + (dragOffsetSlope * spindownTimeMS) + yIntercept;
        } else {
            dragOffset = 0;
        }
        // double alphaOffset = 0.05 * PowerConstants.MomentOfIntertia * aa;

        double power = rawPower + dragOffset;
        if (power < 0) {
            power = 0;
        }
        return (int) power;
    }


    private static double ticksToSeconds(long ticks) {
        return ticks / 32768.0;
    }

    private static boolean hasProFlywheel(double spindown) {
        if (spindown >= SpindownTimes.MinPro && spindown <= SpindownTimes.MaxPro) {
            return true;
        }
        return false;
    }

    private static double speedForTicks(long ticks, long revs) {
        if (ticks == 0 || revs == 0) {
            return 0;
        }
        return (19974.826517 * revs) / ticks;
    }

    private static byte[] translateBytes(byte[] data) {
        byte[] translated = data.clone();
        int rotate = (data[0] & 0xC0) >> 6;
        byte[][] indicesArray = {
                {14, 15, 12, 16, 11, 5, 17, 3, 2, 1, 19, 13, 6, 4, 8, 9, 10, 18, 7},
                {12, 14, 8, 11, 16, 4, 7, 13, 18, 1, 3, 19, 6, 15, 9, 5, 10, 17, 2},
                {11, 5, 1, 9, 4, 18, 7, 15, 6, 2, 10, 12, 16, 3, 14, 13, 19, 17, 8},
                {13, 5, 18, 1, 3, 12, 15, 10, 14, 19, 16, 8, 6, 11, 2, 9, 4, 17, 7},
        };
        int xorIdx1 = rotate + 1;
        xorIdx1 %= 4;
        int xorIdx2 = xorIdx1 + 1;
        xorIdx2 %= 4;
        for (int index = 1; index < 20; ++index) {
            translated[index] = (byte) (translated[index] ^ (byte) ((indicesArray[xorIdx1][index - 1] + indicesArray[xorIdx2][index - 1])));
        }
        byte[] reordered = translated.clone();
        for (int index = 0; index < 19; ++index) {
            reordered[index + 1] = translated[indicesArray[rotate][index]];
        }
        return reordered;
    }
}
