/*
 * WLED Light 
 *  Device Driver for Hubitat Elevation hub
 *  Version 1.0.4
 *
 * Allows keeping device status in sync with WLED light (https://github.com/Aircoookie/WLED) and controlling it via MQTT broker.
 * Presence will update to 'not present' if connection to MQTT broker is not established.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 * 
 */

import groovy.transform.Field

@Field static List lightEffects = [
    "Solid",
    "Blink",
    "Breathe",
    "Wipe",
    "Wipe Random",
    "Random Colors",
    "Sweep",
    "Dynamic",
    "Colorloop",
    "Rainbow",
    "Scan",
    "Dual Scan",
    "Fade",
    "Chase",
    "Chase Rainbow",
    "Running",
    "Saw",
    "Twinkle",
    "Dissolve",
    "Dissolve Rnd",
    "Sparkle",
    "Dark Sparkle",
    "Sparkle+",
    "Strobe",
    "Strobe Rainbow",
    "Mega Strobe",
    "Blink Rainbow",
    "Android",
    "Chase",
    "Chase Random",
    "Chase Rainbow",
    "Chase Flash",
    "Chase Flash Rnd",
    "Rainbow Runner",
    "Colorful",
    "Traffic Light",
    "Sweep Random",
    "Running 2",
    "Red & Blue",
    "Stream",
    "Scanner",
    "Lighthouse",
    "Fireworks",
    "Rain",
    "Merry Christmas",
    "Fire Flicker",
    "Gradient",
    "Loading",
    "In Out",
    "In In",
    "Out Out",
    "Out In",
    "Circus",
    "Halloween",
    "Tri Chase",
    "Tri Wipe",
    "Tri Fade",
    "Lightning",
    "ICU",
    "Multi Comet",
    "Dual Scanner",
    "Stream 2",
    "Oscillate",
    "Pride 2015",
    "Juggle",
    "Palette",
    "Fire 2012",
    "Colorwaves",
    "BPM",
    "Fill Noise",
    "Noise 1",
    "Noise 2",
    "Noise 3",
    "Noise 4",
    "Colortwinkles",
    "Lake",
    "Meteor",
    "Smooth Meteor",
    "Railway",
    "Ripple",
    "Twinklefox",
    "Twinklecat",
    "Halloween Eyes"
]

metadata {
    definition (name: "WLED MQTT Light", namespace: "muxa", author: "Mikhail Diatchenko") {
        capability "Configuration"
        capability "Actuator"
        capability "Switch"
        capability "SwitchLevel"
        capability "Light"
        capability "LightEffects"
        capability "ColorControl"
        capability "PresenceSensor"
		
        command "on"
        command "off"
        command "setEffectSpeed", [[name: "Effect speed *", type: "NUMBER", description: "Effect speed to set (0 to 255)", constraints:[]]]
        command "setEffectIntensity", [[name: "Effect intensity *", type: "NUMBER", description: "Effect intensity to set (0 to 255)", constraints:[]]]
        command "setPreset", [[name: "Preset number *", type: "NUMBER", description: "Preset to set (0 to 16)", constraints:[]]]
        command "reconnect"
        
        attribute "level", "number"
        attribute "effectNumber", "number"
        attribute "effectSpeed", "number"
        attribute "effectIntensity", "number"
    }
}

