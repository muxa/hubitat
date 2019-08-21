/**
 *  Zemismart ZigBee Wall Switch 2 Gang
 *  Device Driver for Hubitat Elevation hub
 *  Version 0.1.0
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
 *  Based on on original by Lazcad / RaveTam
 *  Mods for Zemismart 2 Gang Switch by Muxa
 */

metadata {
    definition (name: "Zemismart ZigBee Wall Switch 2 Gang", namespace: "muxa", author: "Muxa") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Switch"
        capability "Health Check"
 
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006", outClusters: "0019", manufacturer: "Zemismart", model: "TS0002", deviceJoinName: "Zemismart Zigbee Switch"
  
        
        attribute "lastCheckin", "string"
        attribute "switch", "string"
        attribute "switch1", "string"
    	attribute "switch2", "string"
    	command "on1"
    	command "off1"
    	command "on2"
		command "off2"
        command "on"
        command "off"
        
        
        attribute "switch1","ENUM",["on","off"]
        attribute "switch2","ENUM",["on","off"]  
        attribute "switchstate","ENUM",["on","off"] 
    
    }
 
}

// Parse incoming device messages to generate events

def parse(String description) {
   //log.debug "Parsing '${description}'"
   
   def value = zigbee.parse(description)?.text
   //log.debug "Parse: $value"
   Map map = [:]
   
   if (description?.startsWith('catchall:')) {
		map = parseCatchAllMessage(description)
	}
	else if (description?.startsWith('read attr - ')) {
		map = parseReportAttributeMessage(description)
	}
    else if (description?.startsWith('on/off:')){
   // log.debug "onoff"
    
   def refreshCmds = zigbee.readAttribute(0x0006, 0x0000, [destEndpoint: 0x0B]) +
    				  zigbee.readAttribute(0x0006, 0x0000, [destEndpoint: 0x0C])            
    
   return refreshCmds.collect { new hubitat.device.HubAction(it) }     
    	//def resultMap = zigbee.getKnownDescription(description)
   		//log.debug "${resultMap}"
        
        //map = parseCustomMessage(description) 
    }

//	log.debug "Parse returned $map"
    //  send event for heartbeat    
    def now = new Date()
   
    sendEvent(name: "lastCheckin", value: now)
    
	def results = map ? createEvent(map) : null
	return results;
}

private Map parseCatchAllMessage(String description) {
	Map resultMap = [:]
	def cluster = zigbee.parse(description)
	log.debug cluster
    
    if (cluster.clusterId == 0x0006 && cluster.command == 0x01){
    	if (cluster.sourceEndpoint == 0x0B)
        {
        log.debug "Its Switch one"
    	def onoff = cluster.data[-1]
        if (onoff == 1)
        	resultMap = createEvent(name: "switch", value: "on")
        else if (onoff == 0)
            resultMap = createEvent(name: "switch", value: "off")
            }
      if (cluster.sourceEndpoint == 0x0C)
            {
            log.debug "Its Switch two"
    	def onoff = cluster.data[-1]
        if (onoff == 1)
        	resultMap = createEvent(name: "switch2", value: "on")
        else if (onoff == 0)
            resultMap = createEvent(name: "switch2", value: "off")
            }				
//          
    }
    
	return resultMap
}    
//    
private Map parseReportAttributeMessage(String description) {
	Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
	log.debug "Desc Map: $descMap"
 
	Map resultMap = [:]

	if (descMap.cluster == "0006" && descMap.attrId == "0000" && descMap.value =="00" && descMap.endpoint == "01") {
    	resultMap = createEvent(name: "switch1", value: "off")
    } 
        
    if (descMap.cluster == "0006" && descMap.attrId == "0000" && descMap.value =="01" && descMap.endpoint == "01") {
    	resultMap = createEvent(name: "switch1", value: "on")
    }
    if (descMap.cluster == "0006" && descMap.attrId == "0000" && descMap.value =="00" && descMap.endpoint == "02") {
    	resultMap = createEvent(name: "switch2", value: "off")
    } 
        
    if (descMap.cluster == "0006" && descMap.attrId == "0000" && descMap.value =="01" && descMap.endpoint == "02") {
    	resultMap = createEvent(name: "switch2", value: "on")
    }
    
    
	return resultMap
}
  
def off1() {
    log.info "off1()"
	sendEvent(name: "switch1", value: "off")
   	"he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x0 {}" 
  }

def on1() {
   log.info "on1()"
	sendEvent(name: "switch1", value: "on")
    "he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x1 {}" 
    }
def off2() {
    log.info "off2()"
	sendEvent(name: "switch2", value: "off")
    "he cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x0 {}" 
   }

def on2() {
   log.info "on2()"
	sendEvent(name: "switch2", value: "on")
    "he cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x1 {}" 
    }
    
def off() {
    log.info "off()"
	sendEvent(name: "switch", value: "off")
    "he cmd 0x${device.deviceNetworkId} 0xFF 0x0006 0x0 {}" 

   }

def on() {
   log.info "on()"
	sendEvent(name: "switch", value: "on")
    "he cmd 0x${device.deviceNetworkId} 0xFF 0x0006 0x1 {}" 

    }

def refresh() {
	log.debug "refreshing"
    [
        "he rattr 0x${device.deviceNetworkId} 0x01 0x0006 0x0", "delay 1000",
        "he rattr 0x${device.deviceNetworkId} 0x02 0x0006 0x0", "delay 1000",
       
    ]
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private Map parseCustomMessage(String description) {
	def result
	if (description?.startsWith('on/off: ')) {
    	if (description == 'on/off: 0')
    		result = createEvent(name: "switch", value: "off")
    	else if (description == 'on/off: 1')
    		result = createEvent(name: "switch", value: "on")
	}
    
    return result
}