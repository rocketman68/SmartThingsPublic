/**
 *  OSRAM 4 Button Switch Handler
 *
 *  Copyright 2017 AnotherUser
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
 * Modified from code written by  motley74 and sticks18.
 * Original source: https://github.com/motley74/SmartThingsPublic/blob/master/devicetypes/motley74/osram-lightify-dimming-switch.src/osram-lightify-dimming-switch.groovy
 */
 
   
 
metadata {
	definition (name: "OSRAM 4 Button Switch Classic", namespace: "AnotherUser", author: "AN") {
	
    capability "Actuator"
    capability "Battery"
    capability "Button"
    capability "Configuration"
    capability "Refresh"
    capability "Sensor"
       
    attribute "zMessage", "String"
		fingerprint profileId: "0104", deviceId: "0810", inClusters: "0000, 0001, 0020, 1000, FD00", outClusters: "0003, 0004, 0005, 0006, 0008, 0019, 0300, 1000", manufacturer: "OSRAM", model: "Switch 4x EU-LIGHTIFY", deviceJoinName: "OSRAM 4x Switch"
	
    
    }


	simulator {
		// Nothing to see here
	}

	tiles(scale: 2) {
    	standardTile("button", "device.button", width: 6, height: 4) {
      	state "default", label: "", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#ffffff"
    	}
    	valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
      	state "battery", label:'${currentValue}% battery'
    	}
    	standardTile("refresh", "device.button", decoration: "flat", width: 2, height: 2) {
      	state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
    	}
    main "button"
    details(["button", "battery", "refresh"])
  	}
	}


// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
	// TODO: handle 'numberOfButtons' attribute
	// Parse incoming device messages to generate events

  	Map map = [:]
  	log.debug "parse description: $description"
  	if (description?.startsWith('catchall:')) {
    	// call parseCatchAllMessage to parse the catchall message received
    	map = parseCatchAllMessage(description)
  	} else if (description?.startsWith('read')) {
    	// call parseReadMessage to parse the read message received
    	map = parseReadMessage(description)
  	} else {
    	log.debug "Unknown message received: $description"
  	}
  //return event unless map is not set
  return map ? createEvent(map) : null
}

def configure() {
  log.debug "Confuguring Reporting and Bindings."
  def configCmds = [
    // Bind Button 1 and 2. No Change from source.
    "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}",
    // Bind Button 3 and 4. New cluster IDs.
    "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0300 {${device.zigbeeId}} {}",
    // Bind the outgoing level cluster from remote to hub, so the hub receives messages when Dim Up/Down buttons pushed
    "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0008 {${device.zigbeeId}} {}",
   // Bind the incoming battery info cluster from remote to hub, so the hub receives battery updates
    "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0001 {${device.zigbeeId}} {}",
  //ToDO: Determine what other clusters are on there and how they can be used. What purpose do 0003, 0004, 0005, 0019, and 1000 have?
  "zdo bind 0x${device.deviceNetworkId} 0x02 0x01 0x0006 {${device.zigbeeId}} {}",
  "zdo bind 0x${device.deviceNetworkId} 0x03 0x01 0x0006 {${device.zigbeeId}} {}",
  "zdo bind 0x${device.deviceNetworkId} 0x04 0x01 0x0006 {${device.zigbeeId}} {}",
  "zdo bind 0x${device.deviceNetworkId} 0x02 0x01 0x0300 {${device.zigbeeId}} {}",
  "zdo bind 0x${device.deviceNetworkId} 0x03 0x01 0x0300 {${device.zigbeeId}} {}",
  "zdo bind 0x${device.deviceNetworkId} 0x04 0x01 0x0300 {${device.zigbeeId}} {}",
  ]
  return configCmds 
}
def refresh() {
  //Straight copy. Need to check device and clusterID for battery.
  def refreshCmds = [
    zigbee.readAttribute(0x0001, 0x0020)
  ]
  //when refresh button is pushed, read updated status
  return refreshCmds
}

private Map parseReadMessage(String description) {
  // Create a map from the message description to make parsing more intuitive
  def msg = zigbee.parseDescriptionAsMap(description)
  //def msg = zigbee.parse(description)
  if (msg.clusterInt==1 && msg.attrInt==32) {
    // call getBatteryResult method to parse battery message into event map
    def result = getBatteryResult(Integer.parseInt(msg.value, 16))
  } else {
    log.debug "Unknown read message received, parsed message: $msg"
  }
  // return map used to create event
  return result
}

