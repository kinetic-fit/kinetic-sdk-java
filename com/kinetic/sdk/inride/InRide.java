package com.kinetic.sdk.inride;

import com.kinetic.sdk.exceptions.InvalidDataException;

public class InRide {

    public static class DeviceInformation {
        public static final String UUID = "0000180a-0000-1000-8000-00805f9b34fb";

        public static class Characteristics {
            public static final String SYSTEM_ID_UUID = "00002a23-0000-1000-8000-00805f9b34fb";
        }
    }

    public static class PowerService {
        public static final String UUID = "E9410100-B434-446B-B5CC-36592FC4C724";

        public static class Characteristics {
            public static final String POWER_UUID = "E9410101-B434-446B-B5CC-36592FC4C724";
            public static final String CONFIG_UUID = "E9410104-B434-446B-B5CC-36592FC4C724";
            public static final String CONTROL_POINT_UUID = "E9410102-B434-446B-B5CC-36592FC4C724";
        }
    }


    public static ConfigData ProcessConfigurationData(byte[] data) throws InvalidDataException {
        return DataProcessor.ProcessConfigurationData(data);
    }

    public static PowerData ProcessPowerData(byte[] data, byte[] systemId) throws InvalidDataException {
        return DataProcessor.ProcessPowerData(data, systemId);
    }

    public static byte[] StartCalibrationCommandData(byte[] systemId) throws InvalidDataException {
        return CommandFactory.StartCalibrationCommandData(systemId);
    }

    public static byte[] StopCalibrationCommandData(byte[] systemId) throws InvalidDataException {
        return CommandFactory.StopCalibrationCommandData(systemId);
    }

    public static byte[] SetSpindownTimeCommandData(double seconds, byte[] systemId) throws InvalidDataException {
        return CommandFactory.SetSpindownTimeCommandData(seconds, systemId);
    }

    public static byte[] SetPeripheralNameCommandData(String name, byte[] systemId) throws InvalidDataException {
        return CommandFactory.SetPeripheralNameCommandData(name, systemId);
    }

}
