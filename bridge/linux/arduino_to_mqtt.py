#!/usr/bin/env python3
# Read Arduino serial messages and forward events to MQTT
import sys
import os
import time
import serial
import paho.mqtt.client as mqtt

SERIAL_PORT = sys.argv[1] if len(sys.argv) > 1 else "/dev/ttyACM0"
BAUDRATE = 9600
MQTT_BROKER = os.getenv("MQTT_BROKER", "127.0.0.1")
MQTT_PORT = int(os.getenv("MQTT_PORT", "1883"))
MQTT_TOPIC = os.getenv("MQTT_TOPIC", "elektrik/8")
PUBLISH_QOS = 1

def mqtt_connect():
    client = mqtt.Client()
    client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
    client.loop_start()
    return client

def main():
    try:
        ser = serial.Serial(SERIAL_PORT, BAUDRATE, timeout=1)
    except Exception as e:
        print("Seri açılamadı:", e)
        sys.exit(1)

    print("Bağlandı:", SERIAL_PORT)
mqtt_client = mqtt_connect()
    print("MQTT broker:", MQTT_BROKER, "port:", MQTT_PORT, "topic:", MQTT_TOPIC)

    buffer = ""
    last_state = None

    try:
        while True:
            try:
                line = ser.readline().decode(errors="ignore").strip()
            except Exception:
                line = ""
            if not line:
                time.sleep(0.05)
                continue
            print("Gelen:", line)
            if line in ("ON", "OFF"):
                if line != last_state:
                    last_state = line
                    mqtt_client.publish(MQTT_TOPIC, payload=line, qos=PUBLISH_QOS, retain=False)
                    print("MQTT publish:", line)
    except KeyboardInterrupt:
        print("Çıkılıyor")
    finally:
        try:
            mqtt_client.loop_stop()
            mqtt_client.disconnect()
        except Exception:
            pass
        try:
            ser.close()
        except Exception:
            pass

if __name__ == "__main__":
    main()