/**
 *  Copyright 2016 Eric Maycock
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
 *  Aeon WallMote Dual/Quad
 *
 *  Author: Eric Maycock (erocm123)
 *  Date: 2016-12-2
 */
 
metadata {
	definition (name: "Aeon WallMote", namespace: "erocm123", author: "Eric Maycock") {
		capability "Actuator"
		capability "Button"
		capability "Configuration"
		capability "Sensor"
        capability "Battery"
        
        attribute "sequenceNumber", "number"
        attribute "numberOfButtons", "number"
        attribute "needUpdate", "string"

		fingerprint deviceId: "0x1801", inClusters: "0x5E,0x73,0x98,0x86,0x85,0x59,0x8E,0x60,0x72,0x5A,0x84,0x5B,0x71,0x70,0x80,0x7A", outClusters: "0x25,0x26" // secure inclusion
        fingerprint deviceId: "0x1801", inClusters: "0x5E,0x85,0x59,0x8E,0x60,0x86,0x70,0x72,0x5A,0x73,0x84,0x80,0x5B,0x71,0x7A", outClusters: "0x25,0x26"
        
	}
    preferences {
        
        input description: "Once you change values on this page, the \"configure\" Status will become \"syncing\" status. When the parameters have been succesfully changed, the status will change back to \"configure\"", displayDuringSetup: false, type: "paragraph", element: "paragraph"
        
		generate_preferences(configuration_model())
        
    }

	simulator {
        
	}
	tiles (scale: 2) {
        multiAttributeTile(name:"button", type:"generic", width:6, height:4) {
  			tileAttribute("device.button", key: "PRIMARY_CONTROL"){
    		attributeState "default", label:'', backgroundColor:"#44b621", icon: "st.unknown.zwave.remote-controller"
  			}
            tileAttribute ("device.battery", key: "SECONDARY_CONTROL") {
			attributeState "battery", label:'${currentValue} % battery'
            }
            
        }
		standardTile("button", "device.button", width: 2, height: 2) {
			state "default", label: "", icon: "st.unknown.zwave.remote-controller", backgroundColor: "#ffffff"
		}
        valueTile(
			"battery", "device.battery", decoration: "flat", width: 2, height: 2) {
			state "battery", label:'${currentValue}%', unit:""
		}
        valueTile(
			"sequenceNumber", "device.sequenceNumber", decoration: "flat", width: 2, height: 2) {
			state "battery", label:'${currentValue}', unit:""
		}
        standardTile("configure", "device.needUpdate", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state("NO" , label:'configure', action:"configuration.configure", icon: "st.secondary.tools")
            state("YES", label:'syncing', action:"configuration.configure", icon: "st.secondary.tools")
        }
		main "button"
		details(["button", "battery", "sequenceNumber", "configure"])
	}
}

def parse(String description) {
	def results = []
	if (description.startsWith("Err")) {
	    results = createEvent(descriptionText:description, displayed:true)
	} else {
		def cmd = zwave.parse(description, [0x2B: 1, 0x80: 1, 0x84: 1])
		if(cmd) results += zwaveEvent(cmd)
		if(!results) results = [ descriptionText: cmd, displayed: false ]
	}
    
	return results
}

def zwaveEvent(physicalgraph.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
        logging("keyAttributes: $cmd.keyAttributes")
        logging("sceneNumber: $cmd.sceneNumber")
        logging("sequenceNumber: $cmd.sequenceNumber")

        sendEvent(name: "sequenceNumber", value: cmd.sequenceNumber, displayed:false)
        switch (cmd.keyAttributes) {
           case 0:
              buttonEvent(cmd.sceneNumber, "pushed")
           break
           case 1: // released
              //buttonEvent(cmd.sceneNumber, "held")
           break
           case 2: // held
              buttonEvent(cmd.sceneNumber, "held")
           break
           default:
              logging("Unhandled CentralSceneNotification: ${cmd}")
           break
        }
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand([0x5B: 1, 0x20: 1, 0x31: 5, 0x30: 2, 0x84: 1, 0x70: 1])
	state.sec = 1
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	} else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
		createEvent(descriptionText: cmd.toString())
	}
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityCommandsSupportedReport cmd) {
	response(configure())
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd)
{
    logging("Device ${device.displayName} woke up")
    
    def request = update_needed_settings()
    
    if (!state.lastBatteryReport || (now() - state.lastBatteryReport) / 60000 >= 60 * 24)
    {
        logging("Over 24hr since last battery report. Requesting report")
        request << zwave.batteryV1.batteryGet()
    }

    if(request != []){
       response(commands(request) + ["delay 5000", zwave.wakeUpV1.wakeUpNoMoreInformation().format()])
    } else {
       logging("No commands to send")
       response([zwave.wakeUpV1.wakeUpNoMoreInformation().format()])
    }
}

