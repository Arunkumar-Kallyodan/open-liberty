/*******************************************************************************
 * Copyright (c) 2002, 2019 IBM Corporation and others.
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

package com.ibm.ejb2x.base.spec.sll.ejb;

/**
 * Remote interface for Enterprise Bean: SLLTestReentrance
 */
public interface SLLTestReentrance extends javax.ejb.EJBLocalObject {
    /**
     * Call self recursively n times
     *
     * @return number of recursive call
     */
    public int callRecursiveSelf(int level, SLLTestReentrance ejb1) throws SLLApplException;

    /**
     * Call self recursively to cause an exception
     */
    public int callNonRecursiveSelf(int level, SLLTestReentrance ejb1) throws SLLApplException;
}
