/*
 * IBM Confidential
 * OCO Source Materials
 *
 * 5737-H33
 *
 * (C) Copyright IBM Corp. 2019  All Rights Reserved.
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has been
 * deposited with the U.S. Copyright Office.
 */
package com.ibm.eventstreams.api.spec;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.sundr.builder.annotations.Buildable;
import lombok.EqualsAndHashCode;

@JsonDeserialize(using = JsonDeserializer.None.class)
@Buildable(editableEnabled = false, generateBuilderPackage = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"pattern", "name", "value", "valueFactor", "help", "attrNameSnakeCase", "type", "labelNames", "labelValues"})
@EqualsAndHashCode
public class KafkaMetricsJMXRule implements Serializable {
    private static final long serialVersionUID = 1L;
    private Pattern pattern;
    private String name;
    private String value;
    private Double valueFactor;
    private String help;
    private boolean attrNameSnakeCase;
    private Type type;
    private ArrayList<String> labelNames;
    private ArrayList<String> labelValues;

    public enum Type {
        UNTYPED,
        GUADE,
        COUNTER
    }

    /**
     * @return Pattern return the pattern
     */
    public Pattern getPattern() {
        return pattern;
    }

    /**
     * @param pattern the pattern to set
     */
    public void setPattern(Pattern pattern) {
        this.pattern = pattern;
    }

    /**
     * @return String return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return String return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * @param value the value to set
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * @return Double return the valueFactor
     */
    public Double getValueFactor() {
        return valueFactor;
    }

    /**
     * @param valueFactor the valueFactor to set
     */
    public void setValueFactor(Double valueFactor) {
        this.valueFactor = valueFactor;
    }

    /**
     * @return String return the help
     */
    public String getHelp() {
        return help;
    }

    /**
     * @param help the help to set
     */
    public void setHelp(String help) {
        this.help = help;
    }

    /**
     * @return boolean return the attrNameSnakeCase
     */
    public boolean isAttrNameSnakeCase() {
        return attrNameSnakeCase;
    }

    /**
     * @param attrNameSnakeCase the attrNameSnakeCase to set
     */
    public void setAttrNameSnakeCase(boolean attrNameSnakeCase) {
        this.attrNameSnakeCase = attrNameSnakeCase;
    }

    /**
     * @return Type return the type
     */
    public Type getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * @return ArrayList<String> return the labelNames
     */
    public ArrayList<String> getLabelNames() {
        return labelNames;
    }

    /**
     * @param labelNames the labelNames to set
     */
    public void setLabelNames(ArrayList<String> labelNames) {
        this.labelNames = labelNames;
    }

    /**
     * @return ArrayList<String> return the labelValues
     */
    public ArrayList<String> getLabelValues() {
        return labelValues;
    }

    /**
     * @param labelValues the labelValues to set
     */
    public void setLabelValues(ArrayList<String> labelValues) {
        this.labelValues = labelValues;
    }

    @Override
    public String toString() {
        YAMLMapper mapper = new YAMLMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
