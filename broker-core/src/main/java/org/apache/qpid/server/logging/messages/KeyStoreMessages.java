/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.server.logging.messages;

import static org.apache.qpid.server.logging.AbstractMessageLogger.DEFAULT_LOG_HIERARCHY_PREFIX;

import org.slf4j.LoggerFactory;

import org.apache.qpid.server.logging.LogMessage;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * DO NOT EDIT DIRECTLY, THIS FILE WAS GENERATED.
 *
 * Generated using GenerateLogMessages and LogMessages.vm
 * This file is based on the content of KeyStore_logmessages.properties
 *
 * To regenerate, edit the templates/properties and run the build with -Dgenerate=true
 */
public class KeyStoreMessages
{
    private static ResourceBundle _messages;
    private static Locale _currentLocale;

    static
    {
        Locale locale = Locale.US;
        String localeSetting = System.getProperty("qpid.broker_locale");
        if (localeSetting != null)
        {
            String[] localeParts = localeSetting.split("_");
            String language = (localeParts.length > 0 ? localeParts[0] : "");
            String country = (localeParts.length > 1 ? localeParts[1] : "");
            String variant = "";
            if (localeParts.length > 2)
            {
                variant = localeSetting.substring(language.length() + 1 + country.length() + 1);
            }
            locale = new Locale(language, country, variant);
        }
        _currentLocale = locale;
    }

    public static final String KEYSTORE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "keystore";
    public static final String OPEN_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "keystore.open";
    public static final String EXPIRING_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "keystore.expiring";
    public static final String CREATE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "keystore.create";
    public static final String DELETE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "keystore.delete";
    public static final String CLOSE_LOG_HIERARCHY = DEFAULT_LOG_HIERARCHY_PREFIX + "keystore.close";

    static
    {
        LoggerFactory.getLogger(KEYSTORE_LOG_HIERARCHY);
        LoggerFactory.getLogger(OPEN_LOG_HIERARCHY);
        LoggerFactory.getLogger(EXPIRING_LOG_HIERARCHY);
        LoggerFactory.getLogger(CREATE_LOG_HIERARCHY);
        LoggerFactory.getLogger(DELETE_LOG_HIERARCHY);
        LoggerFactory.getLogger(CLOSE_LOG_HIERARCHY);

        _messages = ResourceBundle.getBundle("org.apache.qpid.server.logging.messages.KeyStore_logmessages", _currentLocale);
    }

    /**
     * Log a KeyStore message of the Format:
     * <pre>KST-1002 : Open</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage OPEN()
    {
        String rawMessage = _messages.getString("OPEN");

        final String message = rawMessage;

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return OPEN_LOG_HIERARCHY;
            }

            @Override
            public boolean equals(final Object o)
            {
                if (this == o)
                {
                    return true;
                }
                if (o == null || getClass() != o.getClass())
                {
                    return false;
                }

                final LogMessage that = (LogMessage) o;

                return getLogHierarchy().equals(that.getLogHierarchy()) && toString().equals(that.toString());

            }

            @Override
            public int hashCode()
            {
                int result = toString().hashCode();
                result = 31 * result + getLogHierarchy().hashCode();
                return result;
            }
        };
    }

    /**
     * Log a KeyStore message of the Format:
     * <pre>KST-1005 : KeyStore {0} Certificate expires in {1} days : {2}</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage EXPIRING(String param1, String param2, String param3)
    {
        String rawMessage = _messages.getString("EXPIRING");

        final Object[] messageArguments = {param1, param2, param3};
        // Create a new MessageFormat to ensure thread safety.
        // Sharing a MessageFormat and using applyPattern is not thread safe
        MessageFormat formatter = new MessageFormat(rawMessage, _currentLocale);

        final String message = formatter.format(messageArguments);

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return EXPIRING_LOG_HIERARCHY;
            }

            @Override
            public boolean equals(final Object o)
            {
                if (this == o)
                {
                    return true;
                }
                if (o == null || getClass() != o.getClass())
                {
                    return false;
                }

                final LogMessage that = (LogMessage) o;

                return getLogHierarchy().equals(that.getLogHierarchy()) && toString().equals(that.toString());

            }

            @Override
            public int hashCode()
            {
                int result = toString().hashCode();
                result = 31 * result + getLogHierarchy().hashCode();
                return result;
            }
        };
    }

    /**
     * Log a KeyStore message of the Format:
     * <pre>KST-1001 : Create "{0}"</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage CREATE(String param1)
    {
        String rawMessage = _messages.getString("CREATE");

        final Object[] messageArguments = {param1};
        // Create a new MessageFormat to ensure thread safety.
        // Sharing a MessageFormat and using applyPattern is not thread safe
        MessageFormat formatter = new MessageFormat(rawMessage, _currentLocale);

        final String message = formatter.format(messageArguments);

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return CREATE_LOG_HIERARCHY;
            }

            @Override
            public boolean equals(final Object o)
            {
                if (this == o)
                {
                    return true;
                }
                if (o == null || getClass() != o.getClass())
                {
                    return false;
                }

                final LogMessage that = (LogMessage) o;

                return getLogHierarchy().equals(that.getLogHierarchy()) && toString().equals(that.toString());

            }

            @Override
            public int hashCode()
            {
                int result = toString().hashCode();
                result = 31 * result + getLogHierarchy().hashCode();
                return result;
            }
        };
    }

    /**
     * Log a KeyStore message of the Format:
     * <pre>KST-1004 : Delete "{0}"</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage DELETE(String param1)
    {
        String rawMessage = _messages.getString("DELETE");

        final Object[] messageArguments = {param1};
        // Create a new MessageFormat to ensure thread safety.
        // Sharing a MessageFormat and using applyPattern is not thread safe
        MessageFormat formatter = new MessageFormat(rawMessage, _currentLocale);

        final String message = formatter.format(messageArguments);

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return DELETE_LOG_HIERARCHY;
            }

            @Override
            public boolean equals(final Object o)
            {
                if (this == o)
                {
                    return true;
                }
                if (o == null || getClass() != o.getClass())
                {
                    return false;
                }

                final LogMessage that = (LogMessage) o;

                return getLogHierarchy().equals(that.getLogHierarchy()) && toString().equals(that.toString());

            }

            @Override
            public int hashCode()
            {
                int result = toString().hashCode();
                result = 31 * result + getLogHierarchy().hashCode();
                return result;
            }
        };
    }

    /**
     * Log a KeyStore message of the Format:
     * <pre>KST-1003 : Close</pre>
     * Optional values are contained in [square brackets] and are numbered
     * sequentially in the method call.
     *
     */
    public static LogMessage CLOSE()
    {
        String rawMessage = _messages.getString("CLOSE");

        final String message = rawMessage;

        return new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return CLOSE_LOG_HIERARCHY;
            }

            @Override
            public boolean equals(final Object o)
            {
                if (this == o)
                {
                    return true;
                }
                if (o == null || getClass() != o.getClass())
                {
                    return false;
                }

                final LogMessage that = (LogMessage) o;

                return getLogHierarchy().equals(that.getLogHierarchy()) && toString().equals(that.toString());

            }

            @Override
            public int hashCode()
            {
                int result = toString().hashCode();
                result = 31 * result + getLogHierarchy().hashCode();
                return result;
            }
        };
    }


    private KeyStoreMessages()
    {
    }

}
