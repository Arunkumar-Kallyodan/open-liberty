/*******************************************************************************
 * Copyright (c) 2017,2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
// NOTE: This is a generated file. Do not edit it directly.
package com.ibm.ws.javaee.ddmodel.commonbnd;

import com.ibm.ws.javaee.ddmodel.DDParser;

public class MessageDestinationRefType extends com.ibm.ws.javaee.ddmodel.DDParser.ElementContentParsable implements com.ibm.ws.javaee.dd.commonbnd.MessageDestinationRef {
    public MessageDestinationRefType() {
        this(false);
    }

    public MessageDestinationRefType(boolean xmi) {
        this.xmi = xmi;
    }

    protected final boolean xmi;
    com.ibm.ws.javaee.ddmodel.StringType name;
    private com.ibm.ws.javaee.ddmodel.CrossComponentReferenceType bindingMessageDestinationRef;
    com.ibm.ws.javaee.ddmodel.StringType binding_name;

    @Override
    public java.lang.String getName() {
        return name != null ? name.getValue() : null;
    }

    @Override
    public java.lang.String getBindingName() {
        return binding_name != null ? binding_name.getValue() : null;
    }

    @Override
    public boolean isIdAllowed() {
        return true;
    }

    @Override
    public boolean handleAttribute(DDParser parser, String nsURI, String localName, int index) throws DDParser.ParseException {
        if (nsURI == null) {
            if (!xmi && "name".equals(localName)) {
                this.name = parser.parseStringAttributeValue(index);
                return true;
            }
            if ((xmi ? "jndiName" : "binding-name").equals(localName)) {
                this.binding_name = parser.parseStringAttributeValue(index);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handleChild(DDParser parser, String localName) throws DDParser.ParseException {
        if (xmi && "bindingMessageDestinationRef".equals(localName)) {
            this.bindingMessageDestinationRef = new com.ibm.ws.javaee.ddmodel.CrossComponentReferenceType("bindingMessageDestinationRef", parser.getCrossComponentType());
            parser.parse(bindingMessageDestinationRef);
            com.ibm.ws.javaee.dd.common.MessageDestinationRef referent = this.bindingMessageDestinationRef.resolveReferent(parser, com.ibm.ws.javaee.dd.common.MessageDestinationRef.class);
            if (referent != null) {
                this.name = parser.parseString(referent.getName());
            }
            return true;
        }
        return false;
    }

    @Override
    public void describe(com.ibm.ws.javaee.ddmodel.DDParser.Diagnostics diag) {
        if (xmi) {
            diag.describeIfSet("bindingMessageDestinationRef", bindingMessageDestinationRef);
        } else {
            diag.describeIfSet("name", name);
        }
        diag.describeIfSet(xmi ? "jndiName" : "binding-name", binding_name);
    }
}
