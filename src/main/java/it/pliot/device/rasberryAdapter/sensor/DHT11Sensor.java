package it.pliot.device.rasberryAdapter.sensor;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.*;

public class DHT11Sensor {

    private static final int MAX_TIMINGS = 85;
    private final int pin;
    private final Context pi4j;
    private final DigitalInput dhtPin;
    private final DigitalOutput controlPin;

    public DHT11Sensor(int pin) {
        this.pin = pin;
        this.pi4j = Pi4J.newAutoContext();

        // Configura il pin come input per leggere i dati dal sensore
        this.dhtPin = DigitalInput.newConfigBuilder(pi4j)
                .id("DHT11")
                .name("DHT11 Sensor")
                .address(pin)
                .pull( PullResistance.PULL_DOWN
                ) // Usa la resistenza di pull-down direttamente con PullResistance
                .build();



        // Configura un pin di controllo come output per inviare segnali al sensore
        this.controlPin = DigitalOutput.newConfigBuilder(pi4j)
                .id("ControlPin")
                .name("DHT11 Control")
                .address(pin)
                .initial(DigitalOutput.DigitalState.HIGH)  // Imposta inizialmente l'output a HIGH
                .shutdown(DigitalOutput.DigitalState.LOW)  // Imposta lo stato di spegnimento a LOW
                .build();
    }

    public float[] readData() throws InterruptedException {
        float[] result = new float[2];
        int[] data = new int[5];
        int lastState = DigitalInput.DigitalState.LOW.value();
        int j = 0;

        data[0] = data[1] = data[2] = data[3] = data[4] = 0;

        // Pull pin down for 18 milliseconds
        controlPin.low();
        Thread.sleep(18);

        // Pull pin up for 40 microseconds
        controlPin.high();
        Thread.sleep(40);

        // Set pin as input to read data
        controlPin.config().shutdown(DigitalOutput.DigitalState.LOW);

        // Detect change and read data
        for (int i = 0; i < MAX_TIMINGS; i++) {
            int counter = 0;
            while ((dhtPin.state() == DigitalInput.DigitalState.LOW) == (lastState == DigitalInput.DigitalState.LOW)) {
                counter++;
                if (counter == 1000) {
                    break;
                }
            }

            lastState = dhtPin.state();

            if (counter == 1000) {
                break;
            }

            // Ignore first 3 transitions
            if ((i >= 4) && (i % 2 == 0)) {
                data[j / 8] <<= 1;
                if (counter > 16) {
                    data[j / 8] |= 1;
                }
                j++;
            }
        }

        // Validate data (40 bits + checksum)
        if ((j >= 40) && (data[4] == ((data[0] + data[1] + data[2] + data[3]) & 0xFF))) {
            float humidity = ((data[0] << 8) + data[1]) / 10.0f;
            if (humidity > 100) {
                humidity = data[0]; // DHT22 fix
            }
            float temperature = (((data[2] & 0x7F) << 8) + data[3]) / 10.0f;
            if (temperature > 125) {
                temperature = data[2]; // DHT22 fix
            }
            if ((data[2] & 0x80) != 0) {
                temperature = -temperature;
            }
            result[0] = temperature;
            result[1] = humidity;
        } else {
            throw new RuntimeException("Invalid data received from sensor");
        }

        return result;
    }
}
