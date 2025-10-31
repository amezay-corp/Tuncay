// Arduino UNO: Pin 8 short detection and serial reporting
const uint8_t PIN = 8;
int lastState = HIGH;
unsigned long debounceMillis = 50;
unsigned long lastChangeTime = 0;

void setup() {
  pinMode(PIN, INPUT_PULLUP); // short to GND -> LOW
  Serial.begin(9600);
  delay(200);
  Serial.println("ARDUINO_READY");
  lastState = digitalRead(PIN);
}

void loop() {
  int state = digitalRead(PIN);
  if (state != lastState) {
    unsigned long now = millis();
    if (now - lastChangeTime > debounceMillis) {
      lastChangeTime = now;
      lastState = state;
      if (state == LOW) {
        Serial.println("ON");
      } else {
        Serial.println("OFF");
      }
    }
  }
  delay(10);
}