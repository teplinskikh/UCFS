/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.search.lookup;

import org.elasticsearch.index.mapper.MappedFieldType;

import java.util.ArrayList;
import java.util.List;

public class FieldLookup {

    private final MappedFieldType fieldType;
    private final List<Object> values = new ArrayList<>();
    private boolean valuesLoaded = false;

    public FieldLookup(MappedFieldType fieldType) {
        this.fieldType = fieldType;
    }

    public MappedFieldType fieldType() {
        return fieldType;
    }

    /**
     * Sets the post processed values.
     */
    public void setValues(List<Object> values) {
        assert valuesLoaded == false : "Call clear() before calling setValues()";
        if (values != null) {
            values.stream().map(fieldType::valueForDisplay).forEach(this.values::add);
        }
        this.valuesLoaded = true;
    }

    public boolean isLoaded() {
        return valuesLoaded;
    }

    public void clear() {
        values.clear();
        valuesLoaded = false;
    }

    public List<Object> getValues() {
        assert valuesLoaded;
        return values;
    }

    public Object getValue() {
        assert valuesLoaded;
        return values.isEmpty() ? null : values.get(0);
    }

    public boolean isEmpty() {
        assert valuesLoaded;
        return values.isEmpty();
    }
}