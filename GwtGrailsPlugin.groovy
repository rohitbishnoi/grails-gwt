/*
 * Copyright 2007-2008 Peter Ledbrook.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.codehaus.groovy.grails.plugins.gwt.ActionHandlerArtefactHandler
import org.codehaus.groovy.grails.plugins.gwt.DefaultGwtServiceInterfaceGenerator
import org.codehaus.groovy.grails.web.plugins.support.WebMetaUtils

class GwtGrailsPlugin {
    def version = "0.7-SNAPSHOT"
    def grailsVersion = "2.0 > *"
    def title = "The Google Web Toolkit for Grails."
    def description = """\
Incorporates GWT into Grails. In particular, GWT host pages can be
GSPs and standard Grails services can be used to handle client RPC
requests.
"""
    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPGWT" ]
    def documentation = "http://www.grails.org/plugin/gwt"
    def scm = [url:"https://github.com/dawsonsystems/grails-gwt"]
	
    def license = "APACHE"
	
	
    def observe = [ "services" ]
    def watchedResources = "file:./grails-app/actionHandlers/**/*ActionHandler.groovy"
    def artefacts = [ ActionHandlerArtefactHandler ]

    def srcDir = "src/gwt"

    def doWithSpring = {
        // Create Spring beans for all the actions defined by the user.
        final c = configureActionHandler.clone()
        c.delegate = delegate

        application.actionHandlerClasses.each { handlerClass ->
            log.info "Registering action handler: ${handlerClass.fullName}"
            c.call(handlerClass)
        }

        // Bean for generating RPC interfaces for services.
        gwtInterfaceGenerator(DefaultGwtServiceInterfaceGenerator)
    }   

    /**
     * Registers the common web-related dynamic properties on services
     * that are exposed via GWT.
     */
    def doWithDynamicMethods = { ctx ->
        def interfaceGenerator = ctx.getBean("gwtInterfaceGenerator")

        application.serviceClasses.each { serviceWrapper ->
            if (interfaceGenerator.isGwtExposed(serviceWrapper.clazz)) {
                WebMetaUtils.registerCommonWebProperties(serviceWrapper.clazz.metaClass, application)
            }
        }
    }

    /**
     * If the application has a gwt.nocache.filter.enabled setting with
     * the value of <code>true</code>, this hook installs a servlet
     * filter that ensures the browser never caches the *.nocache.js
     * files generated by GWT.
     */
    def doWithWebDescriptor = { xml ->
        if (application.config.gwt.nocache.filter.enabled) {
            def contextParam = xml.'context-param'
            contextParam[contextParam.size() - 1] + {
                'filter' {
                    'filter-name'('nocacheFilter')
                    'filter-class'('org.codehaus.groovy.grails.plugins.gwt.GwtCacheControlFilter')
                }
            }
        
            // Place the Shiro filter after the Spring character encoding filter, otherwise the latter filter won't work.
            def filter = xml.'filter-mapping'.find { it.'filter-name'.text() == "charEncodingFilter" }
            filter + {
                'filter-mapping' {
                    'filter-name'('nocacheFilter')
                    'url-pattern'("/*")
                }
            }
        }
    }

    /**
     * Registers the common web-related dynamic properties on services
     * that are reloaded and exposed via GWT.
     */
    def onChange = { event ->
        if (application.isServiceClass(event.source)) {
            def interfaceGenerator = event.ctx.getBean("gwtInterfaceGenerator")
            def serviceWrapper = application.getServiceClass(event.source?.name)

            if (interfaceGenerator.isGwtExposed(serviceWrapper.clazz)) {
                WebMetaUtils.registerCommonWebProperties(serviceWrapper.clazz.metaClass, application)
            }
        }
        else if (application.isActionHandlerClass(event.source)) {
            // Update the artifact. Without this step, the reloading
            // won't work.
            def grailsClass = application.addArtefact(ActionHandlerArtefactHandler.TYPE, event.source)

            // Re-register the action handler bean.
            def beans = beans {                 
                final c = configureActionHandler.clone()
                c.delegate = delegate
                c.call(grailsClass)
            }

            if (event.ctx) {         
                beans.registerBeans(event.ctx)
            }				
        }
    }                                                                                  

    def onApplicationChange = { event ->
    }

    /**
     * Adds the appropriate Spring bean for the given action handler
     * descriptor. Note that no check is performed on whether the
     * descriptor represents an action handler or not. The bean is
     * registered under the name "gwt<actionHandleClass>".
     */
    def configureActionHandler = { grailsClass ->
        "gwt${grailsClass.shortName}"(grailsClass.clazz) { bean ->
            bean.autowire = "byName"
        }
    }
    
    /**
     * Searches a given directory for any GWT module files, and
     * returns a list of their fully-qualified names.
     * @param searchDir A string path specifying the directory
     * to search in.
     * @return a list of fully-qualified module names.
     */
    def findModules(searchDir) {
        def modules = []
        def baseLength = searchDir.size()

        searchDir = new File(searchDir)
        if (!searchDir.exists()) return modules

        searchDir.eachFileRecurse { file ->
            // Replace Windows separators with Unix ones.
            file = file.path.replace('\\' as char, '/' as char)

            // Chop off the search directory.
            file = file.substring(baseLength + 1)

            // Now check whether this path matches a module file.
            def m = file =~ /([\w\/]+)\.gwt\.xml$/
            if (m.count > 0) {
                // Extract the fully-qualified module name.
                modules << m[0][1].replace('/' as char, '.' as char)
            }
        }

        return modules
    }
}
