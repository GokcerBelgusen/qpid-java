/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

define(["dojo/dom",
        "dojo/dom-construct",
        "dojo/dom-class",
        "dojo/_base/window",
        "dijit/registry",
        "dojo/parser",
        "dojo/_base/lang",
        "dojo/_base/array",
        "dojo/_base/event",
        'dojo/_base/json',
        "dojo/query",
        "dojo/_base/connect",
        "qpid/common/properties",
        "dojox/html/entities",
        "qpid/common/util",
        "dojo/text!showMessage.html",
        "dojo/domReady!"],
    function (dom, construct, domClass, win, registry, parser, lang, array, event, json, query, connect, properties, entities, util, template) {


        function encode(val){
            return typeof val === 'string' ? entities.encode(val) : val;
        }

        var populatedFields = [];
        var showMessage = {};

        showMessage.hide = function () {
            registry.byId("showMessage").hide();
        };

        showMessage.loadViewMessage = function(data) {
            var node = construct.create("div", null, win.body(), "last");
            node.innerHTML = data;
            var that = this;
            parser.parse(node).then(function(instances)
            {
                that.dialogNode = dom.byId("showMessage");
                var closeButton = query(".closeViewMessage", that.dialogNode)[0];
                registry.byNode(closeButton).on("click",
                                function (evt) {
                                    event.stop(evt);
                                    that.hide();
                                });
            });


        };

        showMessage.populateShowMessage = function(management, modelObj, data) {

            // clear fields set by previous invocation.
            if(populatedFields) {
                for(var i = 0 ; i < populatedFields.length; i++) {
                    populatedFields[i].innerHTML = "";
                }
                populatedFields = [];
            }

            for(var attrName in data) {
                if(data.hasOwnProperty(attrName)) {
                    var fields = query(".message-"+attrName, this.dialogNode);
                    if(fields && fields.length != 0) {
                        var field = fields[0];
                        populatedFields.push(field);
                        var val = data[attrName];
                        if(val != null) {
                            if(domClass.contains(field,"map")) {
                                var tableStr = "<table style='border: 1pt'><tr><th style='width: 6em; font-weight: bold'>Header</th><th style='font-weight: bold'>Value</th></tr>";
                                for(var name in val) {
                                    if(val.hasOwnProperty(name)) {

                                        tableStr += "<tr><td>"+encode(name)+"</td>";
                                        tableStr += "<td>"+encode(val[ name ])+"</td></tr>";
                                    }
                                }
                                tableStr += "</table>";
                                field.innerHTML = tableStr;
                            } else if(domClass.contains(field,"datetime")) {
                                field.innerHTML = management.userPreferences.formatDateTime(val, {addOffset: true, appendTimeZone: true});
                            } else {
                                field.innerHTML = encode(val);
                            }
                        }
                    }
                }
            }

            var contentModelObj = {name: "getMessageContent", parent: modelObj, type: modelObj.type};
            var parameters = {messageId: data.id};

            var url = management.buildObjectURL(contentModelObj, parameters);

            var href = query('a#message-download', this.dialogNode)[0];
            href.title = url;
            connect.connect(href, 'onclick', function ()
            {
                management.download(contentModelObj, parameters);
            });

            var preview = query('#preview', this.dialogNode)[0];
            if (data.mimeType && data.mimeType.match(/text\/.*/))
            {
                var limit = 1024;
                preview.style.display = "block";
                var previewDetail = query('#preview-detail', preview)[0];
                previewDetail.innerHTML = (limit < data.size
                    ? 'showing the first ' + limit + ' of ' + data.size + ' bytes'
                    : 'showing all ' + data.size + ' bytes');
                var previewContent = query("#message-content-preview", preview)[0];
                var previewParameters = lang.mixin({limit: limit}, parameters);
                management.load(contentModelObj, previewParameters, {
                        handleAs: "text",
                        headers: {"Content-Type": data.mimeType}
                    })
                    .then(function (content)
                    {
                        previewContent.innerHTML = encode(content);
                        registry.byId("showMessage")
                            .show();
                    });
            }
            else
            {
                preview.style.display = "none";
                registry.byId("showMessage")
                    .show();
            }
        };

        showMessage.show = function(management, modelObj, message )
        {
            management.load({name: "getMessageInfoById", parent: modelObj, type: modelObj.type}, {messageId: message.id}).then(
                function(data)
                {
                    showMessage.populateShowMessage(management, modelObj, data);
                });
        };

        showMessage.loadViewMessage(template);

        return showMessage;
    });
