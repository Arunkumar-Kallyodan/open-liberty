package com.ibm.ws.sib.msgstore.cache.links;
/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
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

public class ItemLinkStateUnlockingPersistentlyLocked implements ItemLinkState
{
    private static final ItemLinkStateUnlockingPersistentlyLocked _instance = new ItemLinkStateUnlockingPersistentlyLocked();

    private static final String _toString = "UnlockingPersistentlyLocked";

    static ItemLinkState instance()
    {
        return _instance;
    }

    /**
     * private constructor so state can only 
     * be accessed via instance method.
     */
    private ItemLinkStateUnlockingPersistentlyLocked() {}

    public String toString()
    {
        return _toString;
    }
}

