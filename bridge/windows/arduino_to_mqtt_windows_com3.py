#!/usr/bin/env python3
# Arduino -> MQTT bridge for Windows (COM3 fixed)
import sys, os, time
import serial
import paho.mqtt.client as mqtt

SERIAL_PORT = "COM3"
BAUDRATE = 9600
MQTT_BROKER = os.getenv("MQTT_BROKER", "78.187.16.248")
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
        print("Seri port açılamadı:", e)
        print("1) Arduino IDE Serial Monitor kapalı mı kontrol edin.")
        print("2) COM3 doğru port mu? Device Manager -> Ports içinden doğrulayın.")
        print("3) Powershell'i Yönetici olarak çalıştırıp tekrar deneyin.")
        sys.exit(1)

    print("Seri bağlandı:", SERIAL_PORT)
    print("MQTT broker:", MQTT_BROKER)
mqtt_client = mqtt_connect()
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
        print("Çıkış")
    finally:
        try:
            mqtt_client.loop_stop()
            mqtt_client.disconnect()
        except:
            pass
        try:
            ser.close()
        except:
            pass

if __name__ == "__main__":
    main()