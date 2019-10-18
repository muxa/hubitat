/**
 *  Master Switch v1.0.0
 *
 *  Copyright 2019 Mikhail Diatchenko (@muxa)
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

definition (
    name: "Master Switch",
    namespace: "muxa",
    author: "Mikhail Diatchenko",
    description: "Use one switch to control multiple linked switches",
    category: "Convenience",
	iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
     page name: "mainPage", title: "", install: true, uninstall: true
}

def installed() {
    log.info "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.info "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    log.info "There are ${childApps.size()} child apps"
    childApps.each { child ->
    	log.info "Child app: ${child.label}"
    }
}

def installCheck() {         
	state.appInstalled = app.getInstallationState()
	
	if (state.appInstalled != 'COMPLETE') {
		section{paragraph "Please hit 'Done' to install '${app.label}' parent app "}
  	}
  	else {
    	log.info "Parent Installed OK"
  	}
}

def display(){
	section() {
		paragraph "<a href='https://github.com/muxa/hubitat' target='_blank'>https://github.com/muxa/hubitat</a>"
	}       
}

def mainPage() {
    dynamicPage(name: "mainPage") {
    	installCheck()
		
		if (state.appInstalled == 'COMPLETE') {
			section("<h2>${app.label}<h2>") {
				paragraph "Use one master switch to control multiple linked switches."
				paragraph "<ul><li>Turning master switch off will turn off linked switches.</li><li>Turning master switch on will restore previously on switches (all on by default).</li><li>When any of the linked switches are on, the master switch will remain on.</li><li>When all linked switches are off the master switch will be off.</li></ul>"
                app(name: "anyOpenApp", appName: "Master Switch Instance", namespace: "muxa", title: "<b>Add a new Master Switch</b>", multiple: true)
			}
			display()
		}
	}
}