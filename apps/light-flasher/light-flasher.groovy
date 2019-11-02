/**
 *  Light Flasher v1.0.0
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
    name: "Light Flasher",
    namespace: "muxa",
    author: "Mikhail Diatchenko",
    description: "Make lights flash",
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
				paragraph "Make lights flash."
				paragraph "<ul><li>Flashing is controlled by a switch (e.g. a virtual switch).</li><li>Multiple lights to flash can be selected.</li><li>Flashing period can be set.</li><li>Number of times to flash can be specified.</li></ul>"
                app(name: "anyOpenApp", appName: "Light Flasher Instance", namespace: "muxa", title: "<b>Add a new Light Flasher</b>", multiple: true)
			}
			display()
		}
	}
}