/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
define(["dojo/dom",
        "dojo/_base/lang",
        "dijit/registry",
        "dijit/layout/ContentPane",
        "dijit/form/CheckBox",
        "dojox/html/entities",
        "qpid/common/updater",
        "qpid/management/Broker",
        "qpid/management/VirtualHost",
        "qpid/management/Exchange",
        "qpid/management/Queue",
        "qpid/management/Connection",
        "qpid/management/AuthenticationProvider",
        "qpid/management/GroupProvider",
        "qpid/management/group/Group",
        "qpid/management/KeyStore",
        "qpid/management/TrustStore",
        "qpid/management/AccessControlProvider",
        "qpid/management/Port",
        "qpid/management/Plugin",
        "qpid/management/PreferencesProvider",
        "qpid/management/VirtualHostNode",
        "qpid/management/Logger",
        "qpid/management/QueryTab",
        "qpid/management/QueryBrowserTab",
        "qpid/common/util",
        "dojo/ready",
        "dojox/uuid/generateRandomUuid",
        "dojo/domReady!"],
    function (dom,
              lang,
              registry,
              ContentPane,
              CheckBox,
              entities,
              updater,
              Broker,
              VirtualHost,
              Exchange,
              Queue,
              Connection,
              AuthProvider,
              GroupProvider,
              Group,
              KeyStore,
              TrustStore,
              AccessControlProvider,
              Port,
              Plugin,
              PreferencesProvider,
              VirtualHostNode,
              Logger,
              QueryTab,
              QueryBrowserTab,
              util,
              ready)
    {
        var controller = {};

        var constructors = {
            broker: Broker,
            virtualhost: VirtualHost,
            exchange: Exchange,
            queue: Queue,
            connection: Connection,
            authenticationprovider: AuthProvider,
            groupprovider: GroupProvider,
            group: Group,
            keystore: KeyStore,
            truststore: TrustStore,
            accesscontrolprovider: AccessControlProvider,
            port: Port,
            plugin: Plugin,
            preferencesprovider: PreferencesProvider,
            virtualhostnode: VirtualHostNode,
            brokerlogger: Logger,
            virtualhostlogger: Logger,
            query: QueryTab,
            queryBrowser: QueryBrowserTab
        };

        ready(function ()
        {
            controller.tabContainer = registry.byId("managedViews");
            controller.tabContainer.watch("selectedChildWidget", function(name, oval, nval){
                updater.restartTimer();
            });
        });

        controller.viewedObjects = {};

        var generateTabObjId = function(objType, name, parent)
        {
            var parentPart = (parent ? util.generateName(parent) + "/" : "");
            var namePart = null;
            if (typeof name === 'string')
            {
                namePart = name;
            }
            else if (name &&  typeof name === 'object' && name.hasOwnProperty("name"))
            {
                namePart = name.name;
            }
            else
            {
                namePart = "new-" + dojox.uuid.generateRandomUuid();
            }
            return parentPart + objType + ":" + namePart;
        };

        // TODO: find a better way how to pass business object into a tab instead of passing it as a name
        controller.show = function (objType, nameOrObject, parent, objectId)
        {
            var that = this;
            var objId = generateTabObjId(objType, nameOrObject, parent);

            var obj = this.viewedObjects[objId];
            if (obj)
            {
                this.tabContainer.selectChild(obj.contentPane);
            }
            else
            {
                var Constructor = constructors[objType];
                if (Constructor)
                {
                    obj = new Constructor(nameOrObject, parent, this);
                    obj.tabId = objId;
                    obj.tabData = {
                        objectId: objectId,
                        objectType: objType
                    };
                    this.viewedObjects[objId] = obj;

                    var contentPane = new ContentPane({
                        region: "center",
                        title: entities.encode(obj.getTitle()),
                        closable: true,
                        onClose: function ()
                        {
                            obj.close();
                            delete that.viewedObjects[obj.tabId];
                            return true;
                        }
                    });
                    this.tabContainer.addChild(contentPane);
                    var userPreferences = this.management.userPreferences;
                    if (objType != "broker" && nameOrObject &&  typeof nameOrObject === 'string')
                    {
                        var preferencesCheckBox = new dijit.form.CheckBox({
                            checked: userPreferences.isTabStored(obj.tabData),
                            title: "If checked the tab is saved in user preferences and restored on next login"
                        });
                        var tabs = this.tabContainer.tablist.getChildren();
                        preferencesCheckBox.placeAt(tabs[tabs.length - 1].titleNode, "first");
                        preferencesCheckBox.on("change", function (value)
                        {
                            if (value)
                            {
                                userPreferences.appendTab(obj.tabData);
                            }
                            else
                            {
                                userPreferences.removeTab(obj.tabData);
                            }
                        });
                    }
                    obj.open(contentPane);
                    contentPane.startup();
                    if (obj.startup)
                    {
                        obj.startup();
                    }
                    this.tabContainer.selectChild(contentPane);
                }

            }

        };

        controller.init = function (management, structure, treeView)
        {
            controller.management = management;
            controller.structure = structure;

            var structureUpdate = function()
            {
              var promise = management.get({url: "service/structure"});
              return promise.then(lang.hitch(this, function (data)
              {
                  structure.update(data);
                  treeView.update(data);
              }));
            };

            var initialUpdate = structureUpdate();
            initialUpdate.then(lang.hitch(this, function ()
            {
                updater.add({update : structureUpdate});
            }));
        };

        controller.update = function(tabObject, name, parent, objectId)
        {
            var objType = tabObject.tabData.objectType;
            var tabId = tabObject.tabId;
            delete this.viewedObjects[tabId];
            var newTabId = generateTabObjId(objType, name, parent);
            this.viewedObjects[newTabId] = tabObject;
            tabObject.tabData.objectId = objectId;
            tabObject.tabId = newTabId;
        };

        return controller;
    });
