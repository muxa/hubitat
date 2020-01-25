/*
 * MQTT Broker Sensor
 *  Device Driver for Hubitat Elevation hub
 *  Version 1.0.0
 *
 * Detects when MQTT broker goes offline.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
metadata {
    definition(name: "MQTT Broker Sensor", namespace: "muxa", author: "Mikhail Diatchenko") {
        capability "Initialize"
        capability "PresenceSensor"
    }
}

preferences {
    section("URIs") {
        input name: "mqttBroker", type: "text", title: "MQTT broker IP:Port", required: true       
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def installed() {
    logDebug "Installed"

    sendEvent(name:"presence", value:"not present", descriptionText: "MQTT Broker not yet configured")
}

def updated() {
    logDebug "Updated"
    
    initialize()
}

def uninstalled() {
    logDebug "Uninstalled"
    disconnect()
}

def disconnect() {
    log.info "Disconnecting from MQTT..."
    interfaces.mqtt.disconnect()
}

def delayedConnect() {
    // increase delay by 5 seconds every time, to max of 1 hour
    if (state.delay < 3600)
        state.delay = (state.delay ?: 0) + 5

    logDebug "Reconnecting in ${state.delay}s"
    runIn(state.delay, connect)
}

def initialize() {
    logDebug "Initialize"
    state.delay = 0
    connect()
}

def connect() {
    try {
        // open connection
        log.info "Connecting to ${settings.mqttBroker}"
        interfaces.mqtt.connect("tcp://" + settings.mqttBroker, "hubitat_mqtt_${device.id}", null, null)
        // subscribe once received connection succeeded status update below        
    } catch(e) {
        log.error "MQTT Connect error: ${e.message}."
        setDisconnected()
        delayedConnect()
    }
}

def setDisconnected() {
    sendEvent(name:"presence", value:"not present", descriptionText: "MQTT Broker disconnected")
}

def setConnected() {
    sendEvent(name:"presence", value:"present", descriptionText: "MQTT Broker connected")
}

def mqttClientStatus(String status){
    // This method is called with any status messages from the MQTT client connection (disconnections, errors during connect, etc) 
    // The string that is passed to this method with start with "Error" if an error occurred or "Status" if this is just a status message.
    def parts = status.split(': ')
    switch (parts[0]) {
        case 'Error':
            log.warn status
            switch (parts[1]) {
                case 'Connection lost':
                case 'send error':
                    state.delay = 0
                    setDisconnected()
                    delayedConnect()
                    break
            }
            break
        case 'Status':
            log.info "MQTT ${status}"
            switch (parts[1]) {
                case 'Connection succeeded':
                    setConnected()
                    break
            }
            break
        default:
            logDebug "MQTT ${status}"
            break
    }
}

def logDebug(msg) {
    if (logEnable) log.debug msg
}