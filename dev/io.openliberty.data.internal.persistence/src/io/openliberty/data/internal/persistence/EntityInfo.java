/*******************************************************************************
 * Copyright (c) 2022,2024 IBM Corporation and others.
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
package io.openliberty.data.internal.persistence;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.CompletableFuture;

import com.ibm.websphere.ras.annotation.Trivial;

import jakarta.data.exceptions.MappingException;
import jakarta.persistence.Inheritance;

/**
 * Entity information
 */
public class EntityInfo {

    /**
     * Suffix for generated record class names. The name used for a generated
     * record entity class is: [RecordName][RECORD_ENTITY_SUFFIX]
     */
    public static final String RECORD_ENTITY_SUFFIX = "Entity";

    /**
     * Constant to use in place of an entity name to indicate that processing of
     * entity information has failed for an entity.
     */
    static final String FAILED = "ERROR!";

    // properly cased/qualified JPQL attribute name --> accessor methods or fields (multiple in the case of embeddable)
    final Map<String, List<Member>> attributeAccessors;

    // lower case attribute name --> properly cased/qualified JPQL attribute name
    final Map<String, String> attributeNames;

    // names of attributes to use for entity update.
    // excludes id and version.
    // excludes inner relation attributes, such as location.address when there is also a location.address.zipcode
    // TODO updates (and probably deletes) of entities with an embeddable id is not implemented yet.
    final SortedSet<String> attributeNamesForEntityUpdate;

    // properly cased/qualified JPQL attribute name --> type
    final SortedMap<String, Class<?>> attributeTypes;

    final EntityManagerBuilder builder;

    // properly cased/qualified JPQL attribute name --> type of collection
    final Map<String, Class<?>> collectionElementTypes;

    final Class<?> entityClass; // will be a generated class for entity records
    final Class<?> idType; // type of the id, which could be a JPA IdClass for composite ids
    final SortedMap<String, Member> idClassAttributeAccessors; // null if no IdClass
    final boolean inheritance;
    final String name; // entity name to use in query language. If a record, the name will be [RecordName]Entity.
    final Class<?> recordClass; // null if not a record
    final String versionAttributeName; // null if unversioned

    // embeddable class -> fully qualified attribute names of embeddable, or
    // one-to-one entity class -> fully qualified attribute names of one-to-one entity, or
    // many-to-one entity class -> fully qualified attribute names of many-to-one entity
    final Map<Class<?>, List<String>> relationAttributeNames;

    EntityInfo(String entityName,
               Class<?> entityClass,
               Class<?> recordClass,
               Map<String, List<Member>> attributeAccessors,
               Map<String, String> attributeNames,
               SortedSet<String> attributeNamesForUpdate,
               SortedMap<String, Class<?>> attributeTypes,
               Map<String, Class<?>> collectionElementTypes,
               Map<Class<?>, List<String>> relationAttributeNames,
               Class<?> idType,
               SortedMap<String, Member> idClassAttributeAccessors,
               String versionAttributeName,
               EntityManagerBuilder entityManagerBuilder) {
        this.name = entityName;
        this.builder = entityManagerBuilder;
        this.entityClass = entityClass;
        this.attributeAccessors = attributeAccessors;
        this.attributeNames = attributeNames;
        this.attributeNamesForEntityUpdate = attributeNamesForUpdate;
        this.attributeTypes = attributeTypes;
        this.collectionElementTypes = collectionElementTypes;
        this.relationAttributeNames = relationAttributeNames;
        this.idType = idType;
        this.idClassAttributeAccessors = idClassAttributeAccessors;
        this.recordClass = recordClass;
        this.versionAttributeName = versionAttributeName;

        inheritance = entityClass.getAnnotation(Inheritance.class) != null;

        validate();
    }

    /**
     * Obtains the value of an entity attribute.
     *
     * @param entity        the entity from which to obtain the value.
     * @param attributeName name of the entity attribute.
     * @return the value of the attribute.
     */
    Object getAttribute(Object entity, String attributeName) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        List<Member> accessors = attributeAccessors.get(attributeName);
        if (accessors == null)
            throw new IllegalArgumentException(attributeName); // should never occur

        Object value = entity;
        for (Member accessor : accessors) {
            Class<?> type = accessor.getDeclaringClass();
            if (type.isInstance(value)) {
                if (accessor instanceof Method)
                    value = ((Method) accessor).invoke(value);
                else // Field
                    value = ((Field) accessor).get(value);
            } else {
                throw new MappingException("Value of type " + value.getClass().getName() + " is incompatible with attribute type " + type.getName()); // TODO NLS
            }
        }