def buttonEvent(button, value) {
	createEvent(name: "button", value: value, data: [buttonNumber: button], descriptionText: "$device.displayName button $button was $value", isStateChange: true)
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
    logging("Battery Report: $cmd")
	def map = [ name: "battery", unit: "%" ]
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.descriptionText = "${device.displayName} battery is low"
		map.isStateChange = true
	} else {
		map.value = cmd.batteryLevel
	}
	state.lastBatteryReport = now()
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
     update_current_properties(cmd)
     logging("${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is set to '${cmd2Integer(cmd.configurationValue)}'")
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
     update_current_properties(cmd)
     logging("${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is set to '${cmd2Integer(cmd.configurationValue)}'")
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	logging("Unhandled zwaveEvent: ${cmd}")
}

def installed() {
    logging("installed()")
    configure()
}

/**
* Triggered when Done button is pushed on Preference Pane
*/
def updated()
{
    logging("updated() is being called")
    def cmds = configure()
    sendEvent(name:"needUpdate", value: device.currentValue("needUpdate"), displayed:false, isStateChange: true)
    if (cmds != []) response(cmds)
}

def configure() {
	state.enableDebugging = settings.enableDebugging
    logging("Configuring Device For SmartThings Use")
    def cmds = []
    cmds = update_needed_settings()
    sendEvent(name: "numberOfButtons", value: settings.buttons? settings.buttons : 4, displayed: true)
    if (cmds != []) commands(cmds)
}

def generate_preferences(configuration_model)
{
    def configuration = parseXml(configuration_model)
   
    configuration.Value.each
    {
        switch(it.@type)
        {   
            case ["byte","short","four"]:
                input "${it.@index}", "number",
                    title:"${it.@label}\n" + "${it.Help}",
                    range: "${it.@min}..${it.@max}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
            case "list":
                def items = []
                it.Item.each { items << ["${it.@value}":"${it.@label}"] }
                input "${it.@index}", "enum",
                    title:"${it.@label}\n" + "${it.Help}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}",
                    options: items
            break
            case "decimal":
               input "${it.@index}", "decimal",
                    title:"${it.@label}\n" + "${it.Help}",
                    range: "${it.@min}..${it.@max}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
            case "boolean":
               input "${it.@index}", "boolean",
                    title: it.@label != "" ? "${it.@label}\n" + "${it.Help}" : "" + "${it.Help}",
                    defaultValue: "${it.@value}",
                    displayDuringSetup: "${it.@displayDuringSetup}"
            break
        }  
    }
}

def update_current_properties(cmd)
{
    def currentProperties = state.currentProperties ?: [:]
    
    currentProperties."${cmd.parameterNumber}" = cmd.configurationValue

    if (settings."${cmd.parameterNumber}" != null)
    {
        if (convertParam(cmd.parameterNumber, settings."${cmd.parameterNumber}") == cmd2Integer(cmd.configurationValue))
        {
            sendEvent(name:"needUpdate", value:"NO", displayed:false, isStateChange: true)
        }
        else
        {
            sendEvent(name:"needUpdate", value:"YES", displayed:false, isStateChange: true)
        }
    }

    state.currentProperties = currentProperties
}

def update_needed_settings()
{
    def cmds = []
    def currentProperties = state.currentProperties ?: [:]
     
    def configuration = parseXml(configuration_model())
    def isUpdateNeeded = "NO"
    
    configuration.Value.each
    {     
        if ("${it.@setting_type}" == "zwave"){
            if (currentProperties."${it.@index}" == null)
            {
                isUpdateNeeded = "YES"
                logging("Current value of parameter ${it.@index} is unknown")
                cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
            }
            else if (settings."${it.@index}" != null && cmd2Integer(currentProperties."${it.@index}") != convertParam(it.@index.toInteger(), settings."${it.@index}"))
            { 
                isUpdateNeeded = "YES"

                logging("Parameter ${it.@index} will be updated to " + convertParam(it.@index.toInteger(), settings."${it.@index}"))
                def convertedConfigurationValue = convertParam(it.@index.toInteger(), settings."${it.@index}")
                cmds << zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(convertedConfigurationValue, it.@byteSize.toInteger()), parameterNumber: it.@index.toInteger(), size: it.@byteSize.toInteger())
                cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
            } 
        }
    }
    
    sendEvent(name:"needUpdate", value: isUpdateNeeded, displayed:false, isStateChange: true)
    return cmds
}