private Map parseCatchAllMessage(String description) {
  // Create a map from the raw zigbee message to make parsing more intuitive
  def msg = zigbee.parse(description)
  def abc = "zdo active 0x${device.deviceNetworkId}"
  log.debug "cluster ID is $msg.clusterId"
  log.debug "message from source $msg.sourceEndpoint"
  //New content follows
  switch(msg.sourceEndpoint) {
    //Endpoint numbering runs top left, top right, lower left, lower right.
    case 1: //"physical button 1"
    Map result = [:]
    if (msg.command==01){
    	result = [
    	name: 'button',
    	value: 'pushed',
    	data: [buttonNumber: 1],
    	descriptionText: "$device.displayName button 1 was pushed",
        isStateChange: true
        ]
       }
       else if (msg.command==05) {
       	result = [
    	name: 'button',
    	value: 'held',
    	data: [buttonNumber: 1],
    	descriptionText: "$device.displayName button 1 was held",
        isStateChange: true
        ]
       }
       else if (msg.command==03) {
       	result = [
    	name: 'button',
    	value: 'stophold',
    	data: [buttonNumber: 1],
    	descriptionText: "$device.displayName button 1 stopped being held",
        isStateChange: true
        ]
    }
          
    log.debug  "Parse returned ${result?.descriptionText}"
    return result
    break
    case 2: //physical button 3    
    Map result = [:]
    if (msg.command==76){
    		result = [
    		name: 'button',
    		value: 'pushed',
    		data: [buttonNumber: 3],
    		descriptionText: "$device.displayName button 3 was pushed",
        	isStateChange: true
       		]
        }
     else if (msg.command==03){
		    result = [
    		name: 'button',
    		value: 'held',
    		data: [buttonNumber: 3],
    		descriptionText: "$device.displayName button 3 started to be held",
        	isStateChange: true
       		]
         }
     else if (msg.command==01){
     		if(msg.data[0]==1){
           		result = [
    			name: 'button',
	    		value: 'longhold',
    			data: [buttonNumber: 3],
    			descriptionText: "$device.displayName button 3 continued to be held",
        		isStateChange: true
       			]
         	}
            else if (msg.data[0]==0){
            	result = [
    			name: 'button',
	    		value: 'stophold',
    			data: [buttonNumber: 3],
    			descriptionText: "$device.displayName button 3 stopped being held",
        		isStateChange: true
       			]
         	}
            else{
            	result =[
                descriptionText: "$device.displayName button 3 unknown data in command 01",
            	]
			}
         }
      else{
      	result =[
                descriptionText: "$device.displayName button 3 unknown command",
            	]
        }
          
    log.debug "message data $msg.data[0]"
    log.debug "message command $msg.command"
    log.debug  "Parse returned ${result?.descriptionText}"
    return result
    break
    case 3:
    //physical button 2
    Map result = [:]
    result = [
    	name: 'button',
    	value: 'pushed',
    	data: [buttonNumber: 2],
    	descriptionText: "$device.displayName button 2 was pushed",
        isStateChange: true
       ]
    log.debug  "Parse returned ${result?.descriptionText}"
    return result
    break
    case 4:
    //physical button 4
    Map result = [:]
    
    if (msg.command==76){
    		result = [
    		name: 'button',
    		value: 'pushed',
    		data: [buttonNumber: 4],
    		descriptionText: "$device.displayName button 4 was pushed",
        	isStateChange: true
       		]
        }
     else if (msg.command==03){
		    result = [
    		name: 'button',
    		value: 'held',
    		data: [buttonNumber: 4],
    		descriptionText: "$device.displayName button 4 started to be held",
        	isStateChange: true
       		]
         }
     else if (msg.command==01){
     		if(msg.data[0]==3){
           		result = [
    			name: 'button',
	    		value: 'longhold',
    			data: [buttonNumber: 4],
    			descriptionText: "$device.displayName button 4 continued to be held",
        		isStateChange: true
       			]
         	}
            else if (msg.data[0]==0){
            	result = [
    			name: 'button',
	    		value: 'stophold',
    			data: [buttonNumber: 3],
    			descriptionText: "$device.displayName button 4 stopped being held",
        		isStateChange: true
       			]
         	}
            else{
            	result =[
                descriptionText: "$device.displayName button 4 unknown data in command 01",
            	]
			}
         }
      else{
      	result =[
                descriptionText: "$device.displayName button 4 unknown command",
            	]
        }
    log.debug  "Parse returned ${result?.descriptionText}"
    log.debug "message data $msg.data[0]"
    log.debug "message command $msg.command"
    return result
    break
 //ToDo: Look at how to capture hold down of buttons 3 & 4, it doesn't differentiate on cluster or command as per B1 and B2 .
 }  
  
}

//Motley obtained from other examples, converts battery message into event map.
//AN: I don't think this is working yet.
private Map getBatteryResult(rawValue) {
  def linkText = getLinkText(device)
  def result = [
    name: 'battery',
    value: '--'
  ]
  def volts = rawValue / 10
  def descriptionText
  if (rawValue == 0) {
  } else {
    if (volts > 3.5) {
      result.descriptionText = "${linkText} battery has too much power (${volts} volts)."
    } else if (volts > 0){
      def minVolts = 2.1
      def maxVolts = 3.0
      def pct = (volts - minVolts) / (maxVolts - minVolts)
      result.value = Math.min(100, (int) pct * 100)
      result.descriptionText = "${linkText} battery was ${result.value}%"
    }
  }
  log.debug "Parse returned ${result?.descriptionText}"
  return result
}