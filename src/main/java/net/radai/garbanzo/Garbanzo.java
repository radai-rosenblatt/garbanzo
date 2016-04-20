/*
 * Copyright (c) 2016 Radai Rosenblatt.
 * This file is part of Garbanzo.
 *
 *  Garbanzo is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Garbanzo is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Garbanzo.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.radai.garbanzo;

import net.radai.beanz.Beanz;
import net.radai.beanz.api.*;
import net.radai.beanz.util.ReflectionUtil;
import net.radai.garbanzo.annotations.IniDocumentation;
import net.radai.garbanzo.util.Inflection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ini4j.Config;
import org.ini4j.Ini;
import org.ini4j.Profile;
import org.ini4j.spi.IniBuilder;
import org.ini4j.spi.IniParser;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

/**
 * Created by Radai Rosenblatt
 */
public class Garbanzo {
    private static final Logger log = LogManager.getLogger(Garbanzo.class);

    public static <T> String marshal(T beanInstance) {
        Config iniConfig = buildIniConfig();
        Ini ini = new Ini();
        ini.setConfig(iniConfig);
        Profile.Section defaultSection = ini.add(iniConfig.getGlobalSectionName());

        Bean<T> bean = Beanz.wrap(beanInstance);

        IniDocumentation docAnnotation = bean.getAnnotation(IniDocumentation.class);
        if (docAnnotation != null) {
            ini.putComment(iniConfig.getGlobalSectionName(), docAnnotation.value());
        }

        for (Map.Entry<String, Property> propEntry : bean.getProperties().entrySet()) {
            String propName = propEntry.getKey();
            Property prop = propEntry.getValue();
            net.radai.beanz.api.Codec codec = prop.getCodec();
            docAnnotation = prop.getAnnotation(IniDocumentation.class);
            String comment = docAnnotation != null ? docAnnotation.value() : null;
            String singular;
            //TODO - differentiate between nulls and empty sets
            switch (prop.getType()) {
                case SIMPLE:
                    if (codec != null) {
                        //prop --> string
                        String stringValue = prop.getAsString();
                        if (stringValue != null) {
                            defaultSection.put(propName, stringValue);
                            if (comment != null) {
                                defaultSection.putComment(propName, comment);
                            }
                        }
                    } else {
                        //prop --> section
                        Object rawValue = prop.get();
                        if (rawValue != null) {
                            Bean<?> innerBean = Beanz.wrap(rawValue);
                            Profile.Section targetSection = ini.add(propName);
                            if (comment == null) { //if no comment on the field maybe there's one on the value type
                                docAnnotation = innerBean.getAnnotation(IniDocumentation.class);
                                if (docAnnotation != null) {
                                    comment = docAnnotation.value();
                                }
                            }
                            if (comment != null) {
                                ini.putComment(propName, comment);
                            }
                            serializeToSection(innerBean, targetSection);
                        }
                    }
                    break;
                case ARRAY:
                    ArrayProperty arrayProp = (ArrayProperty) prop;
                    singular = Inflection.singularize(propName);
                    if (codec != null) {
                        //prop --> multi value (potentially under singular name)
                        List<String> stringValues = arrayProp.getAsStrings();
                        if (stringValues != null) {
                            defaultSection.putAll(singular, stringValues);
                            if (comment != null) {
                                defaultSection.putComment(propName, comment);
                            }
                        }
                    } else {
                        //prop --> multi section (potentially under singular name)
                        serializeToSections(ini, arrayProp.getAsList(), singular, comment);
                    }
                    break;
                case COLLECTION:
                    CollectionProperty collectionProp = (CollectionProperty) prop;
                    singular = Inflection.singularize(propName);
                    if (codec != null) {
                        //prop --> multi value (potentially under singular name)
                        Collection<String> asStrings = collectionProp.getAsStrings();
                        if (asStrings != null) {
                            defaultSection.putAll(singular, new ArrayList<>(asStrings)); //orig might be a set
                            if (comment != null) {
                                defaultSection.putComment(propName, comment);
                            }
                        }
                    } else {
                        //prop --> multi section (potentially under singular name)
                        serializeToSections(ini, collectionProp.getCollection(), singular, comment);
                    }
                    break;
                case MAP:
                    MapProperty mapProp = (MapProperty) prop;
                    if (codec != null) {
                        //prop --> section
                        Map<String, String> asStrings = mapProp.getAsStrings();
                        if (asStrings != null) {
                            Profile.Section targetSection = ini.add(propName);
                            targetSection.putAll(asStrings);
                            if (comment != null) {
                                ini.putComment(propName, comment);
                            }
                        }
                    } else {
                        throw new UnsupportedOperationException(); //TODO - figure out how to map Map<String, ComplexBean> ?
                    }
                    break;
                default:
                    throw new IllegalStateException("unhandled: " + prop.getType());
            }
        }

        try (StringWriter writer = new StringWriter()) {
            ini.store(writer);
            return writer.toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T unmarshall(Class<T> beanClass, String from) {
        if (from == null) {
            return null;
        }
        Ini ini = readIni(from);
        String globalSectionName = ini.getConfig().getGlobalSectionName();
        Bean<T> bean = Beanz.create(beanClass);
        Set<String> sectionNames = ini.keySet();
        for (String sectionName : sectionNames) {
            List<Profile.Section> sectionInstances = ini.getAll(sectionName);
            if (sectionInstances == null || sectionInstances.isEmpty()) {
                throw new IllegalStateException();
            }
            if (sectionName.equals(globalSectionName)) {
                //global section == top-level fields == properties of the top level class
                if (sectionInstances.size() != 1) {
                    throw new IllegalStateException();
                }
                populate(bean, sectionInstances.get(0));
            } else {
                Property property;
                property = bean.getProperty(sectionName);
                if (property == null) {
                    String pluralName = Inflection.pluralize(sectionName); //if section is "dog" maybe there's a prop "dogs"
                    property = bean.getProperty(pluralName);
                }
                if (property == null) {
                    throw new IllegalArgumentException();
                }
                populateFromSections(property, sectionInstances);
            }
        }
        return bean.getBean();
    }

    private static void populate(Bean what, Profile.Section from) {
        Set<String> keys = from.keySet();
        for (String key : keys) {
            List<String> values = from.getAll(key);
            if (values == null || values.isEmpty()) {
                throw new IllegalStateException();
            }
            Property property;
            property = what.getProperty(key);
            if (property != null) {
                populateFromStrings(property, values);
            } else {
                //could not find prop "bob". look for a list/array prop called "bobs" maybe
                String pluralPropName = Inflection.pluralize(key);
                property = what.getProperty(pluralPropName);
                if (property == null) {
                    throw new IllegalArgumentException("cannot find mapping for key " + from.getSimpleName() + "." + key);
                }
                populateFromStrings(property, values);
            }
        }
    }

    private static void populateFromStrings(Property property, List<String> values) {
        PropertyType propertyType = property.getType();
        switch (propertyType) {
            case SIMPLE:
                if (values.size() != 1) {
                    throw new IllegalArgumentException();
                }
                property.setFromString(values.get(0));
                break;
            case ARRAY:
                ((ArrayProperty)property).setFromStrings(values);
                break;
            case COLLECTION:
                ((CollectionProperty)property).setFromStrings(values);
                break;
            case MAP:
                throw new IllegalArgumentException();
            default:
                throw new UnsupportedOperationException("unhandled " + propertyType);
        }
    }

    private static void populateFromSections(Property property, List<Profile.Section> from) {
        PropertyType propertyType = property.getType();
        Class<?> beanClass;
        switch (propertyType) {
            case SIMPLE:
                if (from.size() != 1) {
                    throw new IllegalArgumentException();
                }
                beanClass = ReflectionUtil.erase(property.getValueType());
                Bean elementPod = Beanz.create(beanClass);
                populate(elementPod, from.get(0)); //empty section here translates into "empty object". a null object for a simple prop would just be missing
                property.set(elementPod.getBean());
                break;
            case ARRAY:
                ArrayProperty arrayProperty = (ArrayProperty) property;
                beanClass = ReflectionUtil.erase(arrayProperty.getElementType());
                arrayProperty.setArray(deserializeBeanCollection(beanClass, from));
                break;
            case COLLECTION:
                CollectionProperty collectionProperty = (CollectionProperty) property;
                beanClass = ReflectionUtil.erase(collectionProperty.getElementType());
                collectionProperty.setCollection(deserializeBeanCollection(beanClass, from));
                break;
            case MAP:
                if (from.size() != 1) {
                    throw new IllegalArgumentException();
                }
                MapProperty mapProperty = (MapProperty) property;
                Map<String, String> strMap = toMap(from.get(0)); //empty section turns to empty map. null map would be the section missing entirely
                mapProperty.setFromStrings(strMap);
                break;
            default:
                throw new UnsupportedOperationException("unhandled " + propertyType);
        }
    }

    private static Collection<Object> deserializeBeanCollection(Class<?> beanClass, List<Profile.Section> from) {
        Collection<Object> values;
        Bean elementPod;
        values = new ArrayList<>(from.size());
        for (Profile.Section section : from) {
            if (section.isEmpty()) {
                values.add(null); //empty sections in lists/arrays turn to nulls
            } else {
                elementPod = Beanz.create(beanClass);
                populate(elementPod, section);
                values.add(elementPod.getBean());
            }
        }
        return values;
    }

    private static void serializeToSections(Ini ini, Iterable<?> beans, String propName, String comment) {
        if (beans != null) {
            for (Object rawValue : beans) {
                Profile.Section targetSection = ini.add(propName);
                if (rawValue != null) { //otherwise its an empty section
                    Bean innerBean = Beanz.wrap(rawValue);
                    serializeToSection(innerBean, targetSection);
                    if (targetSection.isEmpty()) { //ambiguous
                        log.warn("non-null object {} was serialized into an empty section, which would be deserialized into null", rawValue);
                    }
                }
                if (comment != null) {
                    ini.putComment(propName, comment);
                }
            }
        }
    }

    private static void serializeToSection(Bean<?> bean, Profile.Section section) {
        for (Map.Entry<String, Property> propEntry : bean.getProperties().entrySet()) {
            String propName = propEntry.getKey();
            Property prop = propEntry.getValue();
            net.radai.beanz.api.Codec codec = prop.getCodec();
            if (codec == null) {
                throw new UnsupportedOperationException(); //ini does not support nested sections
            }
            IniDocumentation docAnnotation = prop.getAnnotation(IniDocumentation.class);
            String comment = docAnnotation != null ? docAnnotation.value() : null;
            String singular;
            boolean written = false;
            switch (prop.getType()) {
                case SIMPLE:
                    //prop --> string
                    String stringValue = prop.getAsString();
                    if (stringValue != null) {
                        section.put(propName, stringValue);
                        written = true;
                    }
                    break;
                case ARRAY:
                    //prop --> multi value (potentially under singular name)
                    ArrayProperty arrayProp = (ArrayProperty) prop;
                    singular = Inflection.singularize(propName);
                    List<String> stringValues = arrayProp.getAsStrings();
                    if (stringValues != null) {
                        section.putAll(singular, stringValues);
                        written = true;
                    }
                    break;
                case COLLECTION:
                    //prop --> multi value (potentially under singular name)
                    CollectionProperty collectionProp = (CollectionProperty) prop;
                    singular = Inflection.singularize(propName);
                    Collection<String> asStrings = collectionProp.getAsStrings();
                    if (asStrings != null) {
                        section.putAll(singular, new ArrayList<>(asStrings)); //turn into a list (orig might be a set)
                        written = true;
                    }
                    break;
                case MAP:
                    throw new UnsupportedOperationException("unable to serialize "
                            + prop + " because the INI format does not support nested sections");
                default:
                    throw new IllegalStateException("unhandled: " + prop.getType());
            }

            if (written && comment != null) {
                section.putComment(propName, comment);
            }
        }
    }

    private static Map<String, String> toMap(Profile.Section from) {
        Map<String, String> result = new HashMap<>();
        for (String key : from.keySet()) {
            List<String> values = from.getAll(key);
            if (values.size() != 1) {
                throw new IllegalArgumentException();
            }
            result.put(key, values.get(0));
        }
        return result;
    }

    private static Ini readIni(String from) {
        Config iniConfig = buildIniConfig();
        IniParser parser = IniParser.newInstance(iniConfig);
        Ini ini = new Ini();
        ini.setConfig(iniConfig);
        IniBuilder iniBuilder = IniBuilder.newInstance(ini);
        try {
            parser.parse(new StringReader(from), iniBuilder);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return ini;
    }

    private static Config buildIniConfig() {
        Config iniConfig = new Config();
        iniConfig.setMultiSection(true);
        iniConfig.setMultiOption(true);
        iniConfig.setGlobalSection(true);
        iniConfig.setEmptyOption(true);
        iniConfig.setEmptySection(true);
        return iniConfig;
    }
}