def convertParam(number, value) {
    long parValue
	switch (number){
    	case 5:
            switch (value) {
                case "1": 
                parValue = 4278190080
                break
                case "2": 
                parValue = 16711680
                break
                case "3": 
                parValue = 65280
                break
                default:
                parValue = value
                break
            }
        break
        default:
        	parValue = value.toLong()
        break
    }
    return parValue
}

private def logging(message) {
    if (state.enableDebugging == null || state.enableDebugging == "true") log.debug "$message"
}

/**
* Convert 1 and 2 bytes values to integer
*/
def cmd2Integer(array) { 
long value
    if (array != [255, 0, 0, 0]){
        switch(array.size()) {    
            case 1:
                value = array[0]
            break
            case 2:
                value = ((array[0] & 0xFF) << 8) | (array[1] & 0xFF)
            break
            case 3:
                value = ((array[0] & 0xFF) << 16) | ((array[1] & 0xFF) << 8) | (array[2] & 0xFF)
            break
            case 4:
                value = ((array[0] & 0xFF) << 24) | ((array[1] & 0xFF) << 16) | ((array[2] & 0xFF) << 8) | (array[3] & 0xFF)
            break
        }
    } else {
         value = 4278190080
    }
    return value
}

def integer2Cmd(value, size) {
	switch(size) {
	case 1:
		[value.toInteger()]
    break
	case 2:
    	def short value1   = value & 0xFF
        def short value2 = (value >> 8) & 0xFF
        [value2.toInteger(), value1.toInteger()]
    break
    case 3:
    	def short value1   = value & 0xFF
        def short value2 = (value >> 8) & 0xFF
        def short value3 = (value >> 16) & 0xFF
        [value3.toInteger(), value2.toInteger(), value1.toInteger()]
    break
	case 4:
    	def short value1 = value & 0xFF
        def short value2 = (value >> 8) & 0xFF
        def short value3 = (value >> 16) & 0xFF
        def short value4 = (value >> 24) & 0xFF
		[value4.toInteger(), value3.toInteger(), value2.toInteger(), value1.toInteger()]
	break
	}
}

private command(physicalgraph.zwave.Command cmd) {
	if (state.sec) {
		zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	} else {
		cmd.format()
	}
}

private commands(commands, delay=1000) {
	delayBetween(commands.collect{ command(it) }, delay)
}

def configuration_model()
{
'''
<configuration>
  <Value type="list" byteSize="1" index="buttons" label="WallMote Model" min="2" max="4" value="" setting_type="preference" fw="" displayDuringSetup="true">
    <Help>
Which model of WallMote is this?
   </Help>
        <Item label="Dual" value="2" />
        <Item label="Quad" value="4" />
  </Value>
  <Value type="list" byteSize="1" index="1" label="Touch Sound" min="0" max="1" value="1" setting_type="zwave" fw="">
    <Help>
Enable/disable the touch sound.
Default: Enable
   </Help>
        <Item label="Disable" value="0" />
        <Item label="Enable" value="1" />
  </Value>
  <Value type="list" byteSize="1" index="2" label="Touch Vibration" min="0" max="1" value="1" setting_type="zwave" fw="">
    <Help>
Enable/disable the touch vibration.
Default: Enable
   </Help>
        <Item label="Disable" value="0" />
        <Item label="Enable" value="1" />
  </Value>
    <Value type="list" byteSize="1" index="3" label="Button Slide" min="0" max="1" value="1" setting_type="zwave" fw="">
    <Help>
Enable/disable the function of button slide.
Default: Enable
   </Help>
        <Item label="Disable" value="0" />
        <Item label="Enable" value="1" />
  </Value>
      <Value type="list" byteSize="4" index="5" label="Color" min="1" max="3" value="3" setting_type="zwave" fw="">
    <Help>
To configure which color will be displayed when the button is pressed.
Default: Blue
   </Help>
        <Item label="Red" value="1" />
        <Item label="Green" value="2" />
        <Item label="Blue" value="3" />
  </Value>
  <Value type="boolean" index="enableDebugging" label="Enable Debug Logging?" value="true" setting_type="preference" fw="">
    <Help>
    </Help>
  </Value>
</configuration>
'''
}