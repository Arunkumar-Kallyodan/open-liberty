/*******************************************************************************
 * Copyright (c) 2014, 2018 IBM Corporation and others.
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

package com.ibm.ws.security.authentication.filter.internal;

public class ContainsCondition extends SimpleCondition {

    /**
     *
     */
    public ContainsCondition(String key, IValue value, String operand, boolean noAttrValue) {
        super(key, value, operand, noAttrValue);
    }

    @Override
    public boolean checkCondition(IValue test) throws FilterException {

        //does the input string contain the value
        return getValue().containedBy(test);
    }

    @Override
    public String getOperand() {
        return operand;
    }
}
