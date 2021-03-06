/* Copyright (c) 2011 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dk.dma.epd.common.prototype.sensor.nmea;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.TooManyListenersException;

import javax.comm.PortInUseException;
import javax.comm.UnsupportedCommOperationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.dma.ais.reader.SendException;
import dk.dma.ais.reader.SendRequest;
import dk.dma.ais.sentence.Abk;
import dk.dma.enav.util.function.Consumer;


/**
 * Serial port NMEA sensor 
 * 
 * @TODO make thread-safe 
 * 
 */
public class NmeaSerialSensor extends NmeaSensor implements SerialPortEventListener{
    
    
    private static Logger LOG = LoggerFactory.getLogger(NmeaSerialSensor.class);
    
    private String serialPortName;
    private int portSpeed = 38400;
    private int dataBits = SerialPort.DATABITS_8;
    private int stopBits = SerialPort.STOPBITS_1;
    private int parity = SerialPort.PARITY_NONE;
    private long reconnectInterval = 30000; // Default 30 sec
    private SerialPort serialPort;
    CommPortIdentifier portId;
    private InputStream inputStream;
    private OutputStream outputStream;
    private StringBuffer buffer = new StringBuffer();
    private Boolean connected = false;
    
    public NmeaSerialSensor(String serialPortName) {
        this(serialPortName, 38400);
    }

    public NmeaSerialSensor(String serialPortName, int portSpeed) {
        this.serialPortName = serialPortName;
        this.portSpeed = portSpeed;
        LOG.info("Creating NMEA serial sensor with name: " + serialPortName + " portSpeed: " + portSpeed);
    }
    
    @Override
    public void run() {
        
        while (!isStopped()) {
            if (!isConnected()) {
                try {
                    connect();
                } catch (Exception e) {
                    LOG.error("Failed to open serial port");
                    e.printStackTrace();
                }
            }
            
            
            try {
                Thread.sleep(reconnectInterval);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        // Disconnect and flag that the sensor has terminated
        disconnect();
        flagTerminated();
        LOG.warn("Serial NMEA sensor terminated");
    }
    
    private void connect() throws IOException, UnsupportedCommOperationException, PortInUseException, TooManyListenersException, gnu.io.PortInUseException, gnu.io.UnsupportedCommOperationException {
        // Find port
        findPort();        
        // Open port
        serialPort = (SerialPort) portId.open("SerialSource", 2000);
        // Settings
        serialPort.setSerialPortParams(portSpeed, dataBits, stopBits, parity);
        // Get streams
        inputStream = serialPort.getInputStream();
        outputStream = serialPort.getOutputStream();
        // Add event listener
        serialPort.addEventListener(this);
        serialPort.notifyOnDataAvailable(true);
        serialPort.notifyOnOutputEmpty(true);
        setConnected(true);
    }
    
    private void disconnect() {
        if (serialPort != null) {
            try {
                LOG.info("Disconnecting serial port " + serialPortName);
                serialPort.removeEventListener();
                serialPort.close();
                serialPort = null;
                portId = null;
                setConnected(false);
            } catch (Exception ex) {
            }
        }
    }
    
    public boolean isConnected() {
        synchronized (connected) {
            return connected;
        }
    }
    
    public void setConnected(Boolean connected) {
        synchronized (this.connected) {
            this.connected = connected;
        }        
    }
    
    private void findPort() throws IOException {
        LOG.info("Searching for port " + serialPortName);
        Enumeration<?> portList = CommPortIdentifier.getPortIdentifiers();
        while (portList.hasMoreElements()) {
            portId = (CommPortIdentifier) portList.nextElement();
            if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {                
                LOG.debug("portId: " + portId.getName());
                if (portId.getName().equals(serialPortName) || serialPortName.equals("AUTO")) {
                    serialPortName = portId.getName();
                    break;
                }
            }
            portId = null;
        }
        if (portId == null) {
            throw new IOException("Unable to find serial port " + serialPortName);
        }
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        switch (event.getEventType()) {
        case SerialPortEvent.BI:
        case SerialPortEvent.OE:
        case SerialPortEvent.FE:
        case SerialPortEvent.PE:
        case SerialPortEvent.CD:
        case SerialPortEvent.CTS:
        case SerialPortEvent.DSR:
        case SerialPortEvent.RI:
        case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
            LOG.debug("Output buffer empty");
            break;
        case SerialPortEvent.DATA_AVAILABLE:
            byte[] readBuffer = new byte[1024];
            try {
                while (!isStopped() && inputStream.available() > 0) {
                    int count = inputStream.read(readBuffer);
                    for (int i = 0; i < count; i++) {
                        buffer.append((char) readBuffer[i]);
                        // If line feed we have a whole line
                        if (readBuffer[i] == '\n') {
                            String msg = buffer.toString();
                            handleSentence(msg);
                            buffer = new StringBuffer();
                        }
                    }
                }
            } catch (IOException e) {
                LOG.error("Failed to read serial data: " + e.getMessage());
                disconnect();
            }
            break;
        }        
    }

    @Override
    public void send(SendRequest sendRequest, Consumer<Abk> resultListener) throws SendException {
        doSend(sendRequest, resultListener, outputStream);        
    }
        
}