        return value;
    }

    Collection<String> getAttributeNames() {
        return attributeNames.values();
    }

    /**
     * Generates example method names for Query by Method Name using attribute names/types for this entity.
     *
     * @return list of example method names.
     */
    List<String> getExampleMethodNames() {
        List<String> examples = new ArrayList<>(5);
        String[] prefixes = { "find", "delete", "count", "exists" };
        String[] numSuffixes = { "LessThanEqual(max)", "Between(min, max)", "GreaterThan(exclusiveMin)", "NotIn(setOfValues)" };
        String[] strSuffixes = { "StartsWith(prefix)", "IgnoreCaseContains(pattern)", "EndsWith(suffix)", "NotLike(pattern)" };
        int b = 0, e = 0, n = 0, p = 0, s = 0;
        for (Map.Entry<String, Class<?>> attrClass : attributeTypes.entrySet()) {
            String attrName = attrClass.getKey();
            Class<?> attrType = attrClass.getValue();
            if (attrName.length() > 2
                && !attrName.toLowerCase().contains("version")
                && attrName.indexOf('.') < 0 && attrName.indexOf('_') < 0)
                if (CharSequence.class.isAssignableFrom(attrType))
                    examples.add(prefixes[p++] + "By" +
                                 Character.toUpperCase(attrName.charAt(0)) + attrName.substring(1) +
                                 strSuffixes[s++]);
                else if (boolean.class.equals(attrType) || Boolean.class.equals(attrType))
                    examples.add(prefixes[p++] + "By" +
                                 Character.toUpperCase(attrName.charAt(0)) + attrName.substring(1) +
                                 (b++ % 2 == 0 ? "False()" : "True()"));
                else if (attrType.isPrimitive()
                         || Number.class.isAssignableFrom(attrType)
                         || Temporal.class.isAssignableFrom(attrType))
                    examples.add(prefixes[p++] + "By" +
                                 Character.toUpperCase(attrName.charAt(0)) + attrName.substring(1) +
                                 numSuffixes[n++]);
                else if (attrType.isEnum())
                    examples.add(prefixes[p++] + "By" +
                                 Character.toUpperCase(attrName.charAt(0)) + attrName.substring(1) +
                                 (e++ % 2 == 0 ? "NotIn(setOfValues)" : "In(setOfValues)"));
            if (p >= 4)
                break;
        }
        if (p == 0) {
            examples.add("findById(id)");
            examples.add("deleteByIdNotIn(setOfValues)");
        }
        return examples;
    }

    /**
     * Entity class (non-generated) or entity record class.
     *
     * @return the entity class (non-generated) or entity record class.
     */
    @Trivial
    Class<?> getType() {
        return recordClass == null ? entityClass : recordClass;
    }

    /**
     * Creates a CompletableFuture to represent an EntityInfo in PersistenceDataProvider's entityInfoMap.
     *
     * @param entityClass
     * @return new CompletableFuture.
     */
    @Trivial
    public static CompletableFuture<EntityInfo> newFuture(Class<?> entityClass) {
        // It's okay to use Java SE's CompletableFuture here given that *Async methods are never invoked on it
        return new CompletableFuture<>();
    }

    /**
     * Converts a generated entity back to its record equivalent.
     *
     * @param entity generated entity.
     * @return record.
     * @throws Exception if an error occurs.
     */
    @Trivial
    final Object toRecord(Object entity) throws Exception {
        // TODO replace this method by including a toRecord method on an interface that is implemented
        // by the generated entity, then cast to the interface and invoke it to get the record.
        RecordComponent[] components = recordClass.getRecordComponents();
        Class<?>[] argTypes = new Class<?>[components.length];
        Object[] args = new Object[components.length];
        int a = 0;
        for (RecordComponent component : components) {
            PropertyDescriptor desc = new PropertyDescriptor(component.getName(), entity.getClass());
            argTypes[a] = component.getType();
            args[a++] = desc.getReadMethod().invoke(entity);
        }
        return recordClass.getConstructor(argTypes).newInstance(args);
    }

    @Override
    @Trivial
    public String toString() {
        return new StringBuilder("EntityInfo@").append(Integer.toHexString(hashCode())).append(' ') //
                        .append(name).append(' ') //
                        .append(attributeTypes.keySet()) //
                        .toString();
    }

    /**
     * Performs validation on the entity information, such as checking for
     * unsupportable entity attribute types.
     */
    @Trivial
    private void validate() {
        for (Class<?> attrType : attributeTypes.values())
            // ZonedDateTime is not one of the supported Temporal types
            // Jakarta Data and Jakarta Persistence and does not behave
            // correctly in EclipseLink where we have observed reading back
            // a different value from the database than was persisted.
            // If proper support is added for it in the future, then this
            // can be removed.
            if (ZonedDateTime.class.equals(attrType))
                throw new MappingException("The " + getType().getName() + " entity has an attribute of type " +
                                           ZonedDateTime.class.getName() + ", which is not supported."); // TODO NLS
    }
}
