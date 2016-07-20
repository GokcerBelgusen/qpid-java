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
define(["dojo/parser",
        "dojo/query",
        "dojo/json",
        "qpid/common/util",
        "dojo/text!showQueryTab.html",
        "qpid/management/query/QueryWidget",
        "dojo/domReady!"],
    function (parser, query, json, util, template, QueryWidget)
    {
        function getPath(object)
        {
            if (object && object.parent)
            {
                var type = object.type.charAt(0).toUpperCase() + object.type.substring(1);
                var val = object.name;
                for (var i = object.parent; i && i.parent; i = i.parent)
                {
                    val = i.name + "/" + val;
                }
                return " (" + type + ":" + val + ")" ;
            }
            return "";
        }

        function QueryTab(data, parent, controller)
        {
            this.controller = controller;
            this.management = controller.management;
            this.parent = parent;
            this.preference = data;
        }

        QueryTab.prototype.getTitle = function (changed)
        {
            var category = "";
            if (this.preference && this.preference.value && this.preference.value.category)
            {
                category = this.preference.value.category;
                category = category.charAt(0).toUpperCase() + category.substring(1);
            }
            var name = this.preference.id ? this.preference.name : "New";
            var prefix = this.preference.id && !changed ? "" : "*";
            var path = getPath(this.parent);
            return prefix + category + " query:" + name + path;
        };

        QueryTab.prototype.open = function (contentPane)
        {
            var that = this;
            this.contentPane = contentPane;
            contentPane.containerNode.innerHTML = template;
            parser.parse(contentPane.containerNode)
                .then(function (instances)
                {
                    that.onOpen(contentPane.containerNode)
                }, function (e)
                {
                    console.error("Unexpected error on parsing query tab template", e);
                });
        };

        QueryTab.prototype.onOpen = function (containerNode)
        {
            this.queryWidgetNode = query(".queryWidgetNode", containerNode)[0];
            this.queryWidget = new QueryWidget({
                management: this.management,
                parentObject: this.parent,
                preference: this.preference,
                controller: this.controller
            }, this.queryWidgetNode);
            var that = this;
            this.queryWidget.on("save", function(e)
            {
                if (that.preference.name != e.preference.name)
                {
                    that.controller.update(that, e.preference.name, that.parent, e.preference.id);
                }
                that.preference = e.preference;
                var title = that.getTitle();
                that.contentPane.set("title", title);
            });
            this.queryWidget.on("change", function(e)
            {
                var title = that.getTitle(true);
                that.contentPane.set("title", title);
            });
            this.queryWidget.on("delete", function(e)
            {
                that.destroy();
            });
            this.queryWidget.on("clone", function(e)
            {
                that.controller.show("query", e.preference, e.parentObject);
            });
            this.queryWidget.startup();
        };

        QueryTab.prototype.close = function ()
        {
            if (this.queryWidget != null)
            {
                this.queryWidget.destroyRecursive();
                this.queryWidget = null;
            }
        };

        QueryTab.prototype.destroy = function ()
        {
            this.close();
            this.contentPane.onClose();
            this.controller.tabContainer.removeChild(this.contentPane);
            this.contentPane.destroyRecursive();
        };

        return QueryTab;
    });
