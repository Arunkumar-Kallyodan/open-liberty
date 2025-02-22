/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation and others.
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
package io.openliberty.microprofile.openapi20.internal.validation;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.models.media.XML;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;

import io.openliberty.microprofile.openapi20.internal.utils.OpenAPIModelWalker.Context;
import io.openliberty.microprofile.openapi20.internal.services.OASValidationResult.ValidationEvent;
import io.openliberty.microprofile.openapi20.internal.utils.ValidationMessageConstants;

/**
 *
 */
public class XMLValidator extends TypeValidator<XML> {

    private static final TraceComponent tc = Tr.register(XMLValidator.class);

    private static final XMLValidator INSTANCE = new XMLValidator();

    public static XMLValidator getInstance() {
        return INSTANCE;
    }

    private XMLValidator() {}

    /** {@inheritDoc} */
    @Override
    public void validate(ValidationHelper helper, Context context, String key, XML t) {
        if (t != null) {
            if (StringUtils.isNotBlank(t.getNamespace())) {
                String namespace = t.getNamespace();
                if (!ValidatorUtils.isValidURI(namespace)) {
                    final String message = Tr.formatMessage(tc, ValidationMessageConstants.INVALID_URI, namespace);
                    helper.addValidationEvent(new ValidationEvent(ValidationEvent.Severity.ERROR, context.getLocation(), message));
                }
            }
        }
    }
}
