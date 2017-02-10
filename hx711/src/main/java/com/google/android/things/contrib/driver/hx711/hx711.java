/*
 * Copyright 2017 Ciorceri Petru Sorin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.things.contrib.driver.hx711;

import android.graphics.Color;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.pio.SpiDevice;

import java.io.IOException;

/**
 * Device driver for HX711 (24-Bit Analog-to-Digital Converter (ADC) for Weigh Scales).
 *
 * For information on the HX711, see:
 *   https://cdn.sparkfun.com/datasheets/Sensors/ForceFlex/hx711_english.pdf
 */

@SuppressWarnings({"unused", "WeakerAccess"})
public class hx711 implements AutoCloseable {
    private static final String TAG = "hx711";
    private int offset = 0;
    private static final int SPI_FREQUENCY = 115200;
    private static final int SPI_MODE = SpiDevice.MODE0;
    private static final int SPI_BPW = 8;   // bits per word
    private byte[] txBuffer;

    /**
     * The gain of HX711
     */
    public enum Gain {
        Gain32(0), Gain64(1), Gain128(2);
        int value;

        Gain(int value) {
            this.value = value;
        }
    }

    /**
     * The clock pulses used to receive weight and set gain
     */
    public byte[][] gainArray = {
            {(byte) 0xAA, (byte)0xAA, (byte)0xAA, (byte)0xAA, (byte)0xAA, (byte)0xAA, (byte)0xA0},
            {(byte) 0xAA, (byte)0xAA, (byte)0xAA, (byte)0xAA, (byte)0xAA, (byte)0xAA, (byte)0xA8},
            {(byte) 0xAA, (byte)0xAA, (byte)0xAA, (byte)0xAA, (byte)0xAA, (byte)0xAA, (byte)0x80}
    };

    private Gain mGain;
    private SpiDevice mDevice = null;

    /**
     * Create a new hx711 driver.
     *
     * @param spiBusPort Name of the SPI bus
     * @param gain The {@link Gain} indicating the red/green/blue byte ordering for the device.
     */
    public hx711(String spiBusPort, Gain gain) throws IOException {
        setGain(gain);
        PeripheralManagerService pioService = new PeripheralManagerService();
        mDevice = pioService.openSpiDevice(spiBusPort);
        try {
            configure(mDevice);
        } catch (IOException|RuntimeException e) {
            try {
                close();
            } catch (IOException|RuntimeException ignored) {
            }
            throw e;
        }
    }

    /**
     * Initial configuration of driver
     * @param device
     * @throws IOException
     */
    private void configure(SpiDevice device) throws IOException {
        // Note: You may need to set bit justification for your board.
        // mDevice.setBitJustification(SPI_BITJUST);
        device.setFrequency(SPI_FREQUENCY);
        device.setMode(SPI_MODE);
        device.setBitsPerWord(SPI_BPW);
    }

    /**
     * Returns true is HX711 is ready to read weight
     * @return
     */
    public boolean isReady() {
        return true;
    }

    /**
     * It sets the gain and channel used to read
     */
    public void setGain(Gain gain) {
        mGain = gain;
    }

    /**
     * It reads the weight value (one read)
     * @return
     */
    public int read() throws IOException {
        int value = 0;
        byte[] response = new byte[10];
        byte[] response_complement = new byte[10];

        txBuffer = gainArray[mGain.value];
        mDevice.transfer(txBuffer, response, txBuffer.length);

        // TODO: check if response.length != 6

        for (byte i=0; i<6; i++) {
            response[i] = (byte) ~response[i];
            response_complement[i] = (byte) (((response[i] & 0b01000000) >> 3) +
                    ((response[i] & 0b00010000) >> 2) +
                    ((response[i] & 0b00000100) >> 1) +
                    ((response[i] & 0b00000001) >> 0));
        }
        value = (response_complement[0] << 20) +
                (response_complement[1] << 16) +
                (response_complement[2] << 12) +
                (response_complement[3] << 8) +
                (response_complement[4] << 4) +
                response_complement[5];

        if (mDevice != null) {
            mDevice.close();
            mDevice = null;
        }

        return value;
    }

    /**
     * It reads the weight value (multiple reads)
     * @param times
     * @return
     */
    public int readAverage(int times) throws IOException {
        long sum = 0;
        for (int i=0; i<times; i++) {
            sum += read();
        }
        return (int) (sum / times);
    }

    /**
     *
     * @param times
     */
    public void tare(short times) throws IOException {
        setOffset(readAverage(times));
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getOffset() {
        return this.offset;
    }

    public void powerDown() {
    }

    public void powerUp() {
    }


    /**
     * Releases the SPI interface and related resources.
     */
    @Override
    public void close() throws IOException {
        if (mDevice != null) {
            try {
                mDevice.close();
            } finally {
                mDevice = null;
            }
        }
    }
}
