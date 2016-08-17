/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.qpid.server.management.plugin.servlet.rest;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectAttribute;
import org.apache.qpid.server.model.ConfiguredObjectOperation;
import org.apache.qpid.server.model.ConfiguredSettableAttribute;
import org.apache.qpid.server.model.ManagedContextDefault;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.OperationParameter;

public class ApiDocsServlet extends AbstractServlet
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiDocsServlet.class);
    private final Model _model;
    private final List<Class<? extends ConfiguredObject>> _types;

    private Class<? extends ConfiguredObject>[] _hierarchy;

    private static final Set<Character> VOWELS = new HashSet<>(Arrays.asList('a','e','i','o','u'));

    public static final Comparator<Class<? extends ConfiguredObject>> CLASS_COMPARATOR =
            new Comparator<Class<? extends ConfiguredObject>>()
            {
                @Override
                public int compare(final Class<? extends ConfiguredObject> o1,
                                   final Class<? extends ConfiguredObject> o2)
                {
                    return o1.getSimpleName().compareTo(o2.getSimpleName());
                }

            };
    private static final Map<Class<? extends ConfiguredObject>, List<String>> REGISTERED_CLASSES = new TreeMap<>(CLASS_COMPARATOR);


    public ApiDocsServlet(final Model model)
    {
        super();
        _model = model;
        _hierarchy = null;
        _types = null;
    }

    public ApiDocsServlet(final Model model, final List<String> registeredPaths, Class<? extends ConfiguredObject>... hierarchy)
    {
        super();
        _model = model;
        _hierarchy = hierarchy;
        _types = new ArrayList<>(_model.getTypeRegistry().getTypeSpecialisations(getConfiguredClass()));
        Collections.sort(_types, CLASS_COMPARATOR);
        List<String> paths = REGISTERED_CLASSES.get(getConfiguredClass());
        if (paths == null)
        {
            paths = new ArrayList<>();
            REGISTERED_CLASSES.put(getConfiguredClass(), paths);
        }
        paths.addAll(registeredPaths);
    }

    @Override
    protected void doGetWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);


        PrintWriter writer = response.getWriter();

        writePreamble(writer);
        writeHead(writer);

        if(_hierarchy == null)
        {
            writer.println("<table class=\"api\">");
            writer.println("<thead>");
            writer.println("<tr>");
            writer.println("<th class=\"type\">Type</th>");
            writer.println("<th class=\"path\">Path</th>");
            writer.println("<th class=\"description\">Description</th>");
            writer.println("</tr>");
            writer.println("</thead>");
            writer.println("<tbody>");
            for(Map.Entry<Class<? extends ConfiguredObject>, List<String>> entry : REGISTERED_CLASSES.entrySet())
            {
                List<String> paths = entry.getValue();
                Class<? extends ConfiguredObject> objClass = entry.getKey();
                writer.println("<tr>");
                writer.println("<td class=\"type\" rowspan=\""+ paths.size()+"\"><a href=\"latest/"+ objClass.getSimpleName().toLowerCase()+"\">"+objClass.getSimpleName()+"</a></td>");
                writer.println("<td class=\"path\">" + paths.get(0) + "</td>");
                writer.println("<td class=\"description\" rowspan=\""+ paths.size()+"\">"+
                               objClass.getAnnotation(ManagedObject.class).description()+"</td>");
                writer.println("</tr>");
                for(int i = 1; i < paths.size(); i++)
                {
                    writer.println("<tr>");
                    writer.println("<td class=\"path\">" + paths.get(i) + "</td>");
                    writer.println("</tr>");
                }

            }
            writer.println("</tbody>");
            writer.println("</table>");

        }
        else
        {
            writeCategoryDescription(writer);
            writeUsage(writer, request);
            writeTypes(writer);
            writeAttributes(writer);
            writeOperations(writer);
            writeContext(writer);
        }

        writeFoot(writer);
    }

    private void writePreamble(final PrintWriter writer)
    {
        writer.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"");
        writer.println("\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">");
        writer.println("<html>");


    }

    private void writeHead(final PrintWriter writer)
    {
        writer.println("<head>");
        writer.println("<link rel=\"icon\" type=\"image/png\" href=\"/images/qpid-favicon.png\">");
        writer.println("<link rel=\"stylesheet\" type=\"text/css\" href=\"/css/apidocs.css\">");
        writeTitle(writer);

        writer.println("</head>");
        writer.println("<body>");
    }

    private void writeTitle(final PrintWriter writer)
    {
        writer.print("<title>");
        if(_hierarchy == null)
        {
            writer.print("Qpid API");
        }
        else
        {
            writer.print("Qpid API: " + getConfiguredClass().getSimpleName());
        }
        writer.println("</title>");
    }

    private void writeCategoryDescription(PrintWriter writer)
    {
        writer.println("<h1>"+getConfiguredClass().getSimpleName()+"</h1>");
        writer.println(getConfiguredClass().getAnnotation(ManagedObject.class).description());
    }

    private void writeUsage(final PrintWriter writer, final HttpServletRequest request)
    {
        writer.println("<a name=\"usage\"><h2>Usage</h2></a>");
        writer.println("<table class=\"usage\">");
        writer.println("<tbody>");
        writer.print("<tr><th class=\"operation\">Read</th><td class=\"method\">GET</td><td class=\"path\">" + request.getServletPath()
                .replace("apidocs", "api"));

        for (final Class<? extends ConfiguredObject> category : _hierarchy)
        {
            writer.print("[/&lt;" + category.getSimpleName().toLowerCase() + " name or id&gt;");
        }
        for(int i = 0; i < _hierarchy.length; i++)
        {
            writer.print("] ");
        }
        writer.println("</td></tr>");

        writer.print("<tr><th class=\"operation\">Update</th><td class=\"method\">PUT or POST</td><td class=\"path\">"
                     + request.getServletPath().replace("apidocs", "api"));
        for (final Class<? extends ConfiguredObject> category : _hierarchy)
        {
            writer.print("/&lt;" + category.getSimpleName().toLowerCase() + " name or id&gt;");
        }

        writer.print(
                "<tr><th class=\"operation\">Create</th><td class=\"method\">PUT or POST</td><td class=\"path\">"
                + request.getServletPath().replace("apidocs", "api"));
        for (int i = 0; i < _hierarchy.length - 1; i++)
        {
            writer.print("/&lt;" + _hierarchy[i].getSimpleName().toLowerCase() + " name or id&gt;");
        }

        writer.print("<tr><th class=\"operation\">Delete</th><td class=\"method\">DELETE</td><td class=\"path\">"
                     + request.getServletPath().replace("apidocs", "api"));
        for (final Class<? extends ConfiguredObject> category : _hierarchy)
        {
            writer.print("/&lt;" + category.getSimpleName().toLowerCase() + " name or id&gt;");
        }

        writer.println("</tbody>");
        writer.println("</table>");

    }


    private void writeTypes(final PrintWriter writer)
    {
        if(!_types.isEmpty() && !(_types.size() == 1 && getTypeName(_types.iterator().next()).trim().equals("")))
        {
            writer.println("<a name=\"types\"><h2>Types</h2></a>");
            writer.println("<table class=\"types\">");
            writer.println("<thead>");
            writer.println("<tr><th class=\"type\">Type</th><th class=\"description\">Description</th></tr>");
            writer.println("</thead>");

            writer.println("<tbody>");
            for (Class<? extends ConfiguredObject> type : _types)
            {
                writer.print("<tr><td class=\"type\">");
                writer.print(getTypeName(type));
                writer.print("</td><td class=\"description\">");
                writer.print(type.getAnnotation(ManagedObject.class).description());
                writer.println("</td></tr>");

            }
            writer.println("</tbody>");
        }

        writer.println("</table>");
    }

    private String getTypeName(final Class<? extends ConfiguredObject> type)
    {
        return type.getAnnotation(ManagedObject.class).type() == null
                            ? _model.getTypeRegistry().getTypeClass(type).getSimpleName()
                            : type.getAnnotation(ManagedObject.class).type();
    }

    private void writeAttributes(final PrintWriter writer)
    {
        writer.println("<a name=\"types\"><h2>Attributes</h2></a>");
        writer.println("<h2>Common Attributes</h2>");

        writeAttributesTable(writer, _model.getTypeRegistry().getAttributeTypes(getConfiguredClass()).values());

        for(Class<? extends ConfiguredObject> type : _types)
        {
            String typeName = getTypeName(type);
            Collection<ConfiguredObjectAttribute<?, ?>> typeSpecificAttributes =
                    _model.getTypeRegistry().getTypeSpecificAttributes(type);
            if(!typeSpecificAttributes.isEmpty())
            {
                writer.println("<h2><span class=\"type\">"+typeName+"</span> Specific Attributes</h2>");
                writeAttributesTable(writer, typeSpecificAttributes);
            }


        }

    }

    private void writeAttributesTable(final PrintWriter writer,
                                      final Collection<ConfiguredObjectAttribute<?, ?>> attributeTypes)
    {
        writer.println("<table class=\"attributes\">");
        writer.println("<thead>");
        writer.println("<tr><th class=\"name\">Attribute Name</th><th class=\"type\">Type</th><th class=\"description\">Description</th></tr>");
        writer.println("</thead>");
        writer.println("<tbody>");

        for(ConfiguredObjectAttribute attribute : attributeTypes)
        {
            writer.println("<tr><td class=\"name\">"
                           + attribute.getName()
                           + "</td><td class=\"type\">"
                           + renderType(attribute)
                           + "</td><td class=\"description\">"
                           + attribute.getDescription()
                           + "</td></tr>");
        }
        writer.println("</tbody>");

        writer.println("</table>");

    }

    private void writeOperations(final PrintWriter writer)
    {
        writer.println("<a name=\"types\"><h2>Operations</h2></a>");
        writer.println("<h2>Common Operations</h2>");

        Collection<ConfiguredObjectOperation<?>> categoryOperations =
                _model.getTypeRegistry().getOperations(getConfiguredClass()).values();
        writeOperationsTables(writer, categoryOperations);
        for(Class<? extends ConfiguredObject> type : _types)
        {
            String typeName = getTypeName(type);
            Set<ConfiguredObjectOperation<?>> typeSpecificOperations = new HashSet<>(_model.getTypeRegistry().getOperations(type).values());
            typeSpecificOperations.removeAll(categoryOperations);

            if(!typeSpecificOperations.isEmpty() && type != getConfiguredClass())
            {
                writer.println("<h2><span class=\"type\">"+typeName+"</span> Specific Operations</h2>");
                writeOperationsTables(writer, typeSpecificOperations);
            }
        }

    }

    private void writeOperationsTables(PrintWriter writer,
                                       Collection<ConfiguredObjectOperation<?>> operations)
    {
        for(ConfiguredObjectOperation<?> operation : operations)
        {
            writer.println("<table class=\"operation\">");
            writer.println("<thead>");
            writer.println("<tr><th class=\"name\">Operation Name</th><th class=\"returnType\">Return Type</th><th class=\"description\">Description</th></tr>");
            writer.println("</thead>");
            writer.println("<tbody>");

            writer.println("<tr><td class=\"name\">"
                           + operation.getName()
                           + "</td><td class=\"type\">"
                           + renderType(operation)
                           + "</td><td class=\"description\">"
                           + operation.getDescription()
                           + "</td></tr>");
            if (!operation.getParameters().isEmpty())
            {
                writer.println("<tr><td class=\"allparameters\" colspan=\"3\">"
                               + renderParameters(operation.getParameters())
                               + "</td></tr>");
            }

            writer.println("</tbody>");
            writer.println("</table>");
        }
    }

    private String renderParameters(final List<OperationParameter> parameters)
    {
        StringBuilder writer = new StringBuilder();

        writer.append("<table class=\"parameters\">");
        writer.append("<thead>");
        writer.append(
                "<tr><th class=\"name\">Parameter Name</th><th class=\"type\">Type</th><th class=\"description\">Description</th></tr>");
        writer.append("</thead>");
        writer.append("<tbody>");

        for (OperationParameter param : parameters)
        {
            writer.append("<tr><td class=\"name\">"
                          + param.getName()
                          + "</td><td class=\"type\">"
                          + renderType(param)
                          + "</td><td class=\"description\">"
                          + param.getDescription()
                          + "</td></tr>");
        }

        writer.append("</tbody>");
        writer.append("</table>");

        return writer.toString();
    }

    private String renderType(final ConfiguredObjectAttribute attribute)
    {
        final Class type = attribute.getType();

        Collection<String> validValues;
        String validValuePattern;

        if(attribute instanceof ConfiguredSettableAttribute)
        {
            ConfiguredSettableAttribute<?,?> settableAttribute = (ConfiguredSettableAttribute<?,?>) attribute;
            validValues = settableAttribute.hasValidValues() ? settableAttribute.validValues() : null;
            validValuePattern = settableAttribute.validValuePattern();
        }
        else
        {
            validValues = null;
            validValuePattern = "";
        }

        return renderType(type, validValues, validValuePattern);
    }

    private String renderType(final OperationParameter parameter)
    {
        final Class type = parameter.getType();
        List<String> validValues = parameter.getValidValues();
        return renderType(type, validValues, "");
    }

    private String renderType(Class type, Collection<String> validValues, final String validValuePattern) {
        if(Enum.class.isAssignableFrom(type))
        {
            return "<div class=\"restriction\" title=\"enum: " + EnumSet.allOf(type) + "\">string</div>";
        }
        else if(ConfiguredObject.class.isAssignableFrom(type))
        {
            return "<div class=\"restriction\" title=\"name or id of a" + (VOWELS.contains(type.getSimpleName().toLowerCase().charAt(0)) ? "n " : " ") + type.getSimpleName() + "\">string</div>";
        }
        else if(UUID.class == type)
        {
            return "<div class=\"restriction\" title=\"must be a UUID\">string</div>";
        }
        else
        {
            StringBuilder returnVal = new StringBuilder();
            final boolean hasValuesRestriction = validValues != null && !validValues.isEmpty();

            // TODO - valid values and patterns might contain characters which should be escaped
            if(hasValuesRestriction)
            {
                returnVal.append("<div class=\"restricted\" title=\"Valid values: " + validValues + "\">");
            }
            else if(validValuePattern != null && !"".equals(validValuePattern))
            {
                returnVal.append("<div class=\"restricted\" title=\"Valid value pattern: " + validValuePattern+ "\">");
            }

            if(Number.class.isAssignableFrom(type))
            {
                returnVal.append("number");
            }
            else if(Boolean.class == type)
            {
                returnVal.append("boolean");
            }
            else if(String.class == type)
            {
                returnVal.append("string");
            }
            else if(Collection.class.isAssignableFrom(type))
            {
                // TODO - generate a description of the type in the array
                returnVal.append("array");
            }
            else if(Map.class.isAssignableFrom(type))
            {
                // TODO - generate a description of the type in the object
                returnVal.append("object");
            }
            else
            {
                returnVal.append(type.getSimpleName());
            }
            if(hasValuesRestriction)
            {
                returnVal.append("</div>");
            }
            return returnVal.toString();
        }
    }

    private String renderType(final ConfiguredObjectOperation<?> operation)
    {
        return operation.getGenericReturnType() instanceof Class ?
                ((Class) operation.getGenericReturnType()).getName() :
                operation.getGenericReturnType().toString();
    }

    private void writeContext(final PrintWriter writer)
    {
        writer.println("<a name=\"types\"><h2>Context Variables</h2></a>");
        writer.println("<h2>Common Context Variables</h2>");

        Collection<ManagedContextDefault> defaultContexts =
                _model.getTypeRegistry().getContextDependencies(getConfiguredClass());
        writeContextDefaults(writer, defaultContexts);
        for(Class<? extends ConfiguredObject> type : _types)
        {
            String typeName = getTypeName(type);
            Collection<ManagedContextDefault> typeSpecificDefaults = _model.getTypeRegistry().getTypeSpecificContextDependencies(type);

            if(!typeSpecificDefaults.isEmpty() && type != getConfiguredClass())
            {
                writer.println("<h2><span class=\"type\">"+typeName+"</span> Specific Context Variables</h2>");
                writeContextDefaults(writer, typeSpecificDefaults);
            }
        }

    }

    private void writeContextDefaults(PrintWriter writer,
                                       Collection<ManagedContextDefault> contextDefaults)
    {
        writer.println("<table class=\"contextDefault\">");
        writer.println("<thead>");
        writer.println("<tr><th class=\"name\">Name</th><th class=\"description\">Description</th></tr>");
        writer.println("</thead>");
        writer.println("<tbody>");

        for(ManagedContextDefault contextDefault : contextDefaults)
        {

            writer.println("<tr><td class=\"name\">"
                           + contextDefault.name()
                           + "</td><td class=\"description\">"
                           + contextDefault.description()
                           + "</td></tr>");
        }
        writer.println("</tbody>");
        writer.println("</table>");

    }


    private void writeFoot(final PrintWriter writer)
    {
        writer.println("</body>");
        writer.println("</html>");
    }
    private Class<? extends ConfiguredObject> getConfiguredClass()
    {
        return _hierarchy.length == 0 ? Broker.class : _hierarchy[_hierarchy.length-1];
    }
}
