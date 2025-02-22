/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
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
package com.ibm.ws.jaxrs.fat.exceptionmappers.mapped;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class GuestbookExceptionMapProvider implements ExceptionMapper<GuestbookException> {

    @Override
    public Response toResponse(GuestbookException arg0) {
        CommentError error = new CommentError();
        error.setErrorMessage(arg0.getMessage());
        return Response.status(454).entity(error).type("application/xml").build();
    }

}
