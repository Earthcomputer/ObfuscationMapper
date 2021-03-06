/*
 * The MIT License (MIT)
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.obfuscation.data;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.spongepowered.despector.util.Pair;
import org.spongepowered.obfuscation.config.ObfConfigManager;
import org.spongepowered.obfuscation.data.MappingsSet.MethodMapping;

import com.google.common.collect.Lists;

/**
 * Utilities for reading and writing SRG files.
 */
public final class MappingsIO {

    private static final String PACKAGE_LEADER = "PK: ";
    private static final String CLASS_LEADER = "CL: ";
    private static final String FIELD_LEADER = "FD: ";
    private static final String METHOD_LEADER = "MD: ";

    /**
     * Loads the given srg file and returns a {@link MappingsSet} containing the
     * mappings.
     */
    public static MappingsSet load(Path deobf_data) throws IOException {
        MappingsSet mappings = new MappingsSet();
        System.out.println("Loading mappings from " + deobf_data.toString());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(deobf_data.toFile())))) {
            String currentClass = null;
            String line = null;
            outer: while ((line = reader.readLine()) != null) {
                if (line.contains(":")) { // Normal srg
                    if (line.startsWith(PACKAGE_LEADER)) {
                        line = line.substring(PACKAGE_LEADER.length());
                        String[] data = line.split(" ");
                        mappings.addPackageMapping(data[0], data[1]);
                    } else if (line.startsWith(CLASS_LEADER)) {
                        line = line.substring(CLASS_LEADER.length());
                        String[] data = line.split(" ");
                        for (String ex : ObfConfigManager.getConfig().excluded_packages) {
                            if (data[1].startsWith(ex)) {
                                continue outer;
                            }
                        }
                        mappings.addTypeMapping(data[0], data[1]);
                    } else if (line.startsWith(FIELD_LEADER)) {
                        line = line.substring(FIELD_LEADER.length());
                        String[] data = line.split(" ");
                        for (String ex : ObfConfigManager.getConfig().excluded_packages) {
                            if (data[1].startsWith(ex)) {
                                continue outer;
                            }
                        }
                        String obf_name = data[0].substring(0, data[0].lastIndexOf('/'));
                        String obf_fld = data[0].substring(data[0].lastIndexOf('/') + 1, data[0].length());
                        String map_fld = data[1].substring(data[1].lastIndexOf('/') + 1, data[1].length());
                        mappings.addFieldMapping(obf_name, obf_fld, map_fld);
                    } else if (line.startsWith(METHOD_LEADER)) {
                        line = line.substring(METHOD_LEADER.length());
                        String[] data = line.split(" ");
                        for (String ex : ObfConfigManager.getConfig().excluded_packages) {
                            if (data[1].startsWith(ex)) {
                                continue outer;
                            }
                        }
                        String obf_name = data[0].substring(0, data[0].lastIndexOf('/'));
                        String obf_mth = data[0].substring(data[0].lastIndexOf('/') + 1, data[0].length());
                        String map_mth = data[2].substring(data[2].lastIndexOf('/') + 1, data[2].length());
                        mappings.addMethodMapping(obf_name, obf_mth, data[1], map_mth);
                    }
                } else { // compact srg (csrg) or tabbed srg (tsrg)
                    if (line.startsWith("\t")) {
                        line = currentClass + " " + line.substring(1);
                    }
                    String[] tokens = line.split(" ");
                    if (tokens.length == 2) {
                        String obfCls = tokens[0];
                        String mapCls = tokens[1];
                        if (obfCls.endsWith("/")) {
                            mappings.addPackageMapping(obfCls.substring(0, obfCls.length() - 1), mapCls.substring(0, mapCls.length() - 1));
                        } else {
                            mappings.addTypeMapping(obfCls, mapCls);
                            currentClass = obfCls;
                        }
                    } else if (tokens.length == 3) {
                        String obfCls = tokens[0];
                        String obfFld = tokens[1];
                        String mapFld = tokens[2];
                        mappings.addFieldMapping(obfCls, obfFld, mapFld);
                    } else {
                        String obfCls = tokens[0];
                        String obfMth = tokens[1];
                        String obfDsc = tokens[2];
                        String mapMth = tokens[3];
                        mappings.addMethodMapping(obfCls, obfMth, obfDsc, mapMth);
                    }
                }
            }
            System.out.println("Loaded " + mappings.packagesCount() + " packages");
            System.out.println("Loaded " + mappings.typeCount() + " classes");
            System.out.println("Loaded " + mappings.fieldCount() + " fields");
            System.out.println("Loaded " + mappings.methodCount() + " methods");
        }
        return mappings;
    }

    /**
     * Writes the given mappings set to the given file following the srg format.
     */
    public static void write(Path out, MappingsSet mappings, int next_member) throws IOException {
        Files.createDirectories(out.getParent());
        try (FileWriter writer = new FileWriter(out.toFile())) {
            // standard packages
            writer.write("PK: . net/minecraft/src\n"
                    + "PK: net net\n"
                    + "PK: net/minecraft net/minecraft\n"
                    + "PK: net/minecraft/client net/minecraft/client\n"
                    + "PK: net/minecraft/client/main net/minecraft/client/main\n"
                    + "PK: net/minecraft/realms net/minecraft/realms\n"
                    + "PK: net/minecraft/server net/minecraft/server\n");
            List<String> cls_keys = Lists.newArrayList(mappings.getMappedTypes());
            Collections.sort(cls_keys);
            for (String key : cls_keys) {
                writer.write("CL: " + key + " " + mappings.mapType(key) + "\n");
            }
            List<String> fd_keys = Lists.newArrayList(mappings.getMappedFields());
            Collections.sort(fd_keys);
            for (String key : fd_keys) {
                String owner = key.substring(0, key.lastIndexOf('/'));
                String obf_fld = key.substring(key.lastIndexOf('/') + 1, key.length());
                String mapped = mappings.mapType(owner);
                if (mapped == null) {
                    System.err.println("Skipping field mapping " + key + " owner not mapped");
                    continue;
                }
                writer.write("FD: " + key + " " + mapped + "/" + mappings.mapField(owner, obf_fld) + "\n");
            }

            List<String> mth_keys = Lists.newArrayList(mappings.getMappedMethods());
            Collections.sort(mth_keys);
            Set<String> seen_mth = new HashSet<>();
            for (String key : mth_keys) {
                Collection<MethodMapping> overloads = mappings.getMethods(key);
                for (MethodMapping overload : overloads) {
                    if (!overload.isMapped() && overload.getObf().startsWith("<")) {
                        overload.update(overload.getObf());
                    }
                    String mapped_owner = mappings.mapType(overload.getObfOwner());
                    if (mapped_owner == null) {
                        continue;
                    }
                    String mapped_key = mapped_owner + "/" + overload.getMapped() + " " + overload.getMappedSignature();
                    if (seen_mth.contains(mapped_key)) {
                        mapped_key = mapped_owner + "/mth_" + next_member + "_" + overload.getObf() + " " + overload.getMappedSignature();
                        next_member++;
                    }
                    seen_mth.add(mapped_key);
                    writer.write("MD: " + key + " " + overload.getObfSignature() + " " + mapped_key + "\n");
                }
            }
        }
    }
    
    public static void writeCsrg(Path out, MappingsSet mappings, int nextMember, boolean tabbed) throws IOException {
        Files.createDirectories(out.getParent());
        try (FileWriter writer = new FileWriter(out.toFile())) {
            List<String> clsKeys = Lists.newArrayList(mappings.getMappedTypes());
            Collections.sort(clsKeys);
            Map<String, List<String>> fieldsByClass = new HashMap<>();
            for (String field : mappings.getMappedFields()) {
                int slashIdx = field.lastIndexOf('/');
                String owner = field.substring(0, slashIdx);
                String name = field.substring(slashIdx + 1);
                List<String> fields = fieldsByClass.get(owner);
                if (fields == null) {
                    fields = Lists.newArrayList();
                    fieldsByClass.put(owner, fields);
                }
                fields.add(name);
            }
            Map<String, List<Pair<String, String>>> methodsByClass = new HashMap<>();
            for (String key : mappings.getMappedMethods()) {
                Collection<MethodMapping> overloads = mappings.getMethods(key);
                for (MethodMapping overload : overloads) {
                    if (!overload.isMapped() && overload.getObf().startsWith("<")) {
                        overload.update(overload.getObf());
                    }
                    int slashIdx = key.lastIndexOf('/');
                    String owner = key.substring(0, slashIdx);
                    String name = key.substring(slashIdx + 1);
                    String desc = overload.getObfSignature();
                    List<Pair<String, String>> methods = methodsByClass.get(owner);
                    if (methods == null) {
                        methods = Lists.newArrayList();
                        methodsByClass.put(owner, methods);
                    }
                    methods.add(new Pair<>(name, desc));
                }
            }
            for (String clsKey : clsKeys) {
                writer.write(clsKey + " " + mappings.mapType(clsKey) + "\n");
                List<String> fields = fieldsByClass.get(clsKey);
                if (fields != null) {
                    Collections.sort(fields);
                    for (String field : fields) {
                        if (tabbed) {
                            writer.write("\t" + field + " " + mappings.mapField(clsKey, field) + "\n");
                        } else {
                            writer.write(clsKey + " " + field + " " + mappings.mapField(clsKey, field) + "\n");
                        }
                    }
                }
                List<Pair<String, String>> methods = methodsByClass.get(clsKey);
                if (methods != null) {
                    methods.sort(Comparator.<Pair<String, String>, String>comparing(Pair::getFirst).thenComparing(Pair::getSecond));
                    for (Pair<String, String> method : methods) {
                        String mapped = mappings.getMethodMapping(clsKey, method.getFirst(), method.getSecond()).getMapped();
                        if (tabbed) {
                            writer.write("\t" + method.getFirst() + " " + method.getSecond() + " " + mapped + "\n");
                        } else {
                            writer.write(clsKey + " " + method.getFirst() + " " + method.getSecond() + " " + mapped + "\n");
                        }
                    }
                }
            }
        }
    }

    private MappingsIO() {
    }

}