preferences {
    section("URIs") {
        input "mqttBroker", "string", title: "MQTT Broker Address:Port", required: true
		input "mqttTopic", "string", title: "MQTT Topic", required: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def installed() {
    def effectMap = lightEffects.collect { element, index ->
      [(index): element] 
    }
    def le = new groovy.json.JsonBuilder(lightEffects)
    sendEvent(name:"lightEffects",value:le)
    
    log.info "installed..."
}

def xmlToMap(xml) {
    def map = [ cl: [], cs: [] ]
    xml[0].children().each {
        switch (it.name()) {
            case "cl":
            case "cs":
                map[it.name()] += it.text().toInteger()
                break
            default:
                map[it.name()] = it.text()
        }
    }
    return map
}

def parse(String description) {
    def mqtt = interfaces.mqtt.parseMessage(description) 
    
    def xml = new XmlSlurper().parseText(mqtt.payload)
    def map = xmlToMap(xml)
    // logDebug map
    
    /* <ac>	0 to 255	Master Brightness
<cl>	3x 0..255	Primary Color RGB
<cs>	3x 0..255	Secondary RGB
<ns>	0 or 1	Notification Sending on
<nr>	0 or 1	Notification Receive on
<nl>	0 or 1	Nightlight active
<nf>	0 or 1	Nightlight Fade on
<nd>	0 to 255	Nightlight delay
<nt>	0 to 255	Nightlight target brightness
<fx>	0 to 73	Effect index
<sx>	0 to 255	Effect speed
<ix>	0 to 255	Effect intensity
<fp>	0 to 43	FastLED palette
<wv>	-1 to 255	Primary White value
<ws>	0 to 255	Secondary White
<md>	0 or 1	RGB or HSB UI mode
<ds>	String 0..32	Server description
<th>	Hex Colors	Current theme (&IT call required)
    */    
   
    // convert 0..255 level to 0..100 level
    level = Math.round(map.ac.toInteger() / 255 * 100)
    if (level > 0) {
        sendEvent(name: "switch", value: "on")
    } else {
        sendEvent(name: "switch", value: "off")
    }
    if (level != device.currentValue("level")) {
        sendEvent(name: "level", value: level, unit: "%", isStateChange: true)
    }
    
    def primaryColor = hubitat.helper.ColorUtils.rgbToHSV(map.cl)
    if (primaryColor[0] != device.currentValue("hue")) {
        sendEvent(name: "hue", value: primaryColor[0])
        setGenericName(primaryColor[0])
    }    
    if (primaryColor[1] != device.currentValue("saturation")) {
        sendEvent(name:"saturation", value:primaryColor[1], unit: "%")
    }
    
    def effectIndex = map.fx.toInteger()
    if (effectIndex != device.currentValue("effectNumber")) {
        // effect changed
        def effectName = lightEffects[effectIndex]
        def descr = "Effect was was set to ${effectName} (${effectIndex})"
        logInfo "${descr}"
        sendEvent(name:"effectNumber", value:effectIndex, descriptionText: descr)
        sendEvent(name:"effectName", value:effectName, descriptionText: descr)
    }
    
    def effectSpeed = map.sx.toInteger()
    if (effectSpeed != device.currentValue("effectSpeed")) {
        // effect speed changed
        sendEvent(name:"effectSpeed", value:effectSpeed)
    }
    
    def effectIntensity = map.ix.toInteger()
    if (effectIntensity != device.currentValue("effectIntensity")) {
        // effect intensity changed
        sendEvent(name:"effectIntensity", value:effectIntensity)
    }
}


def on() {
    logInfo "on"
    publishCommand "T=1"
}

def off() {
    logInfo "off"
    publishCommand "T=0"
}

def setEffect(String effect){
    logInfo "setEffect $effect"
    def index = lightEffects.indexOf(effect)
    if (index >= 0) setEffect(index)
}

def setEffect(id){
    logInfo "setEffect $id"
    publishCommand "FX=${id}"
} 

def setEffectSpeed(speed){
    logInfo "setEffectSpeed $speed"
    publishCommand "SX=${speed}"
} 

def setEffectIntensity(indensity){
    logInfo "setEffectIntensity $indensity"
    publishCommand "IX=${indensity}"
} 

def setNextEffect(){
    def currentEffectId = device.currentValue("effectNumber") ?: 0
    currentEffectId++
    if (currentEffectId >= lightEffects.size()) 
        currentEffectId = 0
    setEffect(currentEffectId)
}

def setPreviousEffect(){
    def currentEffectId = device.currentValue("effectNumber") ?: 1
    currentEffectId--
    if (currentEffectId < 1) 
        currentEffectId = lightEffects.size() - 1
    setEffect(currentEffectId)
}

def setPreset(preset){
    logInfo "setPreset $preset"
    publishCommand "PL=${preset}"
} 


def setColor(value) {
    logInfo "setColor $value"
    def rgb = hubitat.helper.ColorUtils.hsvToRGB([value.hue, value.saturation, value.level])
    publishCommand "R=${rgb[0]}&G=${rgb[1]}&B=${rgb[2]}"
}

def setHue(value) {
    setColor([hue: value, saturation: device.currentValue("saturation"), level: device.currentValue("level")])
}

def setSaturation(value) {
    setColor([hue: device.currentValue("hue"), saturation: value, level: device.currentValue("level")])
}

def setLevel(value) {
    logInfo "setLevel $value"
    publishCommand "A=${Math.round(value * 2.55)}"
}

def publishCommand(command) {
    logDebug "Publish ${settings.mqttTopic}/api&${command}"
    interfaces.mqtt.publish(settings.mqttTopic + "/api", command)
}

def setGenericName(hue){
    def colorName
    hue = hue.toInteger()
    if (!hiRezHue) hue = (hue * 3.6)
    switch (hue.toInteger()){
        case 0..15: colorName = "Red"
            break
        case 16..45: colorName = "Orange"
            break
        case 46..75: colorName = "Yellow"
            break
        case 76..105: colorName = "Chartreuse"
            break
        case 106..135: colorName = "Green"
            break
        case 136..165: colorName = "Spring"
            break
        case 166..195: colorName = "Cyan"
            break
        case 196..225: colorName = "Azure"
            break
        case 226..255: colorName = "Blue"
            break
        case 256..285: colorName = "Violet"
            break
        case 286..315: colorName = "Magenta"
            break
        case 316..345: colorName = "Rose"
            break
        case 346..360: colorName = "Red"
            break
    }
    logDebug "${device.getDisplayName()} color is ${colorName}"
    sendEvent(name: "colorName", value: colorName)
}

def updated() {
    log.info "Updated..."
    initialize()
}

def uninstalled() {
    disconnect()
}

def reconnect() {
    disconnect()
    initialize()
}

def disconnect() {
    if (state.connected) {
        log.info "Disconnecting from MQTT"
        interfaces.mqtt.unsubscribe(settings.mqttTopic + "/v")
        interfaces.mqtt.disconnect()
    }
}

def delayedInitialise() {
    // increase delay by 5 seconds evety time
    state.delay = (state.delay ?: 0) + 5
    logDebug "Reconnecting in ${state.delay}s"
    runIn(state.delay, initialize)
}

def initialize() {
    logDebug "Initialize"
    try {
        // open connection
        interfaces.mqtt.connect("tcp://" + settings.mqttBroker, "hubitat_wled_${device.id}", null, null)
        // subscribe once received connection succeeded status update below        
    } catch(e) {
        log.error "MQTT Initialize error: ${e.message}."
        delayedInitialise()
    }
}

def subscribe() {
    interfaces.mqtt.subscribe(settings.mqttTopic + "/v") // Contains XML API response (same as HTTP API)
    logDebug "Subscribed to topic ${settings.mqttTopic}/v"
    sendEvent(name: "presence", value: "present", descriptionText: "MQTT connected", isStateChange: true)
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
                    state.connected = false
                    sendEvent(name: "presence", value: "not present", descriptionText: "MQTT disconnected")
                    state.delay = 0
                    delayedInitialise()
                    break
                case 'send error':
                    state.connected = false
                    sendEvent(name: "presence", value: "not present", descriptionText: "MQTT disconnected")
                    state.delay = 0
                    delayedInitialise()
                    break
            }
            break
        case 'Status':
            log.info "MQTT ${status}"
            switch (parts[1]) {
                case 'Connection succeeded':
                    state.connected = true
                    // without this delay the `parse` method is never called
                    // (it seems that there needs to be some delay after cinnection to subscribe)
                    runInMillis(100, subscribe)
                    break
            }
            break
        default:
            logDebug "MQTT ${status}"
            break
    }
}

def logInfo(msg) {
    if (logEnable) log.info msg
}

def logDebug(msg) {
    if (logEnable) log.debug msg
}